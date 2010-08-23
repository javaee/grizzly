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

package com.sun.grizzly;

import com.sun.grizzly.util.State;
import com.sun.grizzly.util.StateHolder;
import com.sun.grizzly.util.StateHolder.ConditionListener;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class StateHolderTest extends TestCase {
    public void testDirectNotification() {
        StateHolder<State> stateHolder = new StateHolder<State>(true);
        stateHolder.setState(State.STARTED);
        
        CountDownLatch l = new CountDownLatch(2);
        
        ConditionListener<State> listener1 = 
                stateHolder.notifyWhenStateIsEqual(State.STARTED, l);
        ConditionListener<State> listener2 = 
            stateHolder.notifyWhenStateIsNotEqual(State.STOPPED, l);
        
        assertEquals(listener1, null);
        assertEquals(listener2, null);
        assertEquals(l.getCount(), 0);
    }
    
    public void testListenerNotification() {
        StateHolder<State> stateHolder = new StateHolder<State>(true);
        stateHolder.setState(State.STARTED);
        
        CountDownLatch l = new CountDownLatch(2);
        
        ConditionListener<State> listener1 = 
                stateHolder.notifyWhenStateIsEqual(State.PAUSED, l);
        ConditionListener<State> listener2 = 
            stateHolder.notifyWhenStateIsEqual(State.STOPPED, l);
        
        assertFalse(listener1 == null);
        assertFalse(listener2 == null);
        
        assertEquals(l.getCount(), 2);
        
        stateHolder.setState(State.PAUSED);
        assertEquals(l.getCount(), 1);
        
        stateHolder.setState(State.STOPPED);
        assertEquals(l.getCount(), 0);
    }

    public void testConcurrentSet() {
        final StateHolder<State> stateHolder = new StateHolder<State>(true);
        final State[] states = new State[] {State.STARTED, State.STOPPED, State.PAUSED};
        final Random r = new Random();
        
        Callable<Object>[] callables = new Callable[1000];
        for (int x = 0; x < callables.length; x++) {
            callables[x] = new Callable<Object>() {
                public Object call() throws Exception {
                    stateHolder.setState(states[r.nextInt(3)]);
                    return null;
                }
            };
        }
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Callable<Object>> c = Arrays.asList(callables);
        try {
            executor.invokeAll(c);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
        
        assert true;
    }
            
}
