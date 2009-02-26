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
package com.sun.grizzly.http;



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

    private int countConnections;
    private int maxConnections;
    private int countHits;
    private int countFlushes;
    private int countRefusals;
    private int countTimeouts;
    private int secondsTimeouts;


    /** 
     * Gets the number of connections in keep-alive mode.
     * 
     * @return Number of connections in keep-alive mode
     */    
    public synchronized int getCountConnections() {
        return countConnections;
    }

    
    /** 
     * Increments the number of connections in keep-alive mode.
     */    
    public synchronized void incrementCountConnections() {
        countConnections++;
    }
    
    
    /** 
     * Decrement the number of connections in keep-alive mode.
     */    
    protected synchronized void decrementCountConnections() {
        countConnections--;
    }

    
    /** 
     * Sets the maximum number of concurrent connections in keep-alive mode.
     *
     * @param maxConnections Maximum number of concurrent connections in
     * keep-alive mode.
     */    
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }  


    /** 
     * Gets the maximum number of concurrent connections in keep-alive mode.
     *
     * @return Maximum number of concurrent connections in keep-alive mode
     */    
    public int getMaxConnections() {
        return maxConnections;
    }

    
    /** 
     * Gets the number of requests received by connections in keep-alive mode.
     *
     * @return Number of requests received by connections in keep-alive mode.
     */    
    public synchronized int getCountHits() {
        return countHits;
    }


    /** 
     * Increments the number of requests received by connections in
     * keep-alive mode.
     */    
    public synchronized void incrementCountHits() {
        countHits++;
    }

    
    /** 
     * Gets the number of keep-alive connections that were closed
     *
     * @return Number of keep-alive connections that were closed
     */    
    public synchronized int getCountFlushes() {
        return countFlushes;
    }

    
    /** 
     * Increments the number of keep-alive connections that were closed
     */    
    public synchronized void incrementCountFlushes() {
        countFlushes++;
    }


    /** 
     * Gets the number of keep-alive connections that were rejected.
     *
     * @return Number of keep-alive connections that were rejected.
     */    
    public synchronized int getCountRefusals() {
        return countRefusals;
    }
    

    /** 
     * Increments the number of keep-alive connections that were rejected.
     */    
    public synchronized void incrementCountRefusals() {
        countRefusals++;
    }


    /** 
     * Gets the number of keep-alive connections that timed out.
     *
     * @return Number of keep-alive connections that timed out.
     */    
    public synchronized int getCountTimeouts() {
        return countTimeouts;
    }

    
    /** 
     * Increments the number of keep-alive connections that timed out.
     */    
    public synchronized void incrementCountTimeouts() {
        countTimeouts++;
    }


    /** 
     * Sets the number of seconds before a keep-alive connection that has
     * been idle times out and is closed.
     *
     * @param timeout Keep-alive timeout in number of seconds
     */    
    public void setSecondsTimeouts(int timeout) {
        secondsTimeouts = timeout;
    }


    /** 
     * Gets the number of seconds before a keep-alive connection that has
     * been idle times out and is closed.
     *
     * @return Keep-alive timeout in number of seconds
     */    
    public int getSecondsTimeouts() {
        return secondsTimeouts;
    } 
}
