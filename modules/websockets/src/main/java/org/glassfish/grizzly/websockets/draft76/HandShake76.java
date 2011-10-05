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

package org.glassfish.grizzly.websockets.draft76;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Random;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.HeapBuffer;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.HandshakeException;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.WebSocketException;

public class HandShake76 extends HandShake {
    // Draft 76 headers
    public static final String SEC_WS_KEY1_HEADER = "Sec-WebSocket-Key1";
    public static final String SEC_WS_KEY2_HEADER = "Sec-WebSocket-Key2";
    public static final String SERVER_SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String SERVER_SEC_WS_LOCATION_HEADER = "Sec-WebSocket-Location";
    private static final int KEY3_SIZE = 8;
    private final SecKey key1;
    private final SecKey key2;
    private final byte[] key3;
    private byte[] serverSecKey;
    private static final Random random = new Random();

    public HandShake76(URI uri) {
        super(uri);
        if(isSecure() && getPort() != 443 || !isSecure() && getPort() != 80) {
            setServerHostName(getServerHostName() + ":" + getPort());
        }
        key1 = SecKey.generateSecKey();
        key2 = SecKey.generateSecKey();
        key3 = new byte[KEY3_SIZE];
        random.nextBytes(key3);
    }

    public HandShake76(HttpContent message) {
        super((HttpRequestPacket) message.getHttpHeader());
        final HttpRequestPacket request = (HttpRequestPacket) message.getHttpHeader();
        final MimeHeaders headers = request.getHeaders();
        key1 = SecKey.parse(headers.getHeader(SEC_WS_KEY1_HEADER));
        key2 = SecKey.parse(headers.getHeader(SEC_WS_KEY2_HEADER));
        key3 = new byte[KEY3_SIZE];

        message.getContent().get(key3);

        serverSecKey = key3 == null ? null : SecKey.generateServerKey(key1, key2, key3);
    }

    @Override
    public HttpContent composeHeaders() {
        final HttpContent httpContent = super.composeHeaders();
        final HttpHeader header = httpContent.getHttpHeader();
        header
//        final HttpRequestPacket.Builder builder = HttpRequestPacket.builder()
            .addHeader(WebSocketEngine.CLIENT_WS_ORIGIN_HEADER, getOrigin());
        header
            .addHeader(SEC_WS_KEY1_HEADER, key1.getSecKey());
        header
            .addHeader(SEC_WS_KEY2_HEADER, key2.getSecKey());
//        httpContent.append(HttpContent.builder(builder.build()).build());
        return httpContent;
    }

    @Override
    public void initiate(FilterChainContext ctx) throws IOException {
        ctx.write(composeHeaders());
        ctx.write(HeapBuffer.wrap(key3));
    }

    @Override
    public void validateServerResponse(HttpResponsePacket headers) {
        super.validateServerResponse(headers);

        final String serverLocation = headers.getHeader(SERVER_SEC_WS_LOCATION_HEADER);
        if(!getLocation().equals(serverLocation)) {
            throw new HandshakeException(String.format("Location field from server doesn't match: client '%s' vs. server '%s'",
                    getLocation(), serverLocation));
        }
        final byte[] clientKey = SecKey.generateServerKey(key1, key2, key3);
        final Buffer buffer = Buffers.EMPTY_BUFFER; // = content.getContent();
        byte[] serverKey = new byte[16];
        buffer.get(serverKey);
        if (!Arrays.equals(clientKey, serverKey)) {
            throw new HandshakeException(String.format("Security keys do not match: client '%s' vs. server '%s'",
                    Arrays.toString(clientKey), Arrays.toString(serverKey)));
        }
    }

    @Override
    public void respond(FilterChainContext ctx, WebSocketApplication app, HttpResponsePacket response) {
        super.respond(ctx, app, response);
        final HeapBuffer buffer = HeapBuffer.wrap(serverSecKey);
        try {
            ctx.write(buffer);
        } catch (IOException e) {
            throw new WebSocketException(e.getMessage(), e);
        }
    }

    @Override
    public void setHeaders(HttpResponsePacket response) {
        response.setReasonPhrase("WebSocket Protocol Handshake");
        response.setHeader("Upgrade", "WebSocket");
        response.setHeader(SERVER_SEC_WS_LOCATION_HEADER, getLocation());
        response.setHeader(SERVER_SEC_WS_ORIGIN_HEADER, getOrigin());
    }
}
