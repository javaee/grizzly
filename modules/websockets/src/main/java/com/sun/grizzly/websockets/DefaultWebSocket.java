package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;

/**
 * This is the default implementation for websockets support.  It will read the incoming data
 * frame and pass it off to the {@see Adapter} for processing.  This will typically be a servlet
 * but could be any implementation based on a Grizzly Adapter.
 */
public class DefaultWebSocket extends BaseServerWebSocket implements WebSocket {

    public DefaultWebSocket(AsyncExecutor asyncExecutor, Request request, Response response,
            ClientHandShake clientHandShake, Selector selector) throws IOException {
        super(asyncExecutor, request, response, clientHandShake, null);
    }

    public final void doRead(SelectableChannel selectableChannel) {
        asyncTask.getAsyncExecutor().getProcessorTask().invokeAdapter();
    }
}