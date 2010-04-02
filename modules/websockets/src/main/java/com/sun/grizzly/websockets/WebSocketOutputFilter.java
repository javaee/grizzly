package com.sun.grizzly.websockets;

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
        String text = new String(chunk.getBytes(), chunk.getStart(), chunk.getLength());
        DataFrame frame = new DataFrame(text);
        final byte[] bytes = frame.frame();
        ByteChunk framed = new ByteChunk(bytes.length);
        framed.setBytes(bytes, 0, bytes.length);
        buffer.doWrite(framed, response);
        return bytes.length;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public void recycle() {
        response = null;
        buffer = null;
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
