/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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


package com.sun.grizzly.websockets;

import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.net.URL;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NioClientWebSocket extends BaseWebSocket {
    private static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);

    private final Selector selector;
    private final AtomicBoolean connecting = new AtomicBoolean(true);
    private final AtomicBoolean running = new AtomicBoolean(true);

    protected volatile long selectTimeout = 1000;
    private final URL address;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public NioClientWebSocket(final String url, final WebSocketListener... listeners) throws IOException {
        this(url, WebSocketEngine.DEFAULT_TIMEOUT, listeners);
    }

    public NioClientWebSocket(final String url, final long timeout, final WebSocketListener... listeners) throws IOException {
        super(listeners);
        address = new URL(url);
        selector = SelectorProvider.provider().openSelector();
        Thread selectorThread = new Thread(new Runnable() {
            public void run() {
                select();
            }
        });
        selectorThread.setDaemon(true);
        selectorThread.start();
        WebSocketConnectTask connectTask = new WebSocketConnectTask(this);
        executorService.execute(connectTask);
        try {
            connectTask.get(timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }

    public URL getAddress() {
        return address;
    }

    private void select() {
        while (running.get()) {
            try {
                if (connecting.compareAndSet(true, false)) {
                    final NioClientNetworkHandler handler = createNetworkHandler(selector);
                    setNetworkHandler(handler);
                    final SocketChannel socketChannel = handler.getChannel();
                    if (socketChannel.isConnected()) { // Channel was immediately connected
                        socketChannel.register(selector, SelectionKey.OP_READ, handler);
                        handler.doConnect(false);
                    } else { // Channel we be connected in NIO fashion
                        socketChannel.register(selector, SelectionKey.OP_CONNECT, handler);
                    }
                }

                final int count = selector.select(selectTimeout);
                if (count != 0) {
                    Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                    while (selectedKeys.hasNext()) {
                        SelectionKey key = selectedKeys.next();
                        selectedKeys.remove();
                        final NioClientNetworkHandler handler = (NioClientNetworkHandler) key.attachment();
                        try {
                            handler.process(key);
                        } catch (IOException e) {
                            e.printStackTrace();
                            handler.shutdown();
                        }
                    }
                }
            } catch (IOException e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_WS_SELECT_ERROR(e.getMessage()),
                            e);
                }
            }
        }
    }

    public void execute(Runnable runnable) {
        executorService.submit(runnable);
    }

    protected NioClientNetworkHandler createNetworkHandler(Selector selector) throws IOException {
        return new NioClientNetworkHandler(selector, this);
    }
}
