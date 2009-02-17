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
/*
 * ControllerUtils.java
 */

package com.sun.grizzlypex.utils;

import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListener;
import com.sun.grizzly.ControllerStateListenerAdapter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

/**
 * @author Alexey Stashok
 */
public class ControllerUtils {
    
    /**
     *  Start controller in seperate thread
     */
    public static void startControllerImmediately(final Controller controller) {
        new Thread(controller).start();
    }

    /**
     *  Start controller in seperate thread and wait until it is ready
     */
    public static void startController(final Controller controller) {
        final CountDownLatch latch = new CountDownLatch(1);
        
        ControllerStateListener controllerStateListener = new ControllerStateListenerAdapter() {
            @Override
            public void onReady() {
                latch.countDown();
            }
            
            @Override
            public void onException(Throwable throwable) {
                throwable.printStackTrace();
                latch.countDown();
            }
        };
        controller.addStateListener(controllerStateListener);
        
        new Thread(controller).start();
        
        try {
            latch.await();
        } catch (InterruptedException ex) {
        }
    }
    
    /**
     *  Stop controller in seperate thread
     */
    public static void stopController(Controller controller) {
        try {
            controller.stop();
        } catch (IOException ex) {
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
    
    public static Controller.Protocol getProtocolByName(String protocolName) {
        if ("tcp".equalsIgnoreCase(protocolName)) {
            return Controller.Protocol.TCP;
        } else if ("udp".equalsIgnoreCase(protocolName)) {
            return Controller.Protocol.UDP;
        } else if ("tls".equalsIgnoreCase(protocolName) || "ssl".equalsIgnoreCase(protocolName)) {
            return Controller.Protocol.TLS;
        }
        
        return null;
    }
}
