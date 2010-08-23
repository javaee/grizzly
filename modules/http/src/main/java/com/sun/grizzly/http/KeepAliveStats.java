/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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
    private volatile boolean isEnabled;
    private final AtomicInteger countConnections = new AtomicInteger();
    private final AtomicInteger countHits        = new AtomicInteger();
    private final AtomicInteger countFlushes     = new AtomicInteger();
    private final AtomicInteger countRefusals    = new AtomicInteger();
    private final AtomicInteger countTimeouts    = new AtomicInteger();

    private volatile int maxKeepAliveRequests = Constants.DEFAULT_MAX_KEEP_ALIVE;
    /*
     * Number of seconds before idle keep-alive connections expire
     */
    private volatile int keepAliveTimeoutInSeconds = 30;

    public synchronized void enable() {
        isEnabled = true;
    }

    public synchronized void disable() {
        isEnabled = false;
        countConnections.set(0);
        countHits.set(0);
        countFlushes.set(0);
        countRefusals.set(0);
        countTimeouts.set(0);
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    /** 
     * Gets the number of connections in keep-alive mode.
     * 
     * @return Number of connections in keep-alive mode
     */    
    public int getCountConnections() {
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

    public int getMaxKeepAliveRequests() {
        return maxKeepAliveRequests;
    }


    /**
     * Set the maximum number of Keep-Alive requests that we will honor.
     */
    public void setMaxKeepAliveRequests(int maxKeepAliveRequests) {
        this.maxKeepAliveRequests = maxKeepAliveRequests;
    }

    /**
     * Sets the number of seconds before a keep-alive connection that has
     * been idle times out and is closed.
     *
     * @param timeout Keep-alive timeout in number of seconds
     */
    public void setKeepAliveTimeoutInSeconds(int timeout) {
        keepAliveTimeoutInSeconds = timeout;
    }


    /**
     * Gets the number of seconds before a keep-alive connection that has
     * been idle times out and is closed.
     *
     * @return Keep-alive timeout in number of seconds
     */
    public int getKeepAliveTimeoutInSeconds() {
        return keepAliveTimeoutInSeconds;
    }
}
