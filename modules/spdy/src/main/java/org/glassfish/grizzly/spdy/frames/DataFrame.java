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
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

public class DataFrame extends SpdyFrame {

    private static final ThreadCache.CachedTypeIndex<DataFrame> CACHE_IDX =
                    ThreadCache.obtainIndex(DataFrame.class, 8);

    public final static byte FLAG_FIN = 0x01;

    private Buffer data;
    private int streamId;


    // ------------------------------------------------------------ Constructors


    private DataFrame() { }

    // ---------------------------------------------------------- Public Methods

    static DataFrame create() {
        DataFrame frame = ThreadCache.takeFromCache(CACHE_IDX);
        if (frame == null) {
            frame = new DataFrame();
        }
        return frame;
    }

    static DataFrame create(final SpdyHeader header) {
        DataFrame frame = create();
        frame.initialize(header);
        return frame;
    }

    public static DataFrameBuilder builder() {
            return new DataFrameBuilder();
    }

    public Buffer getData() {
        return data;
    }

    public int getStreamId() {
        return streamId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DataFrame");
        sb.append("{streamId=").append(streamId);
        sb.append(", data=").append(data);
        sb.append(", fin=").append(isFlagSet(FLAG_FIN));
        sb.append('}');
        return sb.toString();
    }

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        data = null;
        super.recycle();
        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // -------------------------------------------------- Methods from SpdyFrame


    @Override
    public Buffer toBuffer(MemoryManager memoryManager) {
        final Buffer headerBuffer = memoryManager.allocate(8);

        headerBuffer.putInt(streamId & 0x7FFFFFFF);  // C | STREAM_ID
        headerBuffer.putInt((flags << 24) | data.remaining()); // FLAGS | LENGTH
        headerBuffer.trim();

        final Buffer resultBuffer = Buffers.appendBuffers(memoryManager, headerBuffer, data);

        if (resultBuffer.isComposite()) {
            resultBuffer.allowBufferDispose(true);
            ((CompositeBuffer) resultBuffer).allowInternalBuffersDispose(true);
            ((CompositeBuffer) resultBuffer).disposeOrder(CompositeBuffer.DisposeOrder.FIRST_TO_LAST);
        }

        return resultBuffer;
    }

    // ------------------------------------------------------- Protected Methods


    @Override
    protected void initialize(SpdyHeader header) {
        super.initialize(header);
        streamId = header.getStreamId();
        data = header.buffer;
    }


    // ---------------------------------------------------------- Nested Classes


    public static class DataFrameBuilder extends SpdyFrameBuilder<DataFrameBuilder> {

        private DataFrame dataFrame;


        // -------------------------------------------------------- Constructors


        protected DataFrameBuilder() {
            super(DataFrame.create());
            dataFrame = (DataFrame) frame;
        }


        // ------------------------------------------------------ Public Methods


        public DataFrameBuilder streamId(final int streamId) {
            dataFrame.streamId = streamId;
            return this;
        }

        public DataFrameBuilder data(final Buffer data) {
            dataFrame.data = data;
            return this;
        }

        public DataFrameBuilder last(boolean last) {
            if (last) {
                dataFrame.setFlag(SynReplyFrame.FLAG_FIN);
            } else {
                dataFrame.clearFlag(SynReplyFrame.FLAG_FIN);
            }
            return this;
        }

        public DataFrame build() {
            return dataFrame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected DataFrameBuilder getThis() {
            return this;
        }

    } // END DataFrameBuilder

}
