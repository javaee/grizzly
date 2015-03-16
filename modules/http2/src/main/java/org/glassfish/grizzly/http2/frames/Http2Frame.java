/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.http2.Http2Connection;

public abstract class Http2Frame implements Cacheable {
    protected static final boolean DONT_RECYCLE =
            Boolean.getBoolean(Http2Frame.class.getName() + ".dont-recycle");
    
    // these are common to all frames
    private int flags;
    private int streamId = 0;
    private int length = -1;

    private Buffer frameBuffer;
    
    // ------------------------------------------------------------ Constructors


    protected Http2Frame() { }


    // ---------------------------------------------------------- Public Methods

    public abstract Buffer toBuffer(Http2Connection connection);
    
    public boolean isFlagSet(final int flag) {
        return (flags & flag) == flag;
    }

    public void setFlag(final int flag) {
        flags |= flag;
    }

    public void clearFlag(final int flag) {
        flags &= ~flag;
    }
    
    /**
     * @return flags for the frame.  Only the first 8 bits are relevant.
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Sets flags for the frame.  Only the first 8 bits are relevant.
     * @param flags
     */
    protected void setFlags(final int flags) {
        this.flags = flags;
    }

    /**
     * @return the length of this frame.
     */
    public int getLength() {
        if (length == -1) {
            length = calcLength();
        }
        
        return length;
    }

    /**
     * Recalculates the length
     * @return 
     */
    protected abstract int calcLength();
    
    /**
     * @return the {@link Map} with flag bit - to - flag name mapping
     */
    protected abstract Map<Integer, String> getFlagNamesMap();
    
    /**
     * The method should be invoked once packet payload is updated
     */
    protected void onPayloadUpdated() {
        length = -1;
    }
    
    /**
     * @return the type of the frame.
     */
    public abstract int getType();

    /**
     * @return the stream ID associated with the frame.
     */
    public int getStreamId() {
        return streamId;
    }

    /**
     * Sets the stream ID associated with the data frame.
     * @param streamId 
     */
    protected void setStreamId(final int streamId) {
        this.streamId = streamId;
    }
    
    @Override
    public String toString() {
        return '{' + headerToString() + '}';
    }
    
    public String headerToString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("streamId=").append(streamId);
        sb.append(", type=").append(getType());
        sb.append(", flags=[").append(flagsToString(flags, getFlagNamesMap())).append(']');
        sb.append(", length=").append(getLength());
        return sb.toString();
    }

    protected Buffer getFrameBuffer() {
        return frameBuffer;
    }

    protected void setFrameBuffer(Buffer frameBuffer) {
        this.frameBuffer = frameBuffer;
    }
    
    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (DONT_RECYCLE) {
            return;
        }
        
        flags = 0;
        length = -1;
        streamId = 0;
        
        if (frameBuffer != null) {
            frameBuffer.tryDispose();
            frameBuffer = null;
        }
    }

    private static String flagsToString(int flags,
            final Map<Integer, String> flagsNameMap) {
        if (flags == 0) {
            return "none";
        }
        
        final StringBuilder sb = new StringBuilder();
        while (flags != 0) {
            final int flagsNext = flags & (flags - 1); // flags without lowest 1
            
            final int lowestOneBit = flags - flagsNext;
            final String name = flagsNameMap.get(lowestOneBit);
            
            if (sb.length() > 0) {
                sb.append(" | ");
            }

            sb.append(name != null
                    ? name
                    : sb.append('#').append(Integer.numberOfLeadingZeros(flags)));
            
            flags = flagsNext;
        }
        
        return sb.toString();
    }
    // ---------------------------------------------------------- Nested Classes

    protected static abstract class Http2FrameBuilder<T extends Http2FrameBuilder> {
        protected int flags;
        protected int streamId;
        

        // -------------------------------------------------------- Constructors

        protected Http2FrameBuilder() {
        }

        // ------------------------------------------------------ Public Methods
        public abstract Http2Frame build();
        
        public T setFlag(final int flag) {
            this.flags |= flag;
            return getThis();
        }

        public T clearFlag(final int flag) {
            flags &= ~flag;
            return getThis();
        }

        public T streamId(final int streamId) {
            this.streamId = streamId;
            return getThis();
        }
        
        protected void setHeaderValuesTo(final Http2Frame frame) {
            frame.flags = flags;
            frame.streamId = streamId;
        }
        
        // --------------------------------------------------- Protected Methods


        protected abstract T getThis();

    } 
}
