/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

import org.glassfish.grizzly.http2.Http2Connection;

public class ContinuationFrame extends HeaderBlockFragment {

    private static final ThreadCache.CachedTypeIndex<ContinuationFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(ContinuationFrame.class, 8);

    public static final int TYPE = 9;
    
    // ------------------------------------------------------------ Constructors


    private ContinuationFrame() { }


    // ---------------------------------------------------------- Public Methods

    public static ContinuationFrame fromBuffer(final int length,
            final int flags, final int streamId, final Buffer buffer) {
        final ContinuationFrame frame = create();
        frame.setFlags(flags);
        frame.setStreamId(streamId);
        frame.compressedHeaders = buffer.split(buffer.position());
        frame.setFrameBuffer(buffer);
        
        return frame;
    }

    static ContinuationFrame create() {
        ContinuationFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new ContinuationFrame();
        }
        
        return frame;
    }

    public static ContinuationFrameBuilder builder() {
        return new ContinuationFrameBuilder();
    }

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (DONT_RECYCLE) {
            return;
        }
        
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
        final MemoryManager memoryManager = http2Connection.getMemoryManager();
        
        final Buffer buffer = memoryManager.allocate(
                http2Connection.getFrameHeaderSize());
        
        http2Connection.serializeHttp2FrameHeader(this, buffer);

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
        sb.append("ContinuationFrame {")
                .append(headerToString())
                .append(", compressedHeaders=").append(compressedHeaders)
                .append('}');
        return sb.toString();
    }

    @Override
    protected int calcLength() {
        return compressedHeaders.remaining();
    }
    
    // ---------------------------------------------------------- Nested Classes


    public static class ContinuationFrameBuilder
            extends HeaderBlockFragmentBuilder<ContinuationFrameBuilder> {

        // -------------------------------------------------------- Constructors


        protected ContinuationFrameBuilder() {
        }


        // ------------------------------------------------------ Public Methods

        public ContinuationFrame build() {
            final ContinuationFrame frame = ContinuationFrame.create();
            setHeaderValuesTo(frame);
            
            frame.compressedHeaders = compressedHeaders;
            
            return frame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected ContinuationFrameBuilder getThis() {
            return this;
        }

    } // END ContinuationFrameBuilder

}
