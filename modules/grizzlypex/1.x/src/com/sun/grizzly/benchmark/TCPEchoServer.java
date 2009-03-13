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

package com.sun.grizzly.benchmark;

import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.filter.EchoAsyncWriteQueueFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.util.DefaultThreadPool;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author oleksiys
 */
public class TCPEchoServer {
    public static void main(String[] args) throws Exception {
        final Settings settings = Settings.parse(args);
        System.out.println(settings);


        // XXX I couldn't set the memory limitation because I couldn't find
        // a property related with memory limitation.  Perhaps a user needs
        // to implement one by his or her own?

        TCPSelectorHandler selector = new TCPSelectorHandler();
        selector.setPort(settings.getPort());
        selector.setTcpNoDelay(true);

        int poolSize = (settings.getWorkerThreads() + settings.getSelectorThreads());
        // TODO Need to write a new pipeline to support nothreadpool mode.
        ExecutorService threadPool = new DefaultThreadPool(poolSize, poolSize,
                DefaultThreadPool.DEFAULT_MAX_TASKS_QUEUED,
                DefaultThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
                TimeUnit.MILLISECONDS);

        ProtocolChainInstanceHandler pciHandler =
            new ProtocolChainInstanceHandler() {

            final private ProtocolChain protocolChain = new DefaultProtocolChain();

            public ProtocolChain poll() {
                return protocolChain;
            }

            public boolean offer(ProtocolChain instance) {
                return true;
            }
        };

        final Controller controller = new Controller();
        // Commented out as advised by JFA.
        //controller.setReadThreadsCount(Runtime.getRuntime().availableProcessors());
        controller.setReadThreadsCount(settings.getSelectorThreads() - 1);
        controller.addSelectorHandler(selector);
        controller.setHandleReadWriteConcurrently(true);
        controller.setThreadPool(threadPool);
        controller.setProtocolChainInstanceHandler(pciHandler);

        ProtocolChain protocolChain = pciHandler.poll();
        protocolChain.addFilter(new ReadFilter());
        protocolChain.addFilter(new EchoAsyncWriteQueueFilter());
//        protocolChain.addFilter(new EchoFilter());

        final CountDownLatch latch = new CountDownLatch(1);
        
        controller.addStateListener(new ControllerStateListenerAdapter() {

            @Override
            public void onException(Throwable e) {
                e.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onReady() {
                System.out.println("Grizzly EchoServer is ready to serve at port " + settings.getPort() + ".");
                latch.countDown();
            }
        });
        
        new Thread(controller).start();

        latch.await();

        if (controller.isStarted()) {
            System.out.println("Press enter to exit...");
            System.in.read();

            controller.stop();
        }
    }
}
