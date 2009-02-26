

/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * glassfish/bootstrap/legal/CDDLv1.0.txt or
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * glassfish/bootstrap/legal/CDDLv1.0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Portions Copyright Apache Software Foundation.
 */ 

package com.sun.grizzly.util.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;


/**
 * Default server socket factory. Doesn't do much except give us
 * plain ol' server sockets.
 *
 * @author db@eng.sun.com
 * @author Harish Prabandham
 */

// Default implementation of server sockets.

//
// WARNING: Some of the APIs in this class are used by J2EE. 
// Please talk to harishp@eng.sun.com before making any changes.
//
class DefaultServerSocketFactory extends ServerSocketFactory {

    DefaultServerSocketFactory () {
        /* NOTHING */
    }

    public ServerSocket createSocket (int port)
    throws IOException {
        return  new ServerSocket (port);
    }

    public ServerSocket createSocket (int port, int backlog)
    throws IOException {
        return new ServerSocket (port, backlog);
    }

    public ServerSocket createSocket (int port, int backlog,
        InetAddress ifAddress)
    throws IOException {
        return new ServerSocket (port, backlog, ifAddress);
    }
 
    public Socket acceptSocket(ServerSocket socket)
 	throws IOException {
 	return socket.accept();
    }
 
    public void handshake(Socket sock)
 	throws IOException {
 	; // NOOP
    }
 	    
    
    // START SJSAS 6439313
    public void init() throws IOException{
        ;
    }
    // END SJSAS 6439313      
 }
