/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * The {@link Filter} responsible for transforming SPDY {@link Http2Frame}s
 * to {@link Buffer}s and vise versa.
 * 
 * @author Grizzly team
 */
public class Http2FrameCodec {
    private static final Logger LOGGER = Grizzly.logger(Http2FrameCodec.class);
    private static final Level LOGGER_LEVEL = Level.INFO;

    /**
     *
     * @param http2Connection
     * @param parsingState
     * @param srcMessage
     * @return
     * @throws Http2ConnectionException
     */
    public List<Http2Frame> parse(final Http2Connection http2Connection,
            final FrameParsingState parsingState, Buffer srcMessage)
            throws Http2ConnectionException {
        
        if (parsingState.bytesToSkip() > 0) {
            if (!skip(parsingState, srcMessage)) {
                return null;
            }
        }
        
        srcMessage = parsingState.appendToRemainder(
                http2Connection.getMemoryManager(), srcMessage);
        
        ParsingResult parsingResult = parseFrame(
                http2Connection, parsingState, srcMessage);

        if (!parsingResult.isReady()) {
            return null;
        }

        final boolean logit = LOGGER.isLoggable(LOGGER_LEVEL);
        if (logit) {
            LOGGER.log(LOGGER_LEVEL, "Rx [1]: connection={0}, frame={1}",
                    new Object[]{makeString(http2Connection.getConnection()),
                        makeString(parsingResult.frameList().get(parsingResult.frameList().size() - 1))});
        }

        Buffer remainder = parsingResult.remainder();

        while (remainder.remaining() >= http2Connection.getFrameHeaderSize()) {
            parsingResult = parseFrame(http2Connection, parsingState, remainder);

            if (!parsingResult.isReady()) {
                return parsingResult.frameList();
            }

            remainder = parsingResult.remainder();

            if (logit) {
                LOGGER.log(LOGGER_LEVEL, "Rx [2]: connection={0}, frame={1}",
                        new Object[]{makeString(http2Connection.getConnection()),
                            makeString(parsingResult.frameList().get(parsingResult.frameList().size() - 1))});
            }
        }

        return parsingResult.frameList();
        
//        // ------------ ERROR processing block -----------------------------
//        final Buffer sndBuffer;
//        final GoAwayFrame goAwayFrame =
//                GoAwayFrame.builder()
//                .errorCode(error.getErrorCode())
//                .build();
//        sndBuffer = goAwayFrame.toBuffer(http2State.getHttp2Connection());
//
//        // send last message and close the connection
//        ctx.write(sndBuffer);
//        connection.closeSilently();
//
//        return ctx.getStopAction();
    }

    public Buffer serializeAndRecycle(final Http2Connection http2Connection,
            final Http2Frame frame) {

        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            LOGGER.log(LOGGER_LEVEL, "Tx: connection={0}, frame={1}",
                    new Object[]{makeString(http2Connection.getConnection()),
                        makeString(frame)});
        }

        final Buffer resultBuffer = frame.toBuffer(http2Connection);
        frame.recycle();
        return resultBuffer;
    }

    public Buffer serializeAndRecycle(final Http2Connection http2Connection,
            final List<Http2Frame> frames) {

        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            LOGGER.log(LOGGER_LEVEL, "Tx: connection={0}, frames={1}",
                    new Object[]{makeString(http2Connection.getConnection()),
                        makeString(frames)});
        }


        Buffer resultBuffer = null;

        final int framesCount = frames.size();

        for (int i = 0; i < framesCount; i++) {
            final Http2Frame frame = frames.get(i);

            final Buffer buffer = frame.toBuffer(http2Connection);
            frame.recycle();

            resultBuffer = Buffers.appendBuffers(http2Connection.getMemoryManager(),
                    resultBuffer, buffer);
        }
        
        frames.clear();
        
        return resultBuffer;
    }
    
    // --------------------------------------------------------- Private Methods

    private ParsingResult parseFrame(final Http2Connection http2Connection,
            final FrameParsingState state,
            final Buffer buffer) throws Http2ConnectionException {
        
        final int frameHeaderSize = http2Connection.getFrameHeaderSize();
        final int bufferSize = buffer.remaining();
        final ParsingResult parsingResult = state.parsingResult();
        
        
        if (bufferSize < frameHeaderSize) {
            return parsingResult.setNeedMore(buffer);
        }
        
        final int len = http2Connection.getFrameSize(buffer);
        
        if (len > http2Connection.getLocalMaxFramePayloadSize() + frameHeaderSize) {
            
            http2Connection.onOversizedFrame(buffer);

            // skip the frame header
            buffer.position(buffer.position() + frameHeaderSize);
            
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
        final Http2Frame frame = http2Connection.parseHttp2FrameHeader(buffer);

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
    
    /**
     * Method used as a workaround for Glassfish logging issue.
     * @param o
     * @return 
     */
    private static String makeString(final Object o) {
        return o == null ? null : o.toString();
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
        private final List<Http2Frame> frameList = new ArrayList<Http2Frame>(4);
        
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
