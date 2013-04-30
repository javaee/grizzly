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

public class SynReplyFrame extends HeadersProviderFrame {

    private static final ThreadCache.CachedTypeIndex<SynReplyFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(SynReplyFrame.class, 8);

    public static final int TYPE = 2;
    public static final byte FLAG_FIN = 0x01;

    private int streamId;


    // ------------------------------------------------------------ Constructors


    private SynReplyFrame() { }


    // ---------------------------------------------------------- Public Methods


    static SynReplyFrame create() {
        SynReplyFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new SynReplyFrame();
        }
        return frame;
    }

    static SynReplyFrame create(final SpdyHeader header) {
        SynReplyFrame frame = create();
        frame.initialize(header);
        return frame;
    }

    public static SynReplyFrameBuilder builder() {
        return new SynReplyFrameBuilder();
    }

    public int getStreamId() {
        return streamId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SynReplyFrame");
        sb.append("{streamId=").append(streamId);
        sb.append(", compressedHeaders=").append(compressedHeaders);
        sb.append(", fin=").append(isFlagSet(FLAG_FIN));
        sb.append('}');
        return sb.toString();
    }

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        streamId = 0;
        super.recycle();
    }


    // -------------------------------------------------- Methods from SpdyFrame


    @Override
    public Buffer toBuffer(MemoryManager memoryManager) {
        final Buffer frameBuffer = memoryManager.allocate(12);

        frameBuffer.putInt(0x80000000 | (SPDY_VERSION << 16) | TYPE);  // C | SPDY_VERSION | SYN_REPLY

        frameBuffer.putInt((flags << 24) | (compressedHeaders.remaining() + 4)); // FLAGS | LENGTH
        frameBuffer.putInt(streamId & 0x7FFFFFFF); // STREAM_ID
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
        streamId = header.buffer.getInt() & 0x7fffffff;
    }


    // ---------------------------------------------------------- Nested Classes


    public static class SynReplyFrameBuilder extends HeadersProviderFrameBuilder<SynReplyFrameBuilder> {

        private SynReplyFrame synReplyFrame;


        // -------------------------------------------------------- Constructors


        protected SynReplyFrameBuilder() {
            super(SynReplyFrame.create());
            synReplyFrame = (SynReplyFrame) frame;
        }


        // ------------------------------------------------------ Public Methods


        public SynReplyFrameBuilder streamId(final int streamId) {
            synReplyFrame.streamId = streamId;
            return this;
        }

        public SynReplyFrameBuilder last(boolean last) {
            if (last) {
                synReplyFrame.setFlag(SynReplyFrame.FLAG_FIN);
            } else {
                synReplyFrame.clearFlag(SynReplyFrame.FLAG_FIN);
            }
            
            return this;
        }

        public SynReplyFrame build() {
            return synReplyFrame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected SynReplyFrameBuilder getThis() {
            return this;
        }

    } // END SynReplyFrameBuilder

}
