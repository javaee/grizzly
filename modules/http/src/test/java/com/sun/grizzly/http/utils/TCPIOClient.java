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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

/**
 * @author Alexey Stashok
 */
public class TCPIOClient {
    private String host;
    private int port;
    
    private Socket socket;
    private DataInputStream is;
    private DataOutputStream os;
    
    public TCPIOClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(10 * 1000);
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
    }
    
    public void send(byte[] msg) throws IOException {
        send(msg, true);
    }

    public void send(byte[] msg, boolean needFlush) throws IOException {
        os.write(msg);
        if (needFlush) {
            os.flush();
        }
    }
    
    public byte[] receive(byte[] buf) throws IOException {
        return receive(buf, buf.length);
    }

    public byte[] receive(byte[] buf, int length) throws IOException {
        int bytesRead = 0;
        while(bytesRead < length) {
            int readBytesCount = is.read(buf, bytesRead, length - bytesRead);
            if (readBytesCount == -1) throw new EOFException("Unexpected client EOF!");
            bytesRead += readBytesCount;
        }
        
        return buf;
    }
    
    public void close() throws IOException {
        if (is != null) {
            try {
                is.close();
            } catch (IOException ex) {
            }
        }
        
        if (os != null) {
            try {
                os.close();
            } catch (IOException ex) {
            }
        }
        
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
            }
        }
    }
}
