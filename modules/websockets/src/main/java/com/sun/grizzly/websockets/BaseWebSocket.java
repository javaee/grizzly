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

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings({"StringContatenationInLoop"})
public class BaseWebSocket implements WebSocket {
    NetworkHandler networkHandler;
    protected static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    private final List<WebSocketListener> listeners = new ArrayList<WebSocketListener>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final SecureRandom random = new SecureRandom();

    public BaseWebSocket(WebSocketListener... listeners) {
        for (WebSocketListener listener : listeners) {
            add(listener);
        }
    }

    public NetworkHandler getNetworkHandler() {
        return networkHandler;
    }

    public void setNetworkHandler(NetworkHandler handler) {
        networkHandler = handler;
        handler.setWebSocket(this);
    }

    public List<WebSocketListener> getListeners() {
        return listeners;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public final boolean add(WebSocketListener listener) {
        return listeners.add(listener);
    }

    public void close() throws IOException {
        close(-1, null);
    }

    public void close(int code) throws IOException {
        close(code, null);
    }

    public void close(int code, String reason) throws IOException {
        if (connected.compareAndSet(true, false)) {
            try {
                networkHandler.send(new ClosingFrame(code, reason));
            } catch (IOException ignored) {
                logger.log(Level.SEVERE, ignored.getMessage(), ignored);
            }
        }
    }

    public byte[] generateMask() {
        byte[] bytes = new byte[WebSocketEngine.MASK_SIZE];
        synchronized (random) {
            random.nextBytes(bytes);
        }
        return bytes;
    }

    public void onClose(DataFrame frame) throws IOException {
        close(NORMAL_CLOSURE);
        onClose();
    }

    public void onPing(DataFrame frame) throws IOException {
        networkHandler.send(new DataFrame(FrameType.PONG, frame.getBinaryPayload()));
    }

    private void onClose() throws IOException {
        final Iterator<WebSocketListener> it = listeners.iterator();
        while (it.hasNext()) {
            final WebSocketListener listener = it.next();
            it.remove();
            listener.onClose(this);
        }
    }

    public final boolean remove(WebSocketListener listener) {
        return listeners.remove(listener);
    }

    public void send(String data) throws IOException {
        if (connected.get()) {
            networkHandler.send(new DataFrame(data));
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public void send(final byte[] data) throws IOException {
        networkHandler.send(new DataFrame(data));
    }

    public void onConnect() throws IOException {
        for (WebSocketListener listener : listeners) {
            listener.onConnect(this);
        }
        connected.compareAndSet(false, true);
    }

    public void onMessage(DataFrame frame) throws IOException {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, frame);
        }
    }
}
