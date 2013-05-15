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
package org.glassfish.grizzly.spdy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.spdy.frames.GoAwayFrame;
import org.glassfish.grizzly.spdy.frames.OversizedFrame;
import org.glassfish.grizzly.spdy.frames.SpdyFrame;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * The {@link Filter} responsible for transforming SPDY {@link SpdyFrame}s
 * to {@link Buffer}s and vise versa.
 * 
 * @author Grizzly team
 */
public class SpdyFramingFilter extends BaseFilter {
    private static final int DEFAULT_MAX_FRAME_LENGTH = 1 << 24;
    
    private static final Logger LOGGER = Grizzly.logger(SpdyFramingFilter.class);
    private static final Level LOGGER_LEVEL = Level.FINE;

    static final int HEADER_LEN = 8;
    
    private static final Attribute<FrameParsingState> frameParsingState =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            SpdyFramingFilter.class.getName() + ".frameParsingState",
            new NullaryFunction<FrameParsingState>() {
        @Override
        public FrameParsingState evaluate() {
            return new FrameParsingState();
        }
    });

    private volatile int maxFrameLength = DEFAULT_MAX_FRAME_LENGTH;
    
    /**
     * Returns the maximum allowed SPDY frame length.
     */
    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    /**
     * Sets the maximum allowed SPDY frame length.
     */
    public void setMaxFrameLength(final int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }
        
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final FrameParsingState parsingState = frameParsingState.get(connection);
        
        final Buffer message = ctx.getMessage();
        
        if (parsingState.bytesToSkip > 0) {
            if (!skip(parsingState, message)) {
                return ctx.getStopAction();
            }
        }
        
        final SpdySessionException error;
        
        try {
            ParsingResult parsingResult = parseFrame(ctx, parsingState, message);

            SpdyFrame frame = parsingResult.frame();
            Buffer remainder = parsingResult.remainder();

            if (frame == null) {
                return ctx.getStopAction(remainder);
            }

            final boolean logit = LOGGER.isLoggable(LOGGER_LEVEL);
            if (logit) {
                LOGGER.log(LOGGER_LEVEL, "Rx [1]: connection={0}, frame={1}",
                        new Object[] {connection, frame});
            }

            if (frame.isService()) {
                // on service frame - pass it upstream keeping remainder
                ctx.setMessage(frame);
                return ctx.getInvokeAction(
                        remainder.hasRemaining() ? remainder : null);
            }
            
            if (!remainder.hasRemaining()) {
                ctx.setMessage(frame);
                return ctx.getInvokeAction();
            }

            final List<SpdyFrame> frameList = parsingState.getList();
            frameList.add(frame);

            while (remainder.remaining() >= HEADER_LEN) {
                parsingResult = parseFrame(ctx, parsingState, remainder);

                frame = parsingResult.frame();
                remainder = parsingResult.remainder();

                if (frame == null) {
                    break;
                }

                if (logit) {
                    LOGGER.log(LOGGER_LEVEL, "Rx [2]: connection={0}, frame={1}",
                            new Object[] {connection, frame});
                }

                frameList.add(frame);
                
                if (frame.isService()) {
                    // on service frame - pass ready frames upstream, keeping remainder for later processing
                    ctx.setMessage(frameList.size() > 1 ? frameList : frameList.remove(0));

                    return ctx.getInvokeAction(
                            remainder.hasRemaining() ? remainder : null);
                }
            }

            ctx.setMessage(frameList.size() > 1 ? frameList : frameList.remove(0));

            return ctx.getInvokeAction(
                    (remainder.hasRemaining()) ? remainder : null, null);
            
        } catch (SpdySessionException e) {
            error = e;
        }
        
        // ------------ ERROR processing block -----------------------------
        final Buffer sndBuffer;
        final GoAwayFrame goAwayFrame =
                GoAwayFrame.builder()
                .statusCode(error.getGoAwayStatus())
                .build();
        sndBuffer = goAwayFrame.toBuffer(ctx.getMemoryManager());

        // send last message and close the connection
        final NextAction suspendAction = ctx.getSuspendAction();
        ctx.write(sndBuffer, new EmptyCompletionHandler<WriteResult>() {

            @Override
            public void completed(WriteResult result) {
                connection.closeSilently();
                ctx.completeAndRecycle();
            }
        });

        return suspendAction;
    }



    @SuppressWarnings("unchecked")
    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Object message = ctx.getMessage();

        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            LOGGER.log(LOGGER_LEVEL, "Tx: connection={0}, frame={1}",
                    new Object[]{ctx.getConnection(), message});
        }

        final MemoryManager memoryManager = ctx.getMemoryManager();

        if (message instanceof SpdyFrame) {
            final SpdyFrame frame = (SpdyFrame) message;

            ctx.setMessage(frame.toBuffer(memoryManager));

            frame.recycle();
        } else if (message instanceof List) {
            Buffer resultBuffer = null;
            
            final List<SpdyFrame> frames = (List<SpdyFrame>) message;
            final int framesCount = frames.size();
            
            for (int i = 0; i < framesCount; i++) {
                final SpdyFrame frame = frames.get(i);
                
                final Buffer buffer = frame.toBuffer(memoryManager);
                frame.recycle();

                resultBuffer = Buffers.appendBuffers(memoryManager,
                        resultBuffer, buffer);
            }
            frames.clear();
            
            ctx.setMessage(resultBuffer);
        }
        
        return ctx.getInvokeAction();
    }


    // --------------------------------------------------------- Private Methods

    private ParsingResult parseFrame(final FilterChainContext ctx,
            final FrameParsingState state,
            final Buffer buffer) throws SpdySessionException {
        
        final int bufferSize = buffer.remaining();
        
        if (bufferSize < HEADER_LEN) {
            return state.parsingResult.reset(null, buffer);
        }
        
        final int bufferPos = buffer.position();
        final int len = getMessageLength(buffer, bufferPos);
        
        if (!checkFrameLength(len)) {
            final SpdyFrame overSizedFrame = createOversizedFrame(ctx, buffer);
            
            final Buffer remainder;
            final int remaining = buffer.remaining();
            
            if (remaining > len) {
                remainder = buffer.split(bufferPos + len);
            } else {
                remainder = Buffers.EMPTY_BUFFER;
                state.bytesToSkip = len - remaining;
            }
            
            return state.parsingResult.reset(overSizedFrame, remainder);
        }

        int totalLen = len + HEADER_LEN;
        
        if (buffer.remaining() < totalLen) {
            return state.parsingResult.reset(null, buffer);
        }

        final Buffer remainder = buffer.split(bufferPos + totalLen);
        final SpdyFrame frame = SpdyFrame.wrap(buffer);

        return state.parsingResult.reset(frame, remainder);
    }
    
    private static int getMessageLength(Buffer message, int position) {
        return ((message.get(position + 5) & 0xFF) << 16)
                + ((message.get(position + 6) & 0xFF) << 8)
                + (message.get(position + 7) & 0xFF);
    }

    private boolean checkFrameLength(final int frameLen) {
        return frameLen <= maxFrameLength;
    }

    private boolean skip(final FrameParsingState parsingState,
            final Buffer message) {
        
        final int dec = Math.min(parsingState.bytesToSkip, message.remaining());
        parsingState.bytesToSkip -= dec;
        
        message.position(message.position() + dec);
        
        if (message.hasRemaining()) {
            message.shrink();
            return true;
        }
        
        message.tryDispose();
        return false;
    }

    private SpdyFrame createOversizedFrame(
            final FilterChainContext ctx,
            final Buffer message) {
        
        final org.glassfish.grizzly.spdy.frames.SpdyHeader spdyHeader =
                org.glassfish.grizzly.spdy.frames.SpdyHeader.wrap(message);
        final OversizedFrame oversizedFrame = OversizedFrame.create(spdyHeader);
        
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            LOGGER.log(LOGGER_LEVEL, "Rx: oversized frame! connection={0}, header={1}",
                    new Object[]{ctx.getConnection(), spdyHeader});
        }
        
        return oversizedFrame;
    }

    private final static class FrameParsingState {
        private List<SpdyFrame> spdyFrameList;
        private int bytesToSkip;
        private final ParsingResult parsingResult = new ParsingResult();
        
        List<SpdyFrame> getList() {
            if (spdyFrameList == null) {
                spdyFrameList = new ArrayList<SpdyFrame>(4);
            }
            
            return spdyFrameList;
        }
    }

    private final static class ParsingResult {
        private SpdyFrame frame;
        private Buffer remainder;
        
        private ParsingResult reset(final SpdyFrame frame, final Buffer remainder) {
            this.frame = frame;
            this.remainder = remainder;
            
            return this;
        }

        private SpdyFrame frame() {
            return frame;
        }

        private Buffer remainder() {
            return remainder;
        }
    }
}
