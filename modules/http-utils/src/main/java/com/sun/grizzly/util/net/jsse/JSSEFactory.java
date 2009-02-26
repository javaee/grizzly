

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

package com.sun.grizzly.util.net.jsse;

import java.net.Socket;
// START SJSAS 6439313
import javax.net.ssl.SSLEngine;
// END SJSAS 6439313
import com.sun.grizzly.util.net.SSLSupport;
import com.sun.grizzly.util.net.ServerSocketFactory;

/** 
 * Factory interface to construct components based on the JSSE version
 * in use.
 *
 * @author Bill Barker
 */

interface JSSEFactory {

    /**
     * Returns the ServerSocketFactory to use.
     */
    public ServerSocketFactory getSocketFactory();

    /**
     * returns the SSLSupport attached to this socket.
     */    
    public SSLSupport getSSLSupport(Socket socket);
    
    // START SJSAS 6439313
    /**
     * returns the SSLSupport attached to this SSLEngine.
     */
    public SSLSupport getSSLSupport(SSLEngine sslEngine);
    // END SJSAS 6439313
};
