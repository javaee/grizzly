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

package com.sun.grizzly.websockets.draft76;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.net.URL;
import com.sun.grizzly.websockets.HandShake;
import com.sun.grizzly.websockets.HandshakeException;
import com.sun.grizzly.websockets.NetworkHandler;
import com.sun.grizzly.websockets.WebSocketEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

public class HandShake76 extends HandShake {
    // Draft 76 headers
    public static final String SEC_WS_KEY1_HEADER = "Sec-WebSocket-Key1";
    public static final String SEC_WS_KEY2_HEADER = "Sec-WebSocket-Key2";
    public static final String CLIENT_WS_ORIGIN_HEADER = "Origin";
    public static final String SERVER_SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String SERVER_SEC_WS_LOCATION_HEADER = "Sec-WebSocket-Location";
    
    private final SecKey key1;
    private final SecKey key2;
    private final byte[] key3;
    private byte[] serverSecKey;
    private static final Random random = new Random();
    private final NetworkHandler handler;

    public HandShake76(NetworkHandler handler, URL url) {
        super(url);
        this.handler = handler;
        key1 = SecKey.generateSecKey();
        key2 = SecKey.generateSecKey();
        key3 = new byte[8];
        random.nextBytes(key3);
    }

    public HandShake76(NetworkHandler handler, Request request) {
        super(request);
        this.handler = handler;
        final MimeHeaders headers = request.getMimeHeaders();
        key1 = SecKey.parse(headers.getHeader(SEC_WS_KEY1_HEADER));
        key2 = SecKey.parse(headers.getHeader(SEC_WS_KEY2_HEADER));
        key3 = handler.get(8);

        String header = readHeader(headers, CLIENT_WS_ORIGIN_HEADER);
        setOrigin(header != null ? header : "http://localhost");
        determineHostAndPort(headers);
        buildLocation();
        if (getServerHostName() == null || getOrigin() == null) {
            throw new HandshakeException("Missing required headers for WebSocket negotiation");
        }
        serverSecKey = key3 == null ? null : SecKey.generateServerKey(key1, key2, key3);
    }

    @Override
    public void initiate(NetworkHandler handler) {
        try {
            ByteArrayOutputStream chunk = new ByteArrayOutputStream();
            chunk.write(String.format("GET %s HTTP/1.1\r\n", getResourcePath()).getBytes());
            String host = getServerHostName();
            if(!isSecure() && getPort() != 80 || isSecure() &&getPort() != 443) {
                host += ":" + getPort();
            }
            chunk.write(String.format("Host: %s\r\n", host).getBytes());
            chunk.write(String.format("Connection: Upgrade\r\n").getBytes());
            chunk.write(String.format("Upgrade: WebSocket\r\n").getBytes());
            chunk.write(String.format("%s: %s\r\n", CLIENT_WS_ORIGIN_HEADER, getOrigin()).getBytes());
            if (!getSubProtocol().isEmpty()) {
                chunk.write(
                        String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_PROTOCOL_HEADER, join(getSubProtocol()))
                                .getBytes());
            }
            chunk.write(String.format("%s: %s\r\n", SEC_WS_KEY1_HEADER, key1.getSecKey()).getBytes());
            chunk.write(String.format("%s: %s\r\n", SEC_WS_KEY2_HEADER, key2.getSecKey()).getBytes());
            chunk.write("\r\n".getBytes());
            chunk.write(key3);

            handler.write(chunk.toByteArray());
        } catch (IOException e) {
            throw new HandshakeException(e.getMessage(), e);
        }
    }

    @Override
    public void validateServerResponse(Map<String, String> headers) {
        super.validateServerResponse(headers);

        final String serverLocation = headers.get(SERVER_SEC_WS_LOCATION_HEADER);
        if(!getLocation().equals(serverLocation)) {
            throw new HandshakeException(String.format("Location field from server doesn't match: client '%s' vs. server '%s'",
                    getLocation(), serverLocation));
        }
        final byte[] clientKey = SecKey.generateServerKey(key1, key2, key3);
        byte[] serverKey = handler.get(16);
        if (!Arrays.equals(clientKey, serverKey)) {
            throw new HandshakeException(String.format("Security keys do not match: client '%s' vs. server '%s'",
                    Arrays.toString(clientKey), Arrays.toString(serverKey)));
        }
    }

    @Override
    public void respond(Response response) {
        super.respond(response);
        handler.write(serverSecKey);
    }

    @Override
    public void setHeaders(Response response) {
        response.setHeader(SERVER_SEC_WS_LOCATION_HEADER, getLocation());
        response.setHeader(SERVER_SEC_WS_ORIGIN_HEADER, getOrigin());
    }
}
