/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.util.net.URL;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class WebSocketClient extends WebSocketAdapter {
    private final URL address;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private WebSocketHandler webSocketHandler;
    private WebSocket webSocket;
    private NetworkHandler networkHandler;

    public WebSocketClient(final String url, final WebSocketListener... listeners) throws IOException {
        this(WebSocketEngine.DEFAULT_VERSION, url, listeners);
    }

    public WebSocketClient(Version version, final String url, final WebSocketListener... listeners) throws IOException {
        this(version, url, WebSocketEngine.DEFAULT_TIMEOUT, listeners);
    }

    public WebSocketClient(Version version, final String url, final long timeout, final WebSocketListener... listeners)
            throws IOException {
        address = new URL(url);
        networkHandler = new ClientNetworkHandler(this);
        webSocketHandler = version.createHandler(true);
        final Future<?> future = executorService.submit(new Runnable() {
            public void run() {
                try {
                    webSocketHandler.setNetworkHandler(networkHandler);
                    webSocketHandler.handshake(address);
                    webSocket = new BaseWebSocket(webSocketHandler, networkHandler, WebSocketClient.this);
                    for (WebSocketListener listener : listeners) {
                        webSocket.add(listener);
                    }
                    webSocket.onConnect();
                    webSocket.add(new WebSocketAdapter() {
                        public void onClose(WebSocket socket, DataFrame frame) {
                            socket.close();
                        }
                    });
                    queueRead();
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        });
        try {
            future.get(timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }

    private void queueRead() {
        execute(new Runnable() {
            public void run() {
                webSocketHandler.readFrame();
                if (webSocket.isConnected()) {
                    queueRead();
                }
            }
        });
    }

    protected NetworkHandler getNetworkHandler() {
        return networkHandler;
    }

    public URL getAddress() {
        return address;
    }

    public boolean isConnected() {
        return webSocket != null && webSocket.isConnected();
    }

    protected void execute(Runnable runnable) {
        executorService.submit(runnable);
    }

    public void send(String s) {
        webSocket.send(s);
    }

    public void send(byte[] s) {
        webSocket.send(s);
    }

    public void close() {
        webSocket.close();
    }

    public void stream(boolean last, byte[] bytes, int start, int length) {
        webSocket.stream(last, bytes, start, length);
    }
}