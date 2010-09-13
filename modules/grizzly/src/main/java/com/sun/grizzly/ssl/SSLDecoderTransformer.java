/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.ssl;

import com.sun.grizzly.AbstractTransformer;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.memory.MemoryManager;
import javax.net.ssl.SSLException;

/**
 * <tt>Transformer</tt>, which decodes SSL encrypted data, contained in the
 * input Buffer, to the output Buffer.
 * 
 * @author Alexey Stashok
 */
public final class SSLDecoderTransformer extends AbstractTransformer<Buffer, Buffer> {
    public static final int NEED_HANDSHAKE_ERROR = 1;
    public static final int BUFFER_UNDERFLOW_ERROR = 2;
    public static final int BUFFER_OVERFLOW_ERROR = 3;

    private static final TransformationResult<Buffer, Buffer> HANDSHAKE_NOT_EXECUTED_RESULT =
            TransformationResult.createErrorResult(
            NEED_HANDSHAKE_ERROR, "Handshake was not executed");

    private Logger logger = Grizzly.logger(SSLDecoderTransformer.class);

    private final MemoryManager<Buffer> memoryManager;

    public SSLDecoderTransformer() {
        this(TransportFactory.getInstance().getDefaultMemoryManager());
    }

    public SSLDecoderTransformer(MemoryManager<Buffer> memoryManager) {
        this.memoryManager = memoryManager;
    }

    @Override
    public String getName() {
        return SSLDecoderTransformer.class.getName();
    }

    @Override
    protected TransformationResult<Buffer, Buffer> transformImpl(
            AttributeStorage state, Buffer originalMessage)
            throws TransformationException {

        final SSLEngine sslEngine = SSLUtils.getSSLEngine(state);
        if (sslEngine == null) {
            return HANDSHAKE_NOT_EXECUTED_RESULT;
        }

        final int expectedLength;
        try {
            expectedLength = SSLFilter.getSSLPacketSize(originalMessage);
            if (expectedLength == -1 || originalMessage.remaining() < expectedLength) {
                return TransformationResult.createIncompletedResult(
                            originalMessage);
            }
        } catch (SSLException e) {
            throw new TransformationException(e);
        }
        
        final Buffer targetBuffer = memoryManager.allocate(
                    sslEngine.getSession().getApplicationBufferSize());

        TransformationResult<Buffer, Buffer> transformationResult = null;

        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("SSLDecoder engine: " + sslEngine + " input: "
                        + originalMessage + " output: " + targetBuffer);
            }

            final SSLEngineResult sslEngineResult;
            if (!originalMessage.isComposite()) {
                sslEngineResult = sslEngine.unwrap(originalMessage.toByteBuffer(),
                        targetBuffer.toByteBuffer());
            } else {
                final int pos = originalMessage.position();
                final ByteBuffer originalByteBuffer =
                        originalMessage.toByteBuffer(pos,
                        pos + expectedLength);

                sslEngineResult = sslEngine.unwrap(originalByteBuffer,
                        targetBuffer.toByteBuffer());

                originalMessage.position(pos + sslEngineResult.bytesConsumed());
            }

            final SSLEngineResult.Status status = sslEngineResult.getStatus();

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("SSLDecoderr done engine: " + sslEngine
                        + " result: " + sslEngineResult
                        + " input: " + originalMessage
                        + " output: " + targetBuffer);
            }

            if (status == SSLEngineResult.Status.OK) {
                targetBuffer.trim();

                return TransformationResult.createCompletedResult(
                        targetBuffer, originalMessage);
            } else if (status == SSLEngineResult.Status.CLOSED) {
                targetBuffer.dispose();

                return TransformationResult.createCompletedResult(
                        BufferUtils.EMPTY_BUFFER, originalMessage);
            } else {
                targetBuffer.dispose();

                if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    transformationResult =
                            TransformationResult.createIncompletedResult(
                            originalMessage);
                } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    transformationResult =
                            TransformationResult.createErrorResult(
                            BUFFER_OVERFLOW_ERROR,
                            "Buffer overflow during unwrap operation");
                }
            }
        } catch (SSLException e) {
            targetBuffer.dispose();
            throw new TransformationException(e);
        }

        return transformationResult;
    }

    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
        return input != null && input.hasRemaining();
    }
}
