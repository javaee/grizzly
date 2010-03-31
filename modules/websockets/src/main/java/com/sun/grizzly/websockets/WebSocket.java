package com.sun.grizzly.websockets;

import java.io.IOException;
import java.nio.charset.Charset;

public interface WebSocket {
    String ENCODING = "UTF-8";

    String WEBSOCKET = "websocket";

    void onConnect();

    void onMessage();

    void onClose();

    boolean add(WebSocketListener listener);

    boolean remove(WebSocketListener listener);
    
    /**
     * Write the data to the socket.  This text will be converted to a UTF-8 encoded byte[] prior to sending.
     * @param data
     * @throws IOException
     */
    void send(String data) throws IOException;
    
    void close() throws IOException;

    boolean isConnected();
}
