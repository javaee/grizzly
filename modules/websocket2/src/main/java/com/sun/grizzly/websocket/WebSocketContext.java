package com.sun.grizzly.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketContext {
    public WebSocketEvent eventInterrupt;
    ContinuationType continuationType = ContinuationType.AFTER_SERVLET_PROCESSING;
    private int expirationDelay;
    private final WebSocketEvent eventInitialize;
    private final String path;
    private NotificationHandler handler;
    private final Map<WebSocketHandler, WebSocketTask> handlers;

    public WebSocketContext(String contextPath, ContinuationType type) {
        path = contextPath;
        continuationType = type;
        eventInitialize = new WebSocketEvent(WebSocketEvent.Type.INITIALIZE, this);
        eventInterrupt = new WebSocketEvent(WebSocketEvent.Type.INTERRUPT, this);
        handlers = new ConcurrentHashMap<WebSocketHandler, WebSocketTask>(16, 0.75f, 64);
    }

    public int getExpirationDelay() {
        return expirationDelay;
    }

    public void setExpirationDelay(int delay) {
        expirationDelay = delay;
    }

    public void addActiveHandler(WebSocketTask webSocketTask) {
        handlers.put(webSocketTask.webSocketHandler, webSocketTask);
    }

    protected void initialize(WebSocketHandler handler) throws IOException {
        handler.onInitialize(eventInitialize);
    }

    public NotificationHandler getNotificationHandler() {
        return handler;
    }

    public void setNotificationHandler(NotificationHandler notificationHandler) {
        handler = notificationHandler;
    }

    public boolean isActive(WebSocketHandler handler) {
        return handlers.containsKey(handler);
    }
}