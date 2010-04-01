package com.sun.grizzly.websockets;

import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;

/**
 * This is the default implementation for websockets support.  This will hand off processing of the request
 * to an underlying adapter but frame the response.  This will typically be a servlet
 * but could be any implementation based on a Grizzly Adapter.
 */
public class PassThroughWebSocket extends BaseServerWebSocket implements WebSocket {
    private final ProcessorTask task;

    public PassThroughWebSocket(Request request, Response response, final ProcessorTask processorTask) {
        super(null, request, response);
        task = processorTask;
    }

    @Override
    public final void doRead() {
        task.invokeAdapter();
    }
}