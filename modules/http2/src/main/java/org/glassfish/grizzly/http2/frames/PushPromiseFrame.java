/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
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

import java.util.Map;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

import org.glassfish.grizzly.http2.Http2Connection;

public class PushPromiseFrame extends HeaderBlockHead {

    private static final ThreadCache.CachedTypeIndex<PushPromiseFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(PushPromiseFrame.class, 8);

    public static final int TYPE = 5;
    
    private int promisedStreamId;
    
    // ------------------------------------------------------------ Constructors


    private PushPromiseFrame() { }


    // ---------------------------------------------------------- Public Methods

    public static PushPromiseFrame fromBuffer(final int length,
            final int flags, final int streamId, final Buffer buffer) {
        final PushPromiseFrame frame = create();
        frame.setFlags(flags);
        frame.setStreamId(streamId);
        
        if (frame.isFlagSet(PADDED)) {
            frame.padLength = buffer.get() & 0xFF;
        }

        frame.promisedStreamId = buffer.getInt() & 0x7FFFFFFF;
        frame.compressedHeaders = buffer.split(buffer.position());
        frame.setFrameBuffer(buffer);
        
        return frame;
    }

    static PushPromiseFrame create() {
        PushPromiseFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new PushPromiseFrame();
        }
        
        return frame;
    }

    public static PushPromiseFrameBuilder builder() {
        return new PushPromiseFrameBuilder();
    }

    /**
     * Remove HeadersFrame padding (if it was applied).
     * 
     * @return this HeadersFrame instance
     */
    public PushPromiseFrame normalize() {
        if (isPadded()) {
            clearFlag(PADDED);
            compressedHeaders.limit(compressedHeaders.limit() - padLength);
            padLength = 0;
            
            onPayloadUpdated();
        }
        
        return this;
    }
    
    public int getPromisedStreamId() {
        return promisedStreamId;
    }

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (DONT_RECYCLE) {
            return;
        }

        padLength = 0;
        promisedStreamId = 0;
        
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    // -------------------------------------------------- Methods from Http2Frame
    @Override
    public int getType() {
        return TYPE;
    }
    
    @Override
    public Buffer toBuffer(final Http2Connection http2Connection) {
        final boolean isPadded = isFlagSet(PADDED);
        
        final MemoryManager memoryManager = http2Connection.getMemoryManager();
        
        final Buffer buffer = memoryManager.allocate(
                http2Connection.getFrameHeaderSize() + 
                        (isPadded ? 1 : 0) + 4);
        
        http2Connection.serializeHttp2FrameHeader(this, buffer);

        if (isPadded) {
            buffer.put((byte) (padLength & 0xff));
        }

        buffer.putInt(promisedStreamId);
        
        buffer.trim();
        final CompositeBuffer cb = CompositeBuffer.newBuffer(memoryManager,
                buffer, compressedHeaders);
        
        cb.allowBufferDispose(true);
        cb.allowInternalBuffersDispose(true);
        return cb;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PushPromiseFrame {")
                .append(headerToString())
                .append(", promisedStreamId=").append(promisedStreamId)
                .append(", padLength=").append(padLength)
                .append(", compressedHeaders=").append(compressedHeaders)
                .append('}');

        return sb.toString();
    }

    @Override
    protected int calcLength() {
        final boolean isPadded = isFlagSet(PADDED);

        // we consider compressedHeaders buffer already includes the padding (if any)
        return (isPadded ? 1 : 0) + 4 +
                (compressedHeaders != null ? compressedHeaders.remaining() : 0);
    }
    
    @Override
    protected Map<Integer, String> getFlagNamesMap() {
        return FLAG_NAMES_MAP;
    }
    
    // ---------------------------------------------------------- Nested Classes


    public static class PushPromiseFrameBuilder extends HeaderBlockHeadBuilder<PushPromiseFrameBuilder> {
        private int promisedStreamId;

        // -------------------------------------------------------- Constructors


        protected PushPromiseFrameBuilder() {
        }


        // ------------------------------------------------------ Public Methods

        public PushPromiseFrameBuilder promisedStreamId(int promisedStreamId) {
            this.promisedStreamId = promisedStreamId;
            return this;
        }

        @Override
        public PushPromiseFrame build() {
            final PushPromiseFrame frame = PushPromiseFrame.create();
            setHeaderValuesTo(frame);
            
            frame.compressedHeaders = compressedHeaders;
            frame.padLength = padLength;
            frame.promisedStreamId = promisedStreamId;
            
            return frame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected PushPromiseFrameBuilder getThis() {
            return this;
        }

    } // END HeadersFrameBuilder

}
