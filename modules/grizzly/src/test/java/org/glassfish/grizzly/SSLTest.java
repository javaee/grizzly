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

import java.net.URL;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.ssl.BlockingSSLHandshaker;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLHandshaker;
import org.glassfish.grizzly.ssl.SSLStreamReader;
import org.glassfish.grizzly.ssl.SSLStreamWriter;
import org.glassfish.grizzly.util.EchoFilter;

/**
 *
 * @author oleksiys
 */
public class SSLTest extends TestCase {

    public static final int PORT = 7779;

    public void testSimpleSyncSSL() throws Exception {
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        
        SSLEngineConfigurator clientSSLEngineConfigurator =
                new SSLEngineConfigurator(
                sslContextConfigurator.createSSLContext());

        TCPNIOTransport transport =
                TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new SSLFilter());
        transport.getFilterChain().add(new EchoFilter());

        SSLStreamReader reader = null;
        SSLStreamWriter writer = null;

        try {
            transport.bind(PORT);
            transport.start();

            transport.configureBlocking(true);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);

            reader = new SSLStreamReader(connection.getStreamReader());
            writer = new SSLStreamWriter(connection.getStreamWriter());

            reader.setBlocking(true);
            writer.setBlocking(true);

            SSLHandshaker handshaker = new BlockingSSLHandshaker();
            
            Future handshakeFuture = handshaker.handshake(reader, writer,
                    clientSSLEngineConfigurator);

            assertTrue(handshakeFuture.isDone());
            handshakeFuture.get();
            
            byte[] sentMessage = "Hello world!".getBytes();

            // aquire read lock to not allow incoming data to be processed by Processor
            writer.writeByteArray(sentMessage);
            Future writeFuture = writer.flush();
            
            assertTrue("Write timeout", writeFuture.isDone());
            writeFuture.get();

            byte[] receivedMessage = new byte[sentMessage.length];

            Future readFuture = reader.notifyAvailable(receivedMessage.length);
            assertTrue(readFuture.isDone());
            readFuture.get();

            reader.readByteArray(receivedMessage);
            
