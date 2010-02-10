package com.sun.grizzly.websocket;

public interface WebSocketHandler {
    void onInitialize(WebSocketEvent eventInitialize);

    void onInterrupt(WebSocketEvent eventInterrupt);
}
