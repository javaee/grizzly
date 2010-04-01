package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;

import java.io.IOException;

/**
 * This is the default implementation for websockets support.  It will read the incoming data
 * frame and pass it off to the {@see Adapter} for processing.  This will typically be a servlet
 * but could be any implementation based on a Grizzly Adapter.
 */
public class DefaultWebSocket extends BaseServerWebSocket implements WebSocket {

    public DefaultWebSocket(Request request, Response response) throws IOException {
        super(null, request, response);
    }

    @Override
    public final void doRead() {
//        asyncTask.getAsyncExecutor().getProcessorTask().invokeAdapter();
    }
}