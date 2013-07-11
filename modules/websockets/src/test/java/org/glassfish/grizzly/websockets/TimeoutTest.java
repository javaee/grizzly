/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.grizzly.websockets;

import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.junit.Assert;
import org.junit.Test;

public class TimeoutTest extends BaseWebSocketTestUtilities {

    private static final int PORT = 9119;

    // ------------------------------------------------------------ Test Methods

    @Test
    public void testIndependentTimeout() throws Exception {
        HttpServer httpServer = HttpServer.createSimpleServer(".", PORT);
        httpServer.getServerConfiguration().setHttpServerName("WebSocket Server");
        httpServer.getServerConfiguration().setName("WebSocket Server");
        for (NetworkListener networkListener : httpServer.getListeners()) {
            networkListener.registerAddOn(new WebSocketAddOn());
            networkListener.getKeepAlive().setIdleTimeoutInSeconds(5);
        }
        WebSocketEngine.getEngine().register("", "/echo", new EchoApplication());

        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        WebSocketClient socket = null;
        try {
            httpServer.start();
            WebSocketEngine.getEngine().register(app);
            socket = new WebSocketClient("wss://localhost:" + PORT + "/echo");
            socket.connect();
            Thread.sleep(10000);
            Assert.assertTrue(socket.isConnected());
        } finally {
            if (socket != null) {
                socket.close();
            }
            httpServer.shutdownNow();
        }
    }

    @Test
    public void testConfiguredIndependentTimeout() throws Exception {
        HttpServer httpServer = HttpServer.createSimpleServer(".", PORT);
        httpServer.getServerConfiguration().setHttpServerName("WebSocket Server");
        httpServer.getServerConfiguration().setName("WebSocket Server");
        WebSocketEngine.getEngine().register("", "/echo", new EchoApplication());
        httpServer.start();
        for (NetworkListener networkListener : httpServer.getListeners()) {
            networkListener.getKeepAlive().setIdleTimeoutInSeconds(5);
            final Transport t = networkListener.getTransport();
            FilterChain c = (FilterChain) t.getProcessor();
            final int httpServerFilterIdx = c.indexOfType(HttpServerFilter.class);

            if (httpServerFilterIdx >= 0) {
                // Insert the WebSocketFilter right after HttpCodecFilter
                c.add(httpServerFilterIdx, new WebSocketFilter(8)); // in seconds
            }
        }

        final EchoWebSocketApplication app = new EchoWebSocketApplication();
        WebSocketClient socket = null;
        try {
            WebSocketEngine.getEngine().register(app);
            socket = new WebSocketClient("wss://localhost:" + PORT + "/echo");
            socket.connect();
            Thread.sleep(10000);
            Assert.assertFalse(socket.isConnected());
        } finally {
            if (socket != null) {
                socket.close();
            }
            httpServer.shutdownNow();
        }
    }

}
