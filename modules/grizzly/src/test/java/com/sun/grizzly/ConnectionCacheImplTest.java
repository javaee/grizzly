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

package com.sun.grizzly ;

import com.sun.grizzly.connectioncache.spi.concurrent.ConcurrentQueue;
import com.sun.grizzly.connectioncache.spi.concurrent.ConcurrentQueueFactory;
import com.sun.grizzly.connectioncache.spi.transport.ConnectionCache;
import com.sun.grizzly.connectioncache.spi.transport.ConnectionCacheFactory;
import com.sun.grizzly.connectioncache.spi.transport.ConnectionFinder;
import com.sun.grizzly.connectioncache.spi.transport.ContactInfo;
import com.sun.grizzly.connectioncache.spi.transport.InboundConnectionCache;
import com.sun.grizzly.connectioncache.spi.transport.OutboundConnectionCache;
import java.io.Closeable;
import java.util.Collection ;
import java.util.List ;
import java.util.ArrayList ;
import java.util.Set ;
import java.util.HashSet ;

import java.util.concurrent.ConcurrentMap ;
import java.util.concurrent.ConcurrentHashMap ;

import java.util.concurrent.atomic.AtomicBoolean ;
import java.util.concurrent.atomic.AtomicLong ;

import java.util.logging.Logger ;
import java.util.logging.Handler ;
import java.util.logging.Level ;
import java.util.logging.StreamHandler ;
import java.util.logging.Formatter ;
import java.util.logging.LogRecord ;

import java.io.IOException ;

import com.sun.grizzly.util.Utils;
import junit.framework.TestCase;

/**
 * Connection Cache implementation test
 *
 * @author Ken Cavanaugh
 */
public class ConnectionCacheImplTest extends TestCase {
    // Ignore all of the LogRecord information except the message.
    public static class ReallySimpleFormatter extends Formatter {
        public synchronized String format( LogRecord record ) {
            return record.getMessage() + "\n" ;
        }
    }
    
    // Similar to ConsoleHandler, but outputs to System.out
    // instead of System.err, which works better with the
    // CORBA test framework.
    public static class SystemOutputHandler extends StreamHandler {
        public SystemOutputHandler() {
            try {
                setLevel(Level.FINER);
                setFilter(null);
                setFormatter(new ReallySimpleFormatter());
                setEncoding(null);
                setOutputStream(System.out);
            } catch (Exception exc) {
                Utils.dumpOut( "Caught unexpected exception " + exc ) ;
            }
        }
        
        public void publish(LogRecord record) {
            super.publish(record);
            flush();
        }
        
        public void close() {
            flush();
        }
    }
    
    private static final boolean DEBUG = false;
    
    private static final Logger logger = Logger.getLogger( "test.corba" ) ;
    private static final Handler handler = new SystemOutputHandler() ;
    
    static {
        if (DEBUG) {
            logger.setLevel( Level.FINER ) ;
            logger.addHandler( handler ) ;
        }
    }
    
    private static final int NUMBER_TO_RECLAIM = 1 ;
    private static final int MAX_PARALLEL_CONNECTIONS = 4 ;
    private static final int HIGH_WATER_MARK = 20 ; // must be a multiple of
    // MAX_PARALLEL_CONNECTIONS
    // for outboundCacheTest4.
    
    private static final OutboundConnectionCache<ConnectionImpl> obcache =
            ConnectionCacheFactory.<ConnectionImpl>
            makeBlockingOutboundConnectionCache(
            "BlockingOutboundCache", HIGH_WATER_MARK, NUMBER_TO_RECLAIM,
            MAX_PARALLEL_CONNECTIONS, logger ) ;
    
    private static final InboundConnectionCache<ConnectionImpl> ibcache =
            ConnectionCacheFactory.<ConnectionImpl>
            makeBlockingInboundConnectionCache(
            "BlockingInboundCache", HIGH_WATER_MARK, NUMBER_TO_RECLAIM,
            logger ) ;
    
