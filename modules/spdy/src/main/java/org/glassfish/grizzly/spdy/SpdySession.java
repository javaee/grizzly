/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.spdy.compression.SpdyDeflaterOutputStream;
import org.glassfish.grizzly.spdy.compression.SpdyInflaterOutputStream;

import static org.glassfish.grizzly.spdy.Constants.*;

/**
 *
 * @author oleksiys
 */
final class SpdySession {
    private final boolean isServer;
    private final Connection connection;
    
    private SpdyInflaterOutputStream inflaterOutputStream;
    private SpdyDeflaterOutputStream deflaterOutputStream;
    private DataOutputStream deflaterDataOutputStream;

    private int deflaterCompressionLevel = Deflater.DEFAULT_COMPRESSION;
    
    private int lastPeerStreamId;
    private int lastLocalStreamId;
    
    private FilterChain upstreamChain;
    private FilterChain downstreamChain;
    
    private Map<Integer, SpdyStream> streamsMap =
            new ConcurrentHashMap<Integer, SpdyStream>();
    
    final List tmpList = new ArrayList();
    
    private final Object sessionLock = new Object();
    
    private boolean isGoAway;
    
    public SpdySession(final Connection connection) {
        this(connection, true);
    }
    
    public SpdySession(final Connection connection,
            final boolean isServer) {
        this.connection = connection;
        this.isServer = isServer;
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isServer() {
        return isServer;
    }
    
    public SpdyStream getStream(final int streamId) {
        return streamsMap.get(streamId);
    }
    
    public void goAway(final int statusCode) {
        final int lastPeerStreamIdLocal = setGoAway();
        if (lastPeerStreamIdLocal == -1) {
            return; // SpdySession is already in go-away state
        }
        
        final Buffer goAwayFrame = connection
                .getTransport().getMemoryManager().allocate(16);

        goAwayFrame.putInt(0x80000000 | (SPDY_VERSION << 16) | GOAWAY_FRAME); // "C", version, GOAWAY_FRAME
        goAwayFrame.putInt(8); // Flags, Length
        goAwayFrame.putInt(lastPeerStreamIdLocal & 0x7FFFFFFF); // Stream-ID
        goAwayFrame.putInt(statusCode); // Status code
        goAwayFrame.flip();

        writeDownStream(goAwayFrame);
    }
    
    SpdyInflaterOutputStream getInflaterOutputStream() {
        if (inflaterOutputStream == null) {
            inflaterOutputStream = new SpdyInflaterOutputStream(
                    connection.getTransport().getMemoryManager(),
                    Constants.SPDY_ZLIB_DICTIONARY);
        }
        
        return inflaterOutputStream;
    }

    public int getDeflaterCompressionLevel() {
        return deflaterCompressionLevel;
    }

    public void setDeflaterCompressionLevel(int deflaterCompressionLevel) {
        if (deflaterOutputStream != null) {
            throw new IllegalStateException("Deflater has been initialized already");
        }
        
        this.deflaterCompressionLevel = deflaterCompressionLevel;
    }    

    SpdyDeflaterOutputStream getDeflaterOutputStream() {
        if (deflaterOutputStream == null) {
            deflaterOutputStream = new SpdyDeflaterOutputStream(
                    connection.getTransport().getMemoryManager(),
                    deflaterCompressionLevel,
                    Constants.SPDY_ZLIB_DICTIONARY);
        }
        
        return deflaterOutputStream;
    }
    
    DataOutputStream getDeflaterDataOutputStream() {
        if (deflaterDataOutputStream == null) {
            deflaterDataOutputStream = new DataOutputStream(
                    getDeflaterOutputStream());
        }
        
        return deflaterDataOutputStream;
    }

    SpdyStream acceptStream(final FilterChainContext context,
            final SpdyRequest spdyRequest,
            final int streamId, final int associatedToStreamId, 
            final int priority, final int slot) {
        
        final FilterChainContext upstreamContext =
                getUpstreamChain(context).obtainFilterChainContext(
                context.getConnection());
        
        
        final FilterChainContext downstreamContext =
                getDownstreamChain(context).obtainFilterChainContext(
                context.getConnection(), context.getFilterIdx(),
                context.getStartIdx(),
                context.getFilterIdx());
        
        upstreamContext.getInternalContext().setEvent(IOEvent.READ);
        upstreamContext.setMessage(HttpContent.builder(spdyRequest).build());
        upstreamContext.setAddressHolder(context.getAddressHolder());
        
        final SpdyStream spdyStream = new SpdyStream(this, spdyRequest,
                upstreamContext, downstreamContext, streamId, associatedToStreamId,
                priority, slot);
        
        synchronized(sessionLock) {
            if (isGoAway) {
                return null; // if go-away is set - return null to ignore stream creation
            }
            
            streamsMap.put(streamId, spdyStream);
            lastPeerStreamId = streamId;
        }
        
        return spdyStream;
    }
    
    void writeDownStream(final Buffer frame) {
        writeDownStream(frame, null);
    }
    
    void writeDownStream(final Buffer frame,
            final CompletionHandler<WriteResult> completionHandler) {
        
        downstreamChain.write(connection,
                null, frame, completionHandler, null);        
    }

    void initCommunication(final FilterChainContext context) {
        upstreamChain = (FilterChain) context.getFilterChain().subList(
                context.getFilterIdx(), context.getEndIdx());
        
        downstreamChain = (FilterChain) context.getFilterChain().subList(
                context.getStartIdx(), context.getFilterIdx());
    }
    
    FilterChain getUpstreamChain(final FilterChainContext context) {
        return upstreamChain;
    }
    
    FilterChain getDownstreamChain(final FilterChainContext context) {
        return downstreamChain;
    }
    
    /**
     * Method is called, when GOAWAY is initiated by us
     */
    private int setGoAway() {
        synchronized (sessionLock) {
            if (isGoAway) {
                return -1;
            }
            
            isGoAway = true;
            return lastPeerStreamId;
        }
    }
    
    /**
     * Method is called, when GOAWAY is initiated by peer
     */
    void setGoAway(final int lastGoodStreamId) {
        synchronized (sessionLock) {
            // @TODO Notify pending SYNC_STREAMS if streams were aborted
        }
    }
    
    Object getSessionLock() {
        return sessionLock;
    }

    /**
     * Called from {@link SpdyStream} once stream is completely closed.
     */
    void deregisterStream(final SpdyStream spdyStream) {
        streamsMap.remove(spdyStream.getStreamId());
        
        synchronized (sessionLock) {
            // If we're in GOAWAY state and there are no streams left - close this session
            if (isGoAway && streamsMap.isEmpty()) {
                closeSession();
            }
        }
    }

    /**
     * Close the session
     */
    private void closeSession() {
        connection.closeSilently();
    }
}
