package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class WebSocketApplication implements WebSocketListener {
    private final Map<WebSocket, Boolean> sockets =
            new ConcurrentHashMap<WebSocket, Boolean>();

    private final Map<WebSocketListener, Boolean> listeners =
            new ConcurrentHashMap<WebSocketListener, Boolean>();

    /**
     * Returns a set of {@link WebSocket}s, registered with the application.
     * The returned set is unmodifiable, the possible modifications may cause exceptions.
     *
     * @return a set of {@link WebSocket}s, registered with the application.
     */
    protected Set<WebSocket> getWebSockets() {
        return sockets.keySet();
    }

    public boolean add(WebSocket socket) {
        return sockets.put(socket, Boolean.TRUE);
    }
    
    public boolean remove(WebSocket socket) {
        return sockets.remove(socket);
    }
    
    public boolean add(WebSocketListener listener) {
        return listeners.put(listener, Boolean.TRUE);
    }
    
    public boolean remove(WebSocketListener listener) {
        return listeners.remove(listener);
    }

    public WebSocket createSocket(Request request, Response response) throws IOException {
        return new BaseServerWebSocket(this, request, response);
    }
}
