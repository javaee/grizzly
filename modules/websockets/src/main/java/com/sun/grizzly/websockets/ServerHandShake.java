/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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

import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class ServerHandShake extends HandShake {
    private final static byte[] normalResponse = Charset.forName(
            "ASCII").encode("HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
            "Upgrade: WebSocket\r\nConnection: Upgrade\r\n").array();

    private final static byte[] locationResponse = Charset.
            forName("ASCII").encode("\r\nWebSocket-Location: ").array();

    private final static byte[] protocolResponse = Charset.
            forName("ASCII").encode("\r\nWebSocket-Protocol: ").array();
    private byte[] serverSecKey;

    public ServerHandShake(ClientHandShake client) {
        super(client.isSecure(), client.getOrigin(), client.getServerHostName(), client.getPort(),
                client.getResourcePath());
        setSubProtocol(client.getSubProtocol());
        if (client.getKey3() != null) {
            serverSecKey = SecKey.generateServerKey(client.getKey1(), client.getKey2(), client.getKey3());
        }
    }

    public ByteBuffer generate() {
        try {
            final ByteArrayOutputStream bb = new ByteArrayOutputStream();
            bb.write(normalResponse);
            if (serverSecKey != null) {
                write76Response(bb);
            } else {
                write75Response(bb);
            }
            bb.write(Constants.CRLF_BYTES);
            ByteBuffer buffer = ByteBuffer.allocate(bb.size());
            buffer.put(bb.toByteArray());
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void write75Response(ByteArrayOutputStream bb) throws IOException {
        write(bb, "WebSocket-Origin", getOrigin());
        write(bb, "WebSocket-Location", getLocation());
        String protocol = getSubProtocol();
        if (protocol != null) {
            write(bb, "WebSocket-Protocol", protocol);
        }
    }

    private void write76Response(ByteArrayOutputStream bb) throws IOException {
        write(bb, WebSocketEngine.SERVER_SEC_WS_ORIGIN_HEADER, getOrigin());
        write(bb, WebSocketEngine.SERVER_SEC_WS_LOCATION_HEADER, getLocation());
        String protocol = getSubProtocol();
        if (protocol != null) {
            write(bb, WebSocketEngine.SEC_WS_PROTOCOL_HEADER, protocol);
        }
        bb.write(Constants.CRLF_BYTES);
        bb.write(serverSecKey);
        bb.write(Constants.CRLF_BYTES);
    }

    private void write(ByteArrayOutputStream bb, String header, String value) throws IOException {
        bb.write((header + ": " + value).getBytes("ASCII"));
        bb.write(Constants.CRLF_BYTES);
    }

    public void prepare(Response response) throws IOException {
        response.setStatus(101);
        response.setMessage("Web Socket Protocol Handshake");
        response.setHeader("Upgrade", "WebSocket");
        response.setHeader("Connection", "Upgrade");
        if (serverSecKey == null) {
            response.setHeader("WebSocket-Origin", getOrigin());
            response.setHeader("WebSocket-Location", getLocation());
            if (getSubProtocol() != null) {
                response.setHeader("WebSocket-Protocol", getSubProtocol());
            }
        } else {
            response.setHeader(WebSocketEngine.SERVER_SEC_WS_ORIGIN_HEADER, getOrigin());
            response.setHeader(WebSocketEngine.SERVER_SEC_WS_LOCATION_HEADER, getLocation());
            if (getSubProtocol() != null) {
                response.setHeader(WebSocketEngine.SEC_WS_PROTOCOL_HEADER, getSubProtocol());
            }
        }

        if (serverSecKey != null) {

            final OutputBuffer buffer = response.getOutputBuffer();
            ByteChunk chunk = new ByteChunk(serverSecKey.length + Constants.CRLF_BYTES.length);
            chunk.append(serverSecKey, 0, serverSecKey.length);
            chunk.append(Constants.CRLF_BYTES, 0, Constants.CRLF_BYTES.length);
            buffer.doWrite(chunk, response);
        }
        response.flush();
        
//        response.suspend();
    }
}
