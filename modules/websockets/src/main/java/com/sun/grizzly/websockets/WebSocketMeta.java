/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;

/**
 * {@link WebSocket} meta information.
 *
 * @see ClientWebSocketMeta
 * @see ServerWebSocketMeta
 * 
 * @author Alexey Stashok
 */
public abstract class WebSocketMeta {
    private final boolean isSecure;
    private final URI uri;
    private final String origin;
    private final String protocol;

    /**
     * Construct a <tt>WebSocketMeta</tt> using {@link URI}.
     * Origin header will be set to "http://localhost"
     * 
     * @param uri {@link WebSocket} {@link URI}
     */
    public WebSocketMeta(URI uri) {
        this(uri, null, null, null);
    }

    /**
     * Construct a <tt>WebSocketMeta</tt> with the passed parameters.
     *
     * @param uri {@link WebSocket} {@link URI}
     * @param origin the {@link WebSocket} Origin header value.
     * @param protocol the {@link WebSocket} Sec-WebSocket-Protocol header value.
     * @param isSecure <tt>true</tt>, if websocket is secured "wss", or <tt>false</tt> otherwise.
     */
    public WebSocketMeta(URI uri, String origin,
            String protocol, Boolean isSecure) {

        Boolean isSecureSchema = null;
        if (uri != null) {
            final String wsProtocol = uri.getScheme();

            if (wsProtocol != null) {
                boolean isWs;
                if (!(isWs = wsProtocol.equals("ws")) && !wsProtocol.equals("wss")) {
                    throw new IllegalStateException("Unexpected WebSockets schema: " + wsProtocol);
                }

                isSecureSchema = !isWs;
            }
        }

        this.uri = uri;

        this.origin = origin != null ? origin : "http://localhost";
        this.protocol = protocol != null ? protocol : null;
        this.isSecure = isSecure != null ? isSecure :
            (isSecureSchema != null ? isSecureSchema : false);
    }

    /**
     * Gets {@link WebSocket} {@link URI}.
     *
     * @return {@link WebSocket} {@link URI}.
     */
    public URI getURI() {
        return uri;
    }

    /**
     * Gets the {@link WebSocket} Origin header value.
     * 
     * @return the {@link WebSocket} Origin header value.
     */
    public String getOrigin() {
        return origin;
    }

    /**
     * Gets the {@link WebSocket} Sec-WebSocket-Protocol header value.
     * @return the {@link WebSocket} Sec-WebSocket-Protocol header value.
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Returns <tt>true</tt>, if this <tt>WebSocket</tt> communication won't be
     * secured, or <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt>, if this <tt>WebSocket</tt> communication won't be
     * secured, or <tt>false</tt> otherwise.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * The <tt>WebSocketMeta</tt> string description.
     * 
     * @return the <tt>WebSocketMeta</tt> string description.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("uri=").append(uri)
                .append(" origin=").append(origin)
                .append(" protocol=").append(protocol);

        return sb.toString();
    }
}
