package com.sun.grizzly.websocket;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncFilter;

import java.io.IOException;
import java.util.logging.Logger;

public class WebSocketAsyncFilter implements AsyncFilter {
    private static final Logger logger = Logger.getLogger("websocket");

    public boolean doFilter(AsyncExecutor asyncExecutor) {
        try {
            WebSocket socket = getWebSocket(asyncExecutor);
        } catch (IOException e) {
            return true;
        }
        return false;
    }

    protected WebSocket getWebSocket(AsyncExecutor asyncExecutor) throws IOException {
        return new DefaultWebSocket(asyncExecutor);
    }
}
