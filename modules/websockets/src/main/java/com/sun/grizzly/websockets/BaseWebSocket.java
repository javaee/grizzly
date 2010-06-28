/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.websockets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

@SuppressWarnings({"StringContatenationInLoop"})
public abstract class BaseWebSocket implements WebSocket {
    protected enum State {
        STARTING,
        CONNECTING,
        WAITING_ON_HANDSHAKE,
        READY,
        CLOSED
    }

    protected static final Logger logger = Logger.getLogger(WebSocket.WEBSOCKET);
    protected State state = State.STARTING;
    private final Set<WebSocketListener> listeners = new LinkedHashSet<WebSocketListener>();
    private final Queue<DataFrame> incoming = new ConcurrentLinkedQueue<DataFrame>();
    private Selector selector;
    private boolean connected = false;

    public Set<WebSocketListener> getListeners() {
        return listeners;
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector sel) {
        selector = sel;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean conn) {
        connected = conn;
    }

    public final boolean add(WebSocketListener listener) {
        return listeners.add(listener);
    }

    public void close() throws IOException {
        onClose();
        connected = false;
        state = State.CLOSED;
        incoming.clear();
        listeners.clear();
    }

    public void onClose() {
        for (WebSocketListener listener : listeners) {
            listener.onClose(this);
        }
    }

    public final boolean remove(WebSocketListener listener) {
        return listeners.remove(listener);
    }

    public void send(String data) {
        send(new DataFrame(data));
    }

    private void send(final DataFrame frame) {
        try {
            write(frame.frame());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected DataFrame incoming() {
        return incoming.poll();
    }


    protected void doConnect() throws IOException {
    }

    public void onConnect() {
        connected = true;
        for (WebSocketListener listener : listeners) {
            listener.onConnect(this);
        }
    }

    protected void doRead() throws IOException {
        unframe();
    }

    public void onMessage() {
        DataFrame frame;
        while ((frame = incoming()) != null) {
            for (WebSocketListener listener : listeners) {
                listener.onMessage(this, frame);
            }
        }
    }

    protected void unframe(ByteBuffer bytes) throws IOException {
        while (bytes.hasRemaining()) {
            final DataFrame dataFrame = new DataFrame(bytes);
            if (dataFrame.getType() != null) {
                incoming.offer(dataFrame);
                onMessage();
            }
        }
    }

    protected abstract void unframe() throws IOException;

    protected abstract void write(byte[] bytes) throws IOException;
}
