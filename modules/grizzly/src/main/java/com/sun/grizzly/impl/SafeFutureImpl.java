/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Safe {@link FutureImpl} implementation.
 *
 * (Based on the JDK {@link java.util.concurrent.FutureTask})
 *
 * @see Future
 * 
 * @author Alexey Stashok
 */
public class SafeFutureImpl<R> implements FutureImpl<R> {
    private static final ThreadCache.CachedTypeIndex<SafeFutureImpl> CACHE_IDX =
            ThreadCache.obtainIndex(SafeFutureImpl.class, 4);
    /**
     * Construct {@link SafeFutureImpl}.
     */
    public static <R> SafeFutureImpl<R> create() {
        final SafeFutureImpl future = ThreadCache.takeFromCache(CACHE_IDX);
        if (future != null) {
            return future;
        }

        return new SafeFutureImpl<R>();
    }

    private AtomicInteger recycleMark = new AtomicInteger();

    /** Synchronization control for FutureTask */
    private final Sync sync;

    /**
     * Creates <tt>SafeFutureImpl</tt> 
     */
    public SafeFutureImpl() {
        sync = new Sync();
    }

    @Override
    public boolean isCancelled() {
        return sync.innerIsCancelled();
    }

    @Override
    public boolean isDone() {
        return sync.innerIsDone();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return sync.innerCancel(mayInterruptIfRunning);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    @Override
    public R get() throws InterruptedException, ExecutionException {
        return sync.innerGet();
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    @Override
    public R get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return sync.innerGet(unit.toNanos(timeout));
    }


    /**
     * Get current result value without any blocking.
     *
     * @return current result value without any blocking.
     */
    @Override
    public R getResult() {
        return sync.innerWeakGet();
    }

    /**
     * Set the result value and notify about operation completion.
     *
     * @param result the result value
     */
    @Override
    public void result(R result) {
        sync.innerSet(result);
    }

    /**
     * Notify about the failure, occured during asynchronous operation execution.
     *
     * @param failure
     */
    @Override
    public void failure(Throwable failure) {
        sync.innerSetException(failure);
    }

    @Override
    public void markForRecycle(boolean recycleResult) {
        final int mark = recycleResult ? 2 : 1;
        
        if (recycleMark.compareAndSet(0, mark)) {
            if (sync.innerIsDone()) {
                if (recycleMark.compareAndSet(mark, 3)) {
                    recycle(recycleResult);
                }
            }
        }
    }

    protected void reset() {
        sync.innerReset();
        recycleMark.set(0);
    }

    @Override
    public void recycle(boolean recycleResult) {
        final R result;
        if (recycleResult && (result = sync.innerWeakGet()) != null && result instanceof Cacheable) {
            ((Cacheable) result).recycle();
        }

        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public void recycle() {
        recycle(false);
    }
    
    /**
     * Protected method invoked when this task transitions to state
     * <tt>isDone</tt> (whether normally or via cancellation).
     */
    protected void done() {
        final int recycleValue = recycleMark.get();
        if ((recycleValue == 1 || recycleValue == 2) &&
                recycleMark.compareAndSet(recycleValue, 3)) {

            recycle(recycleValue == 2);
        }
    }

    // The following (duplicated) doc comment can be removed once
    //
    // 6270645: Javadoc comments should be inherited from most derived
    //          superinterface or superclass
    // is fixed.
    /**
     * Sets this Future to the result of its computation
     * unless it has been cancelled.
     */
//    public void run() {
//        sync.innerRun();
//    }

    /**
     * Executes the computation without setting its result, and then
     * resets this Future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     * @return true if successfully run and reset
     */
//    protected boolean runAndReset() {
//        return sync.innerRunAndReset();
//    }

    /**
     * Synchronization control for FutureTask. Note that this must be
     * a non-static inner class in order to invoke the protected
     * <tt>done</tt> method. For clarity, all inner class support
     * methods are same as outer, prefixed with "inner".
     *
     * Uses AQS sync state to represent run status
     */
    private final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -7828117401763700385L;

        /** State value representing that task is running */
//        private static final int RUNNING   = 1;
        /** State value representing that task ran */
        private static final int RAN       = 2;
        /** State value representing that task was cancelled */
        private static final int CANCELLED = 4;

        /** The result to return from get() */
        private R result;
        /** The exception to throw from get() */
        private Throwable exception;

        Sync() {
        }

        private boolean ranOrCancelled(int state) {
            return (state & (RAN | CANCELLED)) != 0;
        }

        /**
         * Implements AQS base acquire to succeed if ran or cancelled
         */
        @Override
        protected int tryAcquireShared(int ignore) {
            return innerIsDone()? 1 : -1;
        }

        /**
         * Implements AQS base release to always signal after setting
         * final done status by nulling runner thread.
         */
        @Override
        protected boolean tryReleaseShared(int ignore) {
//            runner = null;
            return true;
        }

        boolean innerIsCancelled() {
            return getState() == CANCELLED;
        }

        boolean innerIsDone() {
            return ranOrCancelled(getState());
//                    && runner == null;
        }

        R innerWeakGet() {
            if (getState() > -1) { // volatile get
                return result;
            }

            // Never should reach this code
            return null;
        }
        
        R innerGet() throws InterruptedException, ExecutionException {
            acquireSharedInterruptibly(0);
            if (getState() == CANCELLED)
                throw new CancellationException();
            if (exception != null)
                throw new ExecutionException(exception);
            return result;
        }

        R innerGet(long nanosTimeout) throws InterruptedException, ExecutionException, TimeoutException {
            if (!tryAcquireSharedNanos(0, nanosTimeout))
                throw new TimeoutException();
            if (getState() == CANCELLED)
                throw new CancellationException();
            if (exception != null)
                throw new ExecutionException(exception);
            return result;
        }

        void innerSet(R result) {
	    for (;;) {
		int s = getState();
		if (s == RAN)
		    return;
                if (s == CANCELLED) {
		    // aggressively release to set runner to null,
		    // in case we are racing with a cancel request
		    // that will try to interrupt runner
                    releaseShared(0);
                    return;
                }
		if (compareAndSetState(s, RAN)) {
                    this.result = result;
                    releaseShared(0);
                    done();
		    return;
                }
            }
        }

        void innerSetException(Throwable t) {
	    for (;;) {
		int s = getState();
		if (s == RAN)
		    return;
                if (s == CANCELLED) {
		    // aggressively release to set runner to null,
		    // in case we are racing with a cancel request
		    // that will try to interrupt runner
                    releaseShared(0);
                    return;
                }
		if (compareAndSetState(s, RAN)) {
                    exception = t;
                    result = null;
                    releaseShared(0);
                    done();
		    return;
                }
	    }
        }

        boolean innerCancel(boolean mayInterruptIfRunning) {
	    for (;;) {
		int s = getState();
		if (ranOrCancelled(s))
		    return false;
		if (compareAndSetState(s, CANCELLED))
		    break;
	    }
//            if (mayInterruptIfRunning) {
//                Thread r = runner;
//                if (r != null)
//                    r.interrupt();
//            }
            releaseShared(0);
            done();
            return true;
        }

        void innerReset() {
            result = null;
            exception = null;
            setState(0);
        }

//        void innerRun() {
//            if (!compareAndSetState(0, RUNNING))
//                return;
//            try {
//                runner = Thread.currentThread();
//                if (getState() == RUNNING) // recheck after setting thread
//                    innerSet(callable.call());
//                else
//                    releaseShared(0); // cancel
//            } catch (Throwable ex) {
//                innerSetException(ex);
//            }
//        }

//        boolean innerRunAndReset() {
//            if (!compareAndSetState(0, RUNNING))
//                return false;
//            try {
//                runner = Thread.currentThread();
//                if (getState() == RUNNING)
//                    callable.call(); // don't set result
//                runner = null;
//                return compareAndSetState(RUNNING, 0);
//            } catch (Throwable ex) {
//                innerSetException(ex);
//                return false;
//            }
//        }
    }
}
