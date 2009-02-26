/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http.utils;

import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListenerAdapter;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import com.sun.grizzly.http.SelectorThread;
import java.io.IOException;
import java.util.logging.Level;

/**
 * @author Alexey Stashok
 */
public class SelectorThreadUtils {

    /**
     *  Start controller in seperate thread
     */
    public static void startSelectorThread(final SelectorThread selectorThread) 
            throws IOException {
        
        selectorThread.setDisplayConfiguration(true);
        try {
            selectorThread.initEndpoint();
        } catch(InstantiationException e){
            throw new IOException(e.getMessage());
        }
        
        final CountDownLatch latch = new CountDownLatch(1);
        Controller controller = selectorThread.getController();
        controller.addStateListener(new ControllerStateListenerAdapter() {
            @Override
            public void onReady() {
                latch.countDown();
            }

            @Override
            public void onException(Throwable e) {
                SelectorThread.logger().log(Level.SEVERE, "Exception during " +
                        "starting the SelectorThread", e);
                latch.countDown();
            }
        });

        new Thread() {
            @Override
            public void run() {
                try {
                    selectorThread.startEndpoint();
                } catch (Exception ex) {
                }
            }
        }.start();

        try {
            latch.await();
        } catch (InterruptedException ex) {
        }
        
        if (!controller.isStarted()) {
            throw new IllegalStateException("SelectorThread is not started!");
        }
    }

    /**
     *  Stop controller in seperate thread
     */
    public static void stopSelectorThread(SelectorThread selectorThread) {
        selectorThread.stopEndpoint();
    }

    public static void startControllers(SelectorThread[] selectorThreads) throws Exception {
        startSelectorThreads(Arrays.asList(selectorThreads));
    }

    public static void startSelectorThreads(Collection<SelectorThread> selectorThreads) throws Exception {
        for (SelectorThread selectorThread : selectorThreads) {
            startSelectorThread(selectorThread);
        }
    }

    public static void stopControllers(SelectorThread[] selectorThreads) {
        stopSelectorThreads(Arrays.asList(selectorThreads));
    }

    public static void stopSelectorThreads(Collection<SelectorThread> selectorThreads) {
        for (SelectorThread selectorThread : selectorThreads) {
            stopSelectorThread(selectorThread);
        }
    }
}
