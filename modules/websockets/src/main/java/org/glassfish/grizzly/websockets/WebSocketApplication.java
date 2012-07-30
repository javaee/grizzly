/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.util.Header;

/**
 * Abstract server-side {@link WebSocket} application, which will handle
 * application {@link WebSocket}s events.
 *
 * @author Alexey Stashok
 */
public abstract class WebSocketApplication extends WebSocketAdapter {

    /*
     * WebSockets registered with this application.
     */
    private final ConcurrentHashMap<WebSocket, Boolean> sockets =
            new ConcurrentHashMap<WebSocket, Boolean>();
    
    
    // ---------------------------------------------------------- Public Methods

    /**
     * Factory method to create new {@link WebSocket} instances.  Developers may
     * wish to override this to return customized {@link WebSocket} implementations.
     * 
     * @param handler the {@link ProtocolHandler} to use with the newly created
     *  {@link WebSocket}.
     *                
     * @param listeners the {@link WebSocketListener}s to associate with the new
     *   {@link WebSocket}.
     *                  
     * @return a new {@link WebSocket} instance.
     * 
     * @deprecated Use {@link WebSocketApplication#createSocket(ProtocolHandler, org.glassfish.grizzly.http.HttpRequestPacket, WebSocketListener...)}
     */
    @Deprecated
    public WebSocket createSocket(ProtocolHandler handler, WebSocketListener... listeners) {
        return createSocket(handler, null, listeners);
    }

    /**
     * Factory method to create new {@link WebSocket} instances.  Developers may
     * wish to override this to return customized {@link WebSocket} implementations.
     * @param handler the {@link ProtocolHandler} to use with the newly created
     *  {@link WebSocket}.
     * @param requestPacket the {@link HttpRequestPacket} that triggered the
     *  creation of the {@link WebSocket} connection.
     * @param listeners the {@link WebSocketListener}s to associate with the new
     *  {@link WebSocket}.
     * @return
     */
    public WebSocket createSocket(final ProtocolHandler handler, 
                                  final HttpRequestPacket requestPacket,
                                  final WebSocketListener... listeners) {
        return new DefaultWebSocket(handler, requestPacket, listeners);
        
    }

    /**
     * When a {@link WebSocket#onClose(DataFrame)} is invoked, the {@link WebSocket}
     * will be unassociated with this application and closed.
     * 
     * @param socket the {@link WebSocket} being closed.
     * @param frame the closing frame.
     */
    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        remove(socket);
        socket.close();
    }

    /**
     * When a new {@link WebSocket} connection is made to this application, the
     * {@link WebSocket} will be associated with this application.  
     * 
     * @param socket the new {@link WebSocket} connection.
     */
    @Override
    public void onConnect(WebSocket socket) {
        add(socket);
    }

    /**
     * Checks protocol specific information can and should be upgraded.
     * 
     * The default implementation will check for the precence of the 
     * <code>Upgrade</code> header with a value of <code>WebSocket</code>.
     * If present, {@link #isApplicationRequest(org.glassfish.grizzly.http.HttpRequestPacket)}
     * will be invoked to determine if the request is a valid websocket request.
     *
     * @return <code>true</code> if the request should be upgraded to a 
     *  WebSocket connection
     */
    public final boolean upgrade(HttpRequestPacket request) {
        return "WebSocket".equalsIgnoreCase(request.getHeader(Header.Upgrade)) && isApplicationRequest(request);
    }

    /**
     * Checks application specific criteria to determine if this application can 
     * process the request as a WebSocket connection.
     *
     * @param request the incoming HTTP request.
     *                
     * @return <code>true</code> if this application can service this request
     */
    public abstract boolean isApplicationRequest(HttpRequestPacket request);

    /**
     * Return the websocket extensions supported by this <code>WebSocketApplication</code>.
     * 
     * @return the websocket extensions supported by this
     *  <code>WebSocketApplication</code>.
     */
    public List<String> getSupportedExtensions() {
        return Collections.emptyList();
    }

    /**
     *
     *
     * @param subProtocol
     * @return
     */
    public List<String> getSupportedProtocols(List<String> subProtocol) {
        return Collections.emptyList();
    }
    
    
    // ------------------------------------------------------- Protected Methods

    
    /**
     * Returns a set of {@link WebSocket}s, registered with the application.
     * The returned set is unmodifiable, the possible modifications may cause exceptions.
     *
     * @return a set of {@link WebSocket}s, registered with the application.
     */
    protected Set<WebSocket> getWebSockets() {
        return sockets.keySet();
    }

    /**
     * Associates the specified {@link WebSocket} with this application.
     * 
     * @param socket the {@link WebSocket} to associate with this application.
     *
     * @return <code>true</code> if the socket was successfully associated,
     *  otherwise returns <code>false</code>.
     */
    protected boolean add(WebSocket socket) {
        return sockets.put(socket, Boolean.TRUE) == null;
    }

    /**
     * Unassociates the specified {@link WebSocket} with this application.
     * 
     * @param socket the {@link WebSocket} to unassociate with this application.
     *               
     * @return <code>true</code> if the socket was successfully unassociated,
     *  otherwise returns <code>false</code>.
     */
    public boolean remove(WebSocket socket) {
        return sockets.remove(socket) != null;
    }


    /**
     * This method will be called, when initial {@link WebSocket} handshake 
     * process has been completed, but allows the application to perform further
     * negotiation/validation.
     *
     * @throws HandshakeException error occurred during the handshake.
     */
    protected void handshake(HandShake handshake) throws HandshakeException {
    }

    /**
     * This method will be invoked if an unexpected exception is caught by
     * the WebSocket runtime.
     *
     * @param webSocket the websocket being processed at the time the
     *                  exception occurred.
     * @param t the unexpected exception.
     *
     * @return <code>true</code> if the WebSocket should be closed otherwise
     *  <code>false</code>.
     */
    protected boolean onError(final WebSocket webSocket,
                              final Throwable t) {
        return true;
    }

}
