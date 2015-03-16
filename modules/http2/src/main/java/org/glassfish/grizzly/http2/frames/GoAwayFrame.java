/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Map;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http2.Http2Connection;
import org.glassfish.grizzly.memory.CompositeBuffer;

public class GoAwayFrame extends Http2Frame {
    private static final ThreadCache.CachedTypeIndex<GoAwayFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(GoAwayFrame.class, 8);

    public static final int TYPE = 7;

    private int lastStreamId;
    private ErrorCode errorCode;
    private Buffer additionalDebugData;    

    // ------------------------------------------------------------ Constructors


    private GoAwayFrame() { }


    // ---------------------------------------------------------- Public Methods


    static GoAwayFrame create() {
        GoAwayFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new GoAwayFrame();
        }
        return frame;
    }

    public static Http2Frame fromBuffer(final int length, final Buffer frameBuffer) {
        GoAwayFrame frame = create();
        frame.lastStreamId = frameBuffer.getInt() & 0x7fffffff;
        frame.errorCode = ErrorCode.lookup(frameBuffer.getInt());
        frame.additionalDebugData = frameBuffer.hasRemaining()
                ? frameBuffer
                : null;

        frame.setFrameBuffer(frameBuffer);
        
        return frame;
    }
    
    public static GoAwayFrameBuilder builder() {
        return new GoAwayFrameBuilder();
    }

    public int getLastStreamId() {
        return lastStreamId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Buffer getAdditionalDebugData() {
        return additionalDebugData;
    }
    
    @Override
    public String toString() {
        final boolean hasAddData =
                additionalDebugData != null && additionalDebugData.hasRemaining();
        
        final StringBuilder sb = new StringBuilder();
        sb.append("GoAwayFrame {")
                .append(headerToString())
                .append("{lastStreamId=").append(lastStreamId)
                .append(", errorCode=").append(errorCode);
        if (hasAddData) {
            sb.append(", additionalDebugData={")
                    .append(additionalDebugData.toStringContent())
                    .append('}');
        }
        
        sb.append('}');
        return sb.toString();
    }

    // -------------------------------------------------- Methods from Http2Frame

    @Override
    public int getType() {
        return TYPE;
    }

    @Override
    public Buffer toBuffer(final Http2Connection http2Connection) {
        final Buffer buffer = http2Connection.getMemoryManager()
                .allocate(http2Connection.getFrameHeaderSize() + 8);
        
        http2Connection.serializeHttp2FrameHeader(this, buffer);
        buffer.putInt(lastStreamId & 0x7fffffff);
        buffer.putInt(errorCode.getCode());
        
        buffer.trim();
        
        if (additionalDebugData == null || !additionalDebugData.hasRemaining()) {
            return buffer;
        }
        
        final CompositeBuffer cb = CompositeBuffer.newBuffer(
                http2Connection.getMemoryManager(),
                buffer, additionalDebugData);

        cb.allowBufferDispose(true);
        cb.allowInternalBuffersDispose(true);
        return cb;
    }

    @Override
    protected int calcLength() {
        return 64 +
                (additionalDebugData != null
                        ? additionalDebugData.remaining()
                        : 0);
    }

    @Override
    protected Map<Integer, String> getFlagNamesMap() {
        return Collections.<Integer, String>emptyMap();
    }
    
    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (DONT_RECYCLE) {
            return;
        }

        errorCode = null;
        lastStreamId = 0;
        additionalDebugData = null;
        
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // ---------------------------------------------------------- Nested Classes


    public static class GoAwayFrameBuilder extends Http2FrameBuilder<GoAwayFrameBuilder> {

        private int lastStreamId;
        private ErrorCode errorCode;
        private Buffer additionalDebugData;    


        // -------------------------------------------------------- Constructors


        protected GoAwayFrameBuilder() {
        }


        // ------------------------------------------------------ Public Methods


        public GoAwayFrameBuilder errorCode(final ErrorCode errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public GoAwayFrameBuilder lastStreamId(final int lastStreamId) {
            this.lastStreamId = lastStreamId;
            return this;
        }

        public GoAwayFrameBuilder additionalDebugData(final Buffer additionalDebugData) {
            this.additionalDebugData = additionalDebugData;
            return this;
        }

        public GoAwayFrame build() {
            final GoAwayFrame frame = GoAwayFrame.create();
            setHeaderValuesTo(frame);
            
            frame.lastStreamId = lastStreamId;
            frame.errorCode = errorCode;
            frame.additionalDebugData = additionalDebugData;
            
            return frame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected GoAwayFrameBuilder getThis() {
            return this;
        }

    } 
}
