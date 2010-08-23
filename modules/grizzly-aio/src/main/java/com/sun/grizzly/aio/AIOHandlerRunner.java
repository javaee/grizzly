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

package com.sun.grizzly.aio;

import com.sun.grizzly.Controller;
import com.sun.grizzly.aio.util.AIOOutputWriter;
import com.sun.grizzly.util.ByteBufferFactory.ByteBufferType;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.State;
import com.sun.grizzly.util.StateHolder;
import com.sun.grizzly.util.StateHolder.ConditionListener;
import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple {@link Runnable} used to manage the lifecycle of an {@link AIOHandler}
 * 
 * @author Jeanfrancois Arcand
 */
public class AIOHandlerRunner implements Runnable{
    
    
    private static boolean useCachedThreadPool = Boolean.valueOf(
            System.getProperty("com.sun.grizzly.cachedThreadPool", "false"))
                  .booleanValue();

    private static int maxCachedThreadPoolSize = Integer.valueOf(
            System.getProperty("com.sun.grizzly.cachedThreadPool.maxThreads", "50"))
                  .intValue();

    private AIOHandler aioHandler;
    private final Controller controller;
    private final Logger logger;
    private AsynchronousChannelGroup asyncChannelGroup;
    
    public AIOHandlerRunner(Controller controller) {
        this.controller = controller;
        this.logger = Controller.logger();
    }
    
    
    public void run() {        
        int maxThreads = DefaultThreadPool.DEFAULT_MAX_THREAD_COUNT;
        ExecutorService controllerThreadPool = controller.getThreadPool();
        if (controllerThreadPool instanceof ExtendedThreadPool) {
            maxThreads = ((ExtendedThreadPool) controllerThreadPool).
                    getMaximumPoolSize();
        }
        
        int corePoolThreads = maxThreads;
        String threadPoolFactory = System.
                getProperty("java.nio.channels.DefaultThreadPool.threadFactory");
        
        if (threadPoolFactory == null){ 
            System.setProperty("java.nio.channels.DefaultThreadPool.threadFactory"
                    ,com.sun.grizzly.util.WorkerThreadFactory.class.getName());
            logger.info("Swithching AIO Thread Pool to: " 
                    + com.sun.grizzly.util.WorkerThreadFactory.class.getName());
        }

        if (useCachedThreadPool){
            maxThreads = maxCachedThreadPoolSize;
        }
        
        if (useCachedThreadPool && corePoolThreads > maxCachedThreadPoolSize){
            logger.warning("cachedThreadPool.maxThreads cannot be lower than maxThreads: " + maxThreads
                    + " .Ignoring the value");
            maxThreads = corePoolThreads;
        }

        DefaultThreadPool threadPool = new DefaultThreadPool();
        threadPool.setMaximumPoolSize(maxThreads);
        threadPool.setCorePoolSize(corePoolThreads);
       

        threadPool.setByteBufferType(ByteBufferType.DIRECT);
        
        ThreadFactory tf = null;
        
        if (threadPoolFactory != null){
            try{
                tf = (ThreadFactory)(Class.forName(threadPoolFactory,true,
                    Thread.currentThread().getContextClassLoader())).newInstance();
            } catch (Throwable t){
                Controller.logger().warning(t.getMessage());
            }

            threadPool.setThreadFactory(tf);
        }
        aioHandler.setThreadPool(threadPool);
        controller.setThreadPool(threadPool);
        
        try{
            if (useCachedThreadPool){
                logger.info("Using CachedThreadPool with asynchronous write set to " 
                        + AIOOutputWriter.ASYNC_WRITE);
                asyncChannelGroup = AsynchronousChannelGroup
                        .withFixedThreadPool(maxThreads, tf);
            } else {
                logger.info("Using FixedThreadPool with asynchronous write set to " 
                        + AIOOutputWriter.ASYNC_WRITE);
                asyncChannelGroup = AsynchronousChannelGroup
                        .withThreadPool(threadPool);
            }
        } catch (IOException ex){
            logger.log(Level.SEVERE, "ThreadPoolCreation exception",ex);
        }
        aioHandler.setAynchronousChannelGroup(asyncChannelGroup);

        StateHolder<State> controllerStateHolder = controller.getStateHolder();
        StateHolder<State> selectorHandlerStateHolder = aioHandler.getStateHolder();
        
        try {
            aioHandler.start();
            aioHandler.getStateHolder().setState(State.STARTED);
            CountDownLatch latch = new CountDownLatch(1);

            while (controllerStateHolder.getState(false) != State.STOPPED &&
                    selectorHandlerStateHolder.getState(false) != State.STOPPED) {
                
                State controllerState = controllerStateHolder.getState(false);
                State selectorHandlerState = selectorHandlerStateHolder.getState(false);
                if (controllerState != State.PAUSED &&
                        selectorHandlerState != State.PAUSED) {
                    try {
                        latch.await(5, TimeUnit.SECONDS);
                    }catch(InterruptedException e) {
                        return;
                    } 
                } else {
                    ConditionListener<State> controllerConditionListener = 
                            registerForNotification(controllerState, 
                            controllerStateHolder, latch);
                    ConditionListener<State> selectorHandlerConditionListener = 
                            registerForNotification(selectorHandlerState, 
                            selectorHandlerStateHolder, latch);
                    
                    try {
                        latch.await(5000, TimeUnit.MILLISECONDS);
                    } catch(InterruptedException e) {
                    } finally {
                        controllerStateHolder.removeConditionListener(controllerConditionListener);
                        selectorHandlerStateHolder.removeConditionListener(selectorHandlerConditionListener);
                    }
                }
            }
        } finally {
            aioHandler.shutdown();
            controller.notifyStopped();
        }        
    }

    
    public void setIOHandler(AIOHandler aioHandler) {
        this.aioHandler = aioHandler;
    }

    
    public AIOHandler getIOHandler() {
        return aioHandler;
    }
    
    
    /**
     * Register {@link CountDownLatch} to be notified, when either Controller
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
