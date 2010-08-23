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

package com.sun.grizzly.connectioncache.spi.transport;

import com.sun.grizzly.connectioncache.impl.transport.InboundConnectionCacheBlockingImpl;
import com.sun.grizzly.connectioncache.impl.transport.OutboundConnectionCacheBlockingImpl;
import java.io.Closeable;
import java.util.logging.Logger ;

/** A factory class for creating connections caches.
 * Note that a rather unusual syntax is needed for calling these methods:
 *
 * ConnectionCacheFactory.<V>makeXXXCache() 
 *
 * This is required because the type variable V is not used in the
 * parameters of the factory method (there are no parameters).
 */
public final class ConnectionCacheFactory {
    private ConnectionCacheFactory() {}

    public static <C extends Closeable> OutboundConnectionCache<C>
    makeBlockingOutboundConnectionCache( String cacheType, int highWaterMark,
	int numberToReclaim, int maxParallelConnections, Logger logger ) {

	return new OutboundConnectionCacheBlockingImpl<C>( cacheType, highWaterMark,
	    numberToReclaim, maxParallelConnections, logger ) ;
    }

    public static <C extends Closeable> InboundConnectionCache<C>
    makeBlockingInboundConnectionCache( String cacheType, int highWaterMark,
	int numberToReclaim, Logger logger ) {
	return new InboundConnectionCacheBlockingImpl<C>( cacheType,
	    highWaterMark, numberToReclaim, logger ) ;
    }
}
