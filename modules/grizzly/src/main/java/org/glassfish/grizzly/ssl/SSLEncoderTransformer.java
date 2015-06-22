/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import static org.glassfish.grizzly.ssl.SSLUtils.*;

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

    private static final Logger LOGGER = Grizzly.logger(SSLEncoderTransformer.class);
    
    private static final TransformationResult<Buffer, Buffer> HANDSHAKE_NOT_EXECUTED_RESULT =
            TransformationResult.createErrorResult(
            NEED_HANDSHAKE_ERROR, "Handshake was not executed");
    
    private final MemoryManager memoryManager;

    public SSLEncoderTransformer() {
        this(MemoryManager.DEFAULT_MEMORY_MANAGER);
    }

    public SSLEncoderTransformer(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    @Override
    public String getName() {
        return SSLEncoderTransformer.class.getName();
    }

    @Override
    protected TransformationResult<Buffer, Buffer> transformImpl(
            final AttributeStorage state, final Buffer originalMessage)
            throws TransformationException {

        final SSLEngine sslEngine = SSLUtils.getSSLEngine((Connection) state);
        if (sslEngine == null) {
            return HANDSHAKE_NOT_EXECUTED_RESULT;
        }

        synchronized(state) {   // synchronize parallel writers here
            return wrapAll(sslEngine, originalMessage);
        }
    }

    private TransformationResult<Buffer, Buffer> wrapAll(
            final SSLEngine sslEngine,
            final Buffer originalMessage) throws TransformationException {

        TransformationResult<Buffer, Buffer> transformationResult = null;
        
        Buffer targetBuffer = null;
        Buffer currentTargetBuffer = null;
        
        final ByteBufferArray originalByteBufferArray =
                originalMessage.toByteBufferArray();
        boolean restore = false;
        for (int i = 0; i < originalByteBufferArray.size(); i++) {
            final int pos = originalMessage.position();
            final ByteBuffer originalByteBuffer = originalByteBufferArray.getArray()[i];
            
            currentTargetBuffer = allowDispose(memoryManager.allocate(
                    sslEngine.getSession().getPacketBufferSize()));
            
            final ByteBuffer currentTargetByteBuffer =
                    currentTargetBuffer.toByteBuffer();

            try {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "SSLEncoder engine: {0} input: {1} output: {2}",
                            new Object[]{sslEngine, originalByteBuffer, currentTargetByteBuffer});
                }
                
                final SSLEngineResult sslEngineResult =
                        sslEngineWrap(sslEngine, originalByteBuffer,
                        currentTargetByteBuffer);

                // If the position of the original message hasn't changed,
                // update the position now.
                if (pos == originalMessage.position()) {
                    restore = true;
                    originalMessage.position(pos + sslEngineResult.bytesConsumed());
                }

                final SSLEngineResult.Status status = sslEngineResult.getStatus();

                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "SSLEncoder done engine: {0} result: {1} input: {2} output: {3}",
                            new Object[]{sslEngine, sslEngineResult, originalByteBuffer, currentTargetByteBuffer});
                }

                if (status == SSLEngineResult.Status.OK) {
                    currentTargetBuffer.position(sslEngineResult.bytesProduced());
                    currentTargetBuffer.trim();
                    targetBuffer = Buffers.appendBuffers(memoryManager,
                            targetBuffer, currentTargetBuffer);

                } else if (status == SSLEngineResult.Status.CLOSED) {
                    transformationResult =
                            TransformationResult.createCompletedResult(
                            Buffers.EMPTY_BUFFER, originalMessage);
                    break;
                } else {
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
                    break;
                }
            } catch (SSLException e) {
                disposeBuffers(currentTargetBuffer, targetBuffer);

                originalByteBufferArray.restore();

                throw new TransformationException(e);
            }
            
            if (originalByteBuffer.hasRemaining()) { // Keep working with the current source ByteBuffer
                i--;
            }
        }
        assert !originalMessage.hasRemaining();

        if (restore) {
            originalByteBufferArray.restore();
        }
        originalByteBufferArray.recycle();
        
        if (transformationResult != null) { // transformation error case
            disposeBuffers(currentTargetBuffer, targetBuffer);
            
            return transformationResult;
        }
        
        return TransformationResult.createCompletedResult(
                allowDispose(targetBuffer), originalMessage);      
    }

    private static void disposeBuffers(final Buffer currentBuffer, final Buffer bigBuffer) {
        if (currentBuffer != null) {
            currentBuffer.dispose();
        }

        if (bigBuffer != null) {
            bigBuffer.allowBufferDispose(true);
            if (bigBuffer.isComposite()) {
                ((CompositeBuffer) bigBuffer).allowInternalBuffersDispose(true);
            }
            
            bigBuffer.dispose();
        }
    }
    
    private static Buffer allowDispose(final Buffer buffer) {
        buffer.allowBufferDispose(true);
        if (buffer.isComposite()) {
            ((CompositeBuffer) buffer).allowInternalBuffersDispose(true);
        }
        
        return buffer;
    }
    
    
    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
        return input != null && input.hasRemaining();
    }
}
