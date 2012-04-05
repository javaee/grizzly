/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.NullaryFunction;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.EchoFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.utils.Futures;

/**
 * Test general {@link FilterChain} functionality.
 *
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class FilterChainTest extends TestCase {
    private static final int PORT = 7788;
    
    private static final Attribute<AtomicInteger> counterAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            FilterChainTest.class.getName() + ".counter");
    
    private static final Attribute<CompositeBuffer> bufferAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            FilterChainTest.class.getName() + ".buffer",
            new NullaryFunction<CompositeBuffer>() {

                @Override
                public CompositeBuffer evaluate() {
                    return CompositeBuffer.newBuffer();
                }
            });

    private static final FilterChainEvent INC_EVENT = new FilterChainEvent() {
        @Override
        public Object type() {
            return "INC_EVENT";
        }
    };

    private static final FilterChainEvent DEC_EVENT = new FilterChainEvent() {
        @Override
        public Object type() {
            return "DEC_EVENT";
        }
    };
    
    public void testEventUpstream() throws Exception {
        final Connection connection =
                new TCPNIOConnection(TCPNIOTransportBuilder.newInstance().build(), null);

        counterAttr.set(connection, new AtomicInteger(0));

        final FilterChain chain = FilterChainBuilder.stateless()
                .add(new EventCounterFilter(0))
                .add(new EventCounterFilter(1))
                .add(new EventCounterFilter(2))
                .add(new EventCounterFilter(3))
                .build();

        final FutureImpl<FilterChainContext> resultFuture =
                Futures.<FilterChainContext>createSafeFuture();
        
        chain.fireEventUpstream(connection, INC_EVENT,
                Futures.toCompletionHandler(resultFuture));

        resultFuture.get(10, TimeUnit.SECONDS);
    }

    public void testEventDownstream() throws Exception {
        final Connection connection =
                new TCPNIOConnection(TCPNIOTransportBuilder.newInstance().build(), null);

        counterAttr.set(connection, new AtomicInteger(3));

        final FilterChain chain = FilterChainBuilder.stateless()
                .add(new EventCounterFilter(0))
                .add(new EventCounterFilter(1))
                .add(new EventCounterFilter(2))
                .add(new EventCounterFilter(3))
                .build();

        final FutureImpl<FilterChainContext> resultFuture =
                Futures.<FilterChainContext>createSafeFuture();
        
        chain.fireEventDownstream(connection, DEC_EVENT,
                Futures.toCompletionHandler(resultFuture));

        resultFuture.get(10, TimeUnit.SECONDS);
    }

    public void testFlush() throws Exception {
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        final MemoryManager mm = transport.getMemoryManager();

        final Buffer msg = Buffers.wrap(mm, "Echo this message");
        final int msgSize = msg.remaining();

        final AtomicInteger serverEchoCounter = new AtomicInteger();
        
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter() {

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                final Buffer msg = ctx.getMessage();
                serverEchoCounter.addAndGet(msg.remaining());
                
                return super.handleRead(ctx);
            }
        });

        transport.setProcessor(filterChainBuilder.build());

        Connection connection = null;
        
        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Integer> resultEcho = SafeFutureImpl.create();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new BufferWriteFilter());
            clientFilterChainBuilder.add(new EchoResultFilter(
                    msgSize, resultEcho));
            final FilterChain clientChain = clientFilterChainBuilder.build();

            SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientChain)
                    .build();

            Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));
            
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.write(msg);

            try {
                resultEcho.get(5, TimeUnit.SECONDS);
                fail("No message expected");
            } catch (TimeoutException expected) {
            }

            final FutureImpl<WriteResult> future =
                    Futures.<WriteResult>createSafeFuture();
            
            clientChain.flush(connection, Futures.toCompletionHandler(future));
            future.get(10, TimeUnit.SECONDS);

            assertEquals((Integer) msgSize, resultEcho.get(10, TimeUnit.SECONDS));
            assertEquals(msgSize, serverEchoCounter.get());

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }

    public void testWriteCloner() throws Exception {
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new EchoFilter());

        transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(-1);
        
        transport.setProcessor(filterChainBuilder.build());

        Connection connection = null;
        
        try {
            transport.bind(PORT);
            transport.start();

            final FutureImpl<Boolean> resultEcho = SafeFutureImpl.create();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ClonerTestEchoResultFilter(resultEcho));
            final FilterChain clientChain = clientFilterChainBuilder.build();

            final SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport)
                    .processor(clientChain).build();
            
            Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            assertTrue(resultEcho.get(10, TimeUnit.SECONDS));

        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.stop();
        }
    }
    
    public void testBufferDisposable() throws Exception {
        final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        final MemoryManager mm = transport.getMemoryManager();
        
        FutureImpl<Boolean> part1Future = Futures.<Boolean>createSafeFuture();
        FutureImpl<Boolean> part2Future = Futures.<Boolean>createSafeFuture();
        
        final Buffer msg1 = Buffers.wrap(mm, "part1");
        final Buffer msg2 = Buffers.wrap(mm, "part2");

        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new BufferStateFilter(part1Future, part2Future));
        transport.setProcessor(filterChainBuilder.build());

        Connection connection = null;

        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            final FilterChain clientChain = clientFilterChainBuilder.build();

            final SocketConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(transport).processor(clientChain).build();

            Future<Connection> connectFuture = connectorHandler.connect(
                    new InetSocketAddress("localhost", PORT));
            connection = connectFuture.get(10, TimeUnit.SECONDS);

            connection.write(msg1);
            assertTrue("simple buffer is not disposable", part1Future.get(5, TimeUnit.SECONDS));
            
            connection.write(msg2);
            assertTrue("composite buffer is not disposable", part2Future.get(5, TimeUnit.SECONDS));

        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
        }
    }

    private static class BufferStateFilter extends BaseFilter {

        private final FutureImpl<Boolean> part1Future;
        private final FutureImpl<Boolean> part2Future;
        
        public BufferStateFilter(FutureImpl<Boolean> part1Future,
                FutureImpl<Boolean> part2Future) {
            this.part1Future = part1Future;
            this.part2Future = part2Future;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            Buffer b = ctx.getMessage();
            
            if (!part1Future.isDone()) {
                part1Future.result(b.allowBufferDispose());
            } else if (!part2Future.isDone()) {
                part2Future.result(b.isComposite() && b.allowBufferDispose());
            }
            
            return ctx.getStopAction(b);
        }
    }

    private static class BufferWriteFilter extends BaseFilter {
        @Override
        public NextAction handleWrite(FilterChainContext ctx) throws IOException {
            final Connection c = ctx.getConnection();
            final Buffer msg = ctx.getMessage();

            final CompositeBuffer buffer = bufferAttr.get(c);
            buffer.append(msg);

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleEvent(final FilterChainContext ctx, final FilterChainEvent event) throws IOException {
            if (event.type() == TransportFilter.FlushEvent.TYPE) {
                final Connection c = ctx.getConnection();
                final Buffer buffer = bufferAttr.remove(c);

                ctx.write(buffer, new EmptyCompletionHandler<WriteResult>() {

                    @Override
                    public void completed(WriteResult result) {
                        ctx.setFilterIdx(ctx.getFilterIdx() - 1);
                        ctx.resume();
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        ctx.fail(throwable);
                        ctx.completeAndRecycle();
                    }
                });

                return ctx.getSuspendAction();
            }

            return ctx.getInvokeAction();
        }

    }
    
    private static class EchoResultFilter extends BaseFilter {
        private final int size;
        private final FutureImpl<Integer> future;

        public EchoResultFilter(int size, FutureImpl<Integer> future) {
            this.size = size;
            this.future = future;
        }
        
        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final Buffer msg = ctx.getMessage();
            final int msgSize = msg.remaining();

            if (msgSize < size) {
                return ctx.getStopAction(msg);
            } else if (msgSize == size) {
                future.result(size);
                return ctx.getStopAction();
            } else {
                throw new IllegalStateException("Response is bigger than expected. Expected=" + size + " got=" + msgSize);
            }
        }

    }

    private static class ClonerTestEchoResultFilter extends BaseFilter {
        private final int msgSize = 8192;
        private volatile int size;
        private final FutureImpl<Boolean> future;

        public ClonerTestEchoResultFilter(final FutureImpl<Boolean> future) {
            this.future = future;
        }

        @Override
        public NextAction handleConnect(final FilterChainContext ctx)
                throws IOException {
            
            final Connection connection = ctx.getConnection();
            final Transport transport = connection.getTransport();
            
            transport.pause();
            
            final byte[] bytesData = new byte[msgSize];
            
            final AtomicInteger doneFlag = new AtomicInteger(2);
            int counter = 0;
            
            while(doneFlag.get() != 0) {
                Arrays.fill(bytesData, (byte) (counter++ % 10));
                final Buffer b = Buffers.wrap(transport.getMemoryManager(), bytesData);
                
                ctx.write(null, b, null, null, new MessageCloner() {

                    @Override
                    public Object clone(final Connection connection,
                            final Object originalMessage) {
                        final Buffer originalBuffer = (Buffer) originalMessage;
                        final int remaining = originalBuffer.remaining();

                        final Buffer cloneBuffer = connection.getTransport()
                                .getMemoryManager().allocate(remaining);
                        cloneBuffer.put(originalBuffer);
                        cloneBuffer.flip();
                        cloneBuffer.allowBufferDispose();
                        
                        doneFlag.decrementAndGet();
                        return cloneBuffer;
                    }
                });
                
                size += bytesData.length;
            }
            transport.resume();
            
            return ctx.getInvokeAction();
        }
        
        @Override
        public NextAction handleRead(final FilterChainContext ctx)
                throws IOException {
            final Buffer msg = ctx.getMessage();
            if (msg.remaining() < size) {
                return ctx.getStopAction(msg);
            }
            
            if (msg.remaining() > size) {
                future.failure(new IllegalStateException("Echoed more bytes than expected"));
            }
            
            int count = -1;
            
            for (int i = 0; i < size; i++) {
                if (i % msgSize == 0) {
                    count = (count + 1) % 10;
                }
                
                if (msg.get(i) != count) {
                    future.failure(new IllegalStateException("Offset " + i + " expected=" + count + " was=" + msg.get(i)));
                }
            }
            
            future.result(Boolean.TRUE);
            
            return ctx.getStopAction();
        }
    }
    
    private static class EventCounterFilter extends BaseFilter {
        private final int checkValue;

        public EventCounterFilter(int checkValue) {
            this.checkValue = checkValue;
        }
        
        @Override
        public NextAction handleEvent(FilterChainContext ctx, FilterChainEvent event)
                throws IOException {
            final Connection c = ctx.getConnection();
            AtomicInteger ai = counterAttr.get(c);
            final int value = ai.get();

            if (event.type() == DEC_EVENT.type()) {
                ai.decrementAndGet();
            } else if (event.type() == INC_EVENT.type()) {
                ai.incrementAndGet();
            } else {
                throw new UnsupportedOperationException("Unsupported event");
            }

            if (value != checkValue) {
                throw new IllegalStateException("Unexpected value. Expected=" + checkValue + " got=" + value);
            }

            return ctx.getInvokeAction();
        }
    }
}
