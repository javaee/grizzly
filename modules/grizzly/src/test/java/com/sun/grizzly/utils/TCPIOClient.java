/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.utils;

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
