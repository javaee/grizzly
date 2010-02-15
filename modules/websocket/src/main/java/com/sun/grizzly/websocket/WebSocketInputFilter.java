package com.sun.grizzly.websocket;

import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.http11.InputFilter;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.IOException;
import java.util.Arrays;

public class WebSocketInputFilter implements InputFilter {
    private Request request;
    private static final String ENCODING_NAME = "UTF-8";
    private static final ByteChunk ENCODING = new ByteChunk();
    private final ByteChunk tempRead = new ByteChunk(1);
    private InputBuffer buffer;
    private final ByteChunk frame = new ByteChunk();

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(), 0, ENCODING_NAME.length());
    }

    public WebSocketInputFilter() {
        frame.setEncoding("UTF-8");
    }

    public int doRead(ByteChunk chunk, Request unused) throws IOException {
        int read = 0;
        if (request.getAttribute("handshake") != null) {
            frame.recycle();
            read = buffer.doRead(tempRead, request);
            System.out.println("WebSocketInputFilter.doRead : read = " + read);
            frame.append(tempRead);
            tempRead.recycle();
            final byte[] bytes = frame.getBytes();
            if (read != -1) {
                if (bytes != null && bytes[0] == 0x00 && bytes[read - 1] == (byte) 0xFF) {
                    chunk.append(bytes, 1, read - 2);
//                    System.out.println("good frame: " + new String(bytes, 1, read - 2));
//                    System.out.println("good frame: " + Arrays.toString(bytes));
                } else {
                    System.out.println("bad frame: '" + Arrays.toString(bytes) + "'");
                    System.out.println("bad frame: '" + new String(bytes) + "'");
                    throw new IOException(String.format("Malformed frame: " + Arrays.toString(bytes)));
                }
            }
        }
        return read;
    }

    public void setRequest(Request req) {
        request = req;
    }

    public void recycle() {
        buffer = null;
        request = null;
        frame.recycle();
    }

    public ByteChunk getEncodingName() {
        return ENCODING;
    }

    public void setBuffer(InputBuffer buf) {
        System.out.println("WebSocketInputFilter.setBuffer : buf = " + buf);
        buffer = buf;
    }

    public long end() throws IOException {
        return 0;
    }
}
