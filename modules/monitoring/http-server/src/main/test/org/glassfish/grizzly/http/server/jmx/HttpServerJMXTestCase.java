package org.glassfish.grizzly.http.server.jmx;

import org.glassfish.grizzly.http.server.*;
import org.junit.Test;

import java.io.IOException;


public class HttpServerJMXTestCase {

    @Test
    public void grizzly1835() throws IOException {
        org.glassfish.grizzly.http.server.HttpServer server = createServer();
        server.start();
        server.shutdown();

        org.glassfish.grizzly.http.server.HttpServer server2 = createServer();
        server2.start();
        server2.shutdown();
    }

    private org.glassfish.grizzly.http.server.HttpServer createServer() {
        org.glassfish.grizzly.http.server.HttpServer server2 = new org.glassfish.grizzly.http.server.HttpServer();
        ServerConfiguration serverConfiguration2 = server2.getServerConfiguration();
        serverConfiguration2.setName("fizzbuzz");
        serverConfiguration2.setJmxEnabled(true);
        return server2;
    }
}
