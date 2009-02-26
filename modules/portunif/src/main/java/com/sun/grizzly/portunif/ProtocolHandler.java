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
import java.nio.channels.SelectionKey;

/**
 * Once a protocol has been found by a <code>ProtocolFinder</code>
 *
 * @author Jeanfrancois Arcand
 */
public interface ProtocolHandler {
    
    /**
     * Return an array of protocols supported by this ProtocolHandler.
     */
    public String[] getProtocols();
    
    
    /**
     * Handle the current request by either redirecting the request to a new 
     * port or by delivering the request to the proper endpoint.
     * 
     * @return true, if connection should be kept alive, false - otherwise
     */
    public boolean handle(Context context, PUProtocolRequest protocolRequest) 
            throws IOException;
    
    
    /**
     * Invoked when the SelectorThread is about to expire a SelectionKey.
     * @return true if the SelectorThread should expire the SelectionKey, false
     *              if not.
     */
    public boolean expireKey(SelectionKey key);  
}
