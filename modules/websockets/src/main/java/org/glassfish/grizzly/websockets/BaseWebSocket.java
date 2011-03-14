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

package org.glassfish.grizzly.websockets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;

@SuppressWarnings({"StringContatenationInLoop"})
public class BaseWebSocket implements WebSocket {
    enum State {
        NEW, CONNECTED, CLOSING, CLOSED
    }

    private EnumSet<State> connectedSet = EnumSet.range(State.CONNECTED, State.CLOSING);

    private static final Logger logger = Grizzly.logger(BaseWebSocket.class);
    Connection connection;
    private final List<WebSocketListener> listeners = new ArrayList<WebSocketListener>();
    private final AtomicReference<State> state = new AtomicReference<State>(State.NEW);

    public BaseWebSocket(WebSocketListener... listeners) {
        for (WebSocketListener listener : listeners) {
            add(listener);
        }
    }

    public BaseWebSocket(final Connection connection, final WebSocketListener[] listeners) {
        this(listeners);
        this.connection = connection;
    }

    public List<WebSocketListener> getListeners() {
        return listeners;
    }

    public boolean isConnected() {
        return connectedSet.contains(state.get());
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
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            write(new ClosingFrame(code, reason), null);
        }
    }

    private <T> GrizzlyFuture<T> write(final DataFrame frame, final CompletionHandler<T> completionHandler) {
        try {
            return connection.write(frame, completionHandler);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new WebSocketException(e.getMessage(), e);
        }
    }

    public void onClose(DataFrame frame) {
        if (state.get() == State.CONNECTED) {
            try {
                write(new ClosingFrame(), new EmptyCompletionHandler<Object>() {
                    @Override
                    public void completed(final Object result) {
                        onClose();
                    }
                });
            } catch (Exception e) {
                throw new WebSocketException(e.getMessage(), e);
            }
        } else {
            onClose();
        }
    }

    public void onPing(DataFrame frame) {
        final byte[] bytes = frame.getBinaryPayload();
        send(new DataFrame(FrameType.PONG, bytes), null);
        for (WebSocketListener listener : listeners) {
            listener.onPing(bytes);
        }
    }

    private void onClose() {
        state.set(State.CLOSED);
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

    public GrizzlyFuture<DataFrame> send(String data) {
        return send(new DataFrame(data), null);
    }

    private GrizzlyFuture<DataFrame> send(DataFrame frame, CompletionHandler<DataFrame> completionHandler) {
        if (state.get() == State.CONNECTED) {
            return write(frame, completionHandler);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    public GrizzlyFuture<DataFrame> send(byte[] data) {
        return send(new DataFrame(data), null);
    }

    public void onConnect() {
        for (WebSocketListener listener : listeners) {
            listener.onConnect(this);
        }
        state.compareAndSet(State.NEW, State.CONNECTED);
    }

    @Override
    public void onMessage(String text) {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, text);
        }
    }
    @Override
    public void onMessage(byte[] data) {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, data);
        }
    }
}
