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

package com.sun.grizzly;

import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import com.sun.grizzly.nio.transport.TCPNIOServerConnection;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;

/**
 * Test standalone Grizzly implementation.
 * 
 * @author Alexey Stashok
 */
public class StandaloneTest extends TestCase {
    private static Logger logger = Grizzly.logger;
    
    public static int PORT = 7780;
    
    public void testStandalone() throws Exception {
        TCPNIOTransport transport =
                TransportFactory.getInstance().createTCPTransport();
        int messageSize = 166434;

        Connection connection = null;
        StreamReader reader = null;
        StreamWriter writer = null;

        try {
            // Enable standalone mode
            transport.setProcessorSelector(new StandaloneProcessorSelector());

            // Start listen on specific port
            final TCPNIOServerConnection serverConnection = transport.bind(PORT);
            // Start transport
            transport.start();

            // Start echo server thread
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
            writer = connection.getStreamWriter();
            writer.writeByteArray(buffer);
            writer.flush();

            reader = connection.getStreamReader();
            
            // prepare receiving buffer
            byte[] receiveBuffer = new byte[messageSize];


            Future readFuture = reader.notifyAvailable(messageSize);
            readFuture.get(20, TimeUnit.SECONDS);

            // Read the response.
            reader.readByteArray(receiveBuffer);

            assertTrue(readFuture.isDone());
            
            // Check the echo result
            assertTrue(Arrays.equals(buffer, receiveBuffer));
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }

    }

    private void startEchoServerThread(final TCPNIOTransport transport,
            final TCPNIOServerConnection serverConnection,
            final int messageSize) {
        new Thread(new Runnable() {
            public void run() {
                while(!transport.isStopped()) {
                    try {
                        Future<Connection> acceptFuture = serverConnection.accept();
                        Connection connection = acceptFuture.get(10, TimeUnit.SECONDS);
                        assertTrue(acceptFuture.isDone());

                        StreamReader reader = connection.getStreamReader();
                        StreamWriter writer = connection.getStreamWriter();
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
                            connection.close();
                        }

                    } catch (Exception e) {
                        if (!transport.isStopped()) {
                            logger.log(Level.WARNING,
                                    "Error accepting connection", e);
                            assertTrue("Error accepting connection", false);
                        }
                    }
                }
            }
        }).start();
    }
}
