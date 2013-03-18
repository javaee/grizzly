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
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.spdy.frames.SpdyFrame;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * The {@link Filter} responsible for transforming SPDY {@link SpdyFrame}s
 * to {@link Buffer}s and vise versa.
 * 
 * @author Grizzly team
 */
public class SpdyFramingFilter extends BaseFilter {

    private static final Logger LOGGER = Grizzly.logger(SpdyFramingFilter.class);
    private static final Level LOGGER_LEVEL = Level.FINE;

    static final int HEADER_LEN = 8;
    
    private final Attribute<ArrayList<SpdyFrame>> framesAttr =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                    SpdyFramingFilter.class.getName() + "." + hashCode(), new NullaryFunction<ArrayList<SpdyFrame>>() {
                @Override
                public ArrayList<SpdyFrame> evaluate() {
                    return new ArrayList<SpdyFrame>(4);
                }
            });

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Buffer message = ctx.getMessage();
        if (message.remaining() < HEADER_LEN) {
            return ctx.getStopAction(message);
        }
        
        int position = message.position();
        
        int len = getMessageLength(message, position);
        int totalLen = len + HEADER_LEN;
        
        if (message.remaining() < totalLen) {
            return ctx.getStopAction(message);
        }
        
        final boolean logit = LOGGER.isLoggable(LOGGER_LEVEL);
        
        if (message.remaining() == totalLen) {
            SpdyFrame frame = SpdyFrame.wrap(message);
            ctx.setMessage(frame);
            if (logit) {
                LOGGER.log(LOGGER_LEVEL, "Rx: connection={0}, frame={1}",
                        new Object[] {ctx.getConnection(), frame});
            }
            return ctx.getInvokeAction();
        }
        
        Buffer remainder = message.split(position + totalLen);
        if (remainder.remaining() < HEADER_LEN) {
            SpdyFrame frame = SpdyFrame.wrap(message);
            ctx.setMessage(frame);
            if (logit) {
                LOGGER.log(LOGGER_LEVEL, "Rx: connection={0}, frame={1}",
                        new Object[] {ctx.getConnection(), frame});
            }
            return ctx.getInvokeAction(remainder, null);
        }

        final List<SpdyFrame> frameList = framesAttr.get(ctx.getConnection());
        SpdyFrame frame = SpdyFrame.wrap(message);
        if (logit) {
            LOGGER.log(LOGGER_LEVEL, "Rx: connection={0}, frame={1}",
                    new Object[] {ctx.getConnection(), frame});
        }
        frameList.add(frame);

        while (remainder.remaining() >= HEADER_LEN) {
            position = remainder.position();
            len = getMessageLength(remainder, position);
            totalLen = len + HEADER_LEN;
            
            if (remainder.remaining() < totalLen) {
                break;
            }
            
            final Buffer remainder2 = remainder.split(position + totalLen);
            final SpdyFrame f = SpdyFrame.wrap(remainder);
            if (logit) {
                LOGGER.log(LOGGER_LEVEL, "Rx: connection={0}, frame={1}",
                        new Object[] {ctx.getConnection(), f});
            }
            frameList.add(f);
            remainder = remainder2;
        }
        
        ctx.setMessage(frameList.size() > 1 ? frameList : frameList.remove(0));
        
        return ctx.getInvokeAction(
                (remainder.hasRemaining()) ? remainder : null,
                null);
    }



    @SuppressWarnings("unchecked")
    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Object message = ctx.getMessage();
        
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
                LOGGER.log(LOGGER_LEVEL, "Tx: connection={0}, frame={1}",
                        new Object[] {ctx.getConnection(), message});
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


    private static int getMessageLength(Buffer message, int position) {
        return ((message.get(position + 5) & 0xFF) << 16)
                + ((message.get(position + 6) & 0xFF) << 8)
                + (message.get(position + 7) & 0xFF);
    }

}
