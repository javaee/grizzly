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

import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.net.URL;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;

public class WebSocketClient extends BaseWebSocket implements WebSocket {
    private SocketChannel channel;

    private static final String CLIENT_HANDSHAKE = "GET /echo HTTP/1.1" + Constants.CRLF
            + "Upgrade: WebSocket" + Constants.CRLF
            + "Connection: Upgrade" + Constants.CRLF
            + "Host: localhost" + Constants.CRLF
            + "Origin: http://localhost" + Constants.CRLF
            + Constants.CRLF;
    private URL url;
    private ClientHandShake clientHS;

    public WebSocketClient(String address, WebSocketListener... listeners) throws IOException {
        url = new URL(address);
        setSelector(SelectorProvider.provider().openSelector());
        new Thread(new Runnable() {
            public void run() {
                select();
            }
        }).start();
        for (WebSocketListener listener : listeners) {
            add(listener);
        }
    }

    public void connect() throws IOException {
        open(url);
    }

    private void open(URL url) throws IOException {
        final SocketChannel socketChannel = SocketChannel.open();
        channel = socketChannel;
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(url.getHost(), url.getPort()));
        socketChannel.socket().setSoTimeout(30000);
        state = State.CONNECTING;
        getSelector().wakeup();
    }

    @Override
    public void close() throws IOException {
        super.close();
        channel.keyFor(getSelector()).cancel();
        channel.close();
    }

    private void select() {
        while (state != State.CLOSED) {
            try {
                if (state == State.CONNECTING) {
                    channel.register(getSelector(), SelectionKey.OP_CONNECT);
                }
                getSelector().select();

                Iterator<SelectionKey> selectedKeys = getSelector().selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();
                    process(key);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
            getSelector().wakeup();
        }
    }

    protected void process(SelectionKey key) throws IOException {
        if (key.isValid()) {
            if (key.isConnectable()) {
                disableOp(SelectionKey.OP_CONNECT);
                doConnect();
            } else if (key.isReadable()) {
                doRead();
            }
            enableOp(SelectionKey.OP_READ);
            key.selector().wakeup();
        }
    }

    @Override
    protected void doConnect() throws IOException {
        channel.finishConnect();
        final boolean isSecure = "wss".equals(url.getProtocol());

        final StringBuilder origin = new StringBuilder();
        origin.append(isSecure ? "https://" : "http://");
        origin.append(url.getHost());
        if(!isSecure && url.getPort() != 80 || isSecure && url.getPort() != 443) {
            origin.append(":")
                    .append(url.getPort());
        }
        clientHS = new ClientHandShake(isSecure, origin.toString(), url.getHost(),
                String.valueOf(url.getPort()), url.getPath());
        write(clientHS.getBytes());
        state = State.WAITING_ON_HANDSHAKE;
        super.doConnect();
    }

    @Override
    protected void doRead() throws IOException {
        switch (state) {
            case WAITING_ON_HANDSHAKE:
                final ByteBuffer buffer = ByteBuffer.allocate(WebSocketEngine.INITIAL_BUFFER_SIZE);
                final int read = channel.read(buffer);
                buffer.flip();
                byte[] serverKey = findServerKey(buffer);
                try {
                    clientHS.validateServerResponse(serverKey);
                } catch (HandshakeException e) {
                    throw new IOException(e.getMessage(), e);
                }
                state = State.READY;
                setConnected(true);
                onConnect();
                break;
            case READY:
                super.doRead();
                break;
            default:
                break;
        }
    }

    private byte[] findServerKey(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining() && readLine(buffer).length > 0) {
            ;
        }
        return buffer.hasRemaining() ? readLine(buffer) : new byte[0];
    }

    private byte[] readLine(ByteBuffer buffer) throws IOException {
        ByteChunk line = new ByteChunk(0);
        byte last = 0x00;
        boolean done = false;
        while (!done && buffer.hasRemaining()) {
            final byte next = buffer.get();
            if (next != '\r' && next != '\n') {
                line.append(next);
            } else {
                done = last == '\r' || last == '\n';
            }
            last = next;
        }
        return Arrays.copyOf(line.getBuffer(), line.getEnd());
    }

    protected void unframe() throws IOException {
        int count;
        do {
            ByteBuffer bytes = ByteBuffer.allocate(WebSocketEngine.INITIAL_BUFFER_SIZE);
            count = channel.read(bytes);
            bytes.flip();
            unframe(bytes);
        } while (count > 0);
    }

    @Override
    protected void write(byte[] bytes) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        final int i = channel.write(buffer);
    }

    protected SelectionKey getKey() {
        return channel.keyFor(getSelector());
    }

    void enableOp(final int op) {
        final SelectionKey key = getKey();
        final int ops = key.interestOps();
        final int newOp = ops | op;
        if (newOp != ops) {
            key.interestOps(newOp);
        }
        key.selector().wakeup();
    }

    void disableOp(final int op) {
        final SelectionKey key = getKey();
        final int ops = key.interestOps();
        final int newOp = ops & ~op;
        if (newOp != ops) {
            key.interestOps(newOp);
        }
    }
}