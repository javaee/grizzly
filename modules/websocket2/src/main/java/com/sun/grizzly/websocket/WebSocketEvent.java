package com.sun.grizzly.websocket;

public class WebSocketEvent {
    private Type type;
    private WebSocketContext context;

    public WebSocketEvent(Type eventType, WebSocketContext socketContext) {
        type = eventType;
        context = socketContext;
    }

    public enum Type {
        INTERRUPT,
        NOTIFY,
        INITIALIZE,
        TERMINATE,
        READ,
        WRITE,
    }
}
