/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.Request;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class WebSocketApplication extends WebSocketAdapter {
    private final ConcurrentHashMap<WebSocket, Boolean> sockets = new ConcurrentHashMap<WebSocket, Boolean>();

    public WebSocket createWebSocket(ProtocolHandler protocolHandler, final WebSocketListener... listeners) {
        return new DefaultWebSocket(protocolHandler, listeners);
    }

    /**
     * Returns a set of {@link WebSocket}s, registered with the application.
     * The returned set is unmodifiable, the possible modifications may cause exceptions.
     *
     * @return a set of {@link WebSocket}s, registered with the application.
     */
    protected Set<WebSocket> getWebSockets() {
        return sockets.keySet();
    }

    protected boolean add(WebSocket socket) {
        return sockets.put(socket, Boolean.TRUE) == null;
    }

    public boolean remove(WebSocket socket) {
        return sockets.remove(socket) != null;
    }

    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        remove(socket);
        socket.close();
    }

    @Override
    public void onConnect(WebSocket socket) {
        add(socket);
    }

    /**
     * Checks application specific criteria to determine if this application can process the Request as a WebSocket
     * connection.
     *
     * @param request
     * @return true if this application can service this Request
     */
    public abstract boolean isApplicationRequest(Request request);

    public List<String> getSupportedExtensions() {
        return Collections.emptyList();
    }

    public List<String> getSupportedProtocols(List<String> subProtocol) {
        return Collections.emptyList();
    }

    /**
     * When invoked, all currently connected WebSockets will be closed.
     */
    void shutdown() {
        for (WebSocket webSocket : sockets.keySet()) {
            if (webSocket.isConnected()) {
                webSocket.onClose(null);
            }
        }
        sockets.clear();
    }
}
