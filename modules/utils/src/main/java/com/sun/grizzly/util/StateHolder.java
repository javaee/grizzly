/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

/**
 * Class, which holds the state.
 * Provides API for state change notification, state read/write access locking.
 * 
 * @author Alexey Stashok
 */
public class StateHolder<E> {
    private AtomicReference<E> stateRef;
    
    private ReentrantReadWriteLock readWriteLock;
    private volatile boolean isLockEnabled;
    
    private Map<ConditionListener<E>, Object> conditionListeners;
    
    /**
     * Constructs {@link StateHolder}.
     * StateHolder will work in not-locking mode.
     */
    public StateHolder() {
        this(false);
    }
    
    /**
     * Constructs {@link StateHolder}.
     * @param isLockEnabled locking mode
     */
    public StateHolder(boolean isLockEnabled) {
        stateRef = new AtomicReference<E>();
        readWriteLock = new ReentrantReadWriteLock();
        conditionListeners = new ConcurrentHashMap<ConditionListener<E>, Object>();
        this.isLockEnabled = isLockEnabled;
    }

    /**
     * Gets current state
     * Current StateHolder locking mode will be used
     * @return state
     */
    public E getState() {
        return getState(isLockEnabled);
    }
    
    /**
     * Gets current state
     * @param locked if true, get will be invoked in locking mode, false - non-locked
     * @return state
     */
    public E getState(boolean locked) {
        if (locked) {
            readWriteLock.readLock().lock();
        }
        
        E retState = stateRef.get();
        
        if (locked) {
            readWriteLock.readLock().unlock();
        }
        return retState;
    }

    /**
     * Sets current state
     * Current StateHolder locking mode will be used
     * @param state
     */
    public void setState(E state) {
        setState(state, isLockEnabled);
    }
    
