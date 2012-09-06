/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.utils;

import java.util.Collection;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.utils.conditions.Condition;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.localization.LogMessages;

/**
 * Class, which holds the state.
 * Provides API for state change notification, state read/write access locking.
 * 
 * @author Alexey Stashok
 */
public final class StateHolder<E> {
    private static final Logger _logger = Grizzly.logger(StateHolder.class);
    
    private volatile E state;
    
    private final ReentrantReadWriteLock readWriteLock;
    
    private final Collection<ConditionElement<E>> conditionListeners;
    
    /**
     * Constructs <code>StateHolder</code>.
     */
    public StateHolder() {
        this(null);
    }
    
    /**
     * Constructs <code>StateHolder</code>.
     */
    public StateHolder(E initialState) {
        state = initialState;
        readWriteLock = new ReentrantReadWriteLock();
        conditionListeners = new ConcurrentLinkedQueue<ConditionElement<E>>();
    }

    /**
     * Gets current state
     * Current StateHolder locking mode will be used
     * @return state
     */
    public E getState() {
        return state;
    }
    
    /**
     * Sets current state
     * Current StateHolder locking mode will be used
     * @param state
     */
    public void setState(E state) {
        readWriteLock.writeLock().lock();

        try {
            this.state = state;

            // downgrading lock to read
            readWriteLock.readLock().lock();
            readWriteLock.writeLock().unlock();

            notifyConditionListeners();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Gets Read/Write locker, which is used by this <code>StateHolder</code>
     * @return locker
     */
    public ReentrantReadWriteLock getStateLocker() {
        return readWriteLock;
    }
    
    /**
     * Register listener, which will be notified, when state will be equal to passed
     * one. Once listener will be notified - it will be removed from this 
     * <code>StateHolder</code>'s listener set.
     * @param state State, listener is interested in
     * @param completionHandler that will be notified. This <code>StateHolder</code>
     *          implementation works with Runnable, Callable, CountDownLatch, Object
     *          listeners
     * @return <code>ConditionListener</code>, if current state is not equal to required 
     *          and listener was registered, null if current state is equal to required.
     *          In both cases listener will be notified
     */
    public Future<E> notifyWhenStateIsEqual(final E state,
            final CompletionHandler<E> completionHandler) {
        return notifyWhenConditionMatchState(new Condition() {

                @Override
                public boolean check() {
                    return state == StateHolder.this.state;
                }

            }, completionHandler);
    }
    
    /**
     * Register listener, which will be notified, when state will become not equal 
     * to passed one. Once listener will be notified - it will be removed from
     * this <code>StateHolder</code>'s listener set.
     * @param state State, listener is interested in
     * @param completionHandler that will be notified. This <code>StateHolder</code>
     *          implementation works with Runnable, Callable, CountDownLatch, Object
     *          listeners
     * @return <code>ConditionListener</code>, if current state is equal to required 
     *          and listener was registered, null if current state is not equal to required.
     *          In both cases listener will be notified
     */
    public Future<E> notifyWhenStateIsNotEqual(final E state,
            final CompletionHandler<E> completionHandler) {
        return notifyWhenConditionMatchState(new Condition() {

                @Override
                public boolean check() {
                    return state != StateHolder.this.state;
                }

            }, completionHandler);
    }
    
    /**
     * Register listener, which will be notified, when state will match the condition.
     * Once listener will be notified - it will be removed from this 
     * <code>StateHolder</code>'s listener set.
     * @param condition Condition, the listener is interested in
     * @param completionHandler that will be notified. This <code>StateHolder</code>
     *          implementation works with Runnable, Callable, CountDownLatch, Object
     *          listeners
     * @return <code>ConditionListener</code>, if current state doesn't match the condition
     *          and listener was registered, null if current state matches the condition.
     *          In both cases listener will be notified
     */
    public Future<E> notifyWhenConditionMatchState(Condition condition,
            CompletionHandler<E> completionHandler) {
        Future<E> resultFuture;

        readWriteLock.readLock().lock();
        try {
            if (condition.check()) {
                if (completionHandler != null) {
                    completionHandler.completed(state);
                }
                resultFuture = ReadyFutureImpl.create(state);
            } else {
                final FutureImpl<E> future = SafeFutureImpl.create();
                final ConditionElement<E> elem =
                        new ConditionElement<E>(condition, future, completionHandler);
                conditionListeners.add(elem);
                resultFuture = future;
            }
        } finally {
            readWriteLock.readLock().unlock();
        }

        return resultFuture;
    }
    
    protected void notifyConditionListeners() {
        Iterator<ConditionElement<E>> it = conditionListeners.iterator();
        while(it.hasNext()) {
            ConditionElement<E> element = it.next();
            try {
                if (element.condition.check()) {
                    it.remove();
                    if (element.completionHandler != null) {
                        element.completionHandler.completed(state);
                    }

                    element.future.result(state);
                }
            } catch(Exception e) {
                _logger.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_STATE_HOLDER_CALLING_CONDITIONLISTENER_EXCEPTION(),
                        e);
            }
        }
    }

    protected static final class ConditionElement<E> {
        private final Condition condition;
        private final FutureImpl<E> future;
        private final CompletionHandler<E> completionHandler;
        
        public ConditionElement(Condition condition, FutureImpl<E> future,
                CompletionHandler<E> completionHandler) {
            this.condition = condition;
            this.future = future;
            this.completionHandler = completionHandler;
        }


        public CompletionHandler<E> getCompletionHandler() {
            return completionHandler;
        }

        public Condition getCondition() {
            return condition;
        }

        public FutureImpl<E> getFuture() {
            return future;
        }
    }
}
