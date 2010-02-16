package com.sun.grizzly.websocket;

import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.SelectionKeyActionAttachment;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.MimeHeaders;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class DefaultWebSocket extends SelectionKeyActionAttachment implements WebSocket {
    private final OutputBuffer outputBuffer;
    private final InputBuffer inputBuffer;
    private final AsyncTask asyncProcessorTask;
    private Request request;

    public DefaultWebSocket(AsyncExecutor asyncExecutor) throws IOException {
        asyncProcessorTask = asyncExecutor.getAsyncTask();
        ProcessorTask task = asyncExecutor.getProcessorTask();
        request = task.getRequest();
        final MimeHeaders headers = request.getMimeHeaders();
        final ServerHandShake handshake =
                new ServerHandShake(headers, new ClientHandShake(headers, false, request.requestURI().toString()));
        final Response response = request.getResponse();
        outputBuffer = response.getOutputBuffer();
        inputBuffer = request.getInputBuffer();
        handshake.prepare(response);
        response.flush();
        request.setAttribute("handshake", handshake);

        final SelectionKey selectionKey = task.getSelectionKey();
        final SelectorHandler handler = task.getSelectorHandler();
        selectionKey.attach(this);
        handler.register(selectionKey, SelectionKey.OP_READ);

        queue(selectionKey);
//        task.invokeAdapter();

    }

    @Override
    public void process(final SelectionKey selectionKey) {
        /* if(selectionKey.isWritable()) {
          doWrite(selectionKey);
      } else */
        if (selectionKey.isValid() && selectionKey.isReadable()) {
            queue(selectionKey);
        }
    }

    private void queue(final SelectionKey selectionKey) {
        asyncProcessorTask.getThreadPool().execute(new Runnable() {
            public void run() {
                doRead(selectionKey);
            }
        });
    }

    private void doWrite(SelectionKey selectionKey) {
        SocketChannel channel = (SocketChannel) selectionKey.channel();
        final ByteBuffer buffer = ByteBuffer.allocate(128);
        try {
            System.out.println(new java.util.Date() + ":  DefaultWebSocket.doWrite");
            while (channel.read(buffer) != 0) {
                System.out.println(new java.util.Date() + ":  reading bytes to write to WebSocket");
                final ByteChunk chunk = new ByteChunk();
                chunk.setBytes(buffer.array(), 0, buffer.limit());
                outputBuffer.doWrite(chunk, request.getResponse());
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        }
        System.out.println(new java.util.Date() + ":  DefaultWebSocket.doWrite -- done");
    }

    private void doRead(SelectionKey selectionKey) {
        if (selectionKey.isValid()) {
            final ProcessorTask task = asyncProcessorTask.getAsyncExecutor().getProcessorTask();
            task.getSelectorHandler().register(selectionKey, SelectionKey.OP_WRITE/* | SelectionKey.OP_WRITE*/);
            task.invokeAdapter();
            task.getSelectorHandler().register(selectionKey, SelectionKey.OP_READ/* | SelectionKey.OP_WRITE*/);
        }
    }

    @Override
    public void postProcess(SelectionKey selectionKey) {
    }
}
