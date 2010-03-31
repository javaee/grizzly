package com.sun.grizzly.websockets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.logging.Level;

public enum FrameType {
    TEXT {
        @Override
        public boolean accept(byte delim) {
            return delim == (byte) 0x00;
        }

        @Override
        public byte[] unframe(ByteBuffer buffer) {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte b = 0;
            while (buffer.hasRemaining() && (b = buffer.get()) != (byte) 0xFF) {
                raw.write(b);
            }

            if (b != (byte) 0xFF) {
                throw new RuntimeException("Malformed frame.  Missing frame end delimiter: " + b);
            }

            return raw.toByteArray();
        }

        public byte[] frame(byte[] data) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 2);
            out.write((byte) 0x00);
            out.write(data, 0, data.length);
            out.write((byte) 0xFF);
            return out.toByteArray();
        }

    };

    public abstract boolean accept(byte stream);

    public abstract byte[] unframe(ByteBuffer buffer) throws IOException;

    public abstract byte[] frame(byte[] data);
}
