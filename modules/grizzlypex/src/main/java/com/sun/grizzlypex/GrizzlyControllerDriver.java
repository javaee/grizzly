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

package com.sun.grizzlypex;

import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListener;
import com.sun.grizzly.SSLSelectorHandler;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.UDPSelectorHandler;
import com.sun.grizzlypex.utils.ControllerConfigurator;
import com.sun.grizzlypex.utils.ControllerUtils;
import com.sun.japex.Constants;
import com.sun.japex.TestCase;
import com.sun.japex.Util;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Alexey Stashok
 */
public class GrizzlyControllerDriver extends GrizzlyJapexDriverBase
        implements ControllerStateListener {
    
    private enum ControllerState {
        START, READY, STOP;
    };
    
    public static final int TIME_OUT = 30;
    private static final String START_CONTROLLER_METHOD = "startController";
    private static final String READY_CONTROLLER_METHOD = "readyController";
    private static final String STOP_CONTROLLER_METHOD = "stopController";
    
    private Controller controller;
    private CountDownLatch[] controllerStateLatches = new CountDownLatch[ControllerState.values().length];
    private double[] stateTime = new double[ControllerState.values().length];
    
    private Throwable controllerException;
    
    private ControllerState controllerTestState;
    
    private double totalDuration;
    private double startTime;
    
    private int iterationsNum;
    
    protected SelectorHandler getSelectorHandler(TestCase testCase) {
        TCPSelectorHandler selectorHandler = null;
        Controller.Protocol protocol = ControllerUtils.getProtocolByName(getParam("protocol"));
        
        switch(protocol) {
            case UDP:
                selectorHandler = new UDPSelectorHandler();
                break;
            case TLS:
                selectorHandler = new SSLSelectorHandler();
                break;
            default:
                selectorHandler = new TCPSelectorHandler();
        }
        
        selectorHandler.setPort(getPort(testCase));
        return selectorHandler;
    }
    
    protected Controller createController(TestCase testCase) {
        ControllerConfigurator controllerConfigurator = new ControllerConfigurator();
        controllerConfigurator.setReadThreadsCount(getReadThreadsCount(testCase));
        controllerConfigurator.addSelectorHandler(getSelectorHandler(testCase));
        return controllerConfigurator.createController();
    }
    
    @Override
    public void prepare(TestCase testCase) {
        totalDuration = 0;
        iterationsNum = 0;
        
        controllerTestState = getTestMethod(getMethodName(testCase));
    }
    
    @Override
    public void run(TestCase testCase) {
        startTime = 0;
        controllerException = null;
        controller = createController(testCase);
        for(int i=0; i<controllerStateLatches.length; i++) {
            controllerStateLatches[i] = new CountDownLatch(1);
            stateTime[i] = 0;
        }
        
        controller.addStateListener(this);
        
        if (controllerTestState == ControllerState.STOP) {
            startController(getLatch(ControllerState.READY));
        }
        
        CountDownLatch testLatch = getLatch(controllerTestState);
        
        switch(controllerTestState) {
            case START:
            case READY:
                startTime = Util.currentTimeMillis();
                startController(testLatch);
                break;
            case STOP:
                startTime = Util.currentTimeMillis();
                stopController(testLatch);
                break;
            default:
                throw new IllegalStateException("Unknown method: " + controllerTestState);
        }
        
        if (controllerException != null) {
            throw new IllegalStateException(controllerException);
        }
        
        if (controllerTestState != ControllerState.STOP) {
            waitOnLatch(getLatch(ControllerState.READY), TIME_OUT, TimeUnit.SECONDS);
            stopController(getLatch(ControllerState.STOP));
        }
        
        double testStopTime = stateTime[controllerTestState.ordinal()];
        
        synchronized(this) {
            totalDuration += (testStopTime - startTime);
            iterationsNum++;
        }
    }
    
    @Override
    public void finish(TestCase testCase) {
        double result = totalDuration/iterationsNum;
        testCase.setDoubleParam(Constants.RESULT_VALUE,
                result);
    }
    
    private void startController(CountDownLatch latch) {
        ControllerUtils.startControllerImmediately(controller);
        waitOnLatch(latch, TIME_OUT, TimeUnit.SECONDS);
    }
    
    private void stopController(CountDownLatch latch) {
        ControllerUtils.stopController(controller);
        controller = null;
        waitOnLatch(latch, TIME_OUT, TimeUnit.SECONDS);
    }
    
////////////// ControllerStateListener ////////////////////////////
    public void onStarted() {
        double startedStateTime = Util.currentTimeMillis();
        setStateTime(startedStateTime, ControllerState.START);
        getLatch(controllerTestState.START).countDown();
    }
    
    public void onReady() {
        double readyStateTime = Util.currentTimeMillis();
        setStateTime(readyStateTime, ControllerState.READY);
        getLatch(controllerTestState.READY).countDown();
    }
    
    public void onStopped() {
        double stoppedStateTime = Util.currentTimeMillis();
        setStateTime(stoppedStateTime, ControllerState.STOP);
        getLatch(controllerTestState.STOP).countDown();
    }
    
    public void onException(Throwable throwable) {
        controllerException = throwable;
        for(CountDownLatch latch : controllerStateLatches) {
            latch.countDown();
        }
    }
    
    private void waitOnLatch(CountDownLatch latch, long timeout, TimeUnit unit) {
        long timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, unit);
        long startTime = System.currentTimeMillis();
        
        do {
            try {
                latch.await(timeout, unit);
            } catch(InterruptedException e) {
                throw new IllegalStateException(e);
            }
            
            
        } while((latch.getCount() > 0) &&
                (System.currentTimeMillis() - startTime < timeoutMillis));
    }
    
    private CountDownLatch getLatch(ControllerState testMethod) {
        return controllerStateLatches[testMethod.ordinal()];
    }
    
    private void setStateTime(double value, ControllerState state) {
        stateTime[state.ordinal()] = value;
    }
    
    private static ControllerState getTestMethod(String methodName) {
        if (START_CONTROLLER_METHOD.equalsIgnoreCase(methodName)) {
            return ControllerState.START;
        } else if (READY_CONTROLLER_METHOD.equalsIgnoreCase(methodName)) {
            return ControllerState.READY;
        } else if (STOP_CONTROLLER_METHOD.equalsIgnoreCase(methodName)) {
            return ControllerState.STOP;
        }
        
        return null;
    }
}
