package com.sun.grizzly.websocket;

import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.http.MimeHeaders;

import java.io.IOException;

public class WebSocketFilter implements AsyncFilter {
    public boolean doFilter(AsyncExecutor asyncExecutor) {
        final ProcessorTask task = asyncExecutor.getProcessorTask();
        Request req = task.getRequest();
        boolean isWSRequest = req.getAttribute("handshake") != null;
        final MimeHeaders headers = req.getMimeHeaders();
        if (!isWSRequest && "WebSocket".equals(req.getHeader("Upgrade")) && "Upgrade".equals(req.getHeader("Connection"))) {
            String origin = req.getHeader("origin");
            String host = req.getHeader("Host");
            if (origin != null && host != null) {
                try {
                    final ServerHandShake handshake = new ServerHandShake(headers, new ClientHandShake(headers, false, req.requestURI().toString()));
                    final Response response = task.getRequest().getResponse();
                    response.suspend();
                    handshake.prepare(response);
                    response.flush();
                    req.setAttribute("handshake", handshake);
                    System.out.println("invoking adapter");
                    task.invokeAdapter();
                    System.out.println("done invoking adapter");
//                    final ByteChunk payload = new ByteChunk();
//                    Thread.sleep(5000);
//                    task.getRequest().doRead(payload);

                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e.getMessage());
                }
            }
        }

        return true;
    }
}
