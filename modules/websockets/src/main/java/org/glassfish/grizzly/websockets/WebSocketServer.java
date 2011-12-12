/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;

public class WebSocketServer {
    private static final Logger logger = Grizzly.logger(WebSocketServer.class);
    private static final Object SYNC = new Object();
    private HttpServer httpServer;

    public WebSocketServer(int port) {
        httpServer = HttpServer.createSimpleServer(".", port);
        httpServer.getServerConfiguration().setHttpServerName("WebSocket Server");
        httpServer.getServerConfiguration().setName("WebSocket Server");
        for (NetworkListener networkListener : httpServer.getListeners()) {
            networkListener.setMaxPendingBytes(-1);
            networkListener.registerAddOn(new WebSocketAddOn());
        }
    }

    /**
     * @param port the network port to which this listener will bind.
     *
     * @return a <code>WebSocketServer</code> configured to listen to requests on {@link
     *         NetworkListener#DEFAULT_NETWORK_HOST}:<code>port</code>, using the specified <code>path</code> as the
     *         server's document root.
     */
    public static WebSocketServer createSimpleServer(int port) {
        return new WebSocketServer(port);
    }

    public void start() throws IOException {
        synchronized (SYNC) {
            httpServer.start();
        }
    }

    public void stop() {
        synchronized (SYNC) {
            WebSocketEngine.getEngine().unregisterAll();
            httpServer.stop();
        }
    }

    public void register(String name, WebSocketApplication application) {
        WebSocketEngine.getEngine().register(name, application);
    }
}
