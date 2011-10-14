package com.sun.grizzly.http.ajp;

import java.io.IOException;
import java.io.InputStream;

class AjpInputStream extends InputStream {
    private InputStream inputStream;

    public AjpInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        return inputStream.read(bytes);
    }

    @Override
    public int read(byte[] bytes, int i, int i1) throws IOException {
        return inputStream.read(bytes, i, i1);
    }

    @Override
    public long skip(long l) throws IOException {
        return inputStream.skip(l);
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    @Override
    public synchronized void mark(int i) {
        inputStream.mark(i);
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }
}
