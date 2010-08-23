/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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
    
    
    /**
     * Return the number of suspended connections associated with the current
     * {@link Grizzlet}
     */
    public int getSuspendedCount();
    
    
}
