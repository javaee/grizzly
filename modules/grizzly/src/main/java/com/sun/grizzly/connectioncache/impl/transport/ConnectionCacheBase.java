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
import com.sun.grizzly.connectioncache.spi.transport.ConnectionCache;
import java.io.Closeable;
import java.util.logging.Logger ;
import java.util.logging.Level ;


public abstract class ConnectionCacheBase<C extends Closeable> 
    implements ConnectionCache<C> {

    // A name for this instance, provided for convenience.
    private final String cacheType ;

    // Log to this logger if FINER is enabled.
    protected final Logger logger ;

    // Configuration data
    // XXX we may want this data to be dynamically re-configurable
    private final int highWaterMark ;		// Maximum number of 
						// connections before we start 
						// closing idle connections
    private final int numberToReclaim ;		// How many connections to 
						// reclaim at once

    // MUST be initialized in a subclass
    protected ConcurrentQueue<C> reclaimableConnections = null ;

    protected boolean debug() {
	return logger.isLoggable( Level.FINER ) ;
    }

    public final String getCacheType() {
	return cacheType ;
    }

    public final int numberToReclaim() {
	return numberToReclaim ;
    }

    public final int highWaterMark() {
	return highWaterMark ;
    }

    // The name of this class, which is implemented in the subclass.
    // I could derive this from this.getClass().getClassName(), but
    // this is easier.
    protected abstract String thisClassName() ;

    ConnectionCacheBase( final String cacheType, 
	final int highWaterMark, final int numberToReclaim, 
	final Logger logger ) {

	if (cacheType == null)
	    throw new IllegalArgumentException( "cacheType must not be null" ) ;

	if (highWaterMark < 0)
	    throw new IllegalArgumentException( "highWaterMark must be non-negative" ) ;

	if (numberToReclaim < 1)
	    throw new IllegalArgumentException( "numberToReclaim must be at least 1" ) ;

	if (logger == null)
	    throw new IllegalArgumentException( "logger must not be null" ) ;

	this.cacheType = cacheType ;
	this.logger = logger ;
	this.highWaterMark = highWaterMark ;
	this.numberToReclaim = numberToReclaim ;
    }
    
    protected final void dprint(final String msg) {
	logger.finer(thisClassName() + msg);
    }

    public String toString() {
	return thisClassName() + "[" 
	    + getCacheType() + "]";
    }
     
    public void dprintStatistics() {
	dprint( ".stats:"
	       + " idle=" + numberOfIdleConnections() 
	       + " reclaimable=" + numberOfReclaimableConnections() 
	       + " busy=" + numberOfBusyConnections() 
	       + " total=" + numberOfConnections() 
	       + " (" 
	       + highWaterMark() + "/"
	       + numberToReclaim() 
	       + ")");
    }

    /** Reclaim some idle cached connections.  Will never 
     * close a connection that is busy.
     * @return  any connections reclaimed, (yes or no)
     */
    protected boolean reclaim() {
	if (debug())
	    dprint( ".reclaim: starting" ) ;

	int ctr = 0 ;
	while (ctr < numberToReclaim()) {
	    C candidate = reclaimableConnections.poll() ;
	    if (candidate == null)
		// If we have closed all idle connections, we must stop 
		// reclaiming.
		break ;

	    if (debug())
		dprint( ".reclaim: closing connection " + candidate ) ;

	    try {
		close( candidate ) ;	    
	    } catch (RuntimeException exc) {
		if (debug())
		    dprint( ".reclaim: caught exception on close: " + exc ) ;
		throw exc ;
	    }

	    ctr++ ;
	}

	if (debug())
	    dprint( ".reclaim: reclaimed " + ctr + " connection(s)" ) ;

	return ctr > 0 ;
    }
}
