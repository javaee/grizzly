package com.sun.grizzly.websocket;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.arp.AsyncProcessorTask;

import java.util.logging.Logger;

public class WebSocketAsyncFilter implements AsyncFilter {
    private static final Logger logger = Logger.getLogger("websocket");

    public boolean doFilter(AsyncExecutor asyncExecutor) {
        return !WebSocketEngine.getEngine().handle((AsyncProcessorTask)asyncExecutor.getAsyncTask());
    }
}
