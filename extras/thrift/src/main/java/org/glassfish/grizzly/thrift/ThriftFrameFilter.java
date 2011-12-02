/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.thrift;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

import java.io.IOException;

/**
 * ThriftFrameFilter supports TFramedTranport that ensures a fully read message by preceding messages with a 4-byte frame size.
 * <p/>
 * If the frame size exceeds the max size which you can set by constructor's parameter, exception will be thrown.
 *
 * @author Bongjae Chang
 */
public class ThriftFrameFilter extends BaseFilter {

    private static final int THRIFT_FRAME_HEADER_LENGTH = 4;
    private static final int DEFAULT_DEFAULT_MAX_THRIFT_FRAME_LENGTH = 512 * 1024;
    private final int maxFrameLength;

    private final Attribute<Integer> lengthAttribute = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("ThriftFilter.FrameSize");

    public ThriftFrameFilter() {
        this(DEFAULT_DEFAULT_MAX_THRIFT_FRAME_LENGTH);
    }

    public ThriftFrameFilter(final int maxFrameLength) {
        if (maxFrameLength < DEFAULT_DEFAULT_MAX_THRIFT_FRAME_LENGTH) {
            this.maxFrameLength = DEFAULT_DEFAULT_MAX_THRIFT_FRAME_LENGTH;
        } else {
            this.maxFrameLength = maxFrameLength;
        }
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final Buffer input = ctx.getMessage();
        if (input == null) {
            throw new IOException("input message could not be null");
        }
        if (!input.hasRemaining()) {
            return ctx.getStopAction();
        }
        final Connection connection = ctx.getConnection();
        if (connection == null) {
            throw new IOException("connection could not be null");
        }

        Integer frameLength = lengthAttribute.get(connection);
        if (frameLength == null) {
            if (input.remaining() < THRIFT_FRAME_HEADER_LENGTH) {
                return ctx.getStopAction(input);
            }
            frameLength = input.getInt();
            if (frameLength > maxFrameLength) {
                throw new IOException("current frame length(" + frameLength + ") exceeds the max frame length(" + maxFrameLength + ")");
            }
            lengthAttribute.set(connection, frameLength);
        }

        final int inputBufferLength = input.remaining();
        if (inputBufferLength < frameLength) {
            return ctx.getStopAction(input);
        }
        lengthAttribute.remove(connection);

        // Check if the input buffer has more than 1 complete thrift message
        // If yes - split up the first message and the remainder
        final Buffer remainder = inputBufferLength > frameLength ? input.split(frameLength) : null;
        ctx.setMessage(input);

        // Instruct FilterChain to store the remainder (if any) and continue execution
        return ctx.getInvokeAction(remainder);
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        final Buffer body = ctx.getMessage();
        if (body == null) {
            throw new IOException("Input message could not be null");
        }
        if (!body.hasRemaining()) {
            return ctx.getStopAction();
        }

        final MemoryManager memoryManager = ctx.getMemoryManager();

        final int frameLength = body.remaining();
        final Buffer header = memoryManager.allocate(THRIFT_FRAME_HEADER_LENGTH);
        header.allowBufferDispose(true);
        header.putInt(frameLength);
        header.trim();

        final Buffer resultBuffer = Buffers.appendBuffers(memoryManager, header, body);
        resultBuffer.allowBufferDispose(true);
        ctx.setMessage(resultBuffer);
        return ctx.getInvokeAction();
    }
}
