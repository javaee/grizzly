package com.sun.grizzly.samples.websockets;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.websockets.BaseServerWebSocket;
import com.sun.grizzly.websockets.ClientHandShake;

import java.io.IOException;

public class ChatWebSocket extends BaseServerWebSocket {
    private String user;
    private ChatApplication app;

    public ChatWebSocket(AsyncExecutor asyncExecutor, Request request, Response response,
            ClientHandShake client, final ChatApplication listener) throws IOException {
        super(asyncExecutor, request, response, client, listener);
        app = listener;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @Override
    public void send(String data) {
        super.send( toJsonp("System Message", data) );
    }

    @Override
    public void close() throws IOException {
        WebSocketsServlet.logger.info("closing : " + getUser());
        app.remove(this);
        super.close();
    }

    private String toJsonp(String name, String message) {
        return "window.parent.app.update({ name: \"" + escape(name) + "\", message: \"" + escape(message) + "\" });\n";
    }

    private String escape(String orig) {
        StringBuilder buffer = new StringBuilder(orig.length());

        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            switch (c) {
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\n':
                    buffer.append("<br />");
                    break;
                case '\r':
                    // ignore
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                case '\'':
                    buffer.append("\\'");
                    break;
                case '\"':
                    buffer.append("\\\"");
                    break;
                case '\\':
                    buffer.append("\\\\");
                    break;
                case '<':
                    buffer.append("&lt;");
                    break;
                case '>':
                    buffer.append("&gt;");
                    break;
                case '&':
                    buffer.append("&amp;");
                    break;
                default:
                    buffer.append(c);
            }
        }

        return buffer.toString();
    }
}
