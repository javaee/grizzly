package com.sun.grizzly.samples.websockets;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.websockets.DataFrame;
import com.sun.grizzly.websockets.WebSocket;
import com.sun.grizzly.websockets.WebSocketApplication;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ChatApplication extends WebSocketApplication {
    List<WebSocket> sockets = new ArrayList<WebSocket>();

    @Override
    public WebSocket createSocket(Request request, Response response) throws IOException {
        final ChatWebSocket socket = new ChatWebSocket(this, request, response);

        sockets.add(socket);
        return socket;
    }

    public void onRead(WebSocket socket, DataFrame frame) {
        final String data = frame.getTextPayload();
        if (data.startsWith("login:")) {
            login((ChatWebSocket) socket, frame);
        } else {
            send(socket, data);
            broadcast(socket, data);
        }
    }

    public void onConnect(WebSocket socket) {
    }

    void broadcast(WebSocket origination, String text) {
        WebSocketsServlet.logger.info("Broadcasting : " + text);
        for (WebSocket webSocket : sockets) {
            if (webSocket != origination) {
                send(webSocket, text);
            }
        }

    }

    private void send(WebSocket socket, String text) {
        try {
            socket.send(text);
        } catch (IOException e) {
            WebSocketsServlet.logger.log(Level.SEVERE, "Removing chat client: " + e.getMessage(), e);
            onClose(socket);
        }
    }

    public void onClose(WebSocket socket) {
        sockets.remove(socket);
    }

    private void login(ChatWebSocket socket, DataFrame frame) {
        if (socket.getUser() == null) {
            WebSocketsServlet.logger.info("ChatApplication.login");
            socket.setUser(frame.getTextPayload().split(":")[1].trim());
            send(socket, socket.getUser() + " has joined the chat.");
            broadcast(socket, socket.getUser() + " has joined the chat.");
        }
    }
}