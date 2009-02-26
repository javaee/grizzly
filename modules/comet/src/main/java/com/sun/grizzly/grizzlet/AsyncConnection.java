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

package com.sun.grizzly.grizzlet;

import com.sun.grizzly.container.GrizzletRequest;
import com.sun.grizzly.container.GrizzletResponse;
import java.io.IOException;

/**
 * This class represent a possible asynchronous connection. An asynchronous
 * connection can always be suspended or resumed, its associated request
 * and response objects be used to construct a response, etc.
 *
 * @author Jeanfrancois Arcand
 */
public interface AsyncConnection {
    
    /**
     * Return <tt>true</tt> is the current connection associated with 
     * this event has been suspended.
     */
    public boolean isSuspended();
    
    
    /**
     * Suspend the current connection. Suspended connection are parked and
     * eventually used when the Grizzlet Container initiates pushes.
     */
    public void suspend() throws AlreadyPausedException;
    
    
    /**
     * Resume a suspended connection. The response will be completed and the 
     * connection become synchronous (e.g. a normal http connection).
     */
    public void resume() throws NotYetPausedException;
    
    
    /**
     * Advises the Grizzlet Container to start intiating a push operation, using 
     * the argument <code>message</code>. All asynchronous connection that has 
     * been suspended will have a chance to push the data back to their 
     * associated clients.
     *
     * @param message The data that will be pushed.
     */
    public void push(String message) throws IOException;
    
    
    /**
     * Return the GrizzletRequest associated with this AsynchConnection. 
     */
    public GrizzletRequest getRequest();
    
    
    /**
     * Return the GrizzletResponse associated with this AsynchConnection. 
     */
    public GrizzletResponse getResponse();
    
    
    /**
     * Is this AsyncConnection being in the process of being resumed?
     */
    public boolean isResuming();
 
    
    /**
     * Is this AsyncConnection has push events ready to push back data to 
     * its associated client.
     */
    public boolean hasPushEvent();
    
    
    /**
     * Return the <code>message</code> that can be pushed back.
     */
    public String getPushEvent();
    
    
    /**
     * Is the current asynchronous connection defined as an HTTP Get.
     */
    public boolean isGet();
    
    
    /**
     * Is the current asynchronous connection defined as an HTTP Get. 
     */
    public boolean isPost();
    
}
