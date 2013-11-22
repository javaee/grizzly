/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.filterchain;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.filterchain.FilterReg.Key;

/**
 * Default {@link FilterChainState} implementation, which holds a {@link Connection}
 * status associated with a {@link FilterChain}.
 */
public class DefaultFilterChainState implements FilterChainState {
    // predefined holders to keep READ state of 2 filters before using the stateMap
    private WeakReference<Key> r1, r2;
    private FilterState r1State, r2State;
    
    // predefined holders to keep WRITE state of 1 filter before using the stateMap
    private WeakReference<Key> w1;
    private FilterState w1State;
    
    // stateMap lock
    private volatile ReadWriteLock lock;
    // state map, shouldn't be normally used as r1, r2, w1 should be enough to cover most of the usecases.
    private Map<Key, Map<Event, FilterState>> stateMap;
    
    @Override
    public boolean hasFilterState(final FilterReg filterReg, final Event event) {
        return getFilterState(filterReg, event) != null;
    }

    @Override
    public FilterState obtainFilterState(final FilterReg filterReg,
            final Event event) {
        FilterState filterState = getFilterState(filterReg, event);
        
        if (filterState != null) {
            return filterState;
        }

        filterState = new FilterState(event);
        
        if (event == IOEvent.READ) {
            if (!isOccupied(r1, r1State)) {
                r1 = new WeakReference<Key>(filterReg.key);
                r1State = filterState;
                return filterState;
            }

            if (!isOccupied(r2, r2State)) {
                r2 = new WeakReference<Key>(filterReg.key);
                r2State = filterState;
                return filterState;
            }

        } else if (event == Event.USER_WRITE) {
            if (!isOccupied(w1, w1State)) {
                w1 = new WeakReference<Key>(filterReg.key);
                w1State = filterState;
                return filterState;
            }
        }
        
        if (lock == null) {
            synchronized (this) {
                if (lock == null) {
                    lock = new ReentrantReadWriteLock();
                }
            }
        }
        
        LazyStaticClass.add(this, filterReg, event, filterState);
        
        return filterState;
    }
    
    @Override
    public FilterState getFilterState(final FilterReg filterReg,
            final Event event) {
        if (event == IOEvent.READ) {
            if (hasState(r1, r1State, filterReg, event)) {
                return r1State;
            }
            
            if (hasState(r2, r2State, filterReg, event)) {
                return r2State;
            }
        } else if (event == Event.USER_WRITE) {
            if (hasState(w1, w1State, filterReg, event)) {
                return w1State;
            }
        }

        if (lock != null) {
            return LazyStaticClass.get(this, filterReg, event);
        }
        
        return null;
    }
    
    private static boolean hasState(final WeakReference<Key> ref,
            final FilterState state, final FilterReg filterReg,
            final Event event) {
        
        return ref != null && ref.get() == filterReg.key
                && state != null && state.getEvent() == event;
    }
    
    private static boolean isOccupied(final WeakReference<Key> ref,
            final FilterState state) {
        return ref != null && ref.get() != null && state != null
                && state.getRemainder() != null;
    }

    // Hide more complex logic path (involving Map lookup), which normally
    // will not be executed in the static class, so it will be lazily
    // initialized when it's really needed.
    private static class LazyStaticClass {

        private static FilterState get(final DefaultFilterChainState impl,
                final FilterReg filterReg, final Event event) {
            final Lock readLock = impl.lock.readLock();
            readLock.lock();
            
            try {
                final Map<Event, FilterState> states =
                        impl.stateMap.get(filterReg.key);
                
                return states ==  null ? null : states.get(event);
            } finally {
                readLock.unlock();
            }
        }

        private static void add(final DefaultFilterChainState impl,
                final FilterReg filterReg, final Event event,
                final FilterState filterState) {
            final Lock writeLock = impl.lock.writeLock();
            writeLock.lock();
            
            try {
                Map<Key, Map<Event, FilterState>> stateMap = impl.stateMap;
                if (stateMap == null) {
                    stateMap = new WeakHashMap<Key, Map<Event, FilterState>>(2);
                    impl.stateMap = stateMap;
                }
                
                Map<Event, FilterState> states = stateMap.get(filterReg.key);
                if (states == null) {
                    states = new HashMap<Event, FilterState>(2);
                    stateMap.put(filterReg.key, states);
                }
                
                states.put(event, filterState);
            } finally {
                writeLock.unlock();
            }
        }
    }
}
