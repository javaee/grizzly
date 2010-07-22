/*
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
 */

package com.sun.grizzly.websockets;

import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.net.URL;
import java.io.EOFException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ClientNetworkHandler implements NetworkHandler {
    private SocketChannel channel;
    private URL url;
    private ClientWebSocketApplication app;
    private WebSocket webSocket;
    private ClientHandShake clientHS;
    private final ByteChunk chunk = new ByteChunk();

    private boolean isHeaderParsed = false;

    ClientNetworkHandler(SocketChannel channel) {
        this.channel = channel;
    }

    public ClientNetworkHandler(URL url, ClientWebSocketApplication application) throws IOException {
        this.url = url;
        app = application;
        channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(url.getHost(), url.getPort()));
        channel.socket().setSoTimeout(30000);
    }

    SocketChannel getChannel() {
        return channel;
    }

    public void setChannel(SocketChannel channel) {
        this.channel = channel;
    }

    public void send(DataFrame frame) throws IOException {
        write(frame.frame());
    }

    public SelectionKey getKey() {
        return channel.keyFor(app.getSelector());
    }

    public void process(SelectionKey key) throws IOException {
        if (key.isValid()) {
            if (key.isConnectable()) {
                disableOp(SelectionKey.OP_CONNECT);
                doConnect(true);
                enableOp(SelectionKey.OP_READ);
            } else if (key.isReadable()) {
                unframe();
                if (webSocket.isConnected()) {
                    enableOp(SelectionKey.OP_READ);
                }
            }
        }

    }

    protected void doConnect(final boolean finishNioConnect) throws IOException {
        if (finishNioConnect) {
            channel.finishConnect();
        }
        
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
        clientHS = new ClientHandShake(isSecure, origin.toString(), url.getHost(),
                String.valueOf(url.getPort()), path);
        write(clientHS.getBytes());
    }

    protected void write(byte[] bytes) throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        channel.write(buffer);
    }

    private void unframe() throws IOException {
        int lastRead;
        while ((lastRead = read()) > 0) {
            if (webSocket.isConnected()) {
                readFrame();
            } else {
                byte[] serverKey = findServerKey();

                if (serverKey == null) return;  // not enough data

                try {
                    clientHS.validateServerResponse(serverKey);
                } catch (HandshakeException e) {
                    throw new IOException(e.getMessage());
                }
                webSocket.onConnect();
            }
        }

        if (lastRead == -1) {
            throw new EOFException();
        }
    }

    private byte[] findServerKey() throws IOException {
        if (!isHeaderParsed) {
            while (true) {
                byte[] line = readLine();
                if (line == null) return null;

                if (line.length == 0) break;
            }
        }

        isHeaderParsed = true;
        return readN(16);
    }

    private byte[] readLine() throws IOException {
        if (chunk.getLength() <= 0) return null;
        
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
        if (chunk.getLength() < n) return null;

        final byte[] result = new byte[n];
        chunk.substract(result, 0, n);

        return result;
    }

    private void enableOp(final int op) {
        final SelectionKey key = getKey();
        final int ops = key.interestOps();
        final int newOp = ops | op;
        if (newOp != ops) {
            key.interestOps(newOp);
        }
    }

    private void disableOp(final int op) {
        final SelectionKey key = getKey();
        final int ops = key.interestOps();
        final int newOp = ops & ~op;
        if (newOp != ops) {
            key.interestOps(newOp);
        }
    }

    public void shutdown() throws IOException {
        getKey().cancel();
        channel.close();
        app.remove(webSocket);
    }

    public void setWebSocket(BaseWebSocket webSocket) {
        this.webSocket = webSocket;
        if (app != null) {
            app.register(this);
        }
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
     *
     * @throws IOException
     */
    private int read() throws IOException {
        int count = chunk.getLength();
        if (count < 1) {
            ByteBuffer bytes = ByteBuffer.allocate(WebSocketEngine.INITIAL_BUFFER_SIZE);
            while ((count = channel.read(bytes)) == WebSocketEngine.INITIAL_BUFFER_SIZE) {
                chunk.append(bytes.array(), 0, count);
            }

            if (count > 0) {
                chunk.append(bytes.array(), 0, count);
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
}
