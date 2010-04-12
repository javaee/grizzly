/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */

package com.sun.grizzly.impl;

import com.sun.grizzly.Cacheable;
import com.sun.grizzly.ThreadCache;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Simple {@link Future} implementation, which uses synchronization
 * {@link Object} to synchronize during the lifecycle.
 *
 * @see Future
 * 
 * @author Alexey Stashok
 */
public class SafeFutureImpl<R> extends FutureImpl<R> {
    private static final ThreadCache.CachedTypeIndex<SafeFutureImpl> CACHE_IDX =
            ThreadCache.obtainIndex(SafeFutureImpl.class, 4);
    /**
     * Construct {@link Future}.
     */
    public static <R> SafeFutureImpl<R> create() {
        final SafeFutureImpl future = ThreadCache.takeFromCache(CACHE_IDX);
        if (future != null) {
            return future;
        }

        return new SafeFutureImpl<R>();
    }

    private final Object sync = new Object();
    
    private SafeFutureImpl() {
    }

    /**
     * Get current result value without any blocking.
     * 
     * @return current result value without any blocking.
     */
    @Override
    public R getResult() {
        synchronized(sync) {
            return result;
        }
    }

    /**
     * Set the result value and notify about operation completion.
     * 
     * @param result the result value
     */
    @Override
    public void result(R result) {
        synchronized(sync) {
            this.result = result;
            notifyHaveResult();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized(sync) {
            isCancelled = true;
            notifyHaveResult();
            return true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        synchronized(sync) {
            return isCancelled;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDone() {
        synchronized(sync) {
            return isDone;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R get() throws InterruptedException, ExecutionException {
        synchronized (sync) {
            for (;;) {
                if (isDone) {
                    if (isCancelled) {
                        throw new CancellationException();
                    } else if (failure != null) {
                        throw new ExecutionException(failure);
                    } else if (result != null) {
                        return result;
                    }
                }

                sync.wait();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R get(long timeout, TimeUnit unit) throws
            InterruptedException, ExecutionException, TimeoutException {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, unit);
        synchronized (sync) {
            for (;;) {
                if (isDone) {
                    if (isCancelled) {
                        throw new CancellationException();
                    } else if (failure != null) {
                        throw new ExecutionException(failure);
                    } else if (result != null) {
                        return result;
                    }
                } else if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    throw new TimeoutException();
                }

                sync.wait(timeoutMillis);
            }
        }
    }

    /**
     * Notify about the failure, occured during asynchronous operation execution.
     * 
     * @param failure
     */
    @Override
    public void failure(Throwable failure) {
        synchronized(sync) {
            this.failure = failure;
            notifyHaveResult();
        }
    }

    /**
     * Notify blocked listeners threads about operation completion.
     */
    protected void notifyHaveResult() {
        if (recycleMark == 0) {
            isDone = true;
            sync.notifyAll();
        } else {
            recycle(recycleMark == 2);
        }
    }

    @Override
    public void markForRecycle(boolean recycleResult) {
        synchronized(sync) {
            if (isDone) {
                recycle(recycleResult);
            } else {
                recycleMark = 1 + (recycleResult ? 1 : 0);
            }
        }
    }


    @Override
    public void recycle(boolean recycleResult) {
        if (recycleResult && result != null && result instanceof Cacheable) {
            ((Cacheable) result).recycle();
        }

        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public void recycle() {
        recycle(false);
    }
}
