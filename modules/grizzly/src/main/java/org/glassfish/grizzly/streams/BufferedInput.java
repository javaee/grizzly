/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.utils.conditions.Condition;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 * @author Alexey Stashok
 */
public abstract class BufferedInput implements Input {

    protected final CompositeBuffer compositeBuffer;
    private volatile boolean isClosed;

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    protected boolean isCompletionHandlerRegistered;
    protected Exception registrationStackTrace; // used for debugging problems
    
    protected Condition condition;
    protected CompletionHandler<Integer> completionHandler;
    protected FutureImpl<Integer> future;

    public BufferedInput() {
        compositeBuffer = CompositeBuffer.newBuffer();
    }

    protected abstract void onOpenInputSource() throws IOException;

    protected abstract void onCloseInputSource() throws IOException;

    
    public boolean append(final Buffer buffer) {
        if (buffer == null) {
            return false;
        }

        lock.writeLock().lock();

        try {
            if (isClosed) {
                buffer.dispose();
            } else {
                final int addSize = buffer.remaining();
                if (addSize > 0) {
                    compositeBuffer.append(buffer);
                }
                notifyUpdate();
            }
        } finally {
            lock.writeLock().unlock();
        }

        return true;
    }

    
    public boolean prepend(final Buffer buffer) {
        if (buffer == null) {
            return false;
        }

        lock.writeLock().lock();

        try {
            if (isClosed) {
                buffer.dispose();
            } else {
                final int addSize = buffer.remaining();
                if (addSize > 0) {
                    compositeBuffer.prepend(buffer);
                }

                notifyUpdate();
            }
        } finally {
            lock.writeLock().unlock();
        }

        return true;
    }

    @Override
    public byte read() throws IOException {
        final byte result = compositeBuffer.get();
        compositeBuffer.shrink();
        return result;
    }

    @Override
    public void skip(int length) {
        if (length > size()) {
            throw new IllegalStateException("Can not skip more bytes than available");
        }

        compositeBuffer.position(compositeBuffer.position() + length);
        compositeBuffer.shrink();
    }

    @Override
    public final boolean isBuffered() {
        return true;
    }

    @Override
    public Buffer getBuffer() {
        return compositeBuffer;
    }

    @Override
    public Buffer takeBuffer() {
        final Buffer duplicate = compositeBuffer.duplicate();
        compositeBuffer.removeAll();
        return duplicate;
    }

    @Override
    public int size() {
        return compositeBuffer.remaining();
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (!isClosed) {
                isClosed = true;

                compositeBuffer.dispose();

                final CompletionHandler<Integer> localCompletionHandler = completionHandler;
                if (localCompletionHandler != null) {
                    completionHandler = null;
                    isCompletionHandlerRegistered = false;
                    notifyFailure(localCompletionHandler,
                            new EOFException("Input is closed"));
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

    }

    @Override
    public GrizzlyFuture<Integer> notifyCondition(Condition condition,
            CompletionHandler<Integer> completionHandler) {
        lock.writeLock().lock();

        try {
            if (!isCompletionHandlerRegistered) {
                if (condition.check()) {
                    notifyCompleted(completionHandler);
                    return ReadyFutureImpl.create(
                            compositeBuffer.remaining());
                }

                registrationStackTrace = new Exception();
                isCompletionHandlerRegistered = true;
                this.completionHandler = completionHandler;
                final FutureImpl<Integer> localFuture = SafeFutureImpl.create();
                this.future = localFuture;
                this.condition = condition;
                
                try {
                    onOpenInputSource();
                } catch (IOException e) {
                    notifyFailure(completionHandler, e);
                    return ReadyFutureImpl.create(e);
                }

                return localFuture;
            } else {
                throw new IllegalStateException("Only one notificator could be registered. Previous registration came from: ", registrationStackTrace);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }


    private void notifyUpdate() {
        if (condition != null && condition.check()) {
            condition = null;
            final CompletionHandler<Integer> localCompletionHandler = completionHandler;
            completionHandler = null;

            final FutureImpl<Integer> localFuture = future;
            future = null;
            isCompletionHandlerRegistered = false;

            try {
                
                onCloseInputSource();
                notifyCompleted(localCompletionHandler);
                localFuture.result(compositeBuffer.remaining());
            } catch(IOException e) {
                notifyFailure(localCompletionHandler, e);
                localFuture.failure(e);
            }
        }
    }

    protected void notifyCompleted(final CompletionHandler<Integer> completionHandler) {
        if (completionHandler != null) {
            completionHandler.completed(compositeBuffer.remaining());
        }
    }

    protected void notifyFailure(final CompletionHandler<Integer> completionHandler,
            final Throwable failure) {
        if (completionHandler != null) {
            completionHandler.failed(failure);
        }
    }
}
