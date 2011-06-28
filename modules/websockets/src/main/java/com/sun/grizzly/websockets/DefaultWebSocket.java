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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@SuppressWarnings({"StringContatenationInLoop"})
public class DefaultWebSocket implements WebSocket {
    protected static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    private final List<WebSocketListener> listeners = new ArrayList<WebSocketListener>();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final ProtocolHandler protocolHandler;

    public DefaultWebSocket(ProtocolHandler protocolHandler, WebSocketListener... listeners) {
        this.protocolHandler = protocolHandler;
        for (WebSocketListener listener : listeners) {
            add(listener);
        }
        protocolHandler.setWebSocket(this);
    }

    public NetworkHandler getNetworkHandler() {
        return protocolHandler.getNetworkHandler();
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

    public void close() {
        close(-1, null);
    }

    public void close(int code) {
        close(code, null);
    }

    public void close(int code, String reason) {
        if (connected.compareAndSet(true, false)) {
            protocolHandler.close(code, reason);
            onClose(null);
        }
    }

    public void onClose(DataFrame frame) {
        final Iterator<WebSocketListener> it = listeners.iterator();
        while (it.hasNext()) {
            final WebSocketListener listener = it.next();
            it.remove();
            listener.onClose(this, frame);
        }
    }

    public void onPing(DataFrame frame) {
        for (WebSocketListener listener : listeners) {
            listener.onPing(this, frame.getBytes());
        }
    }

    public void onPong(DataFrame frame) {
        for (WebSocketListener listener : listeners) {
            listener.onPong(this, frame.getBytes());
        }
    }

    public void onFragment(boolean last, String fragment) {
        for (WebSocketListener listener : listeners) {
            listener.onFragment(this, fragment, last);
        }
    }

    public void onFragment(boolean last, byte[] fragment) {
        for (WebSocketListener listener : listeners) {
            listener.onFragment(this, fragment, last);
        }
    }

    public final boolean remove(WebSocketListener listener) {
        return listeners.remove(listener);
    }

    public void send(String data) {
        if (connected.get()) {
            protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public void send(final byte[] data) {
        if (connected.get()) {
            protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public void send(DataFrame frame) {
        if (connected.get()) {
            protocolHandler.send(frame);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public void onConnect() {
        for (WebSocketListener listener : listeners) {
            listener.onConnect(this);
        }
        connected.compareAndSet(false, true);
    }

    public void onMessage(String text) {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, text);
        }
    }

    public void onMessage(byte[] data) {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, data);
        }
    }

    public void stream(boolean last, String fragment) {
        if (connected.get()) {
            protocolHandler.stream(last, fragment);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public void stream(boolean last, byte[] bytes, int off, int len) {
        if (connected.get()) {
            protocolHandler.stream(last, bytes, off, len);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public final HttpServletRequest getRequest() {
        final NetworkHandler handler = getNetworkHandler();
        try {
            return handler instanceof ServerNetworkHandler ? ((ServerNetworkHandler) handler).getRequest() : null;
        } catch (IOException e) {
            throw new WebSocketException(e.getMessage(), e);
        }
    }

    public final HttpServletResponse getResponse() {
        final NetworkHandler handler = getNetworkHandler();
        try {
            return handler instanceof ServerNetworkHandler ? ((ServerNetworkHandler) handler).getResponse() : null;
        } catch (IOException e) {
            throw new WebSocketException(e.getMessage(), e);
        }
    }
}