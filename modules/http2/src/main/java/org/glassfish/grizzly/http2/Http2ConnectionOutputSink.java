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

package org.glassfish.grizzly.http2;

import java.util.ArrayList;
import java.util.List;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.http2.frames.DataFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;

/**
 * Class represents an output sink associated with specific {@link Http2Connection}
 * and is responsible for session (connection) level flow control.
 * 
 * @author Alexey Stashok
 */
public abstract class Http2ConnectionOutputSink {
    protected final Http2Connection http2Connection;

    public Http2ConnectionOutputSink(Http2Connection session) {
        this.http2Connection = session;
    }

    public void close() {
    }

    protected Http2FrameCodec frameCodec() {
        return http2Connection.handlerFilter.frameCodec;
    }
    
    protected void writeDownStream(final Http2Frame frame) {
        writeDownStream(frame, null);
    }
    
    protected void writeDownStream(final Http2Frame frame,
            final CompletionHandler<WriteResult> completionHandler) {
        
        http2Connection.getHttp2ConnectionChain().write(
                http2Connection.getConnection(), null, 
                frameCodec().serializeAndRecycle(http2Connection, frame),
                completionHandler, (MessageCloner) null);        
    }

    protected void writeDownStream(final List<Http2Frame> frames) {
        writeDownStream(frames, null);
    }

    protected void writeDownStream(final List<Http2Frame> frames,
            final CompletionHandler<WriteResult> completionHandler) {
        
        http2Connection.getHttp2ConnectionChain().write(
                http2Connection.getConnection(), null, 
                frameCodec().serializeAndRecycle(http2Connection, frames),
                completionHandler, (MessageCloner) null);        
    }
    
    @SuppressWarnings("unchecked")
    protected <K> void writeDownStream(final K anyMessage,
            final CompletionHandler<WriteResult> completionHandler,
            final MessageCloner<Buffer> messageCloner) {
        
        // Encode Http2Frame -> Buffer
        final Object msg;
        if (anyMessage instanceof List) {
            msg = frameCodec().serializeAndRecycle(
                    http2Connection, (List<Http2Frame>) anyMessage);
        } else if (anyMessage instanceof Http2Frame) {
            msg = frameCodec().serializeAndRecycle(
                    http2Connection, (Http2Frame) anyMessage);
        } else {
            msg = anyMessage;
        }
        
        http2Connection.getHttp2ConnectionChain().write(http2Connection.getConnection(),
                null, msg, completionHandler, messageCloner);        
    }

    protected void writeDataDownStream(final Http2Stream stream,
            final List<Http2Frame> headerFrames,
            final Buffer data,
            final CompletionHandler<WriteResult> completionHandler,
            final MessageCloner<Buffer> messageCloner,
            final boolean isLast) {
        
        if (data == null) {
            writeDownStream(headerFrames, completionHandler, messageCloner);
            return;
        }
        
        final DataFrame dataFrame = DataFrame.builder()
                .streamId(stream.getId())
                .data(data).endStream(isLast)
                .build();

        final Object msg;
        if (headerFrames != null && !headerFrames.isEmpty()) {
            headerFrames.add(dataFrame);
            msg = headerFrames;
        } else {
            msg = dataFrame;
        }

        writeDownStream(msg, completionHandler, messageCloner);
    }
        
    protected abstract void onPeerWindowUpdate(int delta) throws Http2StreamException;

    protected abstract boolean canWrite();

    protected abstract void notifyCanWrite(WriteHandler writeHandler);

    protected abstract int getAvailablePeerConnectionWindowSize();
}
