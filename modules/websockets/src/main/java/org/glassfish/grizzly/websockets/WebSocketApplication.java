/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.utils.DataStructures;

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
    private final ConcurrentMap<WebSocket, Boolean> sockets =
            DataStructures.<WebSocket, Boolean>getConcurrentMap();

    private final List<Extension> supportedExtensions = new ArrayList<Extension>(2);
    private final List<String> supportedProtocols = new ArrayList<String>(2);
    
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
     * If this method is overridden, the overriding method <em>must</em>
     * call {@link #remove(WebSocket)}.  This is necessary to ensure WebSocket
     * instances are not leaked nor are message operations against closed sockets
     * are performed.
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
     * If this method is overridden, the overriding method <em>must</em>
     * call {@link #add(WebSocket)}.  This is necessary to ensure bulk message
     * sending via facilities such as {@link Broadcaster} function properly.
     * 
     * @param socket the new {@link WebSocket} connection.
     */
    @Override
    public void onConnect(WebSocket socket) {
        add(socket);
    }

    /**
     * Invoked during the handshake if the client has advertised extensions
     * it may use and one or more extensions intersect with those returned
     * by {@link #getSupportedExtensions()}.
     *
     * The {@link Extension}s passed to this method will include any extension
     * parameters included by the client.  It's up to this method to re-order
     * and or adjust any parameter values within the list.  This method must not
     * add any extensions that weren't originally in the list, but it is acceptable
     * to remove one or all extensions if for some reason they can't be supported.
     *
     * If not overridden, the List will be sent as-is back to the client.
     *
     * @param extensions the intersection of extensions between client and
     *                   application.
     *
     * @since 2.3
     */
    public void onExtensionNegotiation(List<Extension> extensions) {
    }

    /**
     * Checks protocol specific information can and should be upgraded.
     * 
     * The default implementation will check for the presence of the
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
     * @return <code>true</code> if this application can service this request
     *         <p/>
     * @deprecated URI mapping shouldn't be intrinsic to the application.
     *  WebSocketApplications should be registered using {@link WebSocketEngine#register(String, String, WebSocketApplication)}
     *  using standard Servlet url-pattern rules.
     */
    public boolean isApplicationRequest(HttpRequestPacket request) {
        return false;
    }


    /**
     * Return the websocket extensions supported by this <code>WebSocketApplication</code>.
     * The {@link Extension}s added to this {@link List} should not include
     * any {@link Extension.Parameter}s as they will be ignored.  This is used
     * exclusively for matching the requested extensions.
     * 
     * @return the websocket extensions supported by this
     *  <code>WebSocketApplication</code>.
     */
    public List<Extension> getSupportedExtensions() {
        return supportedExtensions;
    }

    /**
     *
     *
     * @param subProtocol
     * @return
     */
    public List<String> getSupportedProtocols(List<String> subProtocol) {
        return supportedProtocols;
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
