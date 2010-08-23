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

package com.sun.grizzly.connectioncache.impl.transport;

import com.sun.grizzly.connectioncache.spi.concurrent.ConcurrentQueue;
import com.sun.grizzly.connectioncache.spi.transport.InboundConnectionCache;

import java.io.Closeable;
import java.io.IOException ;

import java.util.logging.Logger ;

import java.util.Map ;
import java.util.HashMap ;

/** Manage connections that are initiated from another VM.
 *
 * @param C  a connection
 * @author Ken Cavanaugh
 */
public final class InboundConnectionCacheBlockingImpl<C extends Closeable>
        extends ConnectionCacheBlockingBase<C>
        implements InboundConnectionCache<C> {
    
    private final Map<C,ConnectionState<C>> connectionMap ;
    
    protected String thisClassName() {
        return "InboundConnectionCacheBlockingImpl" ;
    }
    
    private static final class ConnectionState<C extends Closeable> {
        final C connection ;		// Connection of the
        // ConnectionState
        int busyCount ;			// Number of calls to
        // get without release
        int expectedResponseCount ;	// Number of expected
        // responses not yet
        // received
        
        ConcurrentQueue.Handle<C> reclaimableHandle ;  // non-null iff connection
        // is not in use and has no
        // outstanding requests
        
        ConnectionState( final C conn ) {
            this.connection = conn ;
            
            busyCount = 0 ;
            expectedResponseCount = 0 ;
            reclaimableHandle = null ;
        }
    }
    
    public InboundConnectionCacheBlockingImpl( final String cacheType,
            final int highWaterMark, final int numberToReclaim,
            Logger logger ) {
        
        super( cacheType, highWaterMark, numberToReclaim, logger ) ;
        
        this.connectionMap = new HashMap<C,ConnectionState<C>>() ;
        
        if (debug()) {
            dprint(".constructor completed: " + getCacheType() );
        }
    }
    
    // We do not need to define equals or hashCode for this class.
    
    public synchronized void requestReceived( final C conn ) {
        if (debug())
            dprint( "->requestReceived: connection " + conn ) ;
        
        try {
            ConnectionState<C> cs = getConnectionState( conn ) ;
            
            final int totalConnections = totalBusy + totalIdle ;
            if (totalConnections > highWaterMark())
                reclaim() ;
            
            ConcurrentQueue.Handle<C> reclaimHandle = cs.reclaimableHandle ;
            if (reclaimHandle != null) {
                if (debug())
                    dprint( ".requestReceived: " + conn
                            + " removed from reclaimableQueue" ) ;
                reclaimHandle.remove() ;
            }
            
            int count = cs.busyCount++ ;
            if (count == 0) {
                if (debug())
                    dprint( ".requestReceived: " + conn
                            + " transition from idle to busy" ) ;
                
                totalIdle-- ;
                totalBusy++ ;
            }
        } finally {
            if (debug())
                dprint( "<-requestReceived: connection " + conn ) ;
        }
    }
    
    public synchronized void requestProcessed( final C conn,
            final int numResponsesExpected ) {
        
        if (debug())
            dprint( "->requestProcessed: connection " + conn
                    + " expecting " + numResponsesExpected + " responses" ) ;
        
        try {
            final ConnectionState<C> cs = connectionMap.get( conn ) ;
            
            if (cs == null) {
                if (debug())
                    dprint( ".release: connection " + conn + " was closed" ) ;
                
                return ;
            } else {
                cs.expectedResponseCount += numResponsesExpected ;
                int numResp = cs.expectedResponseCount ;
                int numBusy = --cs.busyCount ;
                
                if (debug()) {
                    dprint( ".release: " + numResp + " responses expected" ) ;
                    dprint( ".release: " + numBusy +
                            " busy count for connection" ) ;
                }
                
                if (numBusy == 0) {
                    totalBusy-- ;
                    totalIdle++ ;
                    
                    if (numResp == 0) {
                        if (debug())
                            dprint( ".release: "
                                    + "queuing reclaimable connection "
                                    + conn ) ;
                        
                        if ((totalBusy+totalIdle) > highWaterMark()) {
                            close( conn ) ;
                        } else {
                            cs.reclaimableHandle =
                                    reclaimableConnections.offer( conn ) ;
                        }
                    }
                }
            }
        } finally {
            if (debug())
                dprint( "<-requestProcessed" ) ;
        }
    }
    
    /** Decrement the number of expected responses.  When a connection is idle
     * and has no expected responses, it can be reclaimed.
     * @param conn  a connection
     */
    public synchronized void responseSent( final C conn ) {
        if (debug())
            dprint( "->responseSent: " + conn ) ;
        
        try {
            final ConnectionState<C> cs = connectionMap.get( conn ) ;
            if (cs == null) {
                if (debug())
                    dprint( ".release: connection " + conn + " was closed" ) ;
                
                return ;
            }
            
            final int waitCount = --cs.expectedResponseCount ;
            if (waitCount == 0) {
                if (debug())
                    dprint( ".responseSent: " + conn + " is now reclaimable" ) ;
                
                if ((totalBusy+totalIdle) > highWaterMark()) {
                    if (debug()) {
                        dprint( ".responseSent: " + conn
                                + " closing connection" ) ;
                    }
                    close( conn ) ;
                } else {
                    cs.reclaimableHandle =
                            reclaimableConnections.offer( conn ) ;
                    
                    if (debug()) {
                        dprint( ".responseSent: " + conn
                                + " is now reclaimable" ) ;
                    }
                }
            } else {
                if (debug()) {
                    dprint( ".responseSent: " + conn + " waitCount="
                            + waitCount ) ;
                }
            }
        } finally {
            if (debug()) {
                dprint( "<-responseSent: " + conn ) ;
            }
        }
    }
    
    /** Close a connection, regardless of whether the connection is busy
     * or not.
     * @param conn  a connection
     */
    public synchronized void close( final C conn ) {
        if (debug())
            dprint( "->close: " + conn ) ;
        
        try {
            final ConnectionState<C> cs = connectionMap.remove( conn ) ;
            if (cs != null) {
                int count = cs.busyCount ;
                if (debug())
                    dprint( ".close: " + conn + " count = " + count ) ;
                
                if (count == 0)
                    totalIdle-- ;
                else
                    totalBusy-- ;
                
                final ConcurrentQueue.Handle rh = cs.reclaimableHandle ;
                if (rh != null) {
                    if (debug())
                        dprint( ".close: " + conn + " connection was reclaimable" ) ;
                    
                    rh.remove() ;
                }
                
                try {
                    conn.close() ;
                } catch (IOException exc) {
                    if (debug())
                        dprint( ".close: " + conn + " close threw "  + exc ) ;
                }
            }
        } finally {
            if (debug())
                dprint( "<-close: " + conn ) ;
        }
    }
    
    // Atomically either get the ConnectionState for conn OR
    // create a new one AND put it in the cache
    private ConnectionState<C> getConnectionState( C conn ) {
        // This should be the only place a CacheEntry is constructed.
        if (debug())
            dprint( "->getConnectionState: " + conn ) ;
        
        try {
            ConnectionState<C> result = connectionMap.get( conn ) ;
            if (result == null) {
                if (debug())
                    dprint( ".getConnectionState: " + conn +
                            " creating new ConnectionState instance" ) ;
                result = new ConnectionState<C>( conn ) ;
                connectionMap.put( conn, result ) ;
                totalIdle++ ;
            }
            
            return result ;
        } finally {
            if (debug())
                dprint( "<-getConnectionState: " + conn ) ;
        }
    }
}

// End of file.