    // A simple implementation of Connection for testing.  No
    // synchronization is required to use this class.
    public static class ConnectionImpl implements Closeable {
        private final String name ;
        private final long id ;
        private final ContactInfoImpl cinfo ;
        private final AtomicBoolean isClosed ;
        
        public ConnectionImpl( String name, long id, ContactInfoImpl cinfo ) {
            this.name = name ;
            this.id = id ;
            this.cinfo = cinfo ;
            this.isClosed = new AtomicBoolean() ;
        }
        
        public ContactInfoImpl getContactInfo() {
            return cinfo ;
        }
        
        // Simulate access (read/write) to a connection to make sure
        // we do not access a closed connection.
        public void access() {
            if (isClosed.get())
                throw new RuntimeException( "Illegal access: connection "
                        + name + " is closed." ) ;
        }
        
        public void close() {
            boolean wasClosed = isClosed.getAndSet( true ) ;
            if (wasClosed)
                throw new RuntimeException(
                        "Attempting to close connection " ) ;
        }
        
        public String toString() {
            return "ConnectionImpl[" + name + ":" + id + "]" ;
        }
    }
    
    // XXX Do we need to list all ContactInfos?
    // XXX Do we need to list all connections created from a ContactInfo?
    public static class ContactInfoImpl implements ContactInfo<ConnectionImpl> {
        private final String address ;
        
        private static final AtomicLong nextId =
                new AtomicLong() ;
        private final AtomicBoolean simulateAddressUnreachable =
                new AtomicBoolean() ;
        
        private static final ConcurrentMap<String,ContactInfoImpl> cinfoMap =
                new ConcurrentHashMap<String,ContactInfoImpl>() ;
        
        private ContactInfoImpl( String address ) {
            this.address = address ;
        }
        
        public static ContactInfoImpl get( String address ) {
            ContactInfoImpl result = new ContactInfoImpl( address ) ;
            ContactInfoImpl entry = cinfoMap.putIfAbsent( address, result ) ;
            if (entry == null)
                return result ;
            else {
                entry.clear();
                return entry ;
            }
        }
        
        public void remove( String address ) {
            cinfoMap.remove( address ) ;
        }
        
        public void setUnreachable( boolean arg ) {
            simulateAddressUnreachable.set( arg ) ;
        }
        
        public ConnectionImpl createConnection() throws IOException {
            if (simulateAddressUnreachable.get()) {
                throw new IOException( "Address " + address
                        + " is currently unreachable" ) ;
            } else {
                long id = nextId.getAndIncrement() ;
                return new ConnectionImpl( address, id, this);
            }
        }
        
        public void clear() {
            simulateAddressUnreachable.set(false);
        }
        
        public String toString() {
            return "ContactInfoImpl[" + address + "]" ;
        }
    }
    
    private void banner( String msg ) {
        if (DEBUG) {
            Utils.dumpOut("=====================================================================" ) ;
            Utils.dumpOut( msg ) ;
            Utils.dumpOut("=====================================================================" ) ;
        }
    }
    
    // Test ConcurrentQueue
    public void testNonBlockingConcurrentQueueTest() {
        banner( "nonBlockingConccurentQueueTest" ) ;
        ConcurrentQueue<Integer> testQ =
                ConcurrentQueueFactory.<Integer>makeConcurrentQueue() ;
        testConcurrentQueue( testQ ) ;
    }
    
    private ConcurrentQueue.Handle<Integer> addData(
            ConcurrentQueue<Integer> arg,
            int[] data, int valueForHandleToReturn ) {
        
        ConcurrentQueue.Handle<Integer> savedHandle = null ;
        
        for (int val : data) {
            ConcurrentQueue.Handle<Integer> handle = arg.offer( val ) ;
            if (val == valueForHandleToReturn) {
                savedHandle = handle ;
            }
        }
        
        return savedHandle ;
    }
    
    private void destructiveValidation( ConcurrentQueue<Integer> queue,
            int[] master ) {
        
        assertEquals( queue.size(), master.length ) ;
        for (int val : master) {
            int qval = queue.poll() ;
            assertEquals( val, qval ) ;
        }
    }
    
