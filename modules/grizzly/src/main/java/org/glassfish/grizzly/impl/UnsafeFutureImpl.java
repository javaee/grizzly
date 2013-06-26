/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.impl;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.ThreadCache;

/**
 * Simple thread-unsafe {@link Future} implementation.
 *
 * @see Future
 * 
 * @author Alexey Stashok
 */
public final class UnsafeFutureImpl<R> implements FutureImpl<R> {

    private static final ThreadCache.CachedTypeIndex<UnsafeFutureImpl> CACHE_IDX =
            ThreadCache.obtainIndex(UnsafeFutureImpl.class, 4);

    /**
     * Construct {@link Future}.
     */
    @SuppressWarnings("unchecked")
    public static <R> UnsafeFutureImpl<R> create() {
        final UnsafeFutureImpl<R> future = ThreadCache.takeFromCache(CACHE_IDX);
        if (future != null) {
            return future;
        }

        return new UnsafeFutureImpl<R>();
    }

    protected boolean isDone;

    protected boolean isCancelled;
    protected Throwable failure;

    protected Set<CompletionHandler<R>> completionHandlers;
    protected R result;

    protected int recycleMark;

    private UnsafeFutureImpl() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addCompletionHandler(final CompletionHandler<R> completionHandler) {
        if (isDone) {
            notifyCompletionHandler(completionHandler);
        } else {
            if (completionHandlers == null) {
                completionHandlers = new HashSet<CompletionHandler<R>>(2);
            }
            
            completionHandlers.add(completionHandler);
        }
        
    }


    /**
     * Get current result value without any blocking.
     * 
     * @return current result value without any blocking.
     */
    @Override
    public R getResult() {
        return result;
    }

    /**
     * Set the result value and notify about operation completion.
     * 
     * @param result the result value
     */
    @Override
    public void result(R result) {
        this.result = result;
        notifyHaveResult();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        isCancelled = true;
        notifyHaveResult();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDone() {
        return isDone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R get() throws InterruptedException, ExecutionException {
        if (isDone) {
            if (isCancelled) {
                throw new CancellationException();
            } else if (failure != null) {
                throw new ExecutionException(failure);
            } else if (result != null) {
                return result;
            }
        }

        throw new ExecutionException(new IllegalStateException("Result is not ready"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R get(long timeout, TimeUnit unit) throws
            InterruptedException, ExecutionException, TimeoutException {
        return get();
    }

    /**
     * Notify about the failure, occured during asynchronous operation execution.
     * 
     * @param failure
     */
    @Override
    public void failure(Throwable failure) {
        this.failure = failure;
        notifyHaveResult();
    }

    /**
     * Notify blocked listeners threads about operation completion.
     */
    protected void notifyHaveResult() {
        if (recycleMark == 0) {
            isDone = true;
            notifyCompletionHandlers();
        } else {
            recycle(recycleMark == 2);
        }
    }

    /**
     * Notify registered {@link CompletionHandler}s about the result.
     */
    private void notifyCompletionHandlers() {
        if (completionHandlers != null) {
            for (CompletionHandler<R> completionHandler : completionHandlers) {
                notifyCompletionHandler(completionHandler);
            }
            
            completionHandlers = null;
        }
    }
    
    /**
     * Notify single {@link CompletionHandler} about the result.
     */
    private void notifyCompletionHandler(final CompletionHandler<R> completionHandler) {
        try {
            if (isCancelled) {
                completionHandler.cancelled();
            } else if (failure != null) {
                completionHandler.failed(failure);
            } else if (result != null) {
                completionHandler.completed(result);
            }
        } catch (Exception ignored) {
        }
    }
    
    @Override
    public void markForRecycle(boolean recycleResult) {
        if (isDone) {
            recycle(recycleResult);
        } else {
            recycleMark = 1 + (recycleResult ? 1 : 0);
        }
    }

    protected void reset() {
        completionHandlers = null;
        result = null;
        failure = null;
        isCancelled = false;
        isDone = false;
        recycleMark = 0;
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
