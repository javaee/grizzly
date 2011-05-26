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

import java.net.URI;

import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Constants;

/**
 * Client-side {@link WebSocket} handshake information.
 *
 * @author Alexey Stashok
 * @see WebSocketHandshake
 * @see ServerHandshake
 */
public class ClientHandshake {
    private final SecKey key;
    private String host;
    private boolean secure;
    private int port;
    private String path;
    private URI uri;
    private String[] protocols;
    private String[] extensions;

    public ClientHandshake(URI uri, String... protocols) {
        this.uri = uri;
        this.protocols = protocols;
        sanitize(this.protocols);
        secure = "wss".equals(uri.getScheme());
        host = uri.getHost();
        port = uri.getPort();
        path = uri.getPath();
        key = new SecKey();
    }

    public SecKey getKey() {
        return key;
    }

    public void validateServerResponse(HttpResponsePacket headers) throws HandshakeException {
        if (WebSocketEngine.RESPONSE_CODE_VALUE != headers.getStatus()) {
            throw new HandshakeException(String.format("Response code was not %s: %s",
                WebSocketEngine.RESPONSE_CODE_VALUE, headers.getStatus()));
        }
        checkForHeader(headers, WebSocketEngine.UPGRADE, WebSocketEngine.WEBSOCKET);
        checkForHeader(headers, WebSocketEngine.CONNECTION, WebSocketEngine.UPGRADE);
        key.validateServerKey(headers.getHeader(WebSocketEngine.SEC_WS_ACCEPT));
    }

    private void checkForHeader(HttpResponsePacket headers, String header, String validValue) {
        final String value = headers.getHeader(header);
        if (!validValue.equalsIgnoreCase(value)) {
            throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
        }
    }

    public HttpContent composeHeaders() {
        final HttpRequestPacket.Builder builder = HttpRequestPacket.builder()
            .method("GET")
            .uri(path)
            .protocol(Protocol.HTTP_1_1)
            .header("Host", host)
            .header("Connection", "Upgrade")
            .upgrade("WebSocket")
            .header(WebSocketEngine.SEC_WS_KEY_HEADER, getKey().toString())
            .header(WebSocketEngine.SEC_WS_VERSION, WebSocketEngine.WS_VERSION + "");
        if (protocols != null) {
            builder.header(WebSocketEngine.SEC_WS_PROTOCOL_HEADER, join(protocols));
        }
        if (extensions != null) {
            builder.header(WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, join(extensions));
        }
        return HttpContent.builder(builder.build())
            .build();
    }

    public String[] getExtensions() {
        return extensions;
    }

    public void setExtensions(String[] extensions) {
        sanitize(extensions);
        this.extensions = extensions;
    }

    private void sanitize(String[] strings) {
        if(strings != null) {
            for (int i = 0; i < strings.length; i++) {
                strings[i] = strings[i] == null ? null : strings[i].trim();

            }
        }
    }

    private String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (String s : values) {
            if(builder.length() != 0) {
                builder.append("; ");
            }
            builder.append(s);
        }
        return null;
    }

    public URI getURI() {
        return uri;
    }
}
