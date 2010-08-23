/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.httpclient;

import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.CallbackHandler;
import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultSelectionKeyHandler;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.TCPConnectorHandler;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.filter.LogFilter;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.util.ConnectionCloseHandler;
import com.sun.grizzly.util.WorkerThreadImpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.logging.Level;

/**
 * Grizzly HTTP Client.
 *
 * @author Hubert Iwaniuk
 */
public class GrizzlyHttpClient {
    private Controller controller;
    private TCPConnectorHandler tcpConnectorHandler;

    public void start() {
        controller = new Controller();
        ProtocolChainInstanceHandler pciHandler
            = new ProtocolChainInstanceHandler() {

            final private ProtocolChain protocolChain
                = new DefaultProtocolChain();

            public ProtocolChain poll() {
                return protocolChain;
            }

            public boolean offer(ProtocolChain instance) {
                return true;
            }
        };
        controller.setProtocolChainInstanceHandler(pciHandler);

        ProtocolChain protocolChain = controller
            .getProtocolChainInstanceHandler().poll();
        protocolChain.addFilter(new ReadFilter());
        protocolChain.addFilter(new LogFilter());

        TCPSelectorHandler selectorHandler = new TCPSelectorHandler(true);
        selectorHandler
            .setSelectionKeyHandler(new DefaultSelectionKeyHandler());
        BaseSelectionKeyHandler selectionKeyHandler
            = new BaseSelectionKeyHandler();
        selectionKeyHandler.setConnectionCloseHandler(
            new ConnectionCloseHandler() {
                public void locallyClosed(SelectionKey key) {
                    System.out.println(key + " localy closed.");
                }

                public void remotlyClosed(SelectionKey key) {
                    System.out.println(key + " remotely closed.");
                }
            });
        selectionKeyHandler.setSelectorHandler(selectorHandler);
        controller.addSelectorHandler(selectorHandler);

        startController(controller);
        tcpConnectorHandler = (TCPConnectorHandler) controller
            .acquireConnectorHandler(Controller.Protocol.TCP);

    }

    private static void startController(Controller controller) {
        final CountDownLatch latch = new CountDownLatch(1);
        controller.addStateListener(
            new ControllerStateListenerAdapter() {
                @Override
                public void onReady() {
                    System.out.println("Ready.");
                    latch.countDown();
                }

                @Override
                public void onException(Throwable e) {
                    if (latch.getCount() > 0) {
                        Controller.logger().log(
                            Level.SEVERE,
                            "Exception during " + "starting the controller", e);
                        latch.countDown();
                    } else {
                        Controller.logger().log(
                            Level.SEVERE,
                            "Exception during " + "controller processing", e);
                    }
                }
            });

        new WorkerThreadImpl("ControllerWorker", controller).start();

        try {
            latch.await();
            System.out.println("Controller started.");
        } catch (InterruptedException ex) {
        }

        if (!controller.isStarted()) {
            throw new IllegalStateException("Controller is not started!");
        }
    }

    public void stop() throws IOException {
        tcpConnectorHandler.close();
        controller.stop();
    }

    public Future<HttpClientResponse> call(final HttpClientRequest clientRequest)
        throws IOException {
        // TODO: implement method
        if (!controller.isStarted()) {
            start();
        }

        final CountDownLatch anotherLatch = new CountDownLatch(1);
        CallbackHandler<Context> callbackHandler
            = new CallbackHandler<Context>() {
            public void onConnect(IOEvent<Context> ioEvent) {
                System.out.println("onConnect");
                SelectionKey selectionKey = ioEvent.attachment()
                    .getSelectionKey();
                try {
                    tcpConnectorHandler.finishConnect(selectionKey);
                    System.out.println("Done finishconnect");
                } catch (IOException e) {
                    System.out.println("onConnect: " + e.getMessage());
                    e.printStackTrace(System.out);
                }
                anotherLatch.countDown();
                ioEvent.attachment().getSelectorHandler()
                    .register(selectionKey, SelectionKey.OP_READ);
            }

            public void onRead(IOEvent<Context> ioEvent) {
                Context context = ioEvent.attachment();
                try {
                    context.getProtocolChain().execute(context);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            /** {@inheritDoc} */
            public void onWrite(IOEvent ioEvent) {
            }
        };

        tcpConnectorHandler.connect(
            new InetSocketAddress(
                clientRequest.getTarget().getHost(),
                clientRequest.getTarget().getPort()), callbackHandler);

        StringBuilder buff = new StringBuilder(
            clientRequest.getMethod().name());
        buff.append(' ').append(clientRequest.getTarget()).append(' ')
            .append(clientRequest.getVersion()).append('\n').append('\n');
        System.out.println(buff);
        final byte[] bytes = buff.toString().getBytes();
        long written = tcpConnectorHandler.write(ByteBuffer.wrap(bytes), true);
        if (written != bytes.length) {
            System.out.println(
                "Written " + written + ", supposed to: " + bytes.length);
        }


        return null;
    }
}
