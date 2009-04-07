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

package org.glassfish.grizzly.benchmark;

import java.lang.reflect.Constructor;
import org.glassfish.grizzly.Strategy;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.DefaultMemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.threadpool.DefaultThreadPool;
import org.glassfish.grizzly.util.EchoFilter;

/**
 *
 * @author oleksiys
 */
public class TCPEchoServer {
    public static void main(String[] args) throws Exception {
        Settings settings = Settings.parse(args);
        System.out.println(settings);
        
        TransportFactory transportFactory = TransportFactory.getInstance();

        DefaultMemoryManager memoryManager = (DefaultMemoryManager)
                transportFactory.getDefaultMemoryManager();
        memoryManager.setMonitoring(false);

        DefaultThreadPool threadPool = (DefaultThreadPool)
                transportFactory.getDefaultWorkerThreadPool();
        int poolSize = (settings.getWorkerThreads());

        threadPool.setMaximumPoolSize(poolSize);
        threadPool.setCorePoolSize(poolSize);

        TCPNIOTransport transport = transportFactory.createTCPTransport();
        transport.setSelectorRunnersCount(settings.getSelectorThreads());
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        Strategy strategy = loadStrategy(settings.getStrategyClass(), transport);

        transport.setStrategy(strategy);

        try {
            transport.bind(settings.getHost(), settings.getPort());
            transport.start();

            System.out.println("Press enter to stop the server...");
            System.in.read();
        } finally {
            transport.stop();
            TransportFactory.getInstance().close();
        }

        System.out.println("Totaly allocated " + memoryManager.getTotalBytesAllocated() + " bytes");
    }

    public static Strategy loadStrategy(Class<? extends Strategy> strategy,
            Transport transport) {
        try {
            return strategy.newInstance();
        } catch (Exception e) {
            try {
                Constructor[] cs = strategy.getConstructors();
                for (Constructor c : cs) {
                    if (c.getParameterTypes().length == 1 && c.getParameterTypes()[0].isAssignableFrom(transport.getClass())) {
                        return (Strategy) c.newInstance(transport);
                    }
                }

                throw new IllegalStateException("Can not initialize strategy: " + strategy);
            } catch (Exception ee) {
                throw new IllegalStateException("Can not initialize strategy: " + strategy + ". Error: " + ee.getClass() + ": " + ee.getMessage());
            }
        }
    }
}
