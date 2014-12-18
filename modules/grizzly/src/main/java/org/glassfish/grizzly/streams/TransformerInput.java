/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.streams;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.conditions.Condition;
import java.io.IOException;

/**
 *
 * @author Alexey Stashok
 */
public final class TransformerInput extends BufferedInput {

    private final Attribute<CompositeBuffer> inputBufferAttr;
    protected final Transformer<Buffer, Buffer> transformer;
    protected final Input underlyingInput;
    protected final MemoryManager memoryManager;
    protected final AttributeStorage attributeStorage;

    public TransformerInput(Transformer<Buffer, Buffer> transformer,
            Input underlyingInput, Connection connection) {
        this(transformer, underlyingInput,
                connection.getMemoryManager(), connection);
    }

    public TransformerInput(Transformer<Buffer, Buffer> transformer,
            Input underlyingInput, MemoryManager memoryManager,
            AttributeStorage attributeStorage) {

        this.transformer = transformer;
        this.underlyingInput = underlyingInput;
        this.memoryManager = memoryManager;
        this.attributeStorage = attributeStorage;

        inputBufferAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "TransformerInput-" + transformer.getName());
    }

    @Override
    protected void onOpenInputSource() throws IOException {
        underlyingInput.notifyCondition(new TransformerCondition(),
                new TransformerCompletionHandler());
    }

    @Override
    protected void onCloseInputSource() throws IOException {
    }

    public final class TransformerCompletionHandler
            extends EmptyCompletionHandler<Integer> {
        
        @Override
        public void failed(Throwable throwable) {
            notifyFailure(completionHandler, throwable);
            future.failure(throwable);
        }
    }

    public final class TransformerCondition implements Condition {

        @Override
        public boolean check() {
            try {
                CompositeBuffer savedBuffer = inputBufferAttr.get(attributeStorage);
                Buffer bufferToTransform = savedBuffer;
                Buffer chunkBuffer;

                final boolean hasSavedBuffer = (savedBuffer != null);

                if (underlyingInput.isBuffered()) {
                    chunkBuffer = underlyingInput.takeBuffer();
                } else {
                    int size = underlyingInput.size();
                    chunkBuffer = memoryManager.allocate(size);
                    while (size-- >= 0) {
                        chunkBuffer.put(underlyingInput.read());
                    }
                    chunkBuffer.flip();
                }

                if (hasSavedBuffer) {
                    savedBuffer.append(chunkBuffer);
                } else {
                    bufferToTransform = chunkBuffer;
                }

                while (bufferToTransform.hasRemaining()) {
                    final TransformationResult<Buffer, Buffer> result =
                            transformer.transform(attributeStorage,
                            bufferToTransform);
                    final Status status = result.getStatus();

                    if (status == Status.COMPLETE) {
                        final Buffer outputBuffer = result.getMessage();
                        lock.writeLock().lock();
                        try {
                            append(outputBuffer);

                            if (!isCompletionHandlerRegistered) {
                                // if !isCompletionHandlerRegistered - it means StreamReader has enough data to continue processing
                                return true;
                            }
                        } finally {
                            lock.writeLock().unlock();
                        }
                    } else if (status == Status.INCOMPLETE) {
                        if (!hasSavedBuffer) {
                            if (bufferToTransform.isComposite()) {
                                inputBufferAttr.set(attributeStorage,
                                        (CompositeBuffer) bufferToTransform);
                            } else {
                                savedBuffer = CompositeBuffer.newBuffer(memoryManager);
                                savedBuffer.append(bufferToTransform);
                                inputBufferAttr.set(attributeStorage, savedBuffer);
                            }
                        }

                        return false;
                    } else if (status == Status.ERROR) {
                        throw new TransformationException(result.getErrorDescription());
                    }
                }

                return false;
            } catch (IOException e) {
                throw new TransformationException(e);
            }
        }
    }
}
