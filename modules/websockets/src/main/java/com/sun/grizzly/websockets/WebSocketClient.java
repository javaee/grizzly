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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WebSocketClient extends DefaultWebSocket {
    public static final int DEFAULT_TIMEOUT = WebSocketEngine.DEFAULT_TIMEOUT;
    public static final TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;
    private final URL address;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public WebSocketClient(final String url, final WebSocketListener... listeners) throws IOException {
        this(url, WebSocketEngine.DEFAULT_VERSION, listeners);
    }

    public WebSocketClient(final String url, Version version, final WebSocketListener... listeners) throws IOException {
        super(version.createHandler(true), listeners);
        address = new URL(url);
        setNetworkHandler(new ClientNetworkHandler(this));
        add(new CloseAdapter());
    }

    /**
     * @return this on successful connection
     */
    public WebSocket connect() {
        return connect(WebSocketEngine.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
    }

    public WebSocket connect(final int timeout, final TimeUnit unit) {
        final Callable<WebSocket> callable = new Callable<WebSocket>() {
            public WebSocketClient call() {
                try {
                    final HandShake handshake = protocolHandler.handshake(address);
                    onConnect();
                    queueRead();
                    return WebSocketClient.this;
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        };
        final Future<WebSocket> future = executorService.<WebSocket>submit(callable);
        try {
            return future.get(timeout, unit);
        } catch (InterruptedException e) {
            throw new WebSocketException(e.getMessage(), e);
        } catch (ExecutionException e) {
            throw new WebSocketException(e.getMessage(), e);
        } catch (TimeoutException e) {
            throw new WebSocketException(e.getMessage(), e);
        }
    }

    private void queueRead() {
        execute(new Runnable() {
            public void run() {
                protocolHandler.readFrame();
                if (isConnected()) {
                    queueRead();
                }
            }
        });
    }

    public URL getAddress() {
        return address;
    }

    protected void execute(Runnable runnable) {
        executorService.submit(runnable);
    }

    private static class CloseAdapter extends WebSocketAdapter {
        public void onClose(WebSocket socket, DataFrame frame) {
            socket.close();
        }
    }
}