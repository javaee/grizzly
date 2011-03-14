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
package org.glassfish.grizzly.websockets;

import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.MimeHeaders;

/**
 * Server-side {@link WebSocket} handshake information.
 *
 * @author Alexey Stashok
 * @see WebSocketHandshake
 * @see ClientHandshake
 */
public class ServerHandshake extends WebSocketHandshake {
    private SecKey serverSecKey;
    private String enabledExtensions;
    private String enabledProtocols;

    public ServerHandshake(HttpRequestPacket httpHeader)
        throws HandshakeException {
        super(httpHeader.isSecure(), httpHeader.getRequestURI());
        
        final MimeHeaders headers = httpHeader.getHeaders();
        determineHostAndPort(headers);

        checkForHeader(headers, "Upgrade", "WebSocket");
        checkForHeader(headers, "Connection", "Upgrade");

        setSubProtocol(split(headers.getHeader(WebSocketEngine.SEC_WS_PROTOCOL_HEADER)));
        setExtensions(split(headers.getHeader(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER)));
        serverSecKey = SecKey.generateServerKey(new SecKey(headers.getHeader(WebSocketEngine.SEC_WS_KEY_HEADER)));

        setOrigin(readHeader(headers, WebSocketEngine.SEC_WS_ORIGIN_HEADER));
        setLocation(buildLocation(isSecure()));
        if (getServerHostName() == null) {
            throw new HandshakeException("Missing host name required for WebSocket negotiation");
        }
    }

    private String[] split(String header) {
        return header == null ? null : header.split(";");
    }

    private void checkForHeader(MimeHeaders headers, String header, String validValue) {
        final String value = headers.getHeader(header);
        if(!validValue.equalsIgnoreCase(value)) {
            throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
        }
    }

    final String readHeader(MimeHeaders headers, String name) {
        final DataChunk value = headers.getValue(name);
        return value == null ? null : value.toString();
    }

    private void determineHostAndPort(MimeHeaders headers) {
        String header;
        header = readHeader(headers, "host");
        final int i = header == null ? -1 : header.indexOf(":");
        if (i == -1) {
            setServerHostName(header);
            setPort(80);
        } else {
            setServerHostName(header.substring(0, i));
            setPort(Integer.parseInt(header.substring(i + 1)));
        }
    }
    public String getEnabledExtensions() {
        return enabledExtensions;
    }

    public void setEnabledExtensions(String[] enabledExtensions) {
        this.enabledExtensions = enabledExtensions != null ? join(enabledExtensions) : null;
    }

    public String getEnabledProtocols() {
        return enabledProtocols;
    }

    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols != null ? join(enabledProtocols) : null;
    }

    public HttpContent respond(HttpRequestPacket request) {

        final HttpResponsePacket response = request.getResponse();

        response.setStatus(101);
        response.setReasonPhrase(WebSocketEngine.RESPONSE_CODE_MESSAGE);

        response.setProtocol(Protocol.HTTP_1_1);
        response.setUpgrade("WebSocket");
        response.setHeader("Connection", "Upgrade");
        response.setHeader(WebSocketEngine.SEC_WS_ACCEPT, serverSecKey.getSecKey());

        if (getEnabledProtocols() != null) {
            response.setHeader(WebSocketEngine.SEC_WS_PROTOCOL_HEADER, join(getSubProtocol()));
        }
        if (getEnabledExtensions() != null) {
            response.setHeader(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, join(getSubProtocol()));
        }

        return HttpContent.builder(response)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ServerHandshake");
        sb.append("{enabledExtensions='").append(enabledExtensions).append('\'');
        sb.append(", serverSecKey=").append(serverSecKey);
        sb.append(", enabledProtocols='").append(enabledProtocols).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