            String sentString = new String(sentMessage);
            String receivedString = new String(receivedMessage);
            assertEquals(sentString, receivedString);
        }  finally {
            if (reader != null) {
                reader.close();
            }

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

    public void testSimpleAsyncSSL() throws Exception {
        Connection connection = null;
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();

        SSLEngineConfigurator clientSSLEngineConfigurator =
                new SSLEngineConfigurator(sslContextConfigurator.createSSLContext());

        TCPNIOTransport transport =
                TransportFactory.getInstance().createTCPTransport();
        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(new SSLFilter());
        transport.getFilterChain().add(new EchoFilter());

        SSLStreamReader reader = null;
        SSLStreamWriter writer = null;

        try {
            transport.bind(PORT);
            transport.start();

            transport.configureBlocking(true);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.setProcessor(null);

            reader = new SSLStreamReader(connection.getStreamReader());
            writer = new SSLStreamWriter(connection.getStreamWriter());

            SSLHandshaker handshaker = new BlockingSSLHandshaker();

            Future handshakeFuture = handshaker.handshake(reader, writer,
                    clientSSLEngineConfigurator);

            handshakeFuture.get(10, TimeUnit.SECONDS);
            assertTrue(handshakeFuture.isDone());

            byte[] sentMessage = "Hello world!".getBytes();

            // aquire read lock to not allow incoming data to be processed by Processor
            writer.writeByteArray(sentMessage);
            Future writeFuture = writer.flush();

            writeFuture.get(10, TimeUnit.SECONDS);
            assertTrue("Write timeout", writeFuture.isDone());

            byte[] receivedMessage = new byte[sentMessage.length];

            Future readFuture = reader.notifyAvailable(receivedMessage.length);
            readFuture.get(10, TimeUnit.SECONDS);
            assertTrue(readFuture.isDone());

            reader.readByteArray(receivedMessage);

            String sentString = new String(sentMessage);
            String receivedString = new String(receivedMessage);
            assertEquals(sentString, receivedString);
        }  finally {
            if (reader != null) {
                reader.close();
            }

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

//    public void testSimpleAsyncSSL() throws Exception {
//        Connection connection = null;
//        SSLCodec sslCodec = new SSLCodec(createSSLContext());
//
//        TCPNIOTransport transport =
//                TransportFactory.getInstance().createTCPTransport();
//        transport.getFilterChain().add(new TransportFilter());
//        transport.getFilterChain().add(new SSLFilter(sslCodec));
//        transport.getFilterChain().add(new EchoFilter());
//
//        try {
//            transport.bind(PORT);
//            transport.start();
//
//            transport.configureBlocking(false);
//
//            Future<Connection> future = transport.connect("localhost", PORT);
//            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
//            assertTrue(connection != null);
//
//            Future handshakeFuture = sslCodec.handshake(connection);
//
//            assertTrue("Handshake timeout",
//                    handshakeFuture.get(10, TimeUnit.SECONDS) != null);
//
//            MemoryManager memoryManager = transport.getMemoryManager();
//            Buffer message = MemoryUtils.wrap(memoryManager, "Hello world!");
//
//            // aquire read lock to not allow incoming data to be processed by Processor
//            connection.obtainProcessorLock(IOEvent.READ).lock();
//            Future<WriteResult> writeFuture = connection.write(message,
//                    sslCodec.getEncoder());
//
//            WriteResult writeResult = writeFuture.get(10, TimeUnit.SECONDS);
//            assertTrue("Write timeout", writeResult != null);
//            writeFuture.get();
//
//
//            Buffer receiverBuffer = SSLResourcesAccessor.getInstance().
//                    obtainAppBuffer(connection);
//
//            Future<ReadResult> readFuture =
//                    connection.read(receiverBuffer, sslCodec.getDecoder());
//
//            connection.obtainProcessorLock(IOEvent.READ).unlock();
//
//            ReadResult readResult = readFuture.get(10, TimeUnit.SECONDS);
//            assertTrue("Read timeout", readResult != null);
//
//            message.flip();
//            receiverBuffer.flip();
//
//            assertEquals(message, receiverBuffer);
//        } finally {
//            if (connection != null) {
//                connection.close();
//            }
//
//            transport.stop();
//            TransportFactory.getInstance().close();
//        }
//    }
//
//    public void testSeveralPacketsSyncSSL() throws Exception {
//        int packetsNumber = 10;
//        int packetSize = 20000;
//
//        Connection connection = null;
//        SSLCodec sslCodec = new SSLCodec(createSSLContext());
//
//        TCPNIOTransport transport =
//                TransportFactory.getInstance().createTCPTransport();
//        transport.getFilterChain().add(new TransportFilter());
//        transport.getFilterChain().add(new SSLFilter(sslCodec));
//        EchoFilter echoFilter = new EchoFilter();
//        transport.getFilterChain().add(echoFilter);
//
//        try {
//            transport.bind(PORT);
//            transport.start();
//
//            transport.configureBlocking(true);
//
//            Future<Connection> future = transport.connect("localhost", PORT);
//            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
//            assertTrue(connection != null);
//
//            Future handshakeFuture = sslCodec.handshake(connection);
//
//            assertTrue("Handshake timeout",
//                    handshakeFuture.get(10, TimeUnit.SECONDS) != null);
//
//            MemoryManager memoryManager = transport.getMemoryManager();
//
//            SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
//            Buffer receiverBuffer = accessor.obtainAppBuffer(connection);
//            if (receiverBuffer.capacity() < packetSize * 2) {
//                receiverBuffer.dispose();
//                receiverBuffer = memoryManager.allocate(packetSize * 2);
//            }
//
//            for (int i = 0; i < packetsNumber; i++) {
//                Buffer message = memoryManager.allocate(packetSize);
//                message.put(
//                        new String("Hello world #" + i).getBytes());
//                message.position(0);
//
//                // aquire read lock to not allow incoming data to be processed by Processor
//                connection.obtainProcessorLock(IOEvent.READ).lock();
//                Future<WriteResult> writeFuture = connection.write(message,
//                        sslCodec.getEncoder());
//                assertTrue("Write timeout", writeFuture.isDone());
//                writeFuture.get();
//
//                try {
//                    Future readFuture =
//                            connection.read(receiverBuffer, null,
//                            sslCodec.getDecoder(),
//                            new MinBufferSizeCondition(packetSize));
//
//                    assertTrue("Read timeout. Echo processed: " +
//                            echoFilter.getProcessedBytes() + " bytes",
//                            readFuture.get() != null);
//                } catch (Exception e) {
//                    assertTrue("Exception happened. Echo processed: " +
//                            echoFilter.getProcessedBytes() + " bytes", false);
//                    throw e;
//                }
//
//                connection.obtainProcessorLock(IOEvent.READ).unlock();
//
//                message.flip();
//                receiverBuffer.flip();
//
//                assertEquals(message, receiverBuffer);
//                receiverBuffer.clear();
//            }
//        } finally {
//            if (connection != null) {
//                connection.close();
//            }
//
//            transport.stop();
//            TransportFactory.getInstance().close();
//        }
//    }
//
//    public void testSeveralPacketsAsyncSSL() throws Exception {
//        int packetsNumber = 10;
//        int packetSize = 20000;
//
//        Connection connection = null;
//        SSLCodec sslCodec = new SSLCodec(createSSLContext());
//
//        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
//        transport.getFilterChain().add(new TransportFilter());
//        transport.getFilterChain().add(new SSLFilter(sslCodec));
//        EchoFilter echoFilter = new EchoFilter();
//        transport.getFilterChain().add(echoFilter);
//
//        try {
//            transport.bind(PORT);
//            transport.start();
//
//            MemoryManager memoryManager = transport.getMemoryManager();
//
//            Future<Connection> future = transport.connect("localhost", PORT);
//            connection = (TCPNIOConnection) future.get(10, TimeUnit.SECONDS);
//            assertTrue(connection != null);
//
//            Future handshakeFuture = sslCodec.handshake(connection);
//
//            assertTrue("Handshake timeout",
//                    handshakeFuture.get(10, TimeUnit.SECONDS) != null);
//
//
//            SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
//            Buffer receiverBuffer = accessor.obtainAppBuffer(connection);
//            if (receiverBuffer.capacity() < packetSize * 2) {
//                receiverBuffer.dispose();
//                receiverBuffer = memoryManager.allocate(packetSize * 2);
//            }
//
//        // Register asyncQueue reads
//            for (int i = 0; i < packetsNumber; i++) {
//                Buffer message = memoryManager.allocate(packetSize);
//                message.put(
//                        new String("Hello world #" + i).getBytes());
//                message.position(0);
//                connection.obtainProcessorLock(IOEvent.READ).lock();
//                Future<WriteResult> writeFuture = connection.write(message,
//                        sslCodec.getEncoder());
//
//                Future<ReadResult> readFuture = connection.read(receiverBuffer,
//                        null,
//                        sslCodec.getDecoder(),
//                        new MinBufferSizeCondition(packetSize));
//
//                connection.obtainProcessorLock(IOEvent.READ).unlock();
//
//                try {
//                    readFuture.get(10, TimeUnit.SECONDS);
//                } catch (TimeoutException e) {
//                    String description = "";
//
//                    ReadResult currentResult = readFuture.get();
//                    if (currentResult == null) {
//                        description = "Result is null for message #" + i;
//                        break;
//                    } else if (currentResult.getReadSize() < packetSize) {
//                        description = "Message #" + i +
//                                " was not read completely, but only: " +
//                                currentResult.getReadSize() + " bytes";
//                        break;
//                    }
//                    assertTrue("Write Timeout! More description: " +
//                            description, false);
//                }
//
//                receiverBuffer.flip();
//                message.flip();
//                assertEquals("Received message doesn't match the pattern",
//                        message, receiverBuffer);
//                receiverBuffer.clear();
//            }
//
//        } finally {
//            if (connection != null) {
//                connection.close();
//            }
//
//            transport.stop();
//            TransportFactory.getInstance().close();
//        }
//    }

    private SSLContextConfigurator createSSLContextConfigurator() {
        SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        ClassLoader cl = getClass().getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
        }

        SSLContextConfigurator.DEFAULT_CONFIG = sslContextConfigurator;
        
        return sslContextConfigurator;
    }
}