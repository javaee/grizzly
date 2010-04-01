package com.sun.grizzly.websockets;

public interface WebSocketListener {
    void onClose(WebSocket socket);

    void onConnect(WebSocket socket);

    void onMessage(WebSocket socket, DataFrame data);
}
