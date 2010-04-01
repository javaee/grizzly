package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.SelectionKeyActionAttachment;
import com.sun.grizzly.util.SelectionKeyAttachment;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.MimeHeaders;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketEngine {
    private static final Logger logger = Logger.getLogger(WebSocket.WEBSOCKET);
    private static final WebSocketEngine engine = new WebSocketEngine();
    private final Map<String, WebSocketApplication> applications = new HashMap<String, WebSocketApplication>();
    static final int INITIAL_BUFFER_SIZE = 8192;

    public static WebSocketEngine getEngine() {
        return engine;
    }

    public WebSocketApplication getApplication(String uri) {
        return applications.get(uri);
    }

    public boolean handle(AsyncExecutor asyncExecutor) {
        WebSocket socket = null;
        try {
            Request request = asyncExecutor.getProcessorTask().getRequest();
            if ("WebSocket".equals(request.getHeader("Upgrade"))) {
                socket = getWebSocket(asyncExecutor, request);
                ((HttpWorkerThread) Thread.currentThread()).getAttachment()
                        .setTimeout(SelectionKeyAttachment.UNLIMITED_TIMEOUT);
            }
        } catch (IOException e) {
            return false;
        }
        return socket != null;
    }

    protected WebSocket getWebSocket(AsyncExecutor asyncExecutor, Request request) throws IOException {
        final WebSocketApplication app = WebSocketEngine.getEngine().getApplication(request.requestURI().toString());
        BaseServerWebSocket socket = null;
        try {
            final Response response = request.getResponse();
            handshake(request, response);
            ProcessorTask task = asyncExecutor.getProcessorTask();
            if (app != null) {
                socket = (BaseServerWebSocket) app.createSocket(request, response);
                app.onConnect(socket);
            } else {
                socket = new PassThroughWebSocket(request, response, task);
            }
            checkBuffered(socket, request);

            final SelectionKey key = task.getSelectionKey();
            register(asyncExecutor, socket, key);

            enableRead(task, key);

        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return socket;
    }

    private void register(final AsyncExecutor asyncExecutor, final BaseServerWebSocket socket,
            final SelectionKey selectionKey) {
        selectionKey.attach(new SelectionKeyActionAttachment() {
            public void process(SelectionKey key) {
                if (key.isValid()) {
                    if (key.isReadable()) {
                        final ProcessorTask task = asyncExecutor.getProcessorTask();
                        try {
                            socket.doRead();
                        } catch (IOException e) {
                            task.setAptCancelKey(true);
                            task.terminateProcess();
                            logger.log(Level.INFO, e.getMessage(), e);
                        }
                        enableRead(task, key);
                    }
                }
            }

            @Override
            public void postProcess(SelectionKey selectionKey1) {
            }
        });
    }


    private void checkBuffered(BaseServerWebSocket socket, Request request) throws IOException {
        final ByteChunk chunk = new ByteChunk(INITIAL_BUFFER_SIZE);
        final InputBuffer inputBuffer = request.getInputBuffer();
        if (inputBuffer.doRead(chunk, request) > 0) {
            socket.unframe(chunk.toByteBuffer());
        }
    }

    final void enableRead(ProcessorTask task, SelectionKey key) {
        task.getSelectorHandler().register(key, SelectionKey.OP_READ);
    }

    private void handshake(Request request, Response response) throws IOException {
        final MimeHeaders headers = request.getMimeHeaders();
        final ClientHandShake client = new ClientHandShake(headers, false, request.requestURI().toString());
        final ServerHandShake server = new ServerHandShake(headers, client);

        server.prepare(response);
        response.flush();
    }

    public void register(String name, WebSocketApplication app) {
        applications.put(name, app);
    }
}