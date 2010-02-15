package com.sun.grizzly.websocket;

import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.http11.InputFilter;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.IOException;
import java.util.Arrays;

public class WebSocketInputFilter implements InputFilter {
    private Request request;
    private static final int FRAME_LENGTH = 512;
    private static final String ENCODING_NAME = "UTF-8";
    private static final ByteChunk ENCODING = new ByteChunk();
    private ByteChunk tempRead = new ByteChunk(FRAME_LENGTH);
    private InputBuffer buffer;
    private byte[] frame = new byte[FRAME_LENGTH];

    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(), 0, ENCODING_NAME.length());
    }

    public int doRead(ByteChunk chunk, Request unused) throws IOException {
        int read = 0;
        if (request.getAttribute("handshake") != null) {
            try {
                read = buffer.doRead(tempRead, request);
                for (int count = 0; read <= 0 && count < 10; count++) {
                    read = buffer.doRead(tempRead, request);
                }
                if (read > 0) {
                    try {
                        if (read > frame.length) {
                            frame = new byte[read + 1];
                            final byte[] bytes = tempRead.getBytes();
                            int index = tempRead.getStart();
                            int count = 0;
                            while(index < tempRead.getLength() && bytes[index] != (byte)0xFF) {
                                frame[count++] = bytes[index++];
                            }
                            frame[count++] = (byte) 0xFF;
                            read = count;
                        } else {
                            System.arraycopy(tempRead.getBytes(), tempRead.getStart(), frame, 0, read);
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                    if (frame[0] == (byte) 0x00 && frame[read - 1] == (byte) 0xFF) {
                        chunk.append(frame, 1, read - 2);
                    } else {
                        System.out.println("bad frame: '" + new String(tempRead.getBytes(), tempRead.getStart(), read - 1) + "'");
                        dump(read);

//                        throw new IOException(String.format("Malformed frame"));
                    }
                } else {
                    dump(read);
                }
            } finally {
                reset();
            }
        }

        return read;
    }

    private void dump(int read) {
        System.out.println("WebSocketInputFilter.doRead.read = " + read);
        System.out.println("WebSocketInputFilter.doRead.tempRead.getStart() = " + tempRead.getStart());
        System.out.println("WebSocketInputFilter.doRead.tempRead.getLength() = " + tempRead.getLength());
        System.out.println("WebSocketInputFilter.doRead.bytes.length = " + tempRead.getBytes().length);
    }

    private void reset() {
        if (tempRead.getBytes().length > FRAME_LENGTH) {
            tempRead = new ByteChunk(FRAME_LENGTH);
        } else {
            tempRead.recycle();
        }
        if (frame.length > FRAME_LENGTH) {
            frame = new byte[FRAME_LENGTH];
        } else {
            Arrays.fill(frame, (byte) 0);
        }
    }

    public void setRequest(Request req) {
        request = req;
    }

    public void recycle() {
        buffer = null;
        request = null;
        reset();
    }

    public ByteChunk getEncodingName() {
        return ENCODING;
    }

    public void setBuffer(InputBuffer buf) {
        buffer = buf;
    }

    public long end() throws IOException {
        return 0;
    }
}
