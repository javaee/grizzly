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

package org.glassfish.grizzly.ssl;

import org.glassfish.grizzly.AbstractTransformer;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.memory.BufferUtils;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * <tt>Transformer</tt>, which encrypts plain data, contained in the
 * input Buffer, into SSL/TLS data and puts the result to the output Buffer.
 *
 * @author Alexey Stashok
 */
public final class SSLEncoderTransformer extends AbstractTransformer<Buffer, Buffer> {

    public static final int NEED_HANDSHAKE_ERROR = 1;
    public static final int BUFFER_UNDERFLOW_ERROR = 2;
    public static final int BUFFER_OVERFLOW_ERROR = 3;

    private Logger logger = Grizzly.logger(SSLEncoderTransformer.class);
    
    private static final TransformationResult<Buffer, Buffer> HANDSHAKE_NOT_EXECUTED_RESULT =
            TransformationResult.createErrorResult(
            NEED_HANDSHAKE_ERROR, "Handshake was not executed");
    
    private final MemoryManager<Buffer> memoryManager;

    public SSLEncoderTransformer() {
        this(TransportFactory.getInstance().getDefaultMemoryManager());
    }

    public SSLEncoderTransformer(MemoryManager<Buffer> memoryManager) {
        this.memoryManager = memoryManager;
    }

    @Override
    public String getName() {
        return SSLEncoderTransformer.class.getName();
    }

    @Override
    protected TransformationResult<Buffer, Buffer> transformImpl(
            AttributeStorage state, Buffer originalMessage)
            throws TransformationException {

        final SSLEngine sslEngine = SSLUtils.getSSLEngine(state);
        if (sslEngine == null) {
            return HANDSHAKE_NOT_EXECUTED_RESULT;
        }

        final Buffer targetBuffer = memoryManager.allocate(
                    sslEngine.getSession().getPacketBufferSize());

        TransformationResult<Buffer, Buffer> transformationResult = null;

        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("SSLEncoder engine: " + sslEngine + " input: "
                        + originalMessage + " output: " + targetBuffer);
            }

            final SSLEngineResult sslEngineResult;
            if (!originalMessage.isComposite()) {
                sslEngineResult = sslEngine.wrap(originalMessage.toByteBuffer(),
                        targetBuffer.toByteBuffer());
            } else {
                final int appBufferSize = sslEngine.getSession().getApplicationBufferSize();
                final int pos = originalMessage.position();
                final ByteBuffer originalByteBuffer =
                        originalMessage.toByteBuffer(pos,
                        pos + Math.min(appBufferSize, originalMessage.remaining()));

                sslEngineResult = sslEngine.wrap(originalByteBuffer,
                        targetBuffer.toByteBuffer());

                originalMessage.position(pos + sslEngineResult.bytesConsumed());
            }

            final SSLEngineResult.Status status = sslEngineResult.getStatus();

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("SSLEncoder done engine: " + sslEngine
                        + " result: " + sslEngineResult
                        + " input: " + originalMessage
                        + " output: " + targetBuffer);
            }
            
            if (status == SSLEngineResult.Status.OK) {
                targetBuffer.trim();
                
                transformationResult =
                        TransformationResult.createCompletedResult(
                        targetBuffer, originalMessage);
            } else if (status == SSLEngineResult.Status.CLOSED) {
                targetBuffer.dispose();
                
                transformationResult =
                        TransformationResult.createCompletedResult(
                        BufferUtils.EMPTY_BUFFER, originalMessage);
            } else {
                targetBuffer.dispose();

                if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    transformationResult =
                            TransformationResult.createErrorResult(
                            BUFFER_UNDERFLOW_ERROR,
                            "Buffer underflow during wrap operation");
                } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    transformationResult =
                            TransformationResult.createErrorResult(
                            BUFFER_OVERFLOW_ERROR,
                            "Buffer overflow during wrap operation");
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
