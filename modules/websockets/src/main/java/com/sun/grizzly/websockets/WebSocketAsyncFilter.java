package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncFilter;

import java.util.logging.Logger;

public class WebSocketAsyncFilter implements AsyncFilter {
    private static final Logger logger = Logger.getLogger(WebSocket.WEBSOCKET);

    public boolean doFilter(AsyncExecutor asyncExecutor) {
        final boolean handled = WebSocketEngine.getEngine().handle(asyncExecutor);
        if(!handled) {
            asyncExecutor.getProcessorTask().invokeAdapter();
        }
        return !handled;
    }

}
