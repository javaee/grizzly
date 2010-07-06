package com.sun.grizzly.samples.websockets;

import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.NetworkHandler;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.WebSocketApplication;
import com.sun.grizzly.websockets.WebSocketListener;

import java.io.IOException;
import java.util.logging.Level;

public class ChatApplication extends WebSocketApplication {
    @Override
    public WebSocket createSocket(NetworkHandler handler, WebSocketListener... listeners) throws IOException {
        return new ChatWebSocket(handler, listeners);
    }

    public void onMessage(WebSocket socket, DataFrame frame) throws IOException {
        final String data = frame.getTextPayload();
        if (data.startsWith("login:")) {
            login((ChatWebSocket) socket, frame);
        } else {
            broadcast(((ChatWebSocket) socket).getUser() + " : " + data);
        }
    }

    private void broadcast(String text) throws IOException {
        WebSocketsServlet.logger.info("Broadcasting : " + text);
        for (WebSocket webSocket : getWebSockets()) {
            try {
                webSocket.send(text);
            } catch (IOException e) {
                e.printStackTrace();
                WebSocketsServlet.logger.info("Removing chat client: " + e.getMessage());
                webSocket.close();
            }
        }

    }

    private void login(ChatWebSocket socket, DataFrame frame) throws IOException {
        if (socket.getUser() == null) {
            WebSocketsServlet.logger.info("ChatApplication.login");
            socket.setUser(frame.getTextPayload().split(":")[1].trim());
            broadcast(socket.getUser() + " has joined the chat.");
        }
    }
}