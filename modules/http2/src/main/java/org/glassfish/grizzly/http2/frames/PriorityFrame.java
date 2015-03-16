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

public class PriorityFrame extends Http2Frame {
    private static final ThreadCache.CachedTypeIndex<PriorityFrame> CACHE_IDX =
                       ThreadCache.obtainIndex(PriorityFrame.class, 8);

    public static final int TYPE = 2;

    private boolean isExclusive;
    private int streamDependency;
    private int weight;

    // ------------------------------------------------------------ Constructors


    private PriorityFrame() { }


    // ---------------------------------------------------------- Public Methods


    static PriorityFrame create() {
        PriorityFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new PriorityFrame();
        }
        return frame;
    }

    public static Http2Frame fromBuffer(final int streamId, final Buffer frameBuffer) {
        PriorityFrame frame = create();
        frame.setStreamId(streamId);
        
        final int int4 = frameBuffer.getInt();
        
        frame.isExclusive = (int4 < 0);  // last bit is set
        frame.streamDependency = int4 & 0x7fffffff;
        frame.weight = frameBuffer.get() & 0xff;

        frame.setFrameBuffer(frameBuffer);
        
        return frame;
    }
    
    public static PriorityFrameBuilder builder() {
        return new PriorityFrameBuilder();
    }

    public int getStreamDependency() {
        return streamDependency;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isExclusive() {
        return isExclusive;
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PriorityFrame {")
                .append(headerToString())
                .append(", exclusive=").append(isExclusive)
                .append(", streamDependency=").append(streamDependency)
                .append(", weight=").append(weight)
                .append('}');
        
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
                .allocate(http2Connection.getFrameHeaderSize() + 5);
        
        http2Connection.serializeHttp2FrameHeader(this, buffer);
        buffer.putInt((isExclusive ? 0x80000000 : 0) |
                (streamDependency & 0x7fffffff));
        buffer.put((byte) weight);
        
        buffer.trim();

        return buffer;
    }

    @Override
    protected int calcLength() {
        return 5;
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

        streamDependency = 0;
        weight = 0;
        
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // ---------------------------------------------------------- Nested Classes


    public static class PriorityFrameBuilder extends Http2FrameBuilder<PriorityFrameBuilder> {

        private int streamDependency;
        private int weight;
        private boolean exclusive;


        // -------------------------------------------------------- Constructors


        protected PriorityFrameBuilder() {
        }


        // ------------------------------------------------------ Public Methods


        public PriorityFrameBuilder streamDependency(final int streamDependency) {
            this.streamDependency = streamDependency;
            return this;
        }

        public PriorityFrameBuilder weight(final int weight) {
            this.weight = weight;
            return this;
        }

        public PriorityFrameBuilder exclusive(final boolean exclusive) {
            this.exclusive = exclusive;
            return this;
        }
        
        @Override
        public PriorityFrame build() {
            final PriorityFrame frame = PriorityFrame.create();
            setHeaderValuesTo(frame);
            
            frame.streamDependency = streamDependency;
            frame.weight = weight;
            frame.isExclusive = exclusive;
            
            return frame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected PriorityFrameBuilder getThis() {
            return this;
        }

    } 
}
