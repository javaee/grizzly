/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http.utils;

import com.sun.grizzly.TCPConnectorHandler;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * TCP client that exercise the non blocking ConnectorHandler.
 *
 * @author Jeanfrancois Arcand
 */
public class NonBlockingTCPIOClient {
    private String host;
    private int port;
    
    private TCPConnectorHandler tcpConnectorHandler;
    
    
    public NonBlockingTCPIOClient(String host, int port) {
        this.host = host;
        this.port = port;
        tcpConnectorHandler = new TCPConnectorHandler();
    }
    
    public void connect() throws IOException {
        tcpConnectorHandler.connect(new InetSocketAddress(host, port));
    }
    
    public void send(byte[] msg) throws IOException {
        send(msg, true);
    }

    public void send(byte[] msg, boolean needFlush) throws IOException {
        tcpConnectorHandler.write(ByteBuffer.wrap(msg),true);
    }
    
    public byte[] receive(byte[] buf) throws IOException {
        return receive(buf, buf.length);
    }

    public byte[] receive(byte[] buf, int length) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf,0,length);
        long bytesRead = tcpConnectorHandler.read(byteBuffer,true);

        if (bytesRead == -1) {
            throw new EOFException("Unexpected client EOF!");
        }
        
        return buf;
    }
    
    public void close() throws IOException {
        tcpConnectorHandler.close();
    }
}
