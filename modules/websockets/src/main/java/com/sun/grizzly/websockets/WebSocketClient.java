package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.util.net.URL;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;

public class WebSocketClient extends BaseWebSocket implements WebSocket {
    private SocketChannel channel;

    private static final String CLIENT_HANDSHAKE = "GET /echo HTTP/1.1" + Constants.CRLF
            + "Upgrade: WebSocket" + Constants.CRLF
            + "Connection: Upgrade" + Constants.CRLF
            + "Host: localhost" + Constants.CRLF
            + "Origin: http://localhost" + Constants.CRLF
            + Constants.CRLF;

    public WebSocketClient(String address) throws IOException {
        URL url = new URL(address);
        setSelector(SelectorProvider.provider().openSelector());
        new Thread(new Runnable() {
            public void run() {
                select();
            }
        }).start();
        open(url);
    }

    private void open(URL url) throws IOException {
        final SocketChannel socketChannel = SocketChannel.open();
        channel = socketChannel;
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(url.getHost(), url.getPort()));
        socketChannel.socket().setSoTimeout(30000);
        state = State.CONNECTING;
        getSelector().wakeup();
    }

    @Override
    public void close() throws IOException {
        super.close();
        channel.close();
    }

    private void select() {
        while (state != State.CLOSED) {
            try {
                if (state == State.CONNECTING) {
                    channel.register(getSelector(), SelectionKey.OP_CONNECT);
                }
                getSelector().select();

                Iterator<SelectionKey> selectedKeys = getSelector().selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();
                    process(key);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
            getSelector().wakeup();
        }
    }

    @Override
    protected void doConnect() throws IOException {
        channel.finishConnect();
        final byte[] bytes = CLIENT_HANDSHAKE.getBytes();
        write(bytes);
        state = State.WAITING_ON_HANDSHAKE;
        super.doConnect();
    }

    @Override
    protected void doRead() throws IOException {
        switch (state) {
            case WAITING_ON_HANDSHAKE:
                final ByteBuffer buffer = ByteBuffer.allocate(INITIAL_BUFFER_SIZE);
                final int read = channel.read(buffer);
                state = State.READY;
                setConnected(true);
                break;
            case READY:
                super.doRead();
                break;
            default:
                break;
        }
    }

    protected void unframe() throws IOException {
        int count;
        do {
            ByteBuffer bytes = ByteBuffer.allocate(INITIAL_BUFFER_SIZE);
            count = channel.read(bytes);
            bytes.flip();
            unframe(bytes);
        } while (count > 0);
    }

    @Override
    protected void write(byte[] bytes) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        final int i = channel.write(buffer);
    }

    protected SelectionKey getKey() {
        return channel.keyFor(getSelector());
    }

    @Override
    void enableOp(final int op) {
        final SelectionKey key = getKey();
        final int ops = key.interestOps();
        final int newOp = ops | op;
        if (newOp != ops) {
            key.interestOps(newOp);
        }
        key.selector().wakeup();
    }

    @Override
    void disableOp(final int op) {
        final SelectionKey key = getKey();
        final int ops = key.interestOps();
        final int newOp = ops & ~op;
        if (newOp != ops) {
            key.interestOps(newOp);
        }
    }
}