package com.sun.grizzly.websockets;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class DataFrame {
    private static final Logger logger = Logger.getLogger(WebSocket.WEBSOCKET);
    private String payload;
    private byte[] bytes;
    private FrameType type;

    public DataFrame(ByteBuffer buffer) throws IOException {
        byte leading = buffer.get();
        for (FrameType frameType : FrameType.values()) {
            if (frameType.accept(leading)) {
                type = frameType;
                bytes = type.unframe(buffer);
            }
        }
    }

    public DataFrame(String data) {
        type = FrameType.TEXT;
        payload = data;
        try {
            bytes = data.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public FrameType getType() {
        return type;
    }

    public String getTextPayload() {
        if (payload == null && bytes != null) {
            try {
                payload = new String(bytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        return payload;
    }

    public byte[] getBinaryPayload() {
        return bytes;
    }

    public byte[] frame() {
        return frame(type);
    }

    public byte[] frame(FrameType type) {
        return type.frame(bytes);
    }

    @Override
    public String toString() {
        return payload;
    }
}