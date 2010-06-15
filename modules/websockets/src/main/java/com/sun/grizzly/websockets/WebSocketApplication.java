/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */

package com.sun.grizzly.websockets;

import com.sun.grizzly.Connection;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract server-side {@link WebSocket} application, which will handle
 * application {@link WebSocket}s events.
 *
 * @see WebSocketHandler
 * @see WebSocketClientHandler
 *
 * @author Alexey Stashok
 */
public abstract class WebSocketApplication implements WebSocketHandler {
    private final Set<WebSocket> websockets = new HashSet<WebSocket>();

    /**
     * Method is called, when new {@link WebSocket} gets accepted.
     *
     * @param websocket {@link WebSocket}
     *
     * @throws IOException
     */
    public abstract void onAccept(WebSocket socket) throws IOException;

    /**
     * Add the {@link WebSocket} to the <tt>WebSocketApplication</tt> websockets list.
     *
     * @param websocket {@link WebSocket} to add.
     *
     * @return <tt>true</tt>, if the {@link WebSocket} was succeessfully added, or
     * <tt>false</tt> otherwise.
     */
    public boolean add(WebSocket websocket) {
        return websockets.add(websocket);
    }
    
    /**
     * Remove the {@link WebSocket} from the <tt>WebSocketApplication</tt> websockets list.
     *
     * @param websocket {@link WebSocket} to remove.
     *
     * @return <tt>true</tt>, if the {@link WebSocket} was succeessfully removed, or
     * <tt>false</tt> otherwise.
     */
    public boolean remove(WebSocket socket) {
        return websockets.remove(socket);
    }
    
    /**
     * Method is called, when inital {@link WebSocket} handshake process was completed,
     * but <tt>WebSocketApplication</tt> may perform additional validation.
     *
     * @param websocketMeta {@link ClientWebSocketMeta}.
     * @throws HandshakeException error, occurred during the handshake.
     */
    protected void handshake(ClientWebSocketMeta websocketMeta)
            throws HandshakeException {
    }

    /**
     * Method is called before the {@link WebSocketEngine} will create a server-side
     * {@link WebSocket} object, so application may return any customized
     * subtype of {@link WebSocket}.
     * 
     * @param connection underlying Grizzly {@link Connection}.
     * @param meta server-side {@link ServerWebSocketMeta}.
     *
     * @return customized {@link WebSocket}, or <tt>null</tt>, if application wants
     * to delegate {@link WebSocket} creation to {@link WebSocketEngine}.
     */
    protected WebSocket createWebSocket(Connection connection,
            ServerWebSocketMeta meta) {
        return null;
    }
}
