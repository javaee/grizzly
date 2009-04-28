/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly;

import com.sun.grizzly.util.State;
import com.sun.grizzly.util.StateHolder;
import com.sun.grizzly.util.StateHolder.ConditionListener;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Class is responsible for processing certain (single)
 * {@link SelectorHandler}
 * 
 * @author Alexey Stashok
 */
public class SelectorHandlerRunner implements Runnable {

    private final SelectorHandler selectorHandler;
    private final Controller controller;
    
    public SelectorHandlerRunner(Controller controller, SelectorHandler selectorHandler) {
        this.controller = controller;
        this.selectorHandler = selectorHandler;
    }
    
    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }
    
    public void run() {
        
        ((WorkerThreadImpl)Thread.currentThread()).setPendingIOhandler(selectorHandler);

        NIOContext serverCtx = controller.pollContext(null,null);
        serverCtx.setSelectorHandler(selectorHandler);

        StateHolder<State> controllerStateHolder = controller.getStateHolder();
        StateHolder<State> selectorHandlerStateHolder = selectorHandler.getStateHolder();
        
        try {
            selectorHandler.getStateHolder().setState(State.STARTED);

            State controllerState;
            State selectorHandlerState;
            while ((controllerState = controllerStateHolder.getState(false)) != State.STOPPED &&
                    (selectorHandlerState = selectorHandlerStateHolder.getState(false)) != State.STOPPED) {
                
                if (controllerState != State.PAUSED &&
                        selectorHandlerState != State.PAUSED) {
                    controller.doSelect(selectorHandler,serverCtx);
                } else {
                    doSelectorPaused(controllerState, selectorHandlerState);
                }
            }
        } finally {
            selectorHandler.shutdown();
            controller.notifyStopped();
        }
    }
    
    /**
     * handle Selector paused state
     */
    private void doSelectorPaused(State controllerState, State selectorHandlerState){
        CountDownLatch latch = new CountDownLatch(1);
        ConditionListener<State> controllerConditionListener =
                registerForNotification(controllerState,
                controller.getStateHolder(), latch);
        ConditionListener<State> selectorHandlerConditionListener =
                registerForNotification(selectorHandlerState,
                 selectorHandler.getStateHolder(), latch);

        try {
            latch.await(5000, TimeUnit.MILLISECONDS);
        } catch(InterruptedException e) {
        } finally {
            controller.getStateHolder().removeConditionListener(controllerConditionListener);
            selectorHandler.getStateHolder().removeConditionListener(selectorHandlerConditionListener);
        }
    }

    /**
     * Register <code>CountDownLatch</code> to be notified, when either Controller
     * or SelectorHandler will change their state to correspondent values
     * @param currentState initial/current {@link StateHolder} state
     * @param stateHolder
     * @param latch
     * @return <code>ConditionListener</code> if listener is registered, null if
     *        condition is true right now
     */
    private ConditionListener<State> registerForNotification(State currentState, 
            StateHolder<State> stateHolder, CountDownLatch latch) {
        if (currentState == State.PAUSED) {
            return stateHolder.notifyWhenStateIsNotEqual(State.PAUSED, latch);
        } else {
            return stateHolder.notifyWhenStateIsEqual(State.STOPPED, latch);
        }
    }
}
