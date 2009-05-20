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

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.filterchain.TransportFilter.Mode;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.nio.transport.UDPNIOConnection;
import org.glassfish.grizzly.nio.transport.UDPNIOStreamReader;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.util.EchoFilter;

/**
 * Unit test for {@link UDPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class UDPNIOTransportTest extends TestCase {
    public static final int PORT = 7777;

    @Override
    protected void setUp() throws Exception {
        ByteBufferWrapper.DEBUG_MODE = true;
    }


    public void testStartStop() throws IOException {
        UDPNIOTransport transport = TransportFactory.getInstance().createUDPTransport();

        try {
            transport.bind(PORT);
            transport.start();
        } catch (Exception e) {
            e.printStackTrace(System.out);
            assertTrue("Exception!!!", false);
        } finally {
            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public void testConnectorHandlerConnect() throws Exception {
        Connection connection = null;
        UDPNIOTransport transport = TransportFactory.getInstance().createUDPTransport();

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

        UDPNIOTransport transport = TransportFactory.getInstance().createUDPTransport();

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);
            connection.setProcessor(null);
            writer = connection.getStreamWriter();
            byte[] sendingBytes = "Hello".getBytes();
            writer.writeByteArray(sendingBytes);
            Future<Integer> writeFuture = writer.flush();
            Integer bytesWritten = writeFuture.get(10, TimeUnit.SECONDS);
            assertTrue(writeFuture.isDone());
            assertEquals(sendingBytes.length, (int) bytesWritten);
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
        UDPNIOTransport transport = TransportFactory.getInstance().createUDPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (UDPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureBlocking(true);
            connection.setProcessor(null);

            byte[] originalMessage = "Hello".getBytes();
            writer = connection.getStreamWriter();
            writer.writeByteArray(originalMessage);
            Future<Integer> writeFuture = writer.flush();

            assertTrue("Write timeout", writeFuture.isDone());
            assertEquals(originalMessage.length, (int) writeFuture.get());


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
        UDPNIOTransport transport = TransportFactory.getInstance().createUDPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.bind(PORT);
            transport.start();
            transport.configureBlocking(true);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (UDPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);

            reader = connection.getStreamReader();
            writer = connection.getStreamWriter();

            for (int i = 0; i < 100; i++) {
                byte[] originalMessage = new String("Hello world #" + i).getBytes();
                writer.writeByteArray(originalMessage);
                Future<Integer> writeFuture = writer.flush();

                assertTrue("Write timeout", writeFuture.isDone());
                assertEquals(originalMessage.length, (int) writeFuture.get());

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
        UDPNIOTransport transport = TransportFactory.getInstance().createUDPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.bind(PORT);
            transport.start();

            Future<Connection> connectFuture = transport.connect("localhost", PORT);
            connection = (UDPNIOConnection) connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);

            byte[] originalMessage = "Hello".getBytes();
            writer = connection.getStreamWriter();
            writer.writeByteArray(originalMessage);
            Future<Integer> writeFuture = writer.flush();

            Integer writtenBytes = writeFuture.get(10, TimeUnit.SECONDS);
            assertEquals(originalMessage.length, (int) writtenBytes);


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
        final int packetSize = 1024;

        Connection connection = null;
        UDPNIOStreamReader reader = null;
        StreamWriter writer = null;
        UDPNIOTransport transport = TransportFactory.getInstance().createUDPTransport();
        transport.getFilterChain().add(new TransportFilter(Mode.Message));
        transport.getFilterChain().add(new EchoFilter());

        try {
            transport.setReadBufferSize(4096);
            transport.setWriteBufferSize(4096);

            transport.bind(PORT);

            transport.start();

            Future<Connection> connectFuture = transport.connect("localhost", PORT);
            connection = (UDPNIOConnection) connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);
            reader = (UDPNIOStreamReader) connection.getStreamReader();
            writer = connection.getStreamWriter();

            final CountDownLatch sendLatch = new CountDownLatch(packetsNumber);

            for (int i = 0; i < packetsNumber; i++) {
                final byte[] message = new byte[packetSize];
                Arrays.fill(message, (byte) i);

                writer.writeByteArray(message);
                writer.flush(new CompletionHandlerAdapter<Integer>() {

                    @Override
                    public void completed(Connection connection, Integer result) {
                        assertEquals(message.length, (int) result);
                        sendLatch.countDown();
                    }

                    @Override
                    public void failed(Connection connection, Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
            }

            boolean[] packetFlags = new boolean[packetsNumber];

            for (int i = 0; i < packetsNumber; i++) {
                byte[] message = new byte[packetSize];
                Future future = reader.notifyAvailable(packetSize);
                future.get(10, TimeUnit.SECONDS);
                assertTrue(future.isDone());
                reader.readByteArray(message);

                byte index = message[0];
                for(int j=0; j<packetSize; j++) {
                    assertEquals("Message is corrupted!", index, message[j]);
                }

                assertFalse("Message duplication: " + index, packetFlags[index]);
                packetFlags[index] = true;
            }

            for (int i = 0; i < packetsNumber; i++) {
                assertTrue("Message #" + i + " is missed", packetFlags[i]);
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
}
