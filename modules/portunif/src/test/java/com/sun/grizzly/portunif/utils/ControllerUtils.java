/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.portunif.utils;

import com.sun.grizzly.*;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

/**
 * @author Alexey Stashok
 */
public class ControllerUtils {
    
    /**
     *  Start controller in seperate thread
     */
    public static void startController(final Controller controller) {
        final CountDownLatch latch = new CountDownLatch(1);
        controller.addStateListener(new ControllerStateListenerAdapter() {
            @Override
            public void onReady() {
                latch.countDown();
            }

            @Override
            public void onException(Throwable e) {
                if (latch.getCount() > 0) {
                    Controller.logger().log(Level.SEVERE, "Exception during " +
                            "starting the controller", e);
                    latch.countDown();
                } else {
                    Controller.logger().log(Level.SEVERE, "Exception during " +
                            "controller processing", e);
                }
            }
        });

        new WorkerThreadImpl("ControllerWorker", controller).start();

        try {
            latch.await();
        } catch (InterruptedException ex) {
        }
        
        if (!controller.isStarted()) {
            throw new IllegalStateException("Controller is not started!");
        }
    }
    
    /**
     *  Stop controller in seperate thread
     */
    public static void stopController(Controller controller) {
        try {
            controller.stop();
        } catch(IOException e) {
        }
    }
    
    public static void startControllers(Controller[] controllers) {
        startControllers(Arrays.asList(controllers));
    }

    public static void startControllers(Collection<Controller> controllers) {
        for(Controller controller : controllers) {
            startController(controller);
        }
    }

    public static void stopControllers(Controller[] controllers) {
        stopControllers(Arrays.asList(controllers));
    }

    public static void stopControllers(Collection<Controller> controllers) {
        for(Controller controller : controllers) {
            stopController(controller);
        }
    }
}
