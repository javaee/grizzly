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
import org.glassfish.grizzly.memory.MemoryManager;

import static org.glassfish.grizzly.spdy.Constants.SPDY_VERSION;

public class RstStreamFrame extends SpdyFrame {

    private static final ThreadCache.CachedTypeIndex<RstStreamFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(RstStreamFrame.class, 8);

    public static final int TYPE = 3;
    public static final int PROTOCOL_ERROR = 1;
    public static final int INVALID_STREAM = 2;
    public static final int REFUSED_STREAM = 3;
    public static final int UNSUPPORTED_VERSION = 4;
    public static final int CANCEL = 5;
    public static final int INTERNAL_ERROR = 6;
    public static final int FLOW_CONTROL_ERROR = 7;
    public static final int STREAM_IN_USE = 8;
    public static final int STREAM_ALREADY_CLOSED = 9;
    public static final int INVALID_CREDENTIALS = 10;
    public static final int FRAME_TOO_LARGE = 11;

    private int streamId;
    private int statusCode;

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

    static RstStreamFrame create(final SpdyHeader header) {
        RstStreamFrame frame = create();
        frame.initialize(header);
        return frame;
    }

    public static RstStreamFrameBuilder builder() {
        return new RstStreamFrameBuilder();
    }

    public int getStreamId() {
        return streamId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public boolean isFlagSet(byte flag) {
        return false;
    }

    @Override
    public void setFlag(byte flag) {
        // no-op
    }

    @Override
    public void clearFlag(byte flag) {
        // no-op
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RstStreamFrame");
        sb.append("{streamId=").append(streamId);
        sb.append(", statusCode=").append(statusCode);
        sb.append('}');
        return sb.toString();
    }

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        streamId = 0;
        statusCode = 0;
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // -------------------------------------------------- Methods from SpdyFrame


    @Override
    public Buffer toBuffer(MemoryManager memoryManager) {
        final Buffer frameBuffer = memoryManager.allocate(16);
        frameBuffer.putInt(0x80000000 | (SPDY_VERSION << 16) | TYPE); // "C", version, RST_STREAM_FRAME
        frameBuffer.putInt(8); // Flags, Length
        frameBuffer.putInt(streamId & 0x7FFFFFFF); // Stream-ID
        frameBuffer.putInt(statusCode); // Status code
        frameBuffer.trim();
        return frameBuffer;
    }


    // ------------------------------------------------------- Protected Methods

    @Override
    protected void initialize(SpdyHeader header) {
        super.initialize(header);
        streamId = header.buffer.getInt() & 0x7fffffff;
        statusCode = header.buffer.getInt();
    }
    
    
    // ---------------------------------------------------------- Nested Classes
    
    
        public static class RstStreamFrameBuilder extends SpdyFrameBuilder<RstStreamFrameBuilder> {
            
            private RstStreamFrame rstStreamFrame;
            
            
            // -------------------------------------------------------- Constructors
    
    
            protected RstStreamFrameBuilder() {
                super(RstStreamFrame.create());
                rstStreamFrame = (RstStreamFrame) frame;
            }
            
            
            // ------------------------------------------------------ Public Methods
            
            
            public RstStreamFrameBuilder streamId(final int streamId) {
                rstStreamFrame.streamId = streamId;
                return this;
            } 
            
            public RstStreamFrameBuilder statusCode(final int statusCode) {
                rstStreamFrame.statusCode = statusCode;
                return this;
            }

            public RstStreamFrame build() {
                return rstStreamFrame;
            }
            
            
            // --------------------------------------- Methods from SpdyFrameBuilder
    
    
            @Override
            protected RstStreamFrameBuilder getThis() {
                return this;
            }
            
        } // END RstStreamFrameBuilder

}
