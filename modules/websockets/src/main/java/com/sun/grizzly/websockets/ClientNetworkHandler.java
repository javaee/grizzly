/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.util.net.URL;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class ClientNetworkHandler extends BaseNetworkHandler {
    private Socket socket;

    private OutputStream outputStream;
    private InputStream inputStream;

    protected ClientNetworkHandler() {
    }

    public ClientNetworkHandler(WebSocketClient webSocket) {
        URL url = webSocket.getAddress();

        try {
            if ("ws".equals(url.getProtocol())) {
                socket = new Socket(url.getHost(), url.getPort());
            } else if ("wss".equals(url.getProtocol())) {
                socket = getSSLSocketFactory().createSocket(url.getHost(), url.getPort());
            } else {
                throw new IOException("Unknown schema: " + url.getProtocol());
            }
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void write(byte[] bytes) {
        try {
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            throw new WebSocketException(e.getMessage(), e);
        }
    }

    public void shutdown() throws IOException {
        socket.close();
    }

    public boolean ready() {
        return read() > 0;
    }

    /**
     * If necessary read more bytes from the channel.
     *
     * @return any number > -1 means bytes were read
     * @throws IOException
     */
    protected int read() {
        try {
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
        } catch (IOException e) {
            throw new WebSocketException(e.getMessage(), e);
        }
    }

    public byte get() {
        synchronized (chunk) {
            fill();
            try {
                return (byte) chunk.substract();
            } catch (IOException e) {
                throw new WebSocketException(e.getMessage(), e);
            }
        }
    }

    public byte[] get(int count) {
        synchronized (chunk) {
            try {
                byte[] bytes = new byte[count];
                int total = 0;
                while (total < count) {
                    if (chunk.getLength() < count) {
                        read();
                    }
                    total += chunk.substract(bytes, total, count - total);
                }
                return bytes;
            } catch (IOException e) {
                throw new WebSocketException(e.getMessage(), e);
            }
        }
    }

    private void fill() {
        if (chunk.getLength() == 0) {
            read();
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

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
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

    public void close() {
    }
    
    public String toString() {
        final StringBuilder sb = new StringBuilder()
                .append("CNH[")
                .append(socket == null ? "unconnected" : socket.getLocalPort())
                .append("] {")
                .append(super.toString())
                .append('}');
        return sb.toString();
    }
}
