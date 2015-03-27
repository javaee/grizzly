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

import java.util.HashMap;
import java.util.Map;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http2.Http2Connection;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

public class DataFrame extends Http2Frame {

    private static final ThreadCache.CachedTypeIndex<DataFrame> CACHE_IDX =
                    ThreadCache.obtainIndex(DataFrame.class, 8);
    
    public static final int TYPE = 0;
    
    public static final byte END_STREAM = 0x1;
    public static final byte PADDED = 0x8;

    static final Map<Integer, String> FLAG_NAMES_MAP =
            new HashMap<Integer, String>(4);
    
    static {
        FLAG_NAMES_MAP.put(Integer.valueOf(END_STREAM), "END_STREAM");
        FLAG_NAMES_MAP.put(Integer.valueOf(PADDED), "PADDED");
    }

    private Buffer data;
    private int padLength;

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

    public static DataFrame fromBuffer(final int length,
            final int flags, final int streamId, final Buffer buffer) {
        final DataFrame frame = create();
        frame.setFlags(flags);
        frame.setStreamId(streamId);
        
        if (frame.isFlagSet(PADDED)) {
            frame.padLength = buffer.get() & 0xFF;
        }

        // split the Buffer so data Buffer won't be disposed on frame recycle
        frame.data = buffer.split(buffer.position());
        frame.setFrameBuffer(buffer);
        
        frame.onPayloadUpdated();

        return frame;
    }

    public static DataFrameBuilder builder() {
        return new DataFrameBuilder();
    }

    /**
     * Remove DataFrame padding (if it was applied).
     * 
     * @return this DataFrame instance
     */
    public DataFrame normalize() {
        if (isPadded()) {
            clearFlag(PADDED);
            data.limit(data.limit() - padLength);
            padLength = 0;
            
            onPayloadUpdated();
        }
        
        return this;
    }
    
    public Buffer getData() {
        return data;
    }

    public boolean isEndStream() {
        return isFlagSet(END_STREAM);
    }
    
    public boolean isPadded() {
        return isFlagSet(PADDED);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DataFrame {")
                .append(headerToString())
                .append(", data=").append(data)
                .append('}');
        return sb.toString();
    }

    @Override
    protected int calcLength() {
        return data.remaining() + (isPadded() ? 1 : 0);
    }
    
    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (DONT_RECYCLE) {
            return;
        }
        
        padLength = 0;
        data = null;
        
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
        
        final boolean isPadded = isFlagSet(PADDED);
        final int extraHeaderLen = isPadded ? 1 : 0;
        final Buffer buffer = memoryManager.allocate(
                http2Connection.getFrameHeaderSize() + extraHeaderLen);
        
        http2Connection.serializeHttp2FrameHeader(this, buffer);

        if (isPadded) {
            buffer.put((byte) (padLength & 0xff));
        }

        buffer.trim();
        final CompositeBuffer cb = CompositeBuffer.newBuffer(
                memoryManager, buffer, data);
        
        cb.allowBufferDispose(true);
        cb.allowInternalBuffersDispose(true);
        
        return cb;
    }

    @Override
    protected Map<Integer, String> getFlagNamesMap() {
        return FLAG_NAMES_MAP;
    }
    // ---------------------------------------------------------- Nested Classes

    public static class DataFrameBuilder extends Http2FrameBuilder<DataFrameBuilder> {

        private Buffer data;
        private int padLength;

        // -------------------------------------------------------- Constructors


        protected DataFrameBuilder() {
        }

        // ------------------------------------------------------ Public Methods

        public DataFrameBuilder data(final Buffer data) {
            this.data = data;
            
            return this;
        }
        
        public DataFrameBuilder endStream(boolean endStream) {
            if (endStream) {
                setFlag(HeadersFrame.END_STREAM);
            }
            return this;
        }

        public DataFrameBuilder padded(boolean isPadded) {
            if (isPadded) {
                setFlag(HeadersFrame.PADDED);
            }
            return this;
        }

        public void padLength(int padLength) {
            this.padLength = padLength;
        }

        public DataFrame build() {
            final DataFrame frame = DataFrame.create();
            setHeaderValuesTo(frame);
            frame.data = data;
            frame.padLength = padLength;
            
            return frame;
        }


        // --------------------------------------- Methods from SpdyFrameBuilder


        @Override
        protected DataFrameBuilder getThis() {
            return this;
        }

    } // END DataFrameBuilder

}
