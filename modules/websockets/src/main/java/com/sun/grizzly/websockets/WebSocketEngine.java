package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.SelectionKeyAttachment;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketEngine {
    private static final Logger logger = Logger.getLogger(WebSocket.WEBSOCKET);
    private static final WebSocketEngine engine = new WebSocketEngine();
    private final Map<String, WebSocketApplication> applications = new HashMap<String, WebSocketApplication>();

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
                ((HttpWorkerThread)Thread.currentThread()).getAttachment().setTimeout(SelectionKeyAttachment.UNLIMITED_TIMEOUT);
            }
        } catch (IOException e) {
            return false;
        }
        return socket != null;
    }

    protected WebSocket getWebSocket(AsyncExecutor asyncExecutor, Request request) throws IOException {
        final WebSocketApplication app = WebSocketEngine.getEngine().getApplication(request.requestURI().toString());
        WebSocket socket = null;
        try {
            final ClientHandShake clientHandShake =
                    new ClientHandShake(request.getMimeHeaders(), false, request.requestURI().toString());
            if (app != null) {
                socket = app.createSocket(asyncExecutor, request, request.getResponse(), clientHandShake, getSelector(asyncExecutor));
            } else {
                socket = new DefaultWebSocket(asyncExecutor, request, request.getResponse(), clientHandShake, getSelector(asyncExecutor));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return socket;
    }

    private Selector getSelector(AsyncExecutor asyncExecutor) {
        return asyncExecutor.getProcessorTask().getSelectionKey().selector();
    }


    public void register(String name, WebSocketApplication app) {
        applications.put(name, app);
    }

}
