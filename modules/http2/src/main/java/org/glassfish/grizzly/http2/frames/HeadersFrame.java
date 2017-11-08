/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.http2.frames;

import java.util.HashMap;
import java.util.Map;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

public class HeadersFrame extends HeaderBlockHead {

    private static final ThreadCache.CachedTypeIndex<HeadersFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(HeadersFrame.class, 8);

    public static final int TYPE = 1;
    
    public static final byte END_STREAM = 0x1;
    public static final byte PRIORITIZED = 0x20;

    static final Map<Integer, String> FLAG_NAMES_MAP =
            new HashMap<>(8);
    
    static {
        FLAG_NAMES_MAP.putAll(HeaderBlockHead.FLAG_NAMES_MAP);
        FLAG_NAMES_MAP.put((int) END_STREAM, "END_STREAM");
        FLAG_NAMES_MAP.put((int) PRIORITIZED, "PRIORITIZED");
    }

    private boolean exclusive;
    private int streamDependency;
    private int weight;
    private int compressedHeadersLen;
    
    // ------------------------------------------------------------ Constructors


    private HeadersFrame() { }


    // ---------------------------------------------------------- Public Methods

    public static HeadersFrame fromBuffer(final int flags,
                                          final int streamId,
                                          final Buffer buffer) {
        final HeadersFrame frame = create();
        frame.setFlags(flags);
        frame.setStreamId(streamId);
        
        if (frame.isFlagSet(PADDED)) {
            frame.padLength = buffer.get() & 0xFF;
        }
        
        if (frame.isFlagSet(PRIORITIZED)) {
            final int dependency = buffer.getInt();
            frame.exclusive = (dependency & 1L << 31) != 0;
            frame.streamDependency = dependency & 0x7FFFFFFF;
            frame.weight = buffer.get() & 0xff;
        }
        
        frame.compressedHeaders = buffer.split(buffer.position());
        frame.compressedHeadersLen = frame.compressedHeaders.remaining();
        frame.setFrameBuffer(buffer);
        
        return frame;
    }

    static HeadersFrame create() {
        HeadersFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new HeadersFrame();
        }
        
        return frame;
    }

    public static HeadersFrameBuilder builder() {
        return new HeadersFrameBuilder();
    }

    /**
     * Remove HeadersFrame padding (if it was applied).
     * 
     * @return this HeadersFrame instance
     */
    public HeadersFrame normalize() {
        if (isPadded()) {
            clearFlag(PADDED);
            compressedHeaders.limit(compressedHeaders.limit() - padLength);
            padLength = 0;
            
            onPayloadUpdated();
        }
        
        return this;
    }
    
    public int getStreamDependency() {
        return streamDependency;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public int getWeight() {
        return weight;
    }
    
    public boolean isEndStream() {
        return isFlagSet(END_STREAM);
    }
    
    public boolean isPrioritized() {
        return isFlagSet(PRIORITIZED);
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("HeadersFrame {")
                .append(headerToString())
                .append(", streamDependency=").append(streamDependency)
                .append(", exclusive=").append(exclusive)
                .append(", weight=").append(weight)
                .append(", padLength=").append(padLength)
                .append(", compressedHeaders=").append(compressedHeaders)
                .append('}');
        return sb.toString();
    }
    
    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (DONT_RECYCLE) {
            return;
        }

        padLength = 0;
        streamDependency = 0;
        weight = 0;
        
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    // -------------------------------------------------- Methods from Http2Frame
    @Override
    public int getType() {
        return TYPE;
    }
    
    @Override
    public Buffer toBuffer(final MemoryManager memoryManager) {
        final boolean isPadded = isFlagSet(PADDED);
        final boolean isPrioritySet = isFlagSet(PRIORITIZED);
        
        final int extraHeaderLen = (isPadded ? 1 : 0) +
                (isPrioritySet ? 5 : 0);
        
        final Buffer buffer = memoryManager.allocate(
                FRAME_HEADER_SIZE + extraHeaderLen);

        serializeFrameHeader(buffer);

        if (isPadded) {
            buffer.put((byte) (padLength & 0xff));
        }

        if (isPrioritySet) {
            buffer.putInt(streamDependency);
            buffer.put((byte) (weight & 0xff));
        }
        
        buffer.trim();
        final CompositeBuffer cb = CompositeBuffer.newBuffer(memoryManager,
                buffer, compressedHeaders);
        
        cb.allowBufferDispose(true);
        cb.allowInternalBuffersDispose(true);
        return cb;
    }

    @Override
    protected int calcLength() {
        final boolean isPadded = isFlagSet(PADDED);
        final boolean isPrioritySet = isFlagSet(PRIORITIZED);

        // we consider compressedHeaders buffer already includes the padding (if any)
        return (isPadded ? 1 : 0) + (isPrioritySet ? 5 : 0) + (compressedHeadersLen);
    }

    @Override
    protected Map<Integer, String> getFlagNamesMap() {
        return FLAG_NAMES_MAP;
    }
    
    // ---------------------------------------------------------- Nested Classes


    public static class HeadersFrameBuilder extends HeaderBlockHeadBuilder<HeadersFrameBuilder> {
        private int padLength;
        private int streamDependency;
        private int weight;

        // -------------------------------------------------------- Constructors


        protected HeadersFrameBuilder() {
        }


        // ------------------------------------------------------ Public Methods

        public HeadersFrameBuilder endStream(boolean endStream) {
            if (endStream) {
                setFlag(HeadersFrame.END_STREAM);
            }
            return this;
        }

        public HeadersFrameBuilder padded(boolean isPadded) {
            if (isPadded) {
                setFlag(HeadersFrame.PADDED);
            }
            return this;
        }

        public HeadersFrameBuilder prioritized(boolean isPrioritized) {
            if (isPrioritized) {
                setFlag(HeadersFrame.PRIORITIZED);
            }
            return this;
        }

        public HeadersFrameBuilder padLength(int padLength) {
            this.padLength = padLength;
            return this;
        }

        public HeadersFrameBuilder streamDependency(int streamDependency) {
            this.streamDependency = streamDependency;
            return this;
        }

        public HeadersFrameBuilder weight(int weight) {
            this.weight = weight;
            return this;
        }
        
        
        public HeadersFrame build() {
            final HeadersFrame frame = HeadersFrame.create();
            setHeaderValuesTo(frame);
            
            frame.compressedHeaders = compressedHeaders;
            frame.compressedHeadersLen = compressedHeaders.remaining();
            frame.padLength = padLength;
            frame.streamDependency = streamDependency;
            frame.weight = weight;
            
            return frame;
        }


        // --------------------------------------- Methods from Http2FrameBuilder


        @Override
        protected HeadersFrameBuilder getThis() {
            return this;
        }

    } // END HeadersFrameBuilder

}
