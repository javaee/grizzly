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

public class HeadersFrame extends HeadersProviderFrame {

    private static final ThreadCache.CachedTypeIndex<HeadersFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(HeadersFrame.class, 8);

    public static final int TYPE = 8;
    public static final byte FLAG_FIN = 0x01;

    private int streamId;


    // ------------------------------------------------------------ Constructors


    private HeadersFrame() { }


    // ---------------------------------------------------------- Public Methods


    static HeadersFrame create() {
        HeadersFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new HeadersFrame();
        }
        return frame;
    }

    static HeadersFrame create(final SpdyHeader header) {
        HeadersFrame frame = create();
        frame.initialize(header);
        frame.streamId = header.buffer.getInt() & 0x7FFFFFFF;
        return frame;
    }


    public static HeadersFrameBuilder builder() {
        return new HeadersFrameBuilder();
    }

    public int getStreamId() {
        return streamId;
    }    

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // -------------------------------------------------- Methods from SpdyFrame


    @Override
    public Buffer toBuffer(MemoryManager memoryManager) {
        final Buffer buffer = memoryManager.allocate(12);
        buffer.putInt(0x80000000 | (SPDY_VERSION << 16) | TYPE);  // C | SPDY_VERSION | WINDOW_UPDATE_FRAME
        buffer.putInt((flags << 24) | compressedHeaders.remaining() + 4); // FLAGS | LENGTH
        buffer.putInt(streamId & 0x7FFFFFFF); // X | STREAM_ID
        buffer.trim();
        CompositeBuffer cb = CompositeBuffer.newBuffer(memoryManager, buffer);
        cb.append(compressedHeaders);
        cb.allowBufferDispose(true);
        cb.allowInternalBuffersDispose(true);
        return cb;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("HeadersFrame");
        sb.append("{streamId=").append(streamId);
        sb.append(", compressedHeaders=").append(compressedHeaders);
        sb.append(", fin=").append(isFlagSet(FLAG_FIN));
        sb.append('}');
        return sb.toString();
    }
    
    // ---------------------------------------------------------- Nested Classes


    public static class HeadersFrameBuilder extends HeadersProviderFrameBuilder<HeadersFrameBuilder> {

        private HeadersFrame headersFrame;


        // -------------------------------------------------------- Constructors


        protected HeadersFrameBuilder() {
            super(HeadersFrame.create());
            headersFrame = (HeadersFrame) frame;
        }


        // ------------------------------------------------------ Public Methods

        public HeadersFrameBuilder streamId(final int streamId) {
            headersFrame.streamId = streamId;
            return this;
        }
        
        public HeadersFrameBuilder last(boolean last) {
            if (last) {
                headersFrame.setFlag(HeadersFrame.FLAG_FIN);
            }
            return this;
        }

        public HeadersFrame build() {
            return headersFrame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected HeadersFrameBuilder getThis() {
            return this;
        }

    } // END HeadersFrameBuilder

}
