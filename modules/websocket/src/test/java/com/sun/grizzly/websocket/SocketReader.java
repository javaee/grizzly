package com.sun.grizzly.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SocketReader {
    private final InputStream stream;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private int count;

    public SocketReader(InputStream chan) {
        stream = chan;
    }

    public byte[] read() {
        count = 0;
        baos.reset();
//            ByteBuffer buffer = ByteBuffer.allocate(1024);
        byte[] buffer = new byte[1024];
        try {
            int tries = 0;
            while (tries++ < 10 && (count == 0 || ready())) {
                if (ready()) {
                    count = stream.read(buffer);
                    baos.write(buffer, 0, count);
                }
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    private boolean ready() throws IOException {
        return stream.available() > 0;
    }

    public byte[] getBytes() {
        if (baos.size() == 0) {
            read();
        }
        return baos.toByteArray();
    }
}
