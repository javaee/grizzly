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
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.portunif;

import com.sun.grizzly.Context;
import java.io.IOException;

/**
 * This interface should be used to implement a TCP 'protocol finder'. From the
 * <code>SelectionKey</code>, it is possible to get a reference to the 
 * <code>SocketChannel</code> and read bytes from it. From the bytes read, the 
 * TCP protocol can be derived and stored inside a <code>ProtocolInfo</code> 
 * instance.
 *
 * @author Jeanfrancois Arcand
 */
public interface ProtocolFinder {
    
    /**
     * Try to determine the TCP protocol used (http, soap, etc.).
     * @param selectionKey The SelectionKey from which the SocketChannel can 
     *                     be derived.
     * @return ProtocolInfo An instance that store information about the 
     *         protocol, if found.
     */
    public String find(Context context, PUProtocolRequest protocolRequest) 
            throws IOException;
}
