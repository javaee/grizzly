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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * @author Alexey Stashok
 */
public class UDPIOClient {
    private String host;
    private int port;
    
    private DatagramSocket socket;
    
    public UDPIOClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    public void connect() throws IOException {
        socket = new DatagramSocket();
        socket.connect(InetAddress.getByName(host), port);
        
        
    }
    
    public void send(byte[] msg) throws IOException {
        DatagramPacket packet = new DatagramPacket(msg, msg.length);
        socket.send(packet);
    }
    
    public int receive(byte[] buf) throws IOException {
        return receive(buf, buf.length);
    }

    public int receive(byte[] buf, int length) throws IOException {
        DatagramPacket packet = new DatagramPacket(buf, length);
        socket.receive(packet);
        return packet.getLength();
    }
    
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}
