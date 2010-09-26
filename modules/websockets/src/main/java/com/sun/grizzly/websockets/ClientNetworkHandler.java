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

package com.sun.grizzly.websockets;

import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.net.URL;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.channels.Selector;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class ClientNetworkHandler implements NetworkHandler {
    private Socket socket;
    private URL url;
    private ClientWebSocket webSocket;
    private Selector selector;
    private final ByteChunk chunk = new ByteChunk();

    private boolean isHeaderParsed = false;
    private OutputStream outputStream;
    private InputStream inputStream;

    public ClientNetworkHandler(ClientWebSocket webSocket) {
        url = webSocket.getAddress();
        this.webSocket = webSocket;

        try {
            connect();
            handshake();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        queueRead();
    }

    private void queueRead() {
        webSocket.execute(new Runnable() {
            public void run() {
                try {
                    unframe();
                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        });
    }

    protected void connect() throws IOException {
        if ("ws".equals(url.getProtocol())) {
            socket = new Socket(url.getHost(), url.getPort());
        } else if ("wss".equals(url.getProtocol())) {
            socket = getSSLSocketFactory().createSocket(url.getHost(), url.getPort());
        } else {
            throw new IOException("Unknown schema: " + url.getProtocol());
        }
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    public void send(DataFrame frame) throws IOException {
        write(frame.frame());
    }

    protected void handshake() throws IOException {
        final boolean isSecure = "wss".equals(url.getProtocol());

        final StringBuilder origin = new StringBuilder();
        origin.append(isSecure ? "https://" : "http://");
        origin.append(url.getHost());
        if (!isSecure && url.getPort() != 80 || isSecure && url.getPort() != 443) {
            origin.append(":")
                    .append(url.getPort());
        }
        String path = url.getPath();
        if ("".equals(path)) {
            path = "/";
        }

        ClientHandShake clientHS = new ClientHandShake(isSecure, origin.toString(), url.getHost(),
                String.valueOf(url.getPort()), path);
        write(clientHS.getBytes());

        byte[] serverKey = findServerKey();

        if (serverKey == null) {
            return;
        }  // not enough data

        try {
            clientHS.validateServerResponse(serverKey);
        } catch (HandshakeException e) {
            throw new IOException(e.getMessage());
        }
        webSocket.onConnect();

    }

    protected void write(byte[] bytes) throws IOException {
        outputStream.write(bytes);
        outputStream.flush();
    }

    private void unframe() throws IOException {
        int lastRead;
        while ((lastRead = read()) > 0) {
            readFrame();
        }

        if (lastRead == -1) {
            throw new EOFException();
        }
        if (webSocket.isConnected()) {
            queueRead();
        }
    }

    private byte[] findServerKey() throws IOException {
        if (!isHeaderParsed) {
            read();
            while (true) {
                byte[] line = readLine();
                if (line == null) {
                    return null;
                }

                if (line.length == 0) {
                    break;
                }
            }
        }

        isHeaderParsed = true;
        return readN(16);
    }

    private byte[] readLine() throws IOException {
        if (chunk.getLength() <= 0) {
            read();
        }

        int idx = chunk.indexOf('\n', 0);
        if (idx != -1) {
            int eolBytes = 1;
            final int offset = chunk.getOffset();
            idx += offset;

            if (idx > offset && chunk.getBuffer()[idx - 1] == '\r') {
                idx--;
                eolBytes = 2;
            }

            final int size = idx - offset;

            final byte[] result = new byte[size];
            chunk.substract(result, 0, size);

            chunk.setOffset(chunk.getOffset() + eolBytes); // Skip \r\n or \n
            return result;
        }

        return null;
    }

    private byte[] readN(int n) throws IOException {
        if (chunk.getLength() < n) {
            return null;
        }

        final byte[] result = new byte[n];
        chunk.substract(result, 0, n);

        return result;
    }

    public void shutdown() throws IOException {
        socket.close();
    }

    public ClientWebSocket getWebSocket() {
        return webSocket;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = (ClientWebSocket) webSocket;
    }

    protected void readFrame() throws IOException {
        while (read() > 0) {
            final DataFrame dataFrame = DataFrame.read(this);
            if (dataFrame != null) {
                dataFrame.respond(webSocket);
            } else {
                webSocket.close();
            }
        }
    }

    /**
     * If necessary read more bytes from the channel.
     *
     * @return any number > -1 means bytes were read
     * @throws IOException
     */
    private int read() throws IOException {
        int count = chunk.getLength();
        if (count < 1) {
            byte[] bytes = new byte[WebSocketEngine.INITIAL_BUFFER_SIZE];
            while ((count = inputStream.read(bytes)) == WebSocketEngine.INITIAL_BUFFER_SIZE) {
                chunk.append(bytes, 0, count);
            }

            if (count > 0) {
                chunk.append(bytes, 0, count);
            }
        }

        final int length = chunk.getLength();

        if (length <= 0) {
            return count;
        }

        return length;
    }

    public byte get() throws IOException {
        synchronized (chunk) {
            fill();
            return (byte) chunk.substract();
        }
    }

    private void fill() throws IOException {
        if (chunk.getLength() == 0) {
            read();
        }
    }

    public boolean peek(byte... bytes) throws IOException {
        synchronized (chunk) {
            fill();
            return chunk.startsWith(bytes);
        }
    }

    public SSLSocketFactory getSSLSocketFactory() throws IOException {
        try {
            //---------------------------------
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(
                                X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(
                                X509Certificate[] certs, String authType) {
                        }
                    }
            };
            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            //---------------------------------
            return sc.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }
    }

}
