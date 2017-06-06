/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2017 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * The {@link Filter} responsible for transforming {@link Http2Frame}s
 * to {@link Buffer}s and vise versa.
 * 
 * @author Grizzly team
 */
public class Http2FrameCodec {

    /**
     *
     * @param http2Session the {@link Http2Session} from which the source buffer was obtained.
     * @param parsingState the current {@link FrameParsingState}.
     * @param srcMessage the inbound buffer.
     * @return one or more {@link Http2Frame}s parsed from the source message.
     *
     * @throws Http2SessionException if an error occurs parsing the frame(s).
     */
    public List<Http2Frame> parse(final Http2Session http2Session,
            final FrameParsingState parsingState, Buffer srcMessage)
            throws Http2SessionException {
        
        if (parsingState.bytesToSkip() > 0) {
            if (!skip(parsingState, srcMessage)) {
                return null;
            }
        }
        
        srcMessage = parsingState.appendToRemainder(
                http2Session.getMemoryManager(), srcMessage);
        
        ParsingResult parsingResult = parseFrame(
                http2Session, parsingState, srcMessage);

        if (!parsingResult.isReady()) {
            return null;
        }

        Buffer remainder = parsingResult.remainder();

        while (remainder.remaining() >= Http2Frame.FRAME_HEADER_SIZE) {
            parsingResult = parseFrame(http2Session, parsingState, remainder);

            if (!parsingResult.isReady()) {
                return parsingResult.frameList();
            }

            remainder = parsingResult.remainder();

        }

        return parsingResult.frameList();
        
//        // ------------ ERROR processing block -----------------------------
//        final Buffer sndBuffer;
//        final GoAwayFrame goAwayFrame =
//                GoAwayFrame.builder()
//                .errorCode(error.getErrorCode())
//                .build();
//        sndBuffer = goAwayFrame.toBuffer(http2State.getHttp2Session());
//
//        // send last message and close the connection
//        ctx.write(sndBuffer);
//        connection.closeSilently();
//
//        return ctx.getStopAction();
    }

    public Buffer serializeAndRecycle(final Http2Session http2Session,
            final Http2Frame frame) {

        NetLogger.log(NetLogger.Context.TX, http2Session, frame);

        final Buffer resultBuffer = frame.toBuffer(http2Session.getMemoryManager());
        frame.recycle();
        return resultBuffer;
    }

    public Buffer serializeAndRecycle(final Http2Session http2Session,
            final List<Http2Frame> frames) {

        Buffer resultBuffer = null;

        final int framesCount = frames.size();

        for (int i = 0; i < framesCount; i++) {
            final Http2Frame frame = frames.get(i);
            NetLogger.log(NetLogger.Context.TX, http2Session, frame);
            final Buffer buffer = frame.toBuffer(http2Session.getMemoryManager());
            frame.recycle();

            resultBuffer = Buffers.appendBuffers(http2Session.getMemoryManager(),
                    resultBuffer, buffer);
        }
        
        frames.clear();
        
        return resultBuffer;
    }
    
    // --------------------------------------------------------- Private Methods

    private ParsingResult parseFrame(final Http2Session http2Session,
            final FrameParsingState state,
            final Buffer buffer) throws Http2SessionException {
        
        final int bufferSize = buffer.remaining();
        final ParsingResult parsingResult = state.parsingResult();
        
        
        if (bufferSize < Http2Frame.FRAME_HEADER_SIZE) {
            return parsingResult.setNeedMore(buffer);
        }
        
        final int len = http2Session.getFrameSize(buffer);
        
        if (len > http2Session.getLocalMaxFramePayloadSize() + Http2Frame.FRAME_HEADER_SIZE) {
            
            http2Session.onOversizedFrame(buffer);

            // skip the frame header
            buffer.position(buffer.position() + Http2Frame.FRAME_HEADER_SIZE);
            
            // figure out what to do with the remainder
            final Buffer remainder;
            final int remaining = buffer.remaining();
            
            if (remaining > len) {
                final int bufferPos = buffer.position();
                remainder = buffer.split(bufferPos + len);
            } else {
                remainder = Buffers.EMPTY_BUFFER;
                state.bytesToSkip(len - remaining);
            }
            
            return parsingResult.setParsed(null, remainder);
        }

        if (buffer.remaining() < len) {
            return parsingResult.setNeedMore(buffer);
        }

        final Buffer remainder = buffer.split(buffer.position() + len);
        final Http2Frame frame = http2Session.parseHttp2FrameHeader(buffer);

        return parsingResult.setParsed(frame, remainder);
    }
    
    private boolean skip(final FrameParsingState parsingState,
            final Buffer message) {
        
        final int bytesToSkip = parsingState.bytesToSkip();
        
        final int dec = Math.min(bytesToSkip, message.remaining());
        parsingState.bytesToSkip(bytesToSkip - dec);
        
        message.position(message.position() + dec);
        
        if (message.hasRemaining()) {
            message.shrink();
            return true;
        }
        
        message.tryDispose();
        return false;
    }
    
    public final static class FrameParsingState {
        private int bytesToSkip;
        private final ParsingResult parsingResult =
                new ParsingResult();
        
        List<Http2Frame> getList() {
            return parsingResult.frameList;
        }

        Buffer appendToRemainder(final MemoryManager mm,
                final Buffer buffer) {
            final Buffer remainderBuffer = parsingResult.remainder;
            parsingResult.remainder = null;
            return Buffers.appendBuffers(mm, remainderBuffer, buffer, true);
        }
        
        int bytesToSkip() {
            return bytesToSkip;
        }
        
        void bytesToSkip(final int bytesToSkip) {
            this.bytesToSkip = bytesToSkip;
        }
        
        ParsingResult parsingResult() {
            return parsingResult;
        }
    }
    
    final static class ParsingResult {
        private Buffer remainder;
        private boolean isReady;
        private final List<Http2Frame> frameList = new ArrayList<>(4);
        
        private ParsingResult() {
        }
        

        ParsingResult setParsed(final Http2Frame frame, final Buffer remainder) {
            if (frame != null) {
                frameList.add(frame);
            }
            
            this.remainder = remainder;
            isReady = true;
            
            return this;
        }
        
        ParsingResult setNeedMore(final Buffer remainder) {
            this.remainder = remainder;
            isReady = false;
            
            return this;
        }

        List<Http2Frame> frameList() {
            return frameList;
        }

        Buffer remainder() {
            return remainder;
        }
        
        boolean isReady() {
            return isReady;
        }
    }    
}