    private void testConcurrentQueue( ConcurrentQueue<Integer> arg ) {
        final int[] data = { 23, 43, 51, 3, 7, 9, 22, 33 } ;
        final int valueToDelete = 51 ;
        final int[] dataAfterDelete = { 23, 43, 3, 7, 9, 22, 33 } ;
        
        addData( arg, data, valueToDelete ) ;
        
        destructiveValidation( arg, data ) ;
        
        final ConcurrentQueue.Handle<Integer> delHandle =
                addData( arg, data, valueToDelete ) ;
        
        delHandle.remove() ;
        
        destructiveValidation( arg, dataAfterDelete ) ;
    }
    
    private void checkStat( long actual, long expected, String type ) {
        assertEquals(type,  actual, expected) ;
    }
    
    private void checkStats( ConnectionCache cc, int idle, int reclaimable,
            int busy, int total ) {
        
        checkStat( cc.numberOfIdleConnections(), idle,
                "Idle connections" ) ;
        checkStat( cc.numberOfReclaimableConnections(), reclaimable,
                "Reclaimable connections" ) ;
        checkStat( cc.numberOfBusyConnections(), busy,
                "Busy connections" ) ;
        checkStat( cc.numberOfConnections(), total,
                "Total connections" ) ;
    }
    
    // Each of the simple tests expects that all connections in the cache have
    // been closed, which may not be the case if a test fails.  So we impose
    // an order on the tests to make sure that we stop if a test leaves things
    // in a bad state.
    //
    // Inbound and Outbound can be tested independently.
    
    // Do a single get/release/responseReceived cycle
    public void testOutboundTest1() throws IOException {
        banner( "outboundTest1: single cycle" ) ;
        ContactInfoImpl cinfo = ContactInfoImpl.get( "FirstContact" ) ;
        ConnectionImpl c1 = obcache.get( cinfo ) ;
        checkStats( obcache, 0, 0, 1, 1 ) ;
        
        obcache.release( c1, 1 ) ;
        checkStats( obcache, 1, 0, 0, 1 ) ;
        
        obcache.responseReceived( c1 ) ;
        checkStats( obcache, 1, 1, 0, 1 ) ;
        
        obcache.close( c1 ) ;
        checkStats( obcache, 0, 0, 0, 0 ) ;
    }
    
    // Do two interleaved get/release/responseReceived cycles
    public void testOutboundTest2() throws IOException {
        banner( "outboundTest2: 2 cycles interleaved" ) ;
        ContactInfoImpl cinfo = ContactInfoImpl.get( "FirstContact" ) ;
        ConnectionImpl c1 = obcache.get( cinfo ) ;
        checkStats( obcache, 0, 0, 1, 1 ) ;
        
        ConnectionImpl c2 = obcache.get( cinfo ) ;
        checkStats( obcache, 0, 0, 2, 2 ) ;
        
        assertNotSame( c1, c2) ;
        
        obcache.release( c1, 1 ) ;
        checkStats( obcache, 1, 0, 1, 2 ) ;
        
        obcache.release( c2, 1 ) ;
        checkStats( obcache, 2, 0, 0, 2 ) ;
        
        obcache.responseReceived( c1 ) ;
        checkStats( obcache, 2, 1, 0, 2 ) ;
        
        obcache.responseReceived( c2 ) ;
        checkStats( obcache, 2, 2, 0, 2 ) ;
        
        obcache.close( c2 ) ;
        checkStats( obcache, 1, 1, 0, 1 ) ;
        
        obcache.close( c1 ) ;
        checkStats( obcache, 0, 0, 0, 0 ) ;
    }
    
