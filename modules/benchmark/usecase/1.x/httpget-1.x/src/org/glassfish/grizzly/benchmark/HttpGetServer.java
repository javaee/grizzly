/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.benchmark;

import org.glassfish.grizzly.Controller;
import org.glassfish.grizzly.http.SelectorThread;
import org.glassfish.grizzly.http.SelectorThreadKeyHandler;
import org.glassfish.grizzly.util.SelectionKeyAttachment;
import java.net.InetAddress;

/**
 *
 * @author oleksiys
 */
public class HttpGetServer {
    public static void main(String[] args) throws Exception {
        final Settings settings = Settings.parse(args);
        System.out.println(settings);

        final Controller controller = new Controller();
        // Commented out as advised by JFA.
        //controller.setReadThreadsCount(Runtime.getRuntime().availableProcessors());
        controller.setReadThreadsCount(settings.getSelectorThreads() - 1);
        controller.setHandleReadWriteConcurrently(true);
        controller.useLeaderFollowerStrategy(settings.useLeaderFollower());

        int poolSize = (settings.getWorkerThreads() + settings.getSelectorThreads());

        SelectorThread selectorThread = new SelectorThread() {

            @Override
            protected SelectorThreadKeyHandler createSelectionKeyHandler() {
                final SelectorThreadKeyHandler handler = super.createSelectionKeyHandler();
                handler.setTimeout(SelectionKeyAttachment.UNLIMITED_TIMEOUT);
                return handler;
            }
        };
        
        selectorThread.setController(controller);
        selectorThread.setMaxThreads(poolSize);
        selectorThread.setCoreThreads(poolSize);
//        selectorThread.setInet(InetAddress.getByName(settings.getHost()));
        selectorThread.setPort(settings.getPort());
        selectorThread.setTcpNoDelay(true);



        selectorThread.setAdapter(new HttpGetAdapter());

        selectorThread.listen();

        System.out.println("Grizzly HttpGetServer is ready to serve at port " + settings.getPort() + ".");

        if (controller.isStarted()) {
            System.out.println("Press enter to exit...");
            System.in.read();
            selectorThread.stopEndpoint();
        }
    }
}
