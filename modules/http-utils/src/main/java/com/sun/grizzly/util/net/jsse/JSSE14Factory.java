

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
import javax.net.ssl.SSLSocket;
// START SJSAS 6439313
import javax.net.ssl.SSLEngine;
// END SJSAS 6439313
import com.sun.grizzly.util.net.SSLSupport;
import com.sun.grizzly.util.net.ServerSocketFactory;

/**
 * Implementation class for JSSEFactory for JSSE 1.1.x (that ships with the
 * 1.4 JVM).
 *
 * @author Bill Barker
 */
// START SJSAS 6240885
//class JSSE14Factory implements JSSEFactory {
public class JSSE14Factory implements JSSEFactory {
// END SJSAS 6240885

    // START SJSAS 6240885
    // 
    //JSSE14Factory() {
    public JSSE14Factory() {
    // END SJSAS 6240885
    }

    public ServerSocketFactory getSocketFactory() {
	return new JSSE14SocketFactory();
    }
    
    
    public SSLSupport getSSLSupport(Socket socket) {
        return new JSSE14Support((SSLSocket)socket);
    }

    // START SJSAS 6439313
    public SSLSupport getSSLSupport(SSLEngine sslEngine) {
        return new JSSE14Support(sslEngine);
    }
    // END SJSAS 6439313
}
