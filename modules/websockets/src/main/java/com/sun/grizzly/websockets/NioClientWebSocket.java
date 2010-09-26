package com.sun.grizzly.websockets;

import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.net.URL;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NioClientWebSocket extends BaseWebSocket {
    private static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);

    private final Selector selector;
    private final AtomicBoolean connecting = new AtomicBoolean(true);
    private final AtomicBoolean running = new AtomicBoolean(true);

    protected volatile long selectTimeout = 1000;
    private final URL address;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public NioClientWebSocket(final String url, final WebSocketListener... listeners) throws IOException {
        this(url, WebSocketEngine.DEFAULT_TIMEOUT, listeners);
    }

    public NioClientWebSocket(final String url, final long timeout, final WebSocketListener... listeners) throws IOException {
        super(listeners);
        address = new URL(url);
        selector = SelectorProvider.provider().openSelector();
        Thread selectorThread = new Thread(new Runnable() {
            public void run() {
                select();
            }
        });
        selectorThread.setDaemon(true);
        selectorThread.start();
        WebSocketConnectTask connectTask = new WebSocketConnectTask(this);
        executorService.execute(connectTask);
        try {
            connectTask.get(timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }

    public URL getAddress() {
        return address;
    }

    private void select() {
        while (running.get()) {
            try {
                if (connecting.compareAndSet(true, false)) {
                    final NioClientNetworkHandler handler = createNetworkHandler(selector);
                    setNetworkHandler(handler);
                    final SocketChannel socketChannel = handler.getChannel();
                    if (socketChannel.isConnected()) { // Channel was immediately connected
                        socketChannel.register(selector, SelectionKey.OP_READ, handler);
                        handler.doConnect(false);
                    } else { // Channel we be connected in NIO fashion
                        socketChannel.register(selector, SelectionKey.OP_CONNECT, handler);
                    }
                }

                final int count = selector.select(selectTimeout);
                if (count != 0) {
                    Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                    while (selectedKeys.hasNext()) {
                        SelectionKey key = selectedKeys.next();
                        selectedKeys.remove();
                        final NioClientNetworkHandler handler = (NioClientNetworkHandler) key.attachment();
                        try {
                            handler.process(key);
                        } catch (IOException e) {
                            e.printStackTrace();
                            handler.shutdown();
                        }
                    }
                }
            } catch (IOException e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_WS_SELECT_ERROR(e.getMessage()),
                            e);
                }
            }
        }
    }

    public void execute(Runnable runnable) {
        executorService.submit(runnable);
    }

    protected NioClientNetworkHandler createNetworkHandler(Selector selector) throws IOException {
        return new NioClientNetworkHandler(selector, this);
    }
}
