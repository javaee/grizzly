package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.tcp.http11.InternalOutputBuffer;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;

public class BaseServerWebSocket extends BaseWebSocket {
    private SocketChannel channel;
    private final Request request;
    private final Response response;
    private final InternalInputBuffer inputBuffer;
    private final InternalOutputBuffer outputBuffer;

    public BaseServerWebSocket(WebSocketListener listener, final Request request, final Response response) {
        this.request = request;
        this.response = response;

        inputBuffer = (InternalInputBuffer) request.getInputBuffer();
        outputBuffer = (InternalOutputBuffer) response.getOutputBuffer();

        add(listener);
    }

    @Override
    protected void unframe() throws IOException {
        final ByteChunk chunk = new ByteChunk(WebSocketEngine.INITIAL_BUFFER_SIZE);
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

}