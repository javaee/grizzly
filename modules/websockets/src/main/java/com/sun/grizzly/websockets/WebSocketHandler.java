package com.sun.grizzly.websockets;

public interface WebSocketHandler {
    void onInitialize(WebSocketEvent eventInitialize);

    void onInterrupt(WebSocketEvent eventInterrupt);
}
