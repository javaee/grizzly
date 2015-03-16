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

public class WindowUpdateFrame extends Http2Frame {

    private static final ThreadCache.CachedTypeIndex<WindowUpdateFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(WindowUpdateFrame.class, 8);

    public static final int TYPE = 8;

    private int windowSizeIncrement;

    // ------------------------------------------------------------ Constructors


    private WindowUpdateFrame() { }


    // ---------------------------------------------------------- Public Methods


    static WindowUpdateFrame create() {
        WindowUpdateFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new WindowUpdateFrame();
        }
        return frame;
    }

    public static Http2Frame fromBuffer(final int flags, final int streamId,
            final Buffer frameBuffer) {
        WindowUpdateFrame frame = create();
        frame.setFlags(flags);
        frame.setStreamId(streamId);
        frame.setFrameBuffer(frameBuffer);
        
        frame.windowSizeIncrement = frameBuffer.getInt() & 0x7fffffff;
        
        return frame;
    }
    
    public static WindowUpdateFrameBuilder builder() {
        return new WindowUpdateFrameBuilder();
    }

    public int getWindowSizeIncrement() {
        return windowSizeIncrement;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("WindowUpdateFrame {")
                .append(headerToString())
                .append(", windowSizeIncrement=").append(windowSizeIncrement)
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

        windowSizeIncrement = 0;
        
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
        final Buffer buffer = http2Connection.getMemoryManager()
                .allocate(http2Connection.getFrameHeaderSize() + 4);
        
        http2Connection.serializeHttp2FrameHeader(this, buffer);
        buffer.putInt(windowSizeIncrement & 0x7fffffff);
        
        buffer.trim();

        return buffer;
    }

    // ---------------------------------------------------------- Nested Classes


    public static class WindowUpdateFrameBuilder extends Http2FrameBuilder<WindowUpdateFrameBuilder> {

        private int windowSizeIncrement;


        // -------------------------------------------------------- Constructors


        protected WindowUpdateFrameBuilder() {
        }


        // ------------------------------------------------------ Public Methods


        public WindowUpdateFrameBuilder windowSizeIncrement(final int windowSizeIncrement) {
            this.windowSizeIncrement = windowSizeIncrement;
            return this;
        }

        public WindowUpdateFrame build() {
            final WindowUpdateFrame frame = WindowUpdateFrame.create();
            setHeaderValuesTo(frame);
            frame.windowSizeIncrement = windowSizeIncrement;

            return frame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected WindowUpdateFrameBuilder getThis() {
            return this;
        }

    } // END WindowUpdateFrameBuilder

}
