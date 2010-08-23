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

import java.io.Closeable;

/** A connection cache manages a group of connections which may be re-used
 * for sending and receiving messages.
 * @param C  a connection
 */
public interface ConnectionCache<C extends Closeable> {
    /** User-provided indentifier for an instance of the 
     * {@link ConnectionCache}.
     * @return  a <code>String</code> identifying an instance of a {@link ConnectionCache}
     */
    String getCacheType() ;

    /** Total number of connections currently managed by the cache.
     * @return  number of connections currently managed by the cache
     */
    long numberOfConnections() ;

    /** Number of idle connections; that is, connections for which the number of
     * get/release or responseReceived/responseProcessed calls are equal.
     * @return  number of idle connections
     */
    long numberOfIdleConnections() ;

    /** Number of non-idle connections.  Normally, busy+idle==total, but this
     * may not be strictly true due to concurrent updates to the connection 
     * cache.
     * @return  number of busy connections
     */
    long numberOfBusyConnections() ;

    /** Number of idle connections that are reclaimable.  Such connections
     * are not in use, and are not waiting to handle any responses.
     * @return  number of idle connections that are reclaimable
     */
    long numberOfReclaimableConnections() ;

    /** Threshold at which connection reclamation begins.
     * @return  threshold at which connection reclamation begins.
     */
    int highWaterMark() ;

    /** Number of connections to reclaim each time reclamation starts.
     * @return  number of connections to reclaim
     */
    int numberToReclaim() ;
    
    /** Close a connection, regardless of its state.  This may cause requests
     * to fail to be sent, and responses to be lost.  Intended for 
     * handling serious errors, such as loss of framing on a TCP stream,
     * that require closing the connection.
     * @param conn  a connection
     */
    void close( final C conn ) ;
}
