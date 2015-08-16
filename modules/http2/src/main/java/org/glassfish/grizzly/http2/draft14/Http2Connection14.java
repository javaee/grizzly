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

package org.glassfish.grizzly.http2.draft14;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http2.DraftVersion;
import org.glassfish.grizzly.http2.Http2Connection;
import org.glassfish.grizzly.http2.Http2ConnectionException;
import org.glassfish.grizzly.http2.Http2ConnectionOutputSink;
import org.glassfish.grizzly.http2.Http2BaseFilter;
import org.glassfish.grizzly.http2.frames.ContinuationFrame;
import org.glassfish.grizzly.http2.frames.DataFrame;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.GoAwayFrame;
import org.glassfish.grizzly.http2.frames.HeadersFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.frames.PingFrame;
import org.glassfish.grizzly.http2.frames.PriorityFrame;
import org.glassfish.grizzly.http2.frames.PushPromiseFrame;
import org.glassfish.grizzly.http2.frames.RstStreamFrame;
import org.glassfish.grizzly.http2.frames.SettingsFrame;
import org.glassfish.grizzly.http2.frames.WindowUpdateFrame;

/**
 * HTTP2 connection draft #14
 * 
 * @author Alexey Stashok
 */
public final class Http2Connection14 extends Http2Connection {

    public Http2Connection14(final Connection<?> connection,
            final boolean isServer, final Http2BaseFilter handlerFilter) {
        super(connection, isServer, handlerFilter);
    }

    @Override
    public DraftVersion getVersion() {
        return DraftVersion.DRAFT_14;
    }
    
    @Override
    protected Http2ConnectionOutputSink newOutputSink() {
        return new Http2ConnectionOutputSink14(this);
    }
    
    @Override
    public int getFrameHeaderSize() {
        return 9;
    }

    @Override
    protected int getSpecDefaultFramePayloadSize() {
        return 16384; //2^14
    }

    @Override
    protected int getSpecMinFramePayloadSize() {
        return 16384; //2^14
    }

    @Override
    protected int getSpecMaxFramePayloadSize() {
        return 0xffffff; // 2^24-1 (16,777,215)
    }

    @Override
    public int getDefaultConnectionWindowSize() {
        return 65535;
    }

    @Override
    public int getDefaultStreamWindowSize() {
        return DraftVersion.DRAFT_14.getDefaultStreamWindowSize();
    }

    @Override
    public int getDefaultMaxConcurrentStreams() {
        return DraftVersion.DRAFT_14.getDefaultMaxConcurrentStreams();
    }
        
    @Override
    protected boolean isFrameReady(final Buffer buffer) {
        final int frameLen = getFrameSize(buffer);
        return frameLen > 0 && buffer.remaining() >= frameLen;
    }

    @Override
    protected int getFrameSize(final Buffer buffer) {
        return buffer.remaining() < 4 // even though we need just 3 bytes - we require 4 for simplicity
                ? -1
                : (buffer.getInt(buffer.position()) >>> 8) + getFrameHeaderSize();
    }

    @Override
    public Http2Frame parseHttp2FrameHeader(final Buffer buffer)
            throws Http2ConnectionException {
        // we assume the passed buffer represents only this frame, no remainders allowed
        assert buffer.remaining() == getFrameSize(buffer);
        
        final int i1 = buffer.getInt();
        
        final int length = (i1 >>> 8) & 0xffffff;
        final int type =  i1 & 0xff;
        
        final int flags = buffer.get() & 0xff;
        final int streamId = (int) (buffer.getInt() & 0x7fffffff);
        
        switch (type) {
            case DataFrame.TYPE:
                return DataFrame.fromBuffer(length, flags, streamId, buffer)
                        .normalize(); // remove padding
            case HeadersFrame.TYPE:
                return HeadersFrame.fromBuffer(length, flags, streamId, buffer)
                        .normalize(); // remove padding
            case PriorityFrame.TYPE:
                return PriorityFrame.fromBuffer(streamId, buffer);
            case RstStreamFrame.TYPE:
                return RstStreamFrame.fromBuffer(flags, streamId, buffer);
            case SettingsFrame.TYPE:
                return SettingsFrame.fromBuffer(length, flags, buffer);
            case PushPromiseFrame.TYPE:
                return PushPromiseFrame.fromBuffer(length, flags, streamId, buffer);
            case PingFrame.TYPE:
                return PingFrame.fromBuffer(flags, buffer);
            case GoAwayFrame.TYPE:
                return GoAwayFrame.fromBuffer(length, buffer);
            case WindowUpdateFrame.TYPE:
                return WindowUpdateFrame.fromBuffer(flags, streamId, buffer);
            case ContinuationFrame.TYPE:
                return ContinuationFrame.fromBuffer(length, flags, streamId, buffer);
            default:
                throw new Http2ConnectionException(ErrorCode.PROTOCOL_ERROR,
                        "Unknown frame type: " + type);
        }
    }

    @Override
    public void serializeHttp2FrameHeader(final Http2Frame frame,
            final Buffer buffer) {
        assert buffer.remaining() >= getFrameHeaderSize();

        buffer.putInt(
                ((frame.getLength() & 0xffffff) << 8) |
                frame.getType());
        buffer.put((byte) frame.getFlags());
        buffer.putInt(frame.getStreamId());
    }
}
