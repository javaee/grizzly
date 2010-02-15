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
    private ByteChunk framed = new ByteChunk(1024);

    static {
        ENCODING.setBytes(ENCODING_NAME, 0, ENCODING_NAME.length);
    }

    public int doWrite(ByteChunk chunk, Response unused) throws IOException {
        System.out.println("WSOF: " + new String(chunk.getBytes(), 0, chunk.getLength(), "UTF-8"));
        framed.append((byte) 0x00);
        framed.append(chunk);
        framed.append((byte) 0xFF);
        buffer.doWrite(framed, response);
        framed.recycle();
        return framed.getLength();
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public void recycle() {
        response = null;
        buffer = null;
        framed.recycle();
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
