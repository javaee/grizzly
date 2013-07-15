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
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import org.glassfish.grizzly.CompletionHandler;

/**
 * Safe {@link FutureImpl} implementation.
 * <p/>
 * (Based on the JDK {@link java.util.concurrent.FutureTask})
 *
 * @author Alexey Stashok
 * @see Future
 */
@SuppressWarnings("deprecation")
public class SafeFutureImpl<R> implements FutureImpl<R> {

    private final Object chSync = new Object();
    private Set<CompletionHandler<R>> completionHandlers;
    private final AtomicReference<Object> ref = new AtomicReference<Object>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void addCompletionHandler(final CompletionHandler<R> completionHandler) {
        if (isDone()) {
            notifyCompletionHandler(completionHandler);
        } else {
            synchronized (chSync) {
                if (!isDone()) {
                    if (completionHandlers == null) {
                        completionHandlers =
                                new HashSet<CompletionHandler<R>>(2);
                    }

                    completionHandlers.add(completionHandler);

                    return;
                }
            }

            notifyCompletionHandler(completionHandler);
        }
    }

    /**
     * Construct {@link SafeFutureImpl}.
     */
    @SuppressWarnings("unchecked")
    public static <R> SafeFutureImpl<R> create() {
        return new SafeFutureImpl<R>();
    }

    /**
     * Creates <tt>SafeFutureImpl</tt>
     */
    @SuppressWarnings("unchecked")
    public SafeFutureImpl() {
        sync = new Sync(new Callable<R>() {
            @Override
            public R call() throws Exception {
                Object val = ref.get();
                if (val instanceof Exception) {
                    throw (Exception) val;
                }
                return (R) val;
            }
        });
    }

    /**
     * Set the result value and notify about operation completion.
     *
     * @param result the result value
     */
    @Override
    public void result(R result) {
        ref.compareAndSet(null, result);
        sync.innerRun();
    }

    /**
     * Notify about the failure, occurred during asynchronous operation execution.
     *
     * @param failure
     */
    @Override
    public void failure(Throwable failure) {
        ref.compareAndSet(null, failure);
        sync.innerRun();
    }

    @Override
    public void recycle(boolean recycleResult) {
    }

    @Override
    public void recycle() {
    }

    /**
     * The method is called when this <tt>SafeFutureImpl</tt> is marked as completed.
     * Subclasses can override this method.
     */
    protected void onComplete() {
    }

