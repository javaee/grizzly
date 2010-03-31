package com.sun.grizzly.websockets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EchoServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(WebSocket.WEBSOCKET);
    public static final String RESPONSE_TEXT = "Nothing to see";

    public EchoServlet() {
        WebSocketEngine.getEngine().register("/echo", new WebSocketApplication() {
            public void onRead(WebSocket socket, DataFrame data) {
                read(socket, data);
            }

            public void onConnect(WebSocket socket) {
            }

            public void onClose(WebSocket socket) {
            }
        });
    }

    public void read(WebSocket socket, DataFrame data) {
        try {
            socket.send(data.getTextPayload());
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain; charset=iso-8859-1");
        resp.getWriter().write(RESPONSE_TEXT);
        resp.getWriter().flush();
    }
}