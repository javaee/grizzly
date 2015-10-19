/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;

/**
 * Test standalone Grizzly implementation.
 * 
 * @author Alexey Stashok
 */
public class StandaloneTest extends GrizzlyTestCase {
    private static final Logger logger = Grizzly.logger(StandaloneTest.class);
    
    public static final int PORT = 7780;
    
    public void testStandalone() throws Exception {
        TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(-1);
        
        int messageSize = 166434;

        Connection connection = null;
        StreamReader reader;
        StreamWriter writer;

        try {
            // Enable standalone mode
            transport.configureStandalone(true);

            // Start listen on specific port
            final TCPNIOServerConnection serverConnection = transport.bind(PORT);
            // Start transport
            transport.start();

            // Start echo server thread
            final Thread serverThread =
                    startEchoServerThread(transport, serverConnection, messageSize);

            // Connect to the server
            Future<Connection> connectFuture = transport.connect("localhost", PORT);
            connection = connectFuture.get(10, TimeUnit.SECONDS);
            assertTrue(connectFuture.isDone());
            
            // fill out buffer
            byte[] buffer = new byte[messageSize];
            for(int i=0; i<messageSize; i++) {
                buffer[i] = (byte) (i % 128);
            }
            // write buffer
            writer = StandaloneProcessor.INSTANCE.getStreamWriter(connection);
            writer.writeByteArray(buffer);
            writer.flush();

            reader = StandaloneProcessor.INSTANCE.getStreamReader(connection);
            
            // prepare receiving buffer
            byte[] receiveBuffer = new byte[messageSize];


            Future readFuture = reader.notifyAvailable(messageSize);
            readFuture.get(20, TimeUnit.SECONDS);

            // Read the response.
            reader.readByteArray(receiveBuffer);

            assertTrue(readFuture.isDone());
            
            // Check the echo result
            assertTrue(Arrays.equals(buffer, receiveBuffer));

            serverThread.join(10 * 1000);
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }

    }

    private Thread startEchoServerThread(final TCPNIOTransport transport,
            final TCPNIOServerConnection serverConnection,
            final int messageSize) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Future<Connection> acceptFuture = serverConnection.accept();
                    Connection connection = acceptFuture.get(10, TimeUnit.SECONDS);
                    assertTrue(acceptFuture.isDone());

                    StreamReader reader =
                            StandaloneProcessor.INSTANCE.getStreamReader(connection);
                    StreamWriter writer =
                            StandaloneProcessor.INSTANCE.getStreamWriter(connection);
                    try {

                        Future readFuture = reader.notifyAvailable(messageSize);
                        readFuture.get(10, TimeUnit.SECONDS);
                        // Read until whole buffer will be filled out

                        byte[] buffer = new byte[messageSize];
                        reader.readByteArray(buffer);

                        assertTrue(readFuture.isDone());

                        // Write the echo
                        writer.writeByteArray(buffer);
                        Future writeFuture = writer.flush();
                        writeFuture.get(10, TimeUnit.SECONDS);

                        assertTrue(writeFuture.isDone());
                    } catch (Throwable e) {
                        logger.log(Level.WARNING,
                                "Error working with accepted connection", e);
                        assertTrue("Error working with accepted connection", false);
                    } finally {
                        connection.closeSilently();
                    }

                } catch (Exception e) {
                    if (!transport.isStopped()) {
                        logger.log(Level.WARNING,
                                "Error accepting connection", e);
                        assertTrue("Error accepting connection", false);
                    }
                }
            }
        });
        thread.start();
        
        return thread;
    }
}
