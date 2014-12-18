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
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import java.io.IOException;

/**
 *
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class TransformerOutput extends BufferedOutput {

    private final Attribute<CompositeBuffer> outputBufferAttr;
    protected final Transformer<Buffer, Buffer> transformer;
    protected final Output underlyingOutput;
    protected final MemoryManager memoryManager;
    protected final AttributeStorage attributeStorage;

    public TransformerOutput(Transformer<Buffer, Buffer> transformer,
            Output underlyingOutput, Connection connection) {
        this(transformer, underlyingOutput,
                connection.getMemoryManager(), connection);
    }

    public TransformerOutput(Transformer<Buffer, Buffer> transformer,
            Output underlyingOutput, MemoryManager memoryManager,
            AttributeStorage attributeStorage) {

        this.transformer = transformer;
        this.underlyingOutput = underlyingOutput;
        this.memoryManager = memoryManager;
        this.attributeStorage = attributeStorage;

        outputBufferAttr = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
                "TransformerOutput-" + transformer.getName());
    }

    @Override
    protected GrizzlyFuture<Integer> flush0(Buffer buffer,
            final CompletionHandler<Integer> completionHandler)
            throws IOException {
        
        if (buffer != null) {
            CompositeBuffer savedBuffer = outputBufferAttr.get(attributeStorage);
            if (savedBuffer != null) {
                savedBuffer.append(buffer);
                buffer = savedBuffer;
            }

            do {
                final TransformationResult<Buffer, Buffer> result =
                        transformer.transform(attributeStorage, buffer);
                final Status status = result.getStatus();

                if (status == Status.COMPLETE) {
                    final Buffer outputBuffer = result.getMessage();
                    underlyingOutput.write(outputBuffer);
                    transformer.release(attributeStorage);
                } else if (status == Status.INCOMPLETE) {
                    buffer.compact();
                    if (!buffer.isComposite()) {
                        buffer = CompositeBuffer.newBuffer(
                                memoryManager, buffer);
                    }
                    outputBufferAttr.set(attributeStorage, (CompositeBuffer) buffer);

                    return ReadyFutureImpl.create(
                            new IllegalStateException("Can not flush data: " +
                            "Insufficient input data for transformer"));
                } else if (status == Status.ERROR) {
                    transformer.release(attributeStorage);
                    throw new IOException("Transformation exception: "
                            + result.getErrorDescription());
                }
            } while (buffer.hasRemaining());

            return underlyingOutput.flush(completionHandler);
        }

        return ZERO_READY_FUTURE;
    }

    @Override
    protected Buffer newBuffer(int size) {
        return memoryManager.allocate(size);
    }

    @Override
    protected Buffer reallocateBuffer(Buffer oldBuffer, int size) {
        return memoryManager.reallocate(oldBuffer, size);
    }

    @Override
    protected void onClosed() throws IOException {
    }
}
