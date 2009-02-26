
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
 *
 * Portions Copyright Apache Software Foundation.
 */

package com.sun.grizzly.container;


import java.io.IOException;

import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.grizzlet.Grizzlet;
import java.util.logging.Logger;


/**
 * Grizzlet implementation of Grizzly <code>CometHandler</code>.
 * 
 * @author Jeanfrancois Arcand,
 */
public class GrizzletCometHandler implements CometHandler<GrizzletRequest> {
     
    /**
     * The request which will be parked/paused by the Grizzly ARP mechaCometnism.
     */
    private GrizzletRequest req;
    
    /**
     * The Grizzly associated with this CometHandler.
     */
    private Grizzlet grizzlet;
    
    
    /**
     * GrizzletEvent implementatation used wheh the CometSelector close the
     * expires the connection.
     */
    private AsyncConnectionImpl gEvent = new AsyncConnectionImpl();
    
    
    /**
     * Attach the GrizzletRequest which is the connection that will 
     * be paused/parked.
     */
    public void attach(GrizzletRequest req) {
        this.req = req;
    }

    
    /**
     * Invoke the Grizzlet.onPush method in reaction to a CometComet.notify()
     * operations.
     */
    public void onEvent(CometEvent event) throws IOException {
        if (event.getType() == CometEvent.NOTIFY){
            AsyncConnectionImpl grizzletEvent = (AsyncConnectionImpl)event.attachment();
            grizzletEvent.setRequest(req);
            grizzletEvent.setResponse(req.getResponse());
            grizzlet.onPush(grizzletEvent);
        }
    }

    
    public void onInitialize(CometEvent event) throws IOException {
    }

    
    /**
     * When the CometContext times out, this method will be invoked and 
     * the associated Grizzlet invoked.
     */
    public void onInterrupt(CometEvent event) throws IOException {
        gEvent.setRequest(req);
        gEvent.setResponse(req.getResponse());
        gEvent.setIsResuming(true);
        grizzlet.onPush(gEvent);
        gEvent.setIsResuming(false);
    }

    
    /**
     * Invoked when the Grizzly resume the continuation.
     */ 
    public void onTerminate(CometEvent event) throws IOException {
        onInterrupt(event);
    }

    
    /**
     * Return the associated Grizzlet.
     */
    public Grizzlet getGrizzlet() {
        return grizzlet;
    }

    
    /**
     * Set the Grizzlet.
     */
    public void setGrizzlet(Grizzlet grizzlet) {
        this.grizzlet = grizzlet;
    }

}
