package com.sun.grizzly.websocket;

import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.OutputFilter;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.IOException;

public class WebSocketOutputFilter implements OutputFilter {
    private Response response;
    private static final byte[] ENCODING_NAME = "UTF-8".getBytes();
    private static final ByteChunk ENCODING = new ByteChunk();
    private OutputBuffer buffer;

    static {
        ENCODING.setBytes(ENCODING_NAME, 0, ENCODING_NAME.length);
    }

    public int doWrite(ByteChunk chunk, Response unused) throws IOException {
        System.out.println("WSOF: " + new String(chunk.getBytes(), 0, chunk.getLength(), "UTF-8"));
        return 0;
    }

    public void setResponse(Response response) {
        System.out.println("WebSocketOutputFilter.setResponse");
        System.out.println("response = " + response);
        this.response = response;
    }

    public void recycle() {
        response = null;
    }

    public ByteChunk getEncodingName() {
        return ENCODING;
    }

    public void setBuffer(OutputBuffer buffer) {
        this.buffer = buffer;
    }

    public long end() throws IOException {
        return 0;
    }
}
