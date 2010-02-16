package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.IOException;
import java.nio.CharBuffer;

public class EchoAdapter extends GrizzlyAdapter {
    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        try {
            final CharBuffer buffer = CharBuffer.allocate(1024);
            request.getReader().read(buffer);
            ByteChunk chunk = new ByteChunk();
            final char[] chars = buffer.array();
            for (int index = 0; index < buffer.position(); index++) {
                chunk.append(chars[index]);
            }
            response.getOutputBuffer().write(buffer.array(), buffer.arrayOffset(), buffer.position());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
