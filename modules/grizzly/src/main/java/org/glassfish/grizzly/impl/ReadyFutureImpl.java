/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.ThreadCache;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.CompletionHandler;

/**
 * {@link Future} implementation with the specific unmodifiable result.
 *
 * @see Future
 * 
 * @author Alexey Stashok
 */
public final class ReadyFutureImpl<R> implements FutureImpl<R> {
    private static final ThreadCache.CachedTypeIndex<ReadyFutureImpl> CACHE_IDX =
            ThreadCache.obtainIndex(ReadyFutureImpl.class, 4);

    /**
     * Construct cancelled {@link Future}.
     */
    public static <R> ReadyFutureImpl<R> create() {
        final ReadyFutureImpl<R> future = takeFromCache();
        if (future != null) {
            future.isCancelled = true;
            return future;
        }

        return new ReadyFutureImpl<R>();
    }

    /**
     * Construct {@link Future} with the result.
     */
    public static <R> ReadyFutureImpl<R> create(R result) {
        final ReadyFutureImpl<R> future = takeFromCache();
        if (future != null) {
            future.result = result;
            return future;
        }

        return new ReadyFutureImpl<R>(result);
    }

    /**
     * Construct failed {@link Future}.
     */
    public static <R> ReadyFutureImpl<R> create(Throwable failure) {
        final ReadyFutureImpl<R> future = takeFromCache();
        if (future != null) {
            future.failure = failure;
            return future;
        }

        return new ReadyFutureImpl<R>(failure);
    }

    @SuppressWarnings("unchecked")
    private static <R> ReadyFutureImpl<R> takeFromCache() {
        return ThreadCache.takeFromCache(CACHE_IDX);
    }


    protected R result;
    private Throwable failure;
    private boolean isCancelled;

    /**
     * Construct cancelled {@link Future}.
     */
    private ReadyFutureImpl() {
        this(null, null, true);
    }

    /**
     * Construct {@link Future} with the result.
     */
    private ReadyFutureImpl(R result) {
        this(result, null, false);
    }

    /**
     * Construct failed {@link Future}.
     */
    private ReadyFutureImpl(Throwable failure) {
        this(null, failure, false);
    }

    private ReadyFutureImpl(R result, Throwable failure, boolean isCancelled) {
        this.result = result;
        this.failure = failure;
        this.isCancelled = isCancelled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addCompletionHandler(final CompletionHandler<R> completionHandler) {
        if (isCancelled) {
            completionHandler.cancelled();
        } else if (failure != null) {
            completionHandler.failed(failure);
        } else {
            completionHandler.completed(result);
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
     * Should not be called for <tt>ReadyFutureImpl</tt>
     */
    public void setResult(R result) {
        throw new IllegalStateException("Can not be reset on ReadyFutureImpl");
    }

    /**
     * Do nothing.
     * 
     * @return cancel state, which was set during construction.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return isCancelled;
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
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R get() throws InterruptedException, ExecutionException {
        if (isCancelled) {
            throw new CancellationException();
        } else if (failure != null) {
            throw new ExecutionException(failure);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R get(long timeout, TimeUnit unit) throws
            InterruptedException, ExecutionException, TimeoutException {
        if (isCancelled) {
            throw new CancellationException();
        } else if (failure != null) {
            throw new ExecutionException(failure);
        } else if (result != null) {
            return result;
        } else {
            throw new TimeoutException();
        }
    }

    /**
     * Should not be called for <tt>ReadyFutureImpl</tt>
     */
    @Override
    public void failure(Throwable failure) {
        throw new IllegalStateException("Can not be reset on ReadyFutureImpl");
    }

    @Override
    public void result(R result) {
        throw new IllegalStateException("Can not be reset on ReadyFutureImpl");
    }
    
    private void reset() {
        result = null;
        failure = null;
        isCancelled = false;
    }

    @Override
    public void markForRecycle(boolean recycleResult) {
        recycle(recycleResult);
    }

    @Override
    public void recycle() {
        recycle(false);
    }

    @Override
    public void recycle(boolean recycleResult) {
        if (recycleResult && result != null && result instanceof Cacheable) {
            ((Cacheable) result).recycle();
        }

        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }
}