    // Do enough gets to start using busy connections.
    public void testOutboundTest3() throws IOException {
        banner( "outboundTest3: cycle to busy connections" ) ;
        ContactInfoImpl cinfo = ContactInfoImpl.get( "FirstContact" ) ;
        Set<ConnectionImpl> conns = new HashSet<ConnectionImpl>() ;
        for (int ctr=0; ctr<MAX_PARALLEL_CONNECTIONS; ctr++) {
            ConnectionImpl conn = obcache.get( cinfo ) ;
            conns.add( conn ) ;
        }
        assertEquals("Connections after add", conns.size(), MAX_PARALLEL_CONNECTIONS);
        checkStats( obcache, 0, 0, 4, 4 ) ;
        
        ConnectionImpl c1 = obcache.get( cinfo ) ;
        assertTrue("Expect connection c1 is already in conns", conns.contains( c1 ));
        checkStats( obcache, 0, 0, 4, 4 ) ;
        
        for (ConnectionImpl conn : conns ) {
            obcache.release( conn, 1 ) ;
        }
        checkStats( obcache, 3, 0, 1, 4 ) ;
        
        for (ConnectionImpl conn : conns ) {
            obcache.responseReceived( conn ) ;
        }
        checkStats( obcache, 3, 3, 1, 4 ) ;
        
        obcache.release( c1, 0 ) ;
        checkStats( obcache, 4, 4, 0, 4 ) ;
        
        for (ConnectionImpl conn : conns ) {
            obcache.close( conn ) ;
        }
        checkStats( obcache, 0, 0, 0, 0 ) ;
    }
    
    // Do enough gets on enough ContactInfos to start reclaiming
    public void testOutboundTest4() throws IOException {
        banner( "outboundTest4: test reclamation" ) ;
        final int numContactInfo = HIGH_WATER_MARK/MAX_PARALLEL_CONNECTIONS;
        final List<ContactInfoImpl> cinfos = new ArrayList<ContactInfoImpl>() ;
        for (int ctr=0; ctr<numContactInfo; ctr++) {
            cinfos.add( ContactInfoImpl.get( "ContactInfo" + ctr ) ) ;
        }
        final ContactInfoImpl overcinfo =
                ContactInfoImpl.get( "OverflowContactInfo" ) ;
        
        // Open up HIGH_WATER_MARK total connections
        List<HashSet<ConnectionImpl>> csa =
                new ArrayList<HashSet<ConnectionImpl>>() ;
        for (int ctr=0; ctr<numContactInfo; ctr++) {
            HashSet<ConnectionImpl> set = new HashSet<ConnectionImpl>() ;
            csa.add( set ) ;
            for (int num=0; num<MAX_PARALLEL_CONNECTIONS; num++) {
                set.add( obcache.get( cinfos.get(ctr) ) ) ;
            }
        }
        
        checkStats( obcache, 0, 0, HIGH_WATER_MARK, HIGH_WATER_MARK ) ;
        
        // Now open up connection on so far unused ContactInfoImpl
        ConnectionImpl over = obcache.get( overcinfo ) ;
        checkStats( obcache, 0, 0, HIGH_WATER_MARK+1, HIGH_WATER_MARK+1 ) ;
        
        // Free the overflow connection, expecting a response
        obcache.release( over, 1 ) ;
        checkStats( obcache, 1, 0, HIGH_WATER_MARK, HIGH_WATER_MARK+1 ) ;
        
        // Get a response to free overflow conn: should close
        obcache.responseReceived( over ) ;
        checkStats( obcache, 0, 0, HIGH_WATER_MARK, HIGH_WATER_MARK ) ;
        
        // Again open up connection on so far unused ContactInfoImpl
        over = obcache.get( overcinfo ) ;
        checkStats( obcache, 0, 0, HIGH_WATER_MARK+1, HIGH_WATER_MARK+1 ) ;
        
        // Free the overflow connection, no response
        obcache.release( over, 0 ) ;
        checkStats( obcache, 0, 0, HIGH_WATER_MARK, HIGH_WATER_MARK ) ;
        
        // get overflow twice: should get same connection back second time
        ConnectionImpl over1 = obcache.get( overcinfo ) ;
        ConnectionImpl over2 = obcache.get( overcinfo ) ;
        checkStats( obcache, 0, 0, HIGH_WATER_MARK+1, HIGH_WATER_MARK+1 ) ;
        assertEquals("Connections from two overflow get calls", over1, over2);
        
        obcache.release( over2, 0 ) ;
        obcache.release( over1, 0 ) ;
        checkStats( obcache, 0, 0, HIGH_WATER_MARK, HIGH_WATER_MARK ) ;
        
        // Clean up everything: just close
        for (Set<ConnectionImpl> conns : csa ) {
            for (ConnectionImpl conn : conns) {
                obcache.close( conn ) ;
            }
        }
        checkStats( obcache, 0, 0, 0, 0 ) ;
    }
    
