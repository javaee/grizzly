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

public class RstStreamFrame extends Http2Frame {

    private static final ThreadCache.CachedTypeIndex<RstStreamFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(RstStreamFrame.class, 8);

    public static final int TYPE = 3;

    private ErrorCode errorCode;

    // ------------------------------------------------------------ Constructors


    private RstStreamFrame() { }


    // ---------------------------------------------------------- Public Methods


    static RstStreamFrame create() {
        RstStreamFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new RstStreamFrame();
        }
        return frame;
    }

    public static Http2Frame fromBuffer(final int flags, final int streamId,
            final Buffer frameBuffer) {
        RstStreamFrame frame = create();
        frame.setFlags(flags);
        frame.setStreamId(streamId);
        frame.setFrameBuffer(frameBuffer);
        frame.errorCode = ErrorCode.lookup(frameBuffer.getInt());
        
        return frame;
    }
    
    public static RstStreamFrameBuilder builder() {
        return new RstStreamFrameBuilder();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RstStreamFrame {")
                .append(headerToString())
                .append(", errorCode=").append(errorCode)
                .append('}');

        return sb.toString();
    }

    @Override
    protected int calcLength() {
        return 4;
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
        final Buffer buffer = http2Connection.getMemoryManager().allocate(
                http2Connection.getFrameHeaderSize() + 4);
        
        http2Connection.serializeHttp2FrameHeader(this, buffer);
        buffer.putInt(errorCode.getCode());
        buffer.trim();
        
        return buffer;
    }


    // ---------------------------------------------------------- Nested Classes
    public static class RstStreamFrameBuilder extends Http2FrameBuilder<RstStreamFrameBuilder> {

        private ErrorCode errorCode;

        // -------------------------------------------------------- Constructors
        protected RstStreamFrameBuilder() {
        }

        // ------------------------------------------------------ Public Methods
        public RstStreamFrameBuilder errorCode(final ErrorCode errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public RstStreamFrame build() {
            final RstStreamFrame frame = RstStreamFrame.create();
            setHeaderValuesTo(frame);
            frame.errorCode = errorCode;

            return frame;
        }

        // --------------------------------------- Methods from SpdyFrameBuilder
        @Override
        protected RstStreamFrameBuilder getThis() {
            return this;
        }

    }
}
