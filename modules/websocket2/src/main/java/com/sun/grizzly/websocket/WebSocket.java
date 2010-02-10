package com.sun.grizzly.websocket;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.http.MimeHeaders;

public class WebSocket {
    public WebSocket(GrizzlyRequest request, GrizzlyResponse response) {
        final MimeHeaders headers = request.getRequest().getMimeHeaders();
        final Request req = request.getRequest();

    }
}
