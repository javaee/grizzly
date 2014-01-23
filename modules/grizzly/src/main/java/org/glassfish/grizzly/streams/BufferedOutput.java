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
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.CompositeBuffer;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author oleksiys
 */
public abstract class BufferedOutput implements Output {

    protected static final Integer ZERO = 0;
    protected static final GrizzlyFuture<Integer> ZERO_READY_FUTURE =
            ReadyFutureImpl.create(0);
    
    protected final int bufferSize;
    protected CompositeBuffer multiBufferWindow;
    private Buffer buffer;
    private int lastSlicedPosition;
    
    protected final AtomicBoolean isClosed = new AtomicBoolean();

    public BufferedOutput() {
        this(8192);
    }

    public BufferedOutput(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    protected abstract void onClosed() throws IOException;

    protected abstract GrizzlyFuture<Integer> flush0(final Buffer buffer,
            final CompletionHandler<Integer> completionHandler)
            throws IOException;

    protected abstract Buffer newBuffer(int size);

    protected abstract Buffer reallocateBuffer(Buffer oldBuffer, int size);

    @Override
    public void write(byte data) throws IOException {
        ensureBufferCapacity(1);
        buffer.put(data);
    }

    @Override
    public void write(Buffer bufferToWrite) throws IOException {
        if (multiBufferWindow == null) {
            multiBufferWindow = CompositeBuffer.newBuffer();
        }

        final boolean isInternalBufferEmpty = buffer == null ||
                (buffer.position() - lastSlicedPosition) == 0;
        
        if (!isInternalBufferEmpty) {
            final Buffer slice =
                    buffer.slice(lastSlicedPosition, buffer.position());
            
            multiBufferWindow.append(slice);

            lastSlicedPosition = buffer.position();
        }

        multiBufferWindow.append(bufferToWrite);
        ensureBufferCapacity(0);
    }

    @Override
    public boolean isBuffered() {
        return true;
    }

    @Override
    public Buffer getBuffer() {
        return buffer;
    }

    @Override
    public void ensureBufferCapacity(final int size) throws IOException {
        if (size > bufferSize) {
            throw new IllegalArgumentException("Size exceeds max size limit: " + bufferSize);
        }

        if (getBufferedSize() >= bufferSize) {
            overflow(null);
        }

        if (size == 0) return;
        
        if (buffer != null) {
            final int bufferRemaining = buffer.remaining();
            if (bufferRemaining < size) {
                overflow(null);
                ensureBufferCapacity(size);
            }
        } else {
            buffer = newBuffer(bufferSize);
        }
    }

    private GrizzlyFuture<Integer> overflow(
            final CompletionHandler<Integer> completionHandler)
            throws IOException {
        if (multiBufferWindow != null) {
            if (buffer != null && buffer.position() > lastSlicedPosition) {
                final Buffer slice =
                        buffer.slice(lastSlicedPosition, buffer.position());

                lastSlicedPosition = buffer.position();
                multiBufferWindow.append(slice);
            }

            final GrizzlyFuture<Integer> future = flush0(multiBufferWindow,
                    completionHandler);

            if (future.isDone()) {
                multiBufferWindow.removeAll();
                multiBufferWindow.clear();
                if (buffer != null) {
                    if (!buffer.isComposite()) {
                        buffer.clear();
                    } else {
                        buffer = null;
                    }
                    lastSlicedPosition = 0;
                }
            } else {
                multiBufferWindow = null;
                buffer = null;
                lastSlicedPosition = 0;
            }
            
            return future;
        } else if (buffer != null && buffer.position() > 0) {
            buffer.flip();

            final GrizzlyFuture<Integer> future = flush0(buffer,
                    completionHandler);
            if (future.isDone() && !buffer.isComposite()) {
                buffer.clear();
            } else {
                buffer = null;
            }

            return future;
        }
        
        return flush0(null, completionHandler);
    }

    @Override
    public GrizzlyFuture<Integer> flush(CompletionHandler<Integer> completionHandler)
            throws IOException {
        return overflow(completionHandler);
    }

    @Override
    public GrizzlyFuture<Integer> close(
            final CompletionHandler<Integer> completionHandler)
            throws IOException {

        if (!isClosed.getAndSet(true) && buffer != null && buffer.position() > 0) {
            final FutureImpl<Integer> future = SafeFutureImpl.create();

            try {
                overflow(new CompletionHandler<Integer>() {

                    @Override
                    public void cancelled() {
                        close(ZERO);
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        close(ZERO);
                    }

                    @Override
                    public void completed(Integer result) {
                        close(result);
                    }

                    @Override
                    public void updated(Integer result) {
                    }

                    public void close(Integer result) {
                        try {
                            onClosed();
                        } catch (IOException ignored) {
                        } finally {
                            if (completionHandler != null) {
                                completionHandler.completed(result);
                            }

                            future.result(result);
                        }
                    }
                });
            } catch (IOException ignored) {
            }

            return future;
        } else {
            if (completionHandler != null) {
                completionHandler.completed(ZERO);
            }

            return ZERO_READY_FUTURE;
        }
    }

    protected int getBufferedSize() {
        int size = 0;
        
        if (multiBufferWindow != null) {
            size = multiBufferWindow.remaining();
        }

        if (buffer != null) {
            size += buffer.position() - lastSlicedPosition;
        }

        return size;
    }
}