    /**
     * Notify registered {@link CompletionHandler}s about the result.
     */
    private void notifyCompletionHandlers() {

        assert isDone();

        final Set<CompletionHandler<R>> localSet;
        synchronized (chSync) {
            if (completionHandlers == null) {
                return;
            }

            localSet = completionHandlers;
            completionHandlers = null;
        }

        final boolean isCancelled = isCancelled();
        R result = null;
        Throwable error = null;

        if (!isCancelled) {
            try {
                result = get();
            } catch (ExecutionException e) {
                error = e.getCause();
            } catch (Exception e) {
                error = e;
            }
        }

        for (Iterator<CompletionHandler<R>> it = localSet.iterator();
             it.hasNext(); ) {
            final CompletionHandler<R> completionHandler = it.next();
            it.remove();
            try {
                if (isCancelled) {
                    completionHandler.cancelled();
                } else if (error != null) {
                    completionHandler.failed(error);
                } else {
                    completionHandler.completed(result);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Notify single {@link CompletionHandler} about the result.
     */
    private void notifyCompletionHandler(final CompletionHandler<R> completionHandler) {
        if (isCancelled()) {
            completionHandler.cancelled();
        } else {
            try {
                final R result = get();

                try {
                    completionHandler.completed(result);
                } catch (Exception ignored) {
                }
            } catch (ExecutionException e) {
                completionHandler.failed(e.getCause());
            } catch (Exception e) {
                completionHandler.failed(e);
            }
        }
    }


    // FROM FUTURETASK =========================================================

    /**
     * Synchronization control for FutureTask
     */
    private final Sync sync;



    public boolean isCancelled() {
        return sync.innerIsCancelled();
    }

    public boolean isDone() {
        return sync.innerIsDone();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return sync.innerCancel(mayInterruptIfRunning);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public R get() throws InterruptedException, ExecutionException {
        return sync.innerGet();
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public R get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return sync.innerGet(unit.toNanos(timeout));
    }

    /**
     * Protected method invoked when this task transitions to state
     * <tt>isDone</tt> (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() {
        notifyCompletionHandlers();
        onComplete();
    }

    /**
     * Sets the result of this Future to the given value unless
     * this future has already been set or has been cancelled.
     * This method is invoked internally by the <tt>run</tt> method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(R v) {
        sync.innerSet(v);
    }

    /**
     * Causes this future to report an <tt>ExecutionException</tt>
     * with the given throwable as its cause, unless this Future has
     * already been set or has been cancelled.
     * This method is invoked internally by the <tt>run</tt> method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        sync.innerSetException(t);
    }

    /**
     * Synchronization control for FutureTask. Note that this must be
     * a non-static inner class in order to invoke the protected
     * <tt>done</tt> method. For clarity, all inner class support
     * methods are same as outer, prefixed with "inner".
     * <p/>
     * Uses AQS sync state to represent run status
     */
    private final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -7828117401763700385L;

        /**
         * State value representing that task is ready to run
         */
        private static final int READY = 0;
        /**
         * State value representing that task is running
         */
        private static final int RUNNING = 1;
        /**
         * State value representing that task ran
         */
        private static final int RAN = 2;
        /**
         * State value representing that task was cancelled
         */
        private static final int CANCELLED = 4;

        /**
         * The underlying callable
         */
        private final Callable<R> callable;
        /**
         * The result to return from get()
         */
        private R result;
        /**
         * The exception to throw from get()
         */
        private Throwable exception;

        /**
         * The thread running task. When nulled after set/cancel, this
         * indicates that the results are accessible.  Must be
         * volatile, to ensure visibility upon completion.
         */
        private volatile Thread runner;

        Sync(Callable<R> callable) {
            this.callable = callable;
        }

        private boolean ranOrCancelled(int state) {
            return (state & (RAN | CANCELLED)) != 0;
        }

        /**
         * Implements AQS base acquire to succeed if ran or cancelled
         */
        protected int tryAcquireShared(int ignore) {
            return innerIsDone() ? 1 : -1;
        }

        /**
         * Implements AQS base release to always signal after setting
         * final done status by nulling runner thread.
         */
        protected boolean tryReleaseShared(int ignore) {
            runner = null;
            return true;
        }

        boolean innerIsCancelled() {
            return getState() == CANCELLED;
        }

        boolean innerIsDone() {
            return ranOrCancelled(getState()) && runner == null;
        }

        R innerGet() throws InterruptedException, ExecutionException {
            acquireSharedInterruptibly(0);
            if (getState() == CANCELLED) {
                throw new CancellationException();
            }
            if (exception != null) {
                throw new ExecutionException(exception);
            }
            return result;
        }

        R innerGet(long nanosTimeout)
        throws InterruptedException, ExecutionException, TimeoutException {
            if (!tryAcquireSharedNanos(0, nanosTimeout)) {
                throw new TimeoutException();
            }
            if (getState() == CANCELLED) {
                throw new CancellationException();
            }
            if (exception != null) {
                throw new ExecutionException(exception);
            }
            return result;
        }

        void innerSet(R v) {
            for (; ; ) {
                int s = getState();
                if (s == RAN) {
                    return;
                }
                if (s == CANCELLED) {
                    // aggressively release to set runner to null,
                    // in case we are racing with a cancel request
                    // that will try to interrupt runner
                    releaseShared(0);
                    return;
                }
                if (compareAndSetState(s, RAN)) {
                    result = v;
                    releaseShared(0);
                    done();
                    return;
                }
            }
        }

        void innerSetException(Throwable t) {
            for (; ; ) {
                int s = getState();
                if (s == RAN) {
                    return;
                }
                if (s == CANCELLED) {
                    // aggressively release to set runner to null,
                    // in case we are racing with a cancel request
                    // that will try to interrupt runner
                    releaseShared(0);
                    return;
                }
                if (compareAndSetState(s, RAN)) {
                    exception = t;
                    releaseShared(0);
                    done();
                    return;
                }
            }
        }

        boolean innerCancel(boolean mayInterruptIfRunning) {
            for (; ; ) {
                int s = getState();
                if (ranOrCancelled(s)) {
                    return false;
                }
                if (compareAndSetState(s, CANCELLED)) {
                    break;
                }
            }
            if (mayInterruptIfRunning) {
                Thread r = runner;
                if (r != null) {
                    r.interrupt();
                }
            }
            releaseShared(0);
            done();
            return true;
        }

        void innerRun() {
            if (!compareAndSetState(READY, RUNNING)) {
                return;
            }

            runner = Thread.currentThread();
            if (getState() == RUNNING) { // recheck after setting thread
                R result;
                try {
                    result = callable.call();
                } catch (Throwable ex) {
                    setException(ex);
                    return;
                }
                set(result);
            } else {
                releaseShared(0); // cancel
            }
        }

    }
}
