/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy.frames;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

import static org.glassfish.grizzly.spdy.Constants.SPDY_VERSION;

public class SynStreamFrame extends HeadersProviderFrame {

    private static final ThreadCache.CachedTypeIndex<SynStreamFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(SynStreamFrame.class, 8);

    public static final int TYPE = 1;

    /**
     * Marks this frame as the last frame to be transmitted on this stream and
     * puts the sender in the half-closed.
     */
    public static final byte FLAG_FIN = 0x01;

    /**
     * A stream created with this flag puts the recipient in the half-closed
     * state.
     */
    public static final byte FLAG_UNIDIRECTIONAL = 0x02;

    protected int streamId;
    protected int associatedToStreamId;
    protected int priority;
    protected int slot;


    // ------------------------------------------------------------ Constructors


    private SynStreamFrame() { }


    // ---------------------------------------------------------- Public Methods


    static SynStreamFrame create() {
        SynStreamFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new SynStreamFrame();
        }
        return frame;
    }

    static SynStreamFrame create(final SpdyHeader header) {
        SynStreamFrame frame = create();
        frame.initialize(header);
        return frame;
    }

    public static SynStreamFrameBuilder builder() {
        return new SynStreamFrameBuilder();
    }

    public int getStreamId() {
        return streamId;
    }

    public int getAssociatedToStreamId() {
        return associatedToStreamId;
    }

    public int getPriority() {
        return priority;
    }

    public int getSlot() {
        return slot;
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SynStreamFrame");
        sb.append("{streamId=").append(streamId);
        sb.append(", associatedToStreamId=").append(associatedToStreamId);
        sb.append(", priority=").append(priority);
        sb.append(", slot=").append(slot);
        sb.append(", compressedHeaders=").append(compressedHeaders);
        sb.append(", fin=").append(isFlagSet(FLAG_FIN));
        sb.append(", unidirectional=").append(isFlagSet(FLAG_UNIDIRECTIONAL));
        sb.append('}');
        return sb.toString();
    }

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        streamId = 0;
        associatedToStreamId = 0;
        priority = 0;
        slot = 0;
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // -------------------------------------------------- Methods from SpdyFrame


    @Override
    public Buffer toBuffer(MemoryManager memoryManager) {

        final Buffer frameBuffer = memoryManager.allocate(18);

        frameBuffer.putInt(0x80000000 | (SPDY_VERSION << 16) | TYPE);  // C | SPDY_VERSION | SYN_STREAM_FRAME

        frameBuffer.putInt((flags << 24) | (compressedHeaders.remaining() + 10)); // FLAGS | LENGTH
        frameBuffer.putInt(streamId & 0x7FFFFFFF); // STREAM_ID
        frameBuffer.putInt(associatedToStreamId & 0x7FFFFFFF); // ASSOCIATED_TO_STREAM_ID
        frameBuffer.putShort((short) ((priority << 13) | (slot & 0xFF))); // PRI | UNUSED | SLOT
        frameBuffer.trim();

        CompositeBuffer cb = CompositeBuffer.newBuffer(memoryManager, frameBuffer);
        cb.append(compressedHeaders);
        cb.allowBufferDispose(true);
        cb.allowInternalBuffersDispose(true);
        return cb;
    }


    // ------------------------------------------------------- Protected Methods


    @Override
    protected void initialize(SpdyHeader header) {
        super.initialize(header);
        streamId = header.buffer.getInt() & 0x7FFFFFFF;
        associatedToStreamId = header.buffer.getInt() & 0x7FFFFFFF;
        final int tmpInt = header.buffer.getShort() & 0xFFFF;
        priority = tmpInt >> 13;
        slot = tmpInt & 0xFF;
    }


    // ---------------------------------------------------------- Nested Classes


    public static class SynStreamFrameBuilder extends HeadersProviderFrameBuilder<SynStreamFrameBuilder> {

        private SynStreamFrame synStreamFrame;


        // -------------------------------------------------------- Constructors


        protected SynStreamFrameBuilder() {
            super(SynStreamFrame.create());
            synStreamFrame = (SynStreamFrame) frame;
        }


        // ------------------------------------------------------ Public Methods


        public SynStreamFrameBuilder streamId(final int streamId) {
            synStreamFrame.streamId = streamId;
            return this;
        }

        public SynStreamFrameBuilder associatedStreamId(final int associatedStreamId) {
            synStreamFrame.associatedToStreamId = associatedStreamId;
            return this;
        }

        public SynStreamFrameBuilder priority(final int priority) {
            synStreamFrame.priority = priority;
            return this;
        }

        public SynStreamFrameBuilder slot(final int slot) {
            synStreamFrame.slot = slot;
            return this;
        }

        public SynStreamFrameBuilder unidirectional(boolean unidirectional) {
            if (unidirectional) {
                synStreamFrame.setFlag(SynStreamFrame.FLAG_UNIDIRECTIONAL);
            } else {
                synStreamFrame.clearFlag(SynStreamFrame.FLAG_UNIDIRECTIONAL);
            }
            return this;
        }

        public SynStreamFrameBuilder last(boolean last) {
            if (last) {
                synStreamFrame.setFlag(SynStreamFrame.FLAG_FIN);
            } else {
                synStreamFrame.clearFlag(SynStreamFrame.FLAG_FIN);
            }
            return this;
        }

        public SynStreamFrame build() {
            return synStreamFrame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected SynStreamFrameBuilder getThis() {
            return this;
        }

    } // END SynStreamFrameBuilder

}
