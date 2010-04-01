package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class WebSocketApplication implements WebSocketListener {
    private final Set<WebSocket> sockets = new HashSet<WebSocket>();
    private final Set<WebSocketListener> listeners = new HashSet<WebSocketListener>();
    
    public boolean add(WebSocket socket) {
        return sockets.add(socket);
    }
    
    public boolean remove(WebSocket socket) {
        return sockets.remove(socket);
    }
    
    public boolean add(WebSocketListener listener) {
        return listeners.add(listener);
    }
    
    public boolean remove(WebSocketListener listener) {
        return listeners.remove(listener);
    }

    public WebSocket createSocket(AsyncExecutor asyncExecutor, Request request, Response response) throws IOException {
        return new BaseServerWebSocket(request, response, this);
    }
}