    // Test a ContactInfoImpl that throws an IOException
    public void testOutboundTest5() throws IOException {
        boolean exception = false;
        banner( "outboundTest5: test connection open error" ) ;
        ContactInfoImpl cinfo = ContactInfoImpl.get( "ExceptionTest" ) ;
        cinfo.setUnreachable( true ) ;
        try {
            obcache.get( cinfo ) ; // should throw an IOException
        } catch(IOException e) {
            exception = true;
        } finally {
            checkStats( obcache, 0, 0, 0, 0 ) ;
        }
        
        assertTrue("IOException should be thrown!", exception);
    }
    
    private static <V> V getSecondOrFirst( Collection<V> coll ) {
        V first = null ;
        int count = 0 ;
        for (V v : coll) {
            if (count == 0) {
                first = v ;
            } else if (count == 1) {
                return v ;
            } else {
                break ;
            }
            count++ ;
        }
        return first ;
    }
    
    // Several tests for ConnectionFinders
    private static final ConnectionFinder<ConnectionImpl> cf1 =
            new ConnectionFinder<ConnectionImpl>() {
        public ConnectionImpl find(ContactInfo<ConnectionImpl> cinfo, Collection<ConnectionImpl> idleConnections,
                Collection<ConnectionImpl> busyConnections) {

            return getSecondOrFirst( idleConnections ) ;
        }
    } ;
    
    private static final ConnectionFinder<ConnectionImpl> cf2 =
            new ConnectionFinder<ConnectionImpl>() {
        public ConnectionImpl find(ContactInfo<ConnectionImpl> cinfo, Collection<ConnectionImpl> idleConnections,
                Collection<ConnectionImpl> busyConnections) {

            return getSecondOrFirst( busyConnections ) ;
        }
    } ;
    
    private static final ConnectionFinder<ConnectionImpl> cf3 =
            new ConnectionFinder<ConnectionImpl>() {
        public ConnectionImpl find(
                ContactInfo<ConnectionImpl> cinfo,
                Collection<ConnectionImpl> idleConnections,
                Collection<ConnectionImpl> busyConnections
                ) throws IOException {
            
            return cinfo.createConnection() ;
        }
    } ;
    
    private static final ConnectionFinder<ConnectionImpl> cf4 =
            new ConnectionFinder<ConnectionImpl>() {
        public ConnectionImpl find(ContactInfo<ConnectionImpl> cinfo, Collection<ConnectionImpl> idleConnections,
                Collection<ConnectionImpl> busyConnections) {

            return null ;
        }
    } ;
    
