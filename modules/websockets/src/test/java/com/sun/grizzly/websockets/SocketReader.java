package com.sun.grizzly.websockets;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class SocketReader {
    private final InputStream stream;
//    private int count;

    public SocketReader(InputStream chan) {
        stream = chan;
    }

    public byte[] read(int size) {
        int bytesRead = 0;
//            ByteBuffer buffer = ByteBuffer.allocate(1024);
        byte[] buffer = new byte[size];
        try {

            while(bytesRead < size) {
                int count = stream.read(buffer, bytesRead, buffer.length - bytesRead);
                if (count == -1) {
                    throw new EOFException();
                }

                bytesRead += count;
            }

            return buffer;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

//    private boolean ready() throws IOException {
//        return stream.available() > 0;
//    }
//
//    public byte[] getBytes() {
//        if (baos.size() == 0) {
//            read();
//        }
//        return baos.toByteArray();
//    }
}
