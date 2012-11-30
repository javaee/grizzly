/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.portunif;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.StringFilter;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Simple port-unification test
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class BasicPUTest {
    public static final int PORT = 17400;
    public static final Charset CHARSET = Charset.forName("UTF-8");
    
    @Test
    public void protocolsXYZ() throws Exception {
        final String[] protocols = {"X", "Y", "Z"};

        Connection connection = null;

        final AtomicInteger blockingWritesCounter = new AtomicInteger();
        final AtomicInteger nonBlockingWritesCounter = new AtomicInteger();
        
        int i = 0;
        final PUFilter puFilter = new PUFilter();
        for (final String protocol : protocols) {
            final boolean isBlocking = (i++ % 2) == 0;
            puFilter.register(createProtocol(puFilter, protocol, isBlocking));
        }

        FilterChainBuilder puFilterChainBuilder = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new BaseFilter() {
                    @Override
                    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
                        if (ctx.getTransportContext().isBlocking()) {
                            blockingWritesCounter.incrementAndGet();
                        } else {
                            nonBlockingWritesCounter.incrementAndGet();
                        }
                        
                        return ctx.getInvokeAction();
                    }
                })
                .add(new StringFilter(CHARSET))
                .add(puFilter);

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setFilterChain(puFilterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            for (final String protocol : protocols) {
                final FutureImpl<Boolean> resultFuture = SafeFutureImpl.create();
                
                final FilterChain clientFilterChain =
                        FilterChainBuilder.stateless()
                        .add(new TransportFilter())
                        .add(new StringFilter(CHARSET))
                        .add(new ClientResultFilter(protocol, resultFuture))
                        .build();

                final SocketConnectorHandler connectorHandler =
                        TCPNIOConnectorHandler.builder(transport)
                        .filterChain(clientFilterChain)
                        .build();

                Future<Connection> future = connectorHandler.connect("localhost", PORT);
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);

                connection.write(protocol);

                assertTrue(resultFuture.get(10, TimeUnit.SECONDS));
            }
            
            final int expectedBlockingWrites = protocols.length / 2 + (protocols.length % 2);
            assertEquals("Number of blocking writes doesn't match", expectedBlockingWrites, blockingWritesCounter.get());
            assertEquals("Number of non-blocking writes doesn't match", protocols.length - expectedBlockingWrites, nonBlockingWritesCounter.get());
            

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }


    @Test
    public void testGrizzly1031_001() throws Exception {

        final TestPUFilter puFilter = new TestPUFilter();
        final TestFinder f1 =  new TestFinder() {
            @Override
            public Result find(PUContext puContext, FilterChainContext ctx) {
                invocationCount++;
                if (invocationCount <= 1) {
                    return Result.NEED_MORE_DATA;
                } else {
                    return Result.NOT_FOUND;
                }
            }
        };
        final TestFinder f2 = new TestFinder() {
            @Override
            public Result find(PUContext puContext, FilterChainContext ctx) {
                invocationCount++;
                if (invocationCount <= 3) {
                    return Result.NEED_MORE_DATA;
                } else {
                    return Result.NOT_FOUND;
                }
            }
        };
        final TestFinder f3 = new TestFinder() {
            @Override
            public Result find(PUContext puContext, FilterChainContext ctx) {
                invocationCount++;
                if (invocationCount <= 2) {
                    return Result.NEED_MORE_DATA;
                } else {
                    return Result.NOT_FOUND;
                }
            }
        };
        final TestFinder f4 = new TestFinder() {
            @Override
            public Result find(PUContext puContext, FilterChainContext ctx) {
                invocationCount++;
                if (invocationCount == 5) {
                    return Result.FOUND;
                } else {
                    return Result.NEED_MORE_DATA;
                }
            }
        };
        puFilter.register(f1, puFilter.getPUFilterChainBuilder().add(new EchoFilter()).build());
        puFilter.register(f2, puFilter.getPUFilterChainBuilder().add(new EchoFilter()).build());
        puFilter.register(f3, puFilter.getPUFilterChainBuilder().add(new EchoFilter()).build());
        puFilter.register(f4, puFilter.getPUFilterChainBuilder().add(new EchoFilter()).build());

        final FilterChainContext ctx = new FilterChainContext();
        final PUContext puContext = new PUContext(puFilter);

        puFilter.findProtocol(puContext, ctx);
        assertTrue(!puContext.noProtocolsFound());
        assertEquals(1, f1.invocationCount);
        assertEquals(1, f2.invocationCount);
        assertEquals(1, f3.invocationCount);
        assertEquals(1, f4.invocationCount);

        puFilter.findProtocol(puContext, ctx);
        assertTrue(!puContext.noProtocolsFound());
        assertEquals(2, f1.invocationCount);
        assertEquals(2, f2.invocationCount);
        assertEquals(2, f3.invocationCount);
        assertEquals(2, f4.invocationCount);

        puFilter.findProtocol(puContext, ctx);
        assertTrue(!puContext.noProtocolsFound());
        assertEquals(2, f1.invocationCount);
        assertEquals(3, f2.invocationCount);
        assertEquals(3, f3.invocationCount);
        assertEquals(3, f4.invocationCount);

        puFilter.findProtocol(puContext, ctx);
        assertTrue(!puContext.noProtocolsFound());
        assertEquals(2, f1.invocationCount);
        assertEquals(4, f2.invocationCount);
        assertEquals(3, f3.invocationCount);
        assertEquals(4, f4.invocationCount);

        puFilter.findProtocol(puContext, ctx);
        assertTrue(!puContext.noProtocolsFound());
        assertEquals(2, f1.invocationCount);
        assertEquals(4, f2.invocationCount);
        assertEquals(3, f3.invocationCount);
        assertEquals(5, f4.invocationCount);

    }

    @Test
    public void testGrizzly1031_002() throws Exception {

        final TestPUFilter puFilter = new TestPUFilter();
        final TestFinder f1 =  new TestFinder() {
            @Override
            public Result find(PUContext puContext, FilterChainContext ctx) {
                invocationCount++;
                return Result.NOT_FOUND;
            }
        };
        final TestFinder f2 = new TestFinder() {
            @Override
            public Result find(PUContext puContext, FilterChainContext ctx) {
                invocationCount++;
                return Result.NOT_FOUND;
            }
        };
        final TestFinder f3 = new TestFinder() {
            @Override
            public Result find(PUContext puContext, FilterChainContext ctx) {
                invocationCount++;
                return Result.NOT_FOUND;
            }
        };
        final TestFinder f4 = new TestFinder() {
            @Override
            public Result find(PUContext puContext, FilterChainContext ctx) {
                invocationCount++;
                return Result.NOT_FOUND;
            }
        };
        puFilter.register(f1, puFilter.getPUFilterChainBuilder().add(new EchoFilter()).build());
        puFilter.register(f2, puFilter.getPUFilterChainBuilder().add(new EchoFilter()).build());
        puFilter.register(f3, puFilter.getPUFilterChainBuilder().add(new EchoFilter()).build());
        puFilter.register(f4, puFilter.getPUFilterChainBuilder().add(new EchoFilter()).build());

        final FilterChainContext ctx = new FilterChainContext();
        final PUContext puContext = new PUContext(puFilter);

        puFilter.findProtocol(puContext, ctx);
        assertTrue(puContext.noProtocolsFound());
        assertEquals(1, f1.invocationCount);
        assertEquals(1, f2.invocationCount);
        assertEquals(1, f3.invocationCount);
        assertEquals(1, f4.invocationCount);

    }

    @Test
    public void notifyUpstream() throws Exception {
        final String[] protocols = {"X", "Y", "Z"};
        final CountDownLatch[] counters = {new CountDownLatch(1),
            new CountDownLatch(1), new CountDownLatch(1)};
        final FutureImpl[] serverConnectionFutures = {Futures.<Connection>createSafeFuture(),
            Futures.<Connection>createSafeFuture(), Futures.<Connection>createSafeFuture()};
        
        Connection connection = null;

        final PUFilter puFilter = new PUFilter();
        for (int i = 0; i < protocols.length; i++) {
            final String protocol = protocols[i];
            puFilter.register(createProtocol(puFilter, protocol,
                    new EventCounterFilter(counters[i], CustomEvent.TYPE, serverConnectionFutures[i])));
        }

        FilterChainBuilder puFilterChainBuilder = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new StringFilter(CHARSET))
                .add(puFilter);

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setFilterChain(puFilterChainBuilder.build());

        try {
            transport.bind(PORT);
            transport.start();

            for (int i = 0; i < protocols.length; i++) {
                
                final String protocol = protocols[i];
                final FutureImpl<Boolean> resultFuture = SafeFutureImpl.create();
                
                final FilterChain clientFilterChain =
                        FilterChainBuilder.stateless()
                        .add(new TransportFilter())
                        .add(new StringFilter(CHARSET))
                        .add(new ClientResultFilter(protocol, resultFuture))
                        .build();

                final SocketConnectorHandler connectorHandler =
                        TCPNIOConnectorHandler.builder(transport)
                        .filterChain(clientFilterChain)
                        .build();

                Future<Connection> future = connectorHandler.connect("localhost", PORT);
                connection = future.get(10, TimeUnit.SECONDS);
                assertTrue(connection != null);

                connection.write(protocol);

                assertTrue(resultFuture.get(10, TimeUnit.SECONDS));
                
                final Connection serverSideConnection =
                        (Connection) serverConnectionFutures[i].get(10, TimeUnit.SECONDS);
                
                transport.fireEvent(new CustomEvent(), serverSideConnection);
                assertTrue("Event haven't come. Counter: " + counters[i].getCount(), counters[i].await(10, TimeUnit.SECONDS));
            }

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    // --------------------------------------------------------- Private Methods

    private PUProtocol createProtocol(final PUFilter puFilter, final String name,
            Filter... additionalFilters) {
        return createProtocol(puFilter, name, false, additionalFilters);
    }

    private PUProtocol createProtocol(final PUFilter puFilter, final String name,
            final boolean isBlocking, Filter... additionalFilters) {
        final FilterChainBuilder puFilterChainBuilder = 
                puFilter.getPUFilterChainBuilder();
        
        for (Filter additionalFilter : additionalFilters) {
            puFilterChainBuilder.add(additionalFilter);
        }
        
        final FilterChain chain = puFilterChainBuilder
                .add(new SimpleResponseFilter(name, isBlocking))
                .build();
        
        return new PUProtocol(new SimpleProtocolFinder(name), chain);
    }

    private static final class SimpleProtocolFinder implements ProtocolFinder {
        public final String name;

        public SimpleProtocolFinder(final String name) {
            this.name = name;
        }


        @Override
        public Result find(PUContext puContext, FilterChainContext ctx) {
            final String requestedProtocolName = ctx.getMessage();

            return name.equals(requestedProtocolName) ? Result.FOUND : Result.NOT_FOUND;
        }
    }

    private static final class SimpleResponseFilter extends BaseFilter {
        private final String name;
        private final boolean isBlocking;
        
        public SimpleResponseFilter(String name, boolean isBlocking) {
            this.name = name;
            this.isBlocking = isBlocking;
        }
        
        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            ctx.write(makeResponseMessage(name), isBlocking);
            return ctx.getStopAction();
        }

    }

    private static final class ClientResultFilter extends BaseFilter {
        private final String expectedResponse;
        private final FutureImpl<Boolean> resultFuture;

        public ClientResultFilter(String name, FutureImpl<Boolean> future) {
            this.resultFuture = future;
            expectedResponse = makeResponseMessage(name);
        }

        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            final String response = ctx.getMessage();
            if (expectedResponse.equals(response)) {
                resultFuture.result(Boolean.TRUE);
            } else {
                resultFuture.failure(new IllegalStateException(
                        "Unexpected response. Expect=" + expectedResponse +
                        " come=" + response));
            }

            return ctx.getStopAction();
        }

    }

    private static String makeResponseMessage(String protocolName) {
        return "Protocol-" + protocolName;
    }


    // ---------------------------------------------------------- Nested Classes


    private static final class TestPUFilter extends PUFilter {

        @Override
        protected void findProtocol(PUContext puContext, FilterChainContext ctx) {
            super.findProtocol(puContext, ctx);    //To change body of overridden methods use File | Settings | File Templates.
        }

    }

    private static abstract class TestFinder implements ProtocolFinder {

        int invocationCount = 0;

    }
    
    private static final class CustomEvent implements Event {
        private static final Object TYPE = new Object();
        @Override
        public Object type() {
            return TYPE;
        }
        
    }
    
    private static final class EventCounterFilter extends BaseFilter {

        private final CountDownLatch counter;
        private final Object eventType;
        private final FutureImpl<Connection> serverSideConnectionFuture;
        
        private EventCounterFilter(CountDownLatch countDownLatch,
                Object eventType, FutureImpl<Connection> serverSideConnectionFuture) {
            this.counter = countDownLatch;
            this.eventType = eventType;
            this.serverSideConnectionFuture = serverSideConnectionFuture;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            serverSideConnectionFuture.result(ctx.getConnection());
            return ctx.getInvokeAction();
        }

        @Override
        public NextAction handleEvent(FilterChainContext ctx, Event event)
                throws IOException {
            
            if (eventType.equals(event.type())) {
                counter.countDown();
            }
            
            return ctx.getInvokeAction();
        }
    }

}
