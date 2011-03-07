/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.glassfish.grizzly.utils;

import java.io.IOException;
import java.io.InputStream;
import org.glassfish.grizzly.Buffer;

/**
 * {@link InputStream} implementation over Grizzly {@link Buffer}.
 *
 * @author Alexey Stashok
 */
public class BufferInputStream extends InputStream {

    private final Buffer buffer;

    public BufferInputStream(Buffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException {
        return buffer.get() & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int length = Math.min(len, available());

        buffer.get(b, off, length);

        return length;
    }

    @Override
    public int available() throws IOException {
        return buffer.remaining();
    }

    @Override
    public long skip(long n) throws IOException {
        int skipped = (int) Math.min(n, available());

        buffer.position(buffer.position() + skipped);
        return skipped;
    }
}
