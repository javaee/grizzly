/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

import java.util.HashMap;
import java.util.Map;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.MemoryManager;

public class PingFrame extends Http2Frame {

    private static final ThreadCache.CachedTypeIndex<PingFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(PingFrame.class, 8);

    public static final int TYPE = 6;
    
    public static final byte ACK_FLAG = 0x1;

    static final Map<Integer, String> FLAG_NAMES_MAP =
            new HashMap<>(2);
    
    static {
        FLAG_NAMES_MAP.put((int) ACK_FLAG, "ACK");
    }
    
    private long opaqueData;

    // ------------------------------------------------------------ Constructors


    private PingFrame() { }


    // ---------------------------------------------------------- Public Methods


    static PingFrame create() {
        PingFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new PingFrame();
        }
        return frame;
    }

    public static Http2Frame fromBuffer(final int flags, final int streamId, final Buffer frameBuffer) {
        PingFrame frame = create();
        frame.setFlags(flags);
        frame.setStreamId(streamId);
        frame.setFrameBuffer(frameBuffer);
        if (frameBuffer.remaining() != 8) {
            frame.length = frameBuffer.remaining();
        } else {
            frame.opaqueData = frameBuffer.getLong();
        }
        
        return frame;
    }
    
    public static PingFrameBuilder builder() {
        return new PingFrameBuilder();
    }

    public long getOpaqueData() {
        return opaqueData;
    }

    public boolean isAckSet() {
        return isFlagSet(ACK_FLAG);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PingFrame {")
                .append(headerToString())
                .append(", opaqueData=").append(opaqueData)
                .append('}');
        return sb.toString();
    }
    
    @Override
    protected int calcLength() {
        return 8;
    }
    
    @Override
    protected Map<Integer, String> getFlagNamesMap() {
        return FLAG_NAMES_MAP;
    }
    
    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (DONT_RECYCLE) {
            return;
        }

        opaqueData = 0;
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // -------------------------------------------------- Methods from Http2Frame

    @Override
    public int getType() {
        return TYPE;
    }

    @Override
    public Buffer toBuffer(final MemoryManager memoryManager) {
        final Buffer buffer = memoryManager.allocate(FRAME_HEADER_SIZE + 8);

        serializeFrameHeader(buffer);
        buffer.putLong(opaqueData);
        buffer.trim();
        
        return buffer;
    }


    // ---------------------------------------------------------- Nested Classes


    public static class PingFrameBuilder extends Http2FrameBuilder<PingFrameBuilder> {

        private long opaqueData;


        // -------------------------------------------------------- Constructors


        protected PingFrameBuilder() {
        }


        // ------------------------------------------------------ Public Methods


        public PingFrameBuilder opaqueData(final long opaqueData) {
            this.opaqueData = opaqueData;
            return this;
        }

        public PingFrameBuilder ack(final boolean isAck) {
            if (isAck) {
                setFlag(ACK_FLAG);
            }
            return this;
        }
        
        public PingFrame build() {
            final PingFrame frame = PingFrame.create();
            setHeaderValuesTo(frame);
            frame.opaqueData = opaqueData;
            
            return frame;
        }


        // --------------------------------------- Methods from Http2FrameBuilder


        @Override
        protected PingFrameBuilder getThis() {
            return this;
        }

    } 
}
