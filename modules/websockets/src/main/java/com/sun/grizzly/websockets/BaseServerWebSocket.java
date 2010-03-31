package com.sun.grizzly.websockets;

import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.tcp.http11.InternalOutputBuffer;
import com.sun.grizzly.util.SelectionKeyActionAttachment;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.MimeHeaders;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.logging.Level;

public class BaseServerWebSocket extends BaseWebSocket {
    final AsyncTask asyncTask;
    private final Request request;
    private final Response response;
    private final InternalInputBuffer inputBuffer;
    private final InternalOutputBuffer outputBuffer;

    public BaseServerWebSocket(AsyncExecutor asyncExecutor, final Request request, final Response response,
            final ClientHandShake client, WebSocketListener listener) throws IOException {
        this.request = request;
        this.response = response;

        final MimeHeaders headers = request.getMimeHeaders();
        final ServerHandShake handshake = new ServerHandShake(headers, client);

        handshake.prepare(response);
        response.flush();
        inputBuffer = (InternalInputBuffer) request.getInputBuffer();
        outputBuffer = (InternalOutputBuffer) response.getOutputBuffer();

        add(listener);
        ProcessorTask task = asyncExecutor.getProcessorTask();
        final SelectionKey selectionKey = task.getSelectionKey();
        selectionKey.attach(getSelectionKeyActionAttachment());
        asyncTask = asyncExecutor.getAsyncTask();
        checkBuffered(request);

        enableOp(SelectionKey.OP_READ);
    }

    @Override
    protected void unframe() throws IOException {
        final ByteChunk chunk = new ByteChunk(INITIAL_BUFFER_SIZE);
        while (inputBuffer.doRead(chunk, request) > 0) {
            unframe(chunk.toByteBuffer());
        }
    }

    @Override
    protected void write(byte[] bytes) throws IOException {
        ByteChunk chunk = new ByteChunk(bytes.length);
        chunk.setBytes(bytes, 0, bytes.length);
        outputBuffer.doWrite(chunk, response);
        outputBuffer.flush();
    }

    private void checkBuffered(Request request) throws IOException {
        final ByteChunk chunk = new ByteChunk(INITIAL_BUFFER_SIZE);
        if (inputBuffer.doRead(chunk, request) > 0) {
            unframe(chunk.toByteBuffer());
            onMessage();
        }
    }

    @Override
    final void disableOp(final int op) {
        final SelectorHandler handler = asyncTask.getAsyncExecutor().getProcessorTask().getSelectorHandler();
        final SelectionKey key = getKey();
        final int ops = key.interestOps();
        final int newOp = ops & ~op;
        handler.register(key, newOp);
        key.selector().wakeup();
    }

    @Override
    final void enableOp(final int op) {
        final SelectorHandler handler = asyncTask.getAsyncExecutor().getProcessorTask().getSelectorHandler();
        handler.register(getKey(), op);
        getKey().selector().wakeup();
    }

    private SelectionKeyActionAttachment getSelectionKeyActionAttachment() {
        return new SelectionKeyActionAttachment() {
            @Override
            public void process(final SelectionKey key) {
                try {
                    BaseServerWebSocket.this.process(key);
                } catch (IOException e) {
                    handle(e);
                }
            }

            @Override
            public void postProcess(SelectionKey selectionKey) {
            }
        };
    }

    protected SelectionKey getKey() {
        return asyncTask.getAsyncExecutor().getProcessorTask().getSelectionKey();
    }

    private void handle(Exception e) {
        final ProcessorTask task = asyncTask.getAsyncExecutor().getProcessorTask();
        task.setAptCancelKey(true);
        task.terminateProcess();
        logger.log(Level.INFO, e.getMessage(), e);
    }
}