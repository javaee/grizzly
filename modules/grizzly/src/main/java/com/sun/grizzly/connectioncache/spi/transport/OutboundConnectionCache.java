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
import java.io.IOException ;

/** A concurrent mostly non-blocking connection cache.  Here a Connection is an 
 * abstraction of a Socket or SocketChannel: basically some sort of resource 
 * that is expensive to acquire, and can be re-used freely.  The cache 
 * maintains a loose upper bound on the number of cached connections, and 
 * reclaims connections as needed.
 * <P>
 * This cache places minimal requirements on the Connections that it contains:
 * <ol>
 * <li>A Connection must implement a close() method.  This is called when idle
 * connections are reclaimed.
 * <li>A Connection must be usable as a HashMap key.  
 * <li>There must be a ContactInfo class that is used to create Connection 
 * instances.  The ContactInfo class must support a create() method that 
 * returns a Connection.
 * <li>The ContactInfo must be usable as a HashMap key.
 * <li>All instances created from a ContactInfo are equal; that is, any request
 * sent to a particular ContactInfo can used an instance created from that 
 * ContactInfo.  For example, in the CORBA case, IP host and port is not always
 * sufficient: we may also need the Codeset type that indicates how Strings are
 * encoded.  Basically, protocols (like GIOP) that bind session state to a 
 * Connection may need more than transport information in the ContactInfo.
 * </ol>
 * <P>
 * Some simple methods are provided for monitoring the state of the cache:
 * numbers of busy and idle connections, and the total number of connections in 
 * the cache.
 * 
 * @param C a connection
 */
public interface OutboundConnectionCache<C extends Closeable> 
    extends ConnectionCache<C> {
    /** Configured maximum number of connections supported per ContactInfo.
     * @return  maximum number of connections supported per <code>ContactInfo</code>
     */
    int maxParallelConnections() ;

    /** Determine whether a new connection could be created by the
     * ConnectionCache or not.
     * @param cinfo  a <code>ContactInfo</code>
     * @return  true if new connection could be created by {@link ConnectionCache}, otherwise false.
     */
    boolean canCreateNewConnection( ContactInfo<C> cinfo ) ;

    /** Return a Connection corresponding to the given ContactInfo.
     * This works as follows:
     * <ul>
     * <li>Call the finder.  If it returns non-null, use that connection;
     * (Note that this may be a new connection, created in the finder)
     * <li>otherwise, Use an idle connection, if one is available; 
     * <li>otherwise, create a new connection, if not too many connections are 
     * open;
     * <li>otherwise, use a busy connection.
     * </ul>
     * Note that creating a new connection requires EITHER:
     * <ul>
     * <li>there is no existing connection for the ContactInfo 
     * <li> OR the total number of connections in the cache is less than the
     * HighWaterMark and the number of connections for this ContactInfo
     * is less than MaxParallelConnections.  
     * </ul>
     * We will always return a 
     * Connection for a get call UNLESS we have no existing connection and
     * an attempt to create a new connection fails.  In this case, the
     * IOException thrown by ContactInfo.create is propagated out of this
     * method.
     * <P>
     * It is possible that the cache contains connections that no longer connect
     * to their destination.  In this case, it is the responsibility of the 
     * client of the cache to close the broken connection as they are detected.
     * Connection reclamation may also handle the cleanup, but note that a 
     * broken connection with pending responses will never be reclaimed.
     * <P>
     * Note that the idle and busy connection collections that are
     * passed to the finder are unmodifiable collections.  They have iterators 
     * that return connections in LRU order, with the least recently used 
     * connection first.  This is done to aid a finder that wishes to consider 
     * load balancing in its determination of an appropriate connection.
     * <P>
     * @param cinfo  a <code>ContactInfo</code>
     * @param finder  a <code>ConnectionFinder</code>
     * @return  a connection
     * @throws java.io.IOException 
     */
    C get( ContactInfo<C> cinfo, ConnectionFinder<C> finder 
	) throws IOException ;

    /** Behaves the same as get( ContactInfo<C>, ConnectionFinder<C> ) 
     * except that no connection finder is provided, so that step is
     * ignored. 
     * @param cinfo  a <code>ContactInfo</code>
     * @return  a connection
     * @throws java.io.IOException 
     */
    C get( ContactInfo<C> cinfo ) throws IOException ;

    /** Release a Connection previously obtained from get.  Connections that
     * have been released as many times as they have been returned by
     * get are idle; otherwise a Connection is busy.  Some number of
     * responses (usually 0 or 1) may be expected ON THE SAME CONNECTION
     * even for an idle connection.  We maintain a count of the number of 
     * outstanding responses we expect for protocols that return the response
     * on the same connection on which the request was received.  This is
     * necessary to prevent reclamation of a Connection that is idle, but
     * still needed to send responses to old requests.
     * @param conn  a connection
     * @param numResponseExpected  number of connections in which a response is expected
     */
    void release( C conn, int numResponseExpected ) ;

    /** Inform the cache that a response has been received on a particular
     * connection.  This must also be called in the event that no response
     * is received, but the client times out waiting for a response, and
     * decides to abandon the request.
     * <P>
     * When a Connection is idle, and has no pending responses, it is
     * eligible for reclamation.
     * @param conn  a connection
     */
    void responseReceived( C conn ) ;
}
