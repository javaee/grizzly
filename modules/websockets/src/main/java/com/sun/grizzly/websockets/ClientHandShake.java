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

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.MimeHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class ClientHandShake extends HandShake {
    private static final Random random = new Random();

    private SecKey key1;
    private SecKey key2;
    private byte[] key3;

    public ClientHandShake(boolean isSecure, String origin, String serverHostName, String portNumber, String path) {
        super(isSecure, origin, serverHostName, portNumber, path);
        key1 = SecKey.generateSecKey();
        key2 = SecKey.generateSecKey();
        key3 = new byte[8];
        random.nextBytes(key3);
    }

    public ClientHandShake(Request request, boolean secure) throws IOException {
        super(secure, request.requestURI().toString());

        final MimeHeaders headers = request.getMimeHeaders();
        boolean upgrade = "WebSocket".equals(headers.getHeader("Upgrade"));
        boolean connection = "Upgrade".equals(headers.getHeader("Connection"));

        if (headers.getHeader(WebSocketEngine.SEC_WS_KEY1_HEADER) != null) {
            parse76Headers(request, headers);
        } else {
            parse75Headers(headers);
        }
        String header = readHeader(headers, WebSocketEngine.CLIENT_WS_ORIGIN_HEADER);
        setOrigin(header != null ? header : "http://localhost");
        determineHostAndPort(headers);
        setLocation(buildLocation(secure));
        if (getServerHostName() == null || getOrigin() == null || !upgrade || !connection) {
            throw new IOException("Missing required headers for WebSocket negotiation");
        }
    }

    public SecKey getKey1() {
        return key1;
    }

    public SecKey getKey2() {
        return key2;
    }

    public byte[] getKey3() {
        return key3;
    }

    private void parse76Headers(Request request, MimeHeaders headers) throws IOException {
        setSubProtocol(headers.getHeader(WebSocketEngine.SEC_WS_PROTOCOL_HEADER));
        key1 = SecKey.create(headers.getHeader(WebSocketEngine.SEC_WS_KEY1_HEADER));
        key2 = SecKey.create(headers.getHeader(WebSocketEngine.SEC_WS_KEY2_HEADER));
        if (key1 != null && key2 != null) {
            final ByteChunk chunk = new ByteChunk(8);
            request.getInputBuffer().doRead(chunk, request);
            if (chunk.getEnd() - chunk.getStart() != 8) {
                throw new IllegalArgumentException("key3 length should be 8 bytes");
            }
            key3 = new byte[8];
            System.arraycopy(chunk.getBytes(), chunk.getStart(), key3, 0, 8);
        }

    }

    private void determineHostAndPort(MimeHeaders headers) {
        String header;
        header = readHeader(headers, "host");
        final int i = header == null ? -1 : header.indexOf(":");
        if (i == -1) {
            setServerHostName(header);
            setPort("80");
        } else {
            setServerHostName(header.substring(0, i));
            setPort(header.substring(i + 1));
        }
    }

    private void parse75Headers(MimeHeaders headers) {
        setSubProtocol(headers.getHeader("WebSocket-Protocol"));
    }

    public void validateServerResponse(final byte[] key) throws HandshakeException {
        if (!Arrays.equals(SecKey.generateServerKey(key1, key2, key3), key)) {
            throw new HandshakeException("Keys do not match");
        }
    }

    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        chunk.write(String.format("GET %s HTTP/1.1\r\n", getResourcePath()).getBytes());
        chunk.write(String.format("Host: %s\r\n", getServerHostName()).getBytes());
        chunk.write(String.format("Connection: Upgrade\r\n").getBytes());
        chunk.write(String.format("Upgrade: WebSocket\r\n").getBytes());
        chunk.write(String.format("%s: %s\r\n", WebSocketEngine.CLIENT_WS_ORIGIN_HEADER, getOrigin()).getBytes());
        if(getSubProtocol() != null) {
            chunk.write(String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_PROTOCOL_HEADER, getSubProtocol()).getBytes());
        }
        chunk.write(String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_KEY1_HEADER, getKey1().getSecKey()).getBytes());
        chunk.write(String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_KEY2_HEADER, getKey2().getSecKey()).getBytes());
        chunk.write("\r\n".getBytes());
        chunk.write(getKey3());

        return chunk.toByteArray();
    }
}