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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public class ClientHandShake extends HandShake {
    private final SecKey key;

    public ClientHandShake(boolean isSecure, String origin, String serverHostName, String portNumber, String path) {
        super(isSecure, origin, serverHostName, portNumber, path);
        key = new SecKey();
    }

    public SecKey getKey() {
        return key;
    }

    public void validateServerResponse(final Map<String, String> headers) throws HandshakeException {
        if(!WebSocketEngine.RESPONSE_CODE_VALUE.equals(headers.get(WebSocketEngine.RESPONSE_CODE_HEADER))) {
            throw new HandshakeException(String.format("Response code was not %s: %s",
                    WebSocketEngine.RESPONSE_CODE_VALUE,
                    headers.get(WebSocketEngine.RESPONSE_CODE_HEADER)));
        }
        checkForHeader(headers, WebSocketEngine.UPGRADE, WebSocketEngine.WEBSOCKET);
        checkForHeader(headers, WebSocketEngine.CONNECTION, WebSocketEngine.UPGRADE);
        key.validateServerKey(headers.get(WebSocketEngine.SEC_WS_ACCEPT));
    }

    private void checkForHeader(Map<String, String> headers, final String header, final String validValue) {
        final String value = headers.get(header);
        if(!validValue.equalsIgnoreCase(value)) {
            throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
        }
    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        chunk.write(String.format("GET %s HTTP/1.1\r\n", getResourcePath()).getBytes());
        chunk.write(String.format("Host: %s\r\n", getServerHostName()).getBytes());
        chunk.write(String.format("Connection: Upgrade\r\n").getBytes());
        chunk.write(String.format("Upgrade: WebSocket\r\n").getBytes());
        chunk.write(String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_KEY_HEADER, getKey()).getBytes());
        chunk.write(String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_ORIGIN_HEADER, getOrigin()).getBytes());
        chunk.write(String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_VERSION, WebSocketEngine.WS_VERSION).getBytes());
        if (getSubProtocol() != null) {
            chunk.write(
                    String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_PROTOCOL_HEADER, join(getSubProtocol())).getBytes());
        }
        if (getExtensions() != null) {
            chunk.write(
                    String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_EXTENSIONS_HEADER, join(getExtensions())).getBytes());
        }
        chunk.write("\r\n".getBytes());

        return chunk.toByteArray();
    }

}
