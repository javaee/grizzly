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
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.ThreadCache;

/**
 * The header of the frame.
 * @since 3.0
 */
public class SpdyHeader implements Cacheable {

    private static final ThreadCache.CachedTypeIndex<SpdyHeader> CACHE_IDX =
                       ThreadCache.obtainIndex(SpdyHeader.class, 8);

    // frame buffer
    protected Buffer buffer;

    // these are common to all frames
    protected int flags;
    protected int length;

    // flag indicating if this frame is a control frame
    protected boolean control;

    // these are used by control frames
    protected int version = -1;
    protected int type = -1;

    // this is used by data frames
    protected int streamId = -1;


    // ------------------------------------------------------------ Constructors


    private SpdyHeader() { }


    // --------------------------------------------------------- Private Methods


    private void initialize(final Buffer buffer) {
        this.buffer = buffer;
        final long header = buffer.getLong();
        final int first32 = (int) (header >>> 32);
        final int second32 = (int) (header & 0xFFFFFFFFL);
        flags = second32 >>> 24;
        length = second32 & 0xFFFFFF;
        control = (first32 & 0x80000000) != 0;
        if (control) {
            version = (first32 >>> 16) & 0x7FFF;
            type = first32 & 0xFFFF;
        } else {
            streamId = first32 & 0x7FFFFFFF;
        }
    }


    // ---------------------------------------------------------- Public Methods


    public static SpdyHeader wrap(final Buffer buffer) {
        SpdyHeader header = ThreadCache.takeFromCache(CACHE_IDX);
        if (header == null) {
            header = new SpdyHeader();
        }
        header.initialize(buffer);
        return header;
    }


    /**
     * @return flags for the frame.  Only the first 8 bits are relevant.
     */
    public int getFlags() {
        return flags;
    }

    /**
     * @return the length of this frame.
     */
    public int getLength() {
        return length;
    }

    /**
     * @return <tt>true</tt> if this is a control frame, otherwise <tt>false</tt>
     */
    public boolean isControl() {
        return control;
    }

    /**
     * @return the SPDY version of this control frame.  Not relevant for
     *  data frames.
     */
    public int getVersion() {
        return version;
    }

    /**
     * @return the type of the control frame.  Not relevant for data frames.
     */
    public int getType() {
        return type;
    }

    /**
     * @return the stream ID associated with the data frame.  Not relevant for
     *  control frames.
     */
    public int getStreamId() {
        return streamId;
    }

    /**
     *
     * @return
     */
    public Buffer getUnderlying() {
        return buffer;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SpdyFrameHeader");
        sb.append("{streamId=").append(streamId);
        sb.append(", version=").append(version);
        sb.append(", type=").append(type);
        sb.append(", flags=").append(flags);
        sb.append(", control=").append(control);
        sb.append(", length=").append(length);
        sb.append('}');
        return sb.toString();
    }
    

    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        buffer = null;
        version = -1;
        type = -1;
        flags = 0;
        length = 0;
        streamId = -1;
        control = false;
        ThreadCache.putToCache(CACHE_IDX, this);
    }

}