    /**
     * Sets current state
     * @param state
     * @param locked if true, set will be invoked in locking mode, false - non-locking
     */
    public void setState(E state, boolean locked) {
        if (locked) {
            readWriteLock.writeLock().lock();
        }
        
        stateRef.set(state);
        
        // downgrading lock to read
        if (locked) {
            readWriteLock.readLock().lock();
            readWriteLock.writeLock().unlock();
        }
        
        checkConditionListeners(state);

        if (locked) {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Gets Read/Write locker, which is used by this {@link StateHolder}
     * @return locker
     */
    public ReentrantReadWriteLock getStateLocker() {
        return readWriteLock;
    }
    
    /**
     * Gets current locking mode
     * @return true, if mode is set to locking, false otherwise
     */
    public boolean isLockEnabled() {
        return isLockEnabled;
    }
    
    /**
     * Setss current locking mode
     * @param isLockEnabled true, if mode will be set to locking, false otherwise
     */
    public void setLockEnabled(boolean isLockEnabled) {
        this.isLockEnabled = isLockEnabled;
    }
    
    /**
     * Register listener, which will be notified, when state will be equal to passed
     * one. Once listener will be notified - it will be removed from this 
     * {@link StateHolder}'s listener set.
     * @param state State, listener is interested in
     * @param listener Object, which will be notified. This {@link StateHolder}
     *          implementation works with Runnable, Callable, CountDownLatch, Object
     *          listeners
     * @return <code>ConditionListener</code>, if current state is not equal to required 
     *          and listener was registered, null if current state is equal to required.
     *          In both cases listener will be notified
     */
    public ConditionListener<E> notifyWhenStateIsEqual(E state, Object listener) {
        boolean isLockEnabledLocal = isLockEnabled;
        if (isLockEnabledLocal) {
            getStateLocker().writeLock().lock();
        }
        
        ConditionListener<E> conditionListener = null;

        if (stateRef.get().equals(state)) {
            EventListener.notifyListener(listener);
        } else {
            conditionListener = new EqualConditionListener<E>();
            EventListener eventListener = new EventListener();
            eventListener.set(listener);
            conditionListener.set(state, eventListener);
            
            conditionListeners.put(conditionListener, this);
        }
        
        if (isLockEnabledLocal) {
            getStateLocker().writeLock().unlock();
        }
        
        return conditionListener;
    }
    
    /**
     * Register listener, which will be notified, when state will become not equal 
     * to passed one. Once listener will be notified - it will be removed from
     * this {@link StateHolder}'s listener set.
     * @param state State, listener is interested in
     * @param listener Object, which will be notified. This {@link StateHolder}
     *          implementation works with Runnable, Callable, CountDownLatch, Object
     *          listeners
     * @return <code>ConditionListener</code>, if current state is equal to required 
     *          and listener was registered, null if current state is not equal to required.
     *          In both cases listener will be notified
     */
    public ConditionListener<E> notifyWhenStateIsNotEqual(E state, Object listener) {
        boolean isLockEnabledLocal = isLockEnabled;
        if (isLockEnabledLocal) {
            getStateLocker().writeLock().lock();
        }
        
        ConditionListener<E> conditionListener = null;
        
        if (!stateRef.get().equals(state)) {
            EventListener.notifyListener(listener);
        } else {
            conditionListener = new NotEqualConditionListener<E>();
            EventListener eventListener = new EventListener();
            eventListener.set(listener);
            conditionListener.set(state, eventListener);
            
            conditionListeners.put(conditionListener, this);
        }
        
        if (isLockEnabledLocal) {
            getStateLocker().writeLock().unlock();
        }
        
        return conditionListener;
    }
    
    /**
     * Register custom condition listener, which will be notified, when listener's
     * condition will become true. Once listener will be notified - it will be 
     * removed from this {@link StateHolder}'s listener set.
     * @param conditionListener contains both condition and listener, which will be
     *          called, when condition become true
     */
    public void notifyWhenConditionMatchState(ConditionListener<E> conditionListener) {
        boolean isLockEnabledLocal = isLockEnabled;
        if (isLockEnabledLocal) {
            getStateLocker().writeLock().lock();
        }
        
        E currentState = getState();

        if (conditionListener.check(currentState)) {
            conditionListener.notifyListener();
        } else {
            conditionListeners.put(conditionListener, this);
        }
        
        if (isLockEnabledLocal) {
            getStateLocker().writeLock().unlock();
        }
    }
    
    public void removeConditionListener(ConditionListener<E> conditionListener) {
        if (conditionListener == null) return;
        
        conditionListeners.remove(conditionListener);
    }
    
    protected void checkConditionListeners(E state) {
        Iterator<ConditionListener<E>> it = conditionListeners.keySet().iterator();
        while(it.hasNext()) {
            ConditionListener<E> listener = it.next();
            try {
                if (listener.check(state)) {
                    it.remove();
                    listener.notifyListener();
                }
            } catch(Exception e) {
                LoggerUtils.getLogger().log(Level.WARNING, "Error calling ConditionListener", e);
            }
        }
    }
    
    /**
     * Common ConditionListener class, which could be used with StateHolder, to
     * register custom conditions.
     * 
     * On each state change - condition will be checked, if it's true - Condition's
     * listener will be notified.
     */
    public static abstract class ConditionListener<E> {
        public E state;
        public EventListener listener;
        
        protected void set(E state, EventListener listener) {
            this.state = state;
            this.listener = listener;
        }
        
        public void notifyListener() {
            listener.notifyEvent();
        }
        
        public abstract boolean check(E state);
    }
    
    /**
     * Equal ConditionListener implementation
     * @param E state class
     */
    public static class EqualConditionListener<E> extends ConditionListener<E> {
        public boolean check(E state) {
            return state.equals(this.state);
        }
    }

    /**
     * Not equal ConditionListener implementation
     * @param E state class
     */
    public static class NotEqualConditionListener<E> extends ConditionListener<E> {
        public boolean check(E state) {
            return !state.equals(this.state);
        }
    }
    
    /**
     * EventListener class, which is a part of 
     * <codE>EventConditionListener</code>, and implements notificatation logic,
     * when condition becomes true.
     */
    public static class EventListener {
        public Object notificationObject;
        
        public void set(Object notificationObject) {
            this.notificationObject = notificationObject;
        }
        
        public void notifyEvent() {
            notifyListener(notificationObject);
        }
        
        protected static void notifyListener(Object listener) {
            if (listener instanceof CountDownLatch) {
                ((CountDownLatch) listener).countDown();
            } else if (listener instanceof Callable) {
                try {
                    ((Callable) listener).call();
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (listener instanceof Runnable) {
                ((Runnable) listener).run();
            } else {
                synchronized(listener) {
                    listener.notify();
                }
            }
        }
    }
}