    public void testOutboundTest6() throws IOException {
        banner( "outboundTest6: test ConnectionFinder non-error case" ) ;
        ContactInfoImpl cinfo = ContactInfoImpl.get( "CFTest" ) ;
        
        // Set up 2 idle and 2 busy connections on cinfo
        ConnectionImpl idle1 = obcache.get( cinfo ) ;
        ConnectionImpl idle2 = obcache.get( cinfo ) ;
        ConnectionImpl busy1 = obcache.get( cinfo ) ;
        ConnectionImpl busy2 = obcache.get( cinfo ) ;
        obcache.release( idle1, 0 ) ;
        obcache.release( idle2, 0 ) ;
        checkStats( obcache, 2, 2, 2, 4 ) ;
        
        // Test cf1
        ConnectionImpl test = obcache.get( cinfo, cf1 ) ;
        assertEquals( test, idle2 ) ;
        checkStats( obcache, 1, 1, 3, 4 ) ;
        obcache.release( test, 0 ) ;
        checkStats( obcache, 2, 2, 2, 4 ) ;
        
        // Test cf2
        test = obcache.get( cinfo, cf2 ) ;
        assertEquals( test, busy2 ) ;
        checkStats( obcache, 2, 2, 2, 4 ) ;
        obcache.release( test, 0 ) ;
        checkStats( obcache, 2, 2, 2, 4 ) ;
        obcache.release( busy2, 0 ) ;
        checkStats( obcache, 3, 3, 1, 4 ) ;
        
        // Test cf3
        test = obcache.get( cinfo, cf3 ) ;
        checkStats( obcache, 3, 3, 2, 5 ) ;
        obcache.release( test, 0 ) ;
        checkStats( obcache, 4, 4, 1, 5 ) ;
        obcache.close( test ) ;
        checkStats( obcache, 3, 3, 1, 4 ) ;
        
        // Test cf4
        test = obcache.get( cinfo, cf4 ) ;
        checkStats( obcache, 2, 2, 2, 4 ) ;
        obcache.release( test, 0 ) ;
        checkStats( obcache, 3, 3, 1, 4 ) ;
        
        obcache.close( idle1 ) ;
        obcache.close( idle2 ) ;
        obcache.close( busy1 ) ;
        obcache.close( busy2 ) ;
        checkStats( obcache, 0, 0, 0, 0 ) ;
        
        
    }
    
    public void testOutboundTest7() throws IOException {
        boolean exception = false;
        
        banner( "outboundTest7: test ConnectionFinder error case" ) ;
        ContactInfoImpl cinfo = ContactInfoImpl.get( "CFTest" ) ;
        cinfo.setUnreachable( true ) ;
        try {
            obcache.get(cinfo, cf3);
        } catch(IOException e) {
            exception = true;
        } finally {
            checkStats( obcache, 0, 0, 0, 0 ) ;
        }
        
        assertTrue("IOException should be thrown!", exception);
    }
    
    // Test inboundConnectionCache
    //
    // Do a single requestReceived/requestProcessed/responseSent cycle
    public void testInboundTest1() throws IOException {
        banner( "inboundTest1: single cycle" ) ;
        ContactInfoImpl cinfo = ContactInfoImpl.get( "FirstContact" ) ;
        ConnectionImpl c1 = cinfo.createConnection() ;
        ibcache.requestReceived( c1 ) ;
        checkStats( ibcache, 0, 0, 1, 1 ) ;
        
        ibcache.requestProcessed( c1, 1 ) ;
        checkStats( ibcache, 1, 0, 0, 1 ) ;
        
        ibcache.responseSent( c1 ) ;
        checkStats( ibcache, 1, 1, 0, 1 ) ;
        
        ibcache.close( c1 ) ;
        checkStats( ibcache, 0, 0, 0, 0 ) ;
    }
    
    // Do two interleaved requestReceived/requestProcessed/responseSent cycles
    public void testInboundTest2() throws IOException {
        banner( "inboundTest2: 2 cycles interleaved" ) ;
        ContactInfoImpl cinfo = ContactInfoImpl.get( "FirstContact" ) ;
        ConnectionImpl c1 = cinfo.createConnection() ;
        ibcache.requestReceived( c1 ) ;
        checkStats( ibcache, 0, 0, 1, 1 ) ;
        
        ConnectionImpl c2 = cinfo.createConnection() ;
        ibcache.requestReceived( c2 ) ;
        checkStats( ibcache, 0, 0, 2, 2 ) ;
        
        assertNotSame( c1, c2) ;
        
        ibcache.requestProcessed( c1, 1 ) ;
        checkStats( ibcache, 1, 0, 1, 2 ) ;
        
        ibcache.requestProcessed( c2, 1 ) ;
        checkStats( ibcache, 2, 0, 0, 2 ) ;
        
        ibcache.responseSent( c1 ) ;
        checkStats( ibcache, 2, 1, 0, 2 ) ;
        
        ibcache.responseSent( c2 ) ;
        checkStats( ibcache, 2, 2, 0, 2 ) ;
        
        ibcache.close( c2 ) ;
        checkStats( ibcache, 1, 1, 0, 1 ) ;
        
        ibcache.close( c1 ) ;
        checkStats( ibcache, 0, 0, 0, 0 ) ;
    }
    
