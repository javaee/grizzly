package com.sun.grizzly.websockets;

public interface WebSocketListener {
    void onRead(WebSocket socket, DataFrame data);

    void onConnect(WebSocket socket);

    void onClose(WebSocket socket);
}
