package com.sun.grizzly.websockets;

import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.SelectionKeyActionAttachment;
import com.sun.grizzly.util.http.MimeHeaders;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public class DefaultWebSocket extends SelectionKeyActionAttachment implements WebSocket {
    private final AsyncTask asyncProcessorTask;

    public DefaultWebSocket(AsyncExecutor asyncExecutor) throws IOException {
        asyncProcessorTask = asyncExecutor.getAsyncTask();
        ProcessorTask task = asyncExecutor.getProcessorTask();
        Request request = task.getRequest();
        final MimeHeaders headers = request.getMimeHeaders();
        final ServerHandShake handshake =
                new ServerHandShake(headers, new ClientHandShake(headers, false, request.requestURI().toString()));
        final Response response = request.getResponse();
        handshake.prepare(response);
        response.flush();
        request.setAttribute("handshake", handshake);

        final SelectionKey selectionKey = task.getSelectionKey();
        selectionKey.attach(this);
        runWebSocketsAdapter(selectionKey);
    }

    @Override
    public void process(final SelectionKey selectionKey) {
        if (selectionKey.isReadable()) {
            runWebSocketsAdapter(selectionKey);
        }
    }

    private void runWebSocketsAdapter(final SelectionKey selectionKey) {
        final ProcessorTask task = asyncProcessorTask.getAsyncExecutor().getProcessorTask();
        task.invokeAdapter();
        enableOpRead(selectionKey);
    }

    @Override
    public void postProcess(SelectionKey selectionKey) {
    }

    /**
     * This method may be called from within worker thread, so we have to use
     * {@link SelectorHandler}.
     *
     * @param selectionKey
     */
    private void enableOpRead(SelectionKey selectionKey) {
        final SelectorHandler selectorHandler = asyncProcessorTask.
                getAsyncExecutor().getProcessorTask().getSelectorHandler();
        selectorHandler.register(selectionKey, SelectionKey.OP_READ);
    }
}