    // Do enough gets on enough ContactInfos to start reclaiming
    public void testInboundTest3() throws IOException {
        banner( "inboundTest3: test reclamation" ) ;
        final int numContactInfo = HIGH_WATER_MARK/MAX_PARALLEL_CONNECTIONS;
        final List<ContactInfoImpl> cinfos = new ArrayList<ContactInfoImpl>() ;
        for (int ctr=0; ctr<numContactInfo; ctr++) {
            cinfos.add( ContactInfoImpl.get( "ContactInfo" + ctr ) ) ;
        }
        final ContactInfoImpl overcinfo =
                ContactInfoImpl.get( "OverflowContactInfo" ) ;
        
        // Open up HIGH_WATER_MARK total connections
        List<HashSet<ConnectionImpl>> csa =
                new ArrayList<HashSet<ConnectionImpl>>() ;
        for (int ctr=0; ctr<numContactInfo; ctr++) {
            ContactInfoImpl cinfo = cinfos.get(ctr) ;
            HashSet<ConnectionImpl> set = new HashSet<ConnectionImpl>() ;
            csa.add( set ) ;
            for (int num=0; num<MAX_PARALLEL_CONNECTIONS; num++) {
                ConnectionImpl conn = cinfo.createConnection() ;
                ibcache.requestReceived( conn ) ;
                set.add( conn ) ;
            }
        }
        
        checkStats( ibcache, 0, 0, HIGH_WATER_MARK, HIGH_WATER_MARK ) ;
        
        // Now open up connection on so far unused ContactInfoImpl
        ConnectionImpl over = overcinfo.createConnection() ;
        ibcache.requestReceived( over ) ;
        checkStats( ibcache, 0, 0, HIGH_WATER_MARK+1, HIGH_WATER_MARK+1 ) ;
        
        // Free the overflow connection, expecting a response
        ibcache.requestProcessed( over, 1 ) ;
        checkStats( ibcache, 1, 0, HIGH_WATER_MARK, HIGH_WATER_MARK+1 ) ;
        
        // Get a response to free overflow conn: should close
        ibcache.responseSent( over ) ;
        checkStats( ibcache, 0, 0, HIGH_WATER_MARK, HIGH_WATER_MARK ) ;
        
        // Again open up connection on so far unused ContactInfoImpl
        over = overcinfo.createConnection() ;
        ibcache.requestReceived( over ) ;
        checkStats( ibcache, 0, 0, HIGH_WATER_MARK+1, HIGH_WATER_MARK+1 ) ;
        
        // Free the overflow connection, no response
        ibcache.requestProcessed( over, 0 ) ;
        checkStats( ibcache, 0, 0, HIGH_WATER_MARK, HIGH_WATER_MARK ) ;
        
        // add overflow twice
        ConnectionImpl over1 = overcinfo.createConnection() ;
        ibcache.requestReceived( over1 ) ;
        ibcache.requestReceived( over1 ) ;
        checkStats( ibcache, 0, 0, HIGH_WATER_MARK+1, HIGH_WATER_MARK+1 ) ;
        
        ibcache.requestProcessed( over1, 0 ) ;
        ibcache.requestProcessed( over1, 0 ) ;
        checkStats( ibcache, 0, 0, HIGH_WATER_MARK, HIGH_WATER_MARK ) ;
        
        // Clean up everything: just close
        for (Set<ConnectionImpl> conns : csa ) {
            for (ConnectionImpl conn : conns) {
                ibcache.close( conn ) ;
            }
        }
        checkStats( ibcache, 0, 0, 0, 0 ) ;
    }
}
