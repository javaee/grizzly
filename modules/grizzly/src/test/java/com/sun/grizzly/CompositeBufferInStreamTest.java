/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2003-2008 Sun Microsystems, Inc. All rights reserved.
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
 */
package com.sun.grizzly;

import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.CompositeBuffer;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOServerConnection;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.Pair;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test how {@link CompositeBuffer} works with Streams.
 * 
 * @author Alexey Stashok
 */
public class CompositeBufferInStreamTest extends GrizzlyTestCase {

    public static final int PORT = 7783;
    private static Logger logger = Grizzly.logger(CompositeBufferInStreamTest.class);

    public void testCompositeBuffer() throws Exception {
        Connection connection = null;
        final TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();

        final Buffer portion1 = MemoryUtils.wrap(transport.getMemoryManager(), "Hello");
        final Buffer portion2 = MemoryUtils.wrap(transport.getMemoryManager(), " ");
        final Buffer portion3 = MemoryUtils.wrap(transport.getMemoryManager(), "world!");

        final FutureImpl lock1 = FutureImpl.create();
        final FutureImpl lock2 = FutureImpl.create();
        final FutureImpl lock3 = FutureImpl.create();

        final Pair<Buffer, FutureImpl>[] portions = new Pair[]{
            new Pair<Buffer, FutureImpl>(portion1, lock1),
            new Pair<Buffer, FutureImpl>(portion2, lock2),
            new Pair<Buffer, FutureImpl>(portion3, lock3)
        };

        try {
            // Start listen on specific port
            final TCPNIOServerConnection serverConnection = transport.bind(PORT);

            transport.configureStandalone(true);

            transport.start();

            // Start echo server thread
            startEchoServerThread(transport, serverConnection, portions);

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            connection.configureStandalone(true);
            
            final StreamWriter writer =
                    ((StandaloneProcessor) connection.getProcessor()).
                    getStreamWriter(connection);

            for (Pair<Buffer, FutureImpl> portion : portions) {
                final Buffer buffer = portion.getFirst().duplicate();
                final Future locker = portion.getSecond();

                writer.writeBuffer(buffer);
                final Future writeFuture = writer.flush();
                writeFuture.get(5000, TimeUnit.MILLISECONDS);

                locker.get(5000, TimeUnit.MILLISECONDS);
            }

            assertTrue(true);

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
            final Pair<Buffer, FutureImpl>[] portions) {
        new Thread(new Runnable() {

            @Override
            public void run() {
//                while (!transport.isStopped()) {
                    try {
                        Future<Connection> acceptFuture = serverConnection.accept();
                        Connection connection = acceptFuture.get(10, TimeUnit.SECONDS);
                        assertTrue(acceptFuture.isDone());

                        int availableExp = 0;
                        
                        StreamReader reader =
                                ((StandaloneProcessor) connection.getProcessor()).
                                getStreamReader(connection);
                        
                        int i = 0;
                        try {
                            for (; i < portions.length; i++) {
                                final Pair<Buffer, FutureImpl> portion = portions[i];
                                final FutureImpl currentLocker = portion.getSecond();

                                availableExp += portion.getFirst().remaining();
                                Future readFuture = reader.notifyAvailable(availableExp);
                                readFuture.get(30, TimeUnit.SECONDS);

                                if (readFuture.isDone()) {
                                    final Buffer compositeBuffer = reader.getBufferWindow();
                                    int counter = 0;
                                    for (int j = 0; j <= i; j++) {
                                        final Buffer currentBuffer = portions[j].getFirst();
                                        for (int k = 0; k < currentBuffer.limit(); k++) {
                                            final byte found = compositeBuffer.get(counter++);
                                            final byte expected = currentBuffer.get(k);
                                            if (found != expected) {
                                                currentLocker.failure(new IllegalStateException(
                                                        "CompositeBuffer content is broken. Offset: "
                                                        + compositeBuffer.position() + " found: " + found
                                                        + " expected: " + expected));
                                                return;
                                            }
                                        }
                                    }
                                } else {
                                    currentLocker.failure(new IllegalStateException("Error reading content portion: " + i));
                                    return;
                                }

                                currentLocker.result(i);
                            }
                            // Read until whole buffer will be filled out
                        } catch (Throwable e) {
                            portions[i].getSecond().failure(e);
                            logger.log(Level.WARNING,
                                    "Error working with accepted connection on step: " + i, e);
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
//                }
            }
        }).start();
    }
}
