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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.server.StaticHttpHandler;

public class WebSocketServer {
    private static final Logger logger = Grizzly.logger(WebSocketServer.class);
    private static final Object SYNC = new Object();
    private HttpServer httpServer;

    /**
     * Empty constructor, which doesn't do any network initialization.
     */
    protected WebSocketServer() {
        
    }
    
    /**
     * @param port the network port to which this listener will bind.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests on {@link
     *         NetworkListener#DEFAULT_NETWORK_HOST}:<code>port</code>.
     * 
     * @deprecated please use {@link #createServer(int)}.
     */
    public WebSocketServer(final int port) {
        this(NetworkListener.DEFAULT_NETWORK_HOST, new PortRange(port));
    }
    
    /**
     * @param host the network port to which this listener will bind.
     * @param portRange port range to attempt to bind to.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests
     * on <code>host</code>:<code>[port-range]</code>.
     */
    protected WebSocketServer(final String host, final PortRange portRange) {
        final NetworkListener networkListener =
                new NetworkListener("WebSocket NetworkListener",
                                    host,
                                    portRange);
        networkListener.setMaxPendingBytes(-1);
        networkListener.registerAddOn(new WebSocketAddOn());

        httpServer = new HttpServer();
        final ServerConfiguration config = httpServer.getServerConfiguration();
        config.addHttpHandler(new StaticHttpHandler("."), "/");
        
        config.setHttpServerName("WebSocket Server");
        config.setName("WebSocket Server");
        
        httpServer.addListener(networkListener);
    }

    /**
     * @param port the network port to which this listener will bind.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests on {@link
     *         NetworkListener#DEFAULT_NETWORK_HOST}:<code>port</code>.
     * 
     * @deprecated please use {@link #createServer(int)}.
     */
    public static WebSocketServer createSimpleServer(final int port) {
        return createServer(port);
    }

    /**
     * @param port the network port to which this listener will bind.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests on {@link
     *         NetworkListener#DEFAULT_NETWORK_HOST}:<code>port</code>.
     */
    public static WebSocketServer createServer(final int port) {
        return createServer(NetworkListener.DEFAULT_NETWORK_HOST,
                new PortRange(port));
    }

    /**
     * @param range port range to attempt to bind to.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests
     * on {@link NetworkListener#DEFAULT_NETWORK_HOST}:<code>[port-range]</code>.
     */
    public static WebSocketServer createServer(final PortRange range) {

        return createServer(NetworkListener.DEFAULT_NETWORK_HOST, range);

    }

    /**
     * @param socketAddress the endpoint address to which this listener will bind.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests
     * on <code>socketAddress</code>.
     */
    public static WebSocketServer createServer(final SocketAddress socketAddress) {

        final InetSocketAddress inetAddr = (InetSocketAddress) socketAddress;
        return createServer(inetAddr.getHostName(), inetAddr.getPort());
    }

    /**
     * @param host the network port to which this listener will bind.
     * @param port the network port to which this listener will bind.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests
     * on <code>host</code>:<code>port</code>.
     */
    public static WebSocketServer createServer(final String host,
                                                final int port) {
        
        return createServer(host, new PortRange(port));

    }
    
    /**
     * @param host the network port to which this listener will bind.
     * @param range port range to attempt to bind to.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests
     * on <code>host</code>:<code>[port-range]</code>.
     */
    public static WebSocketServer createServer(final String host,
                                                final PortRange range) {

        return new WebSocketServer(host, range);
    }

    public void start() throws IOException {
        synchronized (SYNC) {
            httpServer.start();
        }
    }

    public void stop() {
        synchronized (SYNC) {
            httpServer.shutdownNow();
            WebSocketEngine.getEngine().unregisterAll();
        }
    }

    public void register(String contextPath, String urlPattern, WebSocketApplication application) {
        WebSocketEngine.getEngine().register(contextPath, urlPattern, application);
    }
}
