/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.websockets.frametypes.PingFrameType;
import com.sun.grizzly.websockets.frametypes.PongFrameType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"StringContatenationInLoop"})
public class DefaultWebSocket implements WebSocket {
    private final Collection<WebSocketListener> listeners = new ConcurrentLinkedQueue<WebSocketListener>();
    protected final ProtocolHandler protocolHandler;
    protected HttpServletRequest request;
    protected HttpServletResponse response;

    enum State {
        NEW,
        CONNECTED,
        CLOSING,
        CLOSED
    }

    private final AtomicReference<State> state = new AtomicReference<State>(State.NEW);
    EnumSet<State> connected = EnumSet.range(State.CONNECTED, State.CLOSING);

    public DefaultWebSocket(ProtocolHandler protocolHandler, WebSocketListener... listeners) {
        this.protocolHandler = protocolHandler;
        final NetworkHandler handler = protocolHandler.getNetworkHandler();
        if (handler instanceof ServerNetworkHandler) {
            final ServerNetworkHandler networkHandler =
                    (ServerNetworkHandler) handler;
            try {
                request = networkHandler.getRequest();
                response = networkHandler.getResponse();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        for (WebSocketListener listener : listeners) {
            add(listener);
        }
        protocolHandler.setWebSocket(this);
    }

    public NetworkHandler getNetworkHandler() {
        return protocolHandler.getNetworkHandler();
    }

    public void setNetworkHandler(NetworkHandler handler) {
        protocolHandler.setNetworkHandler(handler);
    }

    public Collection<WebSocketListener> getListeners() {
        return listeners;
    }

    public final boolean add(WebSocketListener listener) {
        return listeners.add(listener);
    }

    public final boolean remove(WebSocketListener listener) {
        return listeners.remove(listener);
    }

    public boolean isConnected() {
        return connected.contains(state.get());
    }

    public void setClosed() {
        state.set(State.CLOSED);
    }

    public void onClose(DataFrame frame) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING) || state.get() == State.CLOSING) {
            if(frame != null) {
                protocolHandler.close(frame);
            }

            final Iterator<WebSocketListener> it = listeners.iterator();
            while (it.hasNext()) {
                final WebSocketListener listener = it.next();
                it.remove();
                listener.onClose(this, frame);
            }
            
            state.set(State.CLOSED);
        }
    }

    public void onConnect() {
        state.set(State.CONNECTED);
        for (WebSocketListener listener : listeners) {
            listener.onConnect(this);
        }
    }

    public void onFragment(boolean last, byte[] fragment) {
        for (WebSocketListener listener : listeners) {
            listener.onFragment(this, fragment, last);
        }
    }

    public void onFragment(boolean last, String fragment) {
        for (WebSocketListener listener : listeners) {
            listener.onFragment(this, fragment, last);
        }
    }

    public void onMessage(byte[] data) {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, data);
        }
    }

    public void onMessage(String text) {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, text);
        }
    }

    public void onPing(DataFrame frame) {
        send(new DataFrame(new PongFrameType(), frame.getBytes()));
        for (WebSocketListener listener : listeners) {
            listener.onPing(this, frame.getBytes());
        }
    }

    public void onPong(DataFrame frame) {
        for (WebSocketListener listener : listeners) {
            listener.onPong(this, frame.getBytes());
        }
    }

    public void close() {
        close(-1, null);
    }

    public void close(int code) {
        close(code, null);
    }

    public void close(int code, String reason) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(code, reason);
        }
    }

    public void send(final byte[] data) {
        if (state.get() == State.CONNECTED) {
            protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public void send(String data) {
        if (state.get() == State.CONNECTED) {
            protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void sendPing(byte[] data) {
        send(new DataFrame(new PingFrameType(), data));
    }

    /**
     * {@inheritDoc}
     */
    public void sendPong(byte[] data) {
        send(new DataFrame(new PongFrameType(), data));
    }

    private void send(DataFrame frame) {
        if (state.get() == State.CONNECTED) {
            protocolHandler.send(frame);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public void stream(boolean last, String fragment) {
        if (state.get() == State.CONNECTED) {
            protocolHandler.stream(last, fragment);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public void stream(boolean last, byte[] bytes, int off, int len) {
        if (state.get() == State.CONNECTED) {
            protocolHandler.stream(last, bytes, off, len);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public final HttpServletRequest getRequest() {
        return request;
    }

    public final HttpServletResponse getResponse() {
        return response;
    }
}