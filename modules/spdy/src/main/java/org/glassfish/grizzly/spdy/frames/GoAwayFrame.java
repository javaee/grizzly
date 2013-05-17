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
import org.glassfish.grizzly.spdy.Constants;

public class GoAwayFrame extends SpdyFrame {
    public static final int OK_STATUS = 0;
    public static final int PROTOCOL_ERROR_STATUS = 1;
    public static final int INTERNAL_ERROR_STATUS = 11;
    
    private static final ThreadCache.CachedTypeIndex<GoAwayFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(GoAwayFrame.class, 8);

    public static final int TYPE = 7;

    private int lastGoodStreamId;
    private int statusCode;


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

    static GoAwayFrame create(final SpdyHeader header) {
        GoAwayFrame frame = create();
        frame.initialize(header);
        return frame;
    }

    public static GoAwayFrameBuilder builder() {
        return new GoAwayFrameBuilder();
    }

    public int getLastGoodStreamId() {
        return lastGoodStreamId;
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
        sb.append("GoAwayFrame");
        sb.append("{lastGoodStreamId=").append(lastGoodStreamId);
        sb.append(", statusCode=").append(statusCode);
        sb.append('}');
        return sb.toString();
    }

    // -------------------------------------------------- Methods from SpdyFrame


    @Override
    public Buffer toBuffer(MemoryManager memoryManager) {
        final Buffer buffer = memoryManager.allocate(16);

        buffer.putInt(0x80000000 | (Constants.SPDY_VERSION << 16) | TYPE); // "C", version, GOAWAY_FRAME
        buffer.putInt(8); // Flags, Length
        buffer.putInt(lastGoodStreamId & 0x7FFFFFFF); // Stream-ID
        buffer.putInt(statusCode); // Status code
        buffer.trim();
        return buffer;
    }


    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        statusCode = 0;
        lastGoodStreamId = 0;
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // ------------------------------------------------------- Protected Methods

    @Override
    protected void initialize(SpdyHeader header) {
        super.initialize(header);
        lastGoodStreamId = header.buffer.getInt() & 0x7FFFFFFF;
        if (header.buffer.hasRemaining()) {
            statusCode = header.buffer.getInt();
        }
        header.buffer.dispose();
    }


    // ---------------------------------------------------------- Nested Classes


    public static class GoAwayFrameBuilder extends SpdyFrameBuilder<GoAwayFrameBuilder> {

        private GoAwayFrame goAwayFrame;


        // -------------------------------------------------------- Constructors


        protected GoAwayFrameBuilder() {
            super(GoAwayFrame.create());
            goAwayFrame = (GoAwayFrame) frame;
        }


        // ------------------------------------------------------ Public Methods


        public GoAwayFrameBuilder statusCode(final int statusCode) {
            goAwayFrame.statusCode = statusCode;
            return this;
        }

        public GoAwayFrameBuilder lastGoodStreamId(final int lastGoodStreamId) {
            goAwayFrame.lastGoodStreamId = lastGoodStreamId;
            return this;
        }

        public GoAwayFrame build() {
            return goAwayFrame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected GoAwayFrameBuilder getThis() {
            return this;
        }

    } // END GoAwayFrameBuilder

}
