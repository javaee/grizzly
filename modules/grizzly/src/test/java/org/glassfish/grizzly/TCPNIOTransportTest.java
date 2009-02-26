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
package org.glassfish.grizzly;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.TestCase;
import org.glassfish.grizzly.filterchain.FilterAdapter;
import org.glassfish.grizzly.filterchain.StopAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOStreamReader;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.util.EchoFilter;

/**
 * Unit test for {@link TCPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class TCPNIOTransportTest extends TestCase {

    public static final int PORT = 7777;

    public void testStartStop() throws IOException {
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();

        try {
            transport.bind(PORT);
            transport.start();
        } finally {
            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testConnectorHandlerConnect() throws Exception {
        Connection connection = null;
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testConnectorHandlerConnectAndWrite() throws Exception {
        Connection connection = null;
        StreamWriter writer = null;
        
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);
            connection.setProcessor(null);
            writer = connection.getStreamWriter();
            writer.writeByteArray("Hello".getBytes());
            Future writeFuture = writer.flush();
            writeFuture.get(10, TimeUnit.SECONDS);
            assertTrue(writeFuture.isDone());
        } finally {
            if (writer != null) {
                writer.close();
            }
            
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testSimpleEcho() throws Exception {
        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);
            connection.setProcessor(null);

            byte[] originalMessage = "Hello".getBytes();
            writer = connection.getStreamWriter();
            writer.writeByteArray(originalMessage);
            Future writeFuture = writer.flush();

            assertTrue("Write timeout", writeFuture.isDone());


            reader = connection.getStreamReader();
            Future readFuture = reader.notifyAvailable(originalMessage.length);
            assertTrue("Read timeout", readFuture.get(10, TimeUnit.SECONDS) != null);

            byte[] echoMessage = new byte[originalMessage.length];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(echoMessage, originalMessage));
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testSeveralPacketsEcho() throws Exception {
        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.bind(PORT);
            transport.start();
            transport.configureBlocking(true);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);
            
            reader = connection.getStreamReader();
            writer = connection.getStreamWriter();

            for (int i = 0; i < 100; i++) {
                byte[] originalMessage = new String("Hello world #" + i).getBytes();
                writer.writeByteArray(originalMessage);
                Future writeFuture = writer.flush();

                assertTrue("Write timeout", writeFuture.isDone());

                Future readFuture = reader.notifyAvailable(originalMessage.length);
                assertTrue("Read timeout", readFuture.get(10, TimeUnit.SECONDS) != null);

                byte[] echoMessage = new byte[originalMessage.length];
                reader.readByteArray(echoMessage);
                assertTrue(Arrays.equals(echoMessage, originalMessage));
            }
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testAsyncReadWriteEcho() throws Exception {
        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);

            byte[] originalMessage = "Hello".getBytes();
            writer = connection.getStreamWriter();
            writer.writeByteArray(originalMessage);
            Future writeFuture = writer.flush();

            assertTrue("Write timeout", writeFuture.isDone());


            reader = connection.getStreamReader();
            Future readFuture = reader.notifyAvailable(originalMessage.length);
            assertTrue("Read timeout", readFuture.get(10, TimeUnit.SECONDS) != null);

            byte[] echoMessage = new byte[originalMessage.length];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(echoMessage, originalMessage));
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testSeveralPacketsAsyncReadWriteEcho() throws Exception {
        int packetsNumber = 20;
        final int packetSize = 17644;

        Connection connection = null;
        TCPNIOStreamReader reader = null;
        StreamWriter writer = null;
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);
            reader = (TCPNIOStreamReader) connection.getStreamReader();
            writer = connection.getStreamWriter();

            final CountDownLatch sendLatch = new CountDownLatch(packetsNumber);
            final CountDownLatch receiveLatch = new CountDownLatch(packetsNumber);

            for (int i = 0; i < packetsNumber; i++) {
                byte[] message = new byte[packetSize];
                Arrays.fill(message, (byte) i);

                writer.writeByteArray(message);
                writer.flush(new CompletionHandlerAdapter() {

                    @Override
                    public void completed(Connection connection, Object result) {
                        sendLatch.countDown();
                    }

                    @Override
                    public void failed(Connection connection, Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
            }

            for (int i = 0; i < packetsNumber; i++) {
                byte[] pattern = new byte[packetSize];
                Arrays.fill(pattern, (byte) i);

                byte[] message = new byte[packetSize];
                Future future = reader.notifyAvailable(packetSize);
                future.get(10, TimeUnit.SECONDS);
                assertTrue(future.isDone());
                reader.readByteArray(message);
                assertTrue(Arrays.equals(pattern, message));
            }

            assertEquals(0, sendLatch.getCount());
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testFeeder() throws Exception {
        class CheckSizeFilter extends FilterAdapter {
            private int size;
            private CountDownLatch latch;
            volatile Future resultFuture;
            
            public CheckSizeFilter(int size) {
                latch = new CountDownLatch(1);
                this.size = size;
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx,
                    NextAction nextAction) throws IOException {
                StreamReader reader = ctx.getStreamReader();
                resultFuture = reader.notifyAvailable(size);
                try {
                    resultFuture.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return new StopAction();
                } finally {
                    latch.countDown();
                }

                return nextAction;
            }

        }

        int fullMessageSize = 2048;

        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;
        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        CheckSizeFilter checkSizeFilter = new CheckSizeFilter(fullMessageSize);
        transport.getFilterChain().add(checkSizeFilter);
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);

            byte[] firstChunk = new byte[fullMessageSize / 5];
            Arrays.fill(firstChunk, (byte) 1);
            writer = connection.getStreamWriter();
            writer.writeByteArray(firstChunk);
            Future writeFuture = writer.flush();
            assertTrue("First chunk write timeout", writeFuture.isDone());

            Thread.sleep(1000);
            
            byte[] secondChunk = new byte[fullMessageSize - firstChunk.length];
            Arrays.fill(secondChunk, (byte) 2);
            writer = connection.getStreamWriter();
            writer.writeByteArray(secondChunk);
            writeFuture = writer.flush();
            assertTrue("Second chunk write timeout", writeFuture.isDone());

            reader = connection.getStreamReader();
            Future readFuture = reader.notifyAvailable(fullMessageSize);
            try {
                assertTrue("Read timeout. CheckSizeFilter latch: " +
                        checkSizeFilter.latch,
                        readFuture.get(10, TimeUnit.SECONDS) != null);
            } catch (TimeoutException e) {
                assertTrue("Read timeout. CheckSizeFilter latch: " +
                        checkSizeFilter.latch, false);
            }

            byte[] pattern = new byte[fullMessageSize];
            Arrays.fill(pattern, 0, firstChunk.length, (byte) 1);
            Arrays.fill(pattern, firstChunk.length, pattern.length, (byte) 2);
            byte[] echoMessage = new byte[fullMessageSize];
            reader.readByteArray(echoMessage);
            assertTrue(Arrays.equals(pattern, echoMessage));
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }
}
