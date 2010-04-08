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
        connected = true;
        onConnect();
    }

    public void onConnect() {
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