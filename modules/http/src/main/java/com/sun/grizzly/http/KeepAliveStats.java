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
package com.sun.grizzly.http;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class collecting keep-alive statistics.
 *
 * There is one instance of this class per HTTP listener (ie., SelectorThread).
 * Each instance exposes its stats as an MBean with an object name of the
 * form "<domain>:type=KeepAlive,name=http<port>", where <port> is replaced
 * with the port number of the associated HTTP listener.
 * 
 * @author Jan Luehe
 */
public class KeepAliveStats {

    private final AtomicInteger countConnections = new AtomicInteger();
    private final AtomicInteger countHits        = new AtomicInteger();
    private final AtomicInteger countFlushes     = new AtomicInteger();
    private final AtomicInteger countRefusals    = new AtomicInteger();
    private final AtomicInteger countTimeouts    = new AtomicInteger();



    /** 
     * Gets the number of connections in keep-alive mode.
     * 
     * @return Number of connections in keep-alive mode
     */    
    public  int getCountConnections() {
        return countConnections.get();
    }

    
    /** 
     * Increments the number of connections in keep-alive mode.
     */    
    public void incrementCountConnections() {
        countConnections.incrementAndGet();
    }
    
    
    /** 
     * Decrement the number of connections in keep-alive mode.
     */    
    protected void decrementCountConnections() {
        countConnections.decrementAndGet();
    }

    
    /** 
     * Gets the number of requests received by connections in keep-alive mode.
     *
     * @return Number of requests received by connections in keep-alive mode.
     */    
    public int getCountHits() {
        return countHits.get();
    }


    /** 
     * Increments the number of requests received by connections in
     * keep-alive mode.
     */    
    public void incrementCountHits() {
        countHits.incrementAndGet();
    }

    
    /** 
     * Gets the number of keep-alive connections that were closed
     *
     * @return Number of keep-alive connections that were closed
     */    
    public int getCountFlushes() {
        return countFlushes.get();
    }

    
    /** 
     * Increments the number of keep-alive connections that were closed
     */    
    public void incrementCountFlushes() {
        countFlushes.incrementAndGet();
    }


    /** 
     * Gets the number of keep-alive connections that were rejected.
     *
     * @return Number of keep-alive connections that were rejected.
     */    
    public int getCountRefusals() {
        return countRefusals.get();
    }
    

    /** 
     * Increments the number of keep-alive connections that were rejected.
     */    
    public void incrementCountRefusals() {
        countRefusals.incrementAndGet();
    }


    /** 
     * Gets the number of keep-alive connections that timed out.
     *
     * @return Number of keep-alive connections that timed out.
     */    
    public int getCountTimeouts() {
        return countTimeouts.get();
    }

    
    /** 
     * Increments the number of keep-alive connections that timed out.
     */    
    public void incrementCountTimeouts() {
        countTimeouts.incrementAndGet();
    }
}
