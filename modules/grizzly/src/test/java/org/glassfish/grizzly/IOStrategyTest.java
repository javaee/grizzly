/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;
import java.io.IOException;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.impl.FutureImpl;
import java.util.concurrent.TimeUnit;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.junit.Test;
import org.junit.Before;
import org.junit.runners.Parameterized.Parameters;
import java.util.Collection;
import java.util.Arrays;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.strategies.LeaderFollowerNIOStrategy;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.strategies.SimpleDynamicNIOStrategy;
import org.glassfish.grizzly.strategies.WorkerThreadIOStrategy;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.StringFilter;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Basic IOStrategies test.
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
@SuppressWarnings("unchecked")
public class IOStrategyTest {
    private static final int PORT = 7789;
    
    private final IOStrategy strategy;
    
    @Parameters
    public static Collection<Object[]> getLazySslInit() {
        return Arrays.asList(new Object[][]{
                    {WorkerThreadIOStrategy.getInstance()},
                    {LeaderFollowerNIOStrategy.getInstance()},
                    {SameThreadIOStrategy.getInstance()},
                    {SimpleDynamicNIOStrategy.getInstance()}});
    }

    @Before
    public void before() throws Exception {
        Grizzly.setTrackingThreadCache(true);
    }

    public IOStrategyTest(final IOStrategy strategy) {
        this.strategy = strategy;
    }

    
    @Test
    public void testSimplePackets() throws Exception {
        final Integer msgNum = 100;
        final String pattern = "Message #";
        final int clientsNum = Runtime.getRuntime().availableProcessors() * 4;
        
        Connection connection = null;

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new StringFilter(Charsets.UTF8_CHARSET));
        filterChainBuilder.add(new EchoFilter());
        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .setIOStrategy(strategy)
                .build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            for (int i = 0; i < clientsNum; i++) {
                final FutureImpl<Integer> resultEcho = SafeFutureImpl.create();
                FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
                clientFilterChainBuilder.add(new TransportFilter());
                clientFilterChainBuilder.add(new StringFilter(Charsets.UTF8_CHARSET));
                clientFilterChainBuilder.add(new EchoResultFilter(msgNum, pattern, resultEcho));

                final FilterChain clientChain = clientFilterChainBuilder.build();

                SocketConnectorHandler connectorHandler =
                        TCPNIOConnectorHandler.builder(transport)
                        .processor(clientChain)
                        .build();

                Future<Connection> connectFuture = connectorHandler.connect(
                        new InetSocketAddress("localhost", PORT));

                connection = connectFuture.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);

                for (int j = 0; j < msgNum; j++) {
                    connection.write(pattern + j);
                }

                assertEquals(msgNum, resultEcho.get(10, TimeUnit.SECONDS));
                
                connection.close();
                connection = null;
            }
            
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
        }
    }
    
    private static final class EchoResultFilter extends BaseFilter {
        // handleReads should be executed synchronously, so plain "int" is ok
        private int counter;

        private final int msgNum;
        private final String pattern;
        private final FutureImpl<Integer> resultFuture;
        private EchoResultFilter(Integer msgNum, String pattern,
                FutureImpl<Integer> resultFuture) {
            this.msgNum = msgNum;
            this.pattern = pattern;
            this.resultFuture = resultFuture;
        }
        
        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            final String msg = ctx.getMessage();
            final String check = pattern + counter;
            
            if (!check.equals(msg)) {
                resultFuture.failure(new IllegalStateException(
                        "Unexpected echo came: " + msg +
                        ". Expected response: " + check));
                return ctx.getStopAction();
            }
            
            counter++;
            
            if (counter == msgNum) {
                resultFuture.result(msgNum);
            }
            
            return ctx.getStopAction();
        }
        
    }
    
}
