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

package com.sun.grizzly.connectioncache.client;

import com.sun.grizzly.AbstractConnectorHandler;
import com.sun.grizzly.CallbackHandler;
import com.sun.grizzly.CallbackHandlerSelectionKeyAttachment;
import com.sun.grizzly.ConnectorHandler;
import com.sun.grizzly.Context;
import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.DefaultCallbackHandler;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.NIOContext;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.async.AsyncQueueReadUnit;
import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.async.AsyncReadCallbackHandler;
import com.sun.grizzly.async.AsyncReadCondition;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import com.sun.grizzly.async.ByteBufferCloner;
import com.sun.grizzly.connectioncache.spi.transport.ContactInfo;
import com.sun.grizzly.connectioncache.spi.transport.OutboundConnectionCache;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;

/**
 * Extended implementation of the DefaultSelectionKeyHandler with
 * ConnectionManagement integrated in it
 *
 * @author Alexey Stashok
 */
public class CacheableConnectorHandler 
        extends AbstractConnectorHandler<SelectorHandler, CallbackHandler> {

    private SocketAddress targetAddress;
    
    private final CacheableConnectorHandlerPool parentPool;
    private ConnectorHandler underlyingConnectorHandler;
    
    private final ConnectExecutor connectExecutor;

    private boolean isReusing;
    
    public CacheableConnectorHandler(CacheableConnectorHandlerPool parentPool) {
        this.parentPool = parentPool;
        connectExecutor = new ConnectExecutor();
    }
    
    // connect method #1
    public void connect(SocketAddress remoteAddress, CallbackHandler callbackHandler, SelectorHandler selectorHandler) throws IOException {
        connectExecutor.setParameters(remoteAddress, callbackHandler, selectorHandler);
        doConnect(remoteAddress);
    }
    
    // connect method #2
    public void connect(SocketAddress remoteAddress, CallbackHandler callbackHandler) throws IOException {
        connectExecutor.setParameters(remoteAddress, callbackHandler);
        doConnect(remoteAddress);
    }
    
    // connect method #3
    public void connect(SocketAddress remoteAddress) throws IOException {
        connectExecutor.setParameters(remoteAddress);
        doConnect(remoteAddress);
    }
    
    // connect method #4
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress,
            CallbackHandler callbackHandler, SelectorHandler selectorHandler) throws IOException {
        connectExecutor.setParameters(remoteAddress, localAddress, callbackHandler, selectorHandler);
        doConnect(remoteAddress);
    }
    
    // connect method #5
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress, CallbackHandler callbackHandler) throws IOException {
        connectExecutor.setParameters(remoteAddress, localAddress, callbackHandler);
        doConnect(remoteAddress);
    }
    
    // connect method #6
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress) throws IOException {
        connectExecutor.setParameters(remoteAddress, localAddress);
        doConnect(remoteAddress);
    }
    
    
    /**
     * Releases underlying connection, which means it could be reused for writing
     * by other <code>CacheableConnectorHandler</code>, however this 
     * <code>CacheableConnectorHandler</code> will be still interested in getting 
     * expectedResponseCount responses on it.
     * 
     * @param expectedResponseCount number of reponses expected on the connection
     */
    public void release(int expectedResponseCount) {
        parentPool.getOutboundConnectionCache().release(underlyingConnectorHandler, expectedResponseCount);
    }

    /**
     * Notifies connection cache, that response was received. And connection cache
     * could decrease expected response counter by 1
     */
    public void responseReceived() {
        parentPool.getOutboundConnectionCache().responseReceived(underlyingConnectorHandler);
    }

    /**
     * @param targetAddress target connection address
     * @throws java.io.IOException if IO error happened
     */
    private void doConnect(SocketAddress targetAddress) throws IOException {
        this.targetAddress = targetAddress;
        callbackHandler = connectExecutor.callbackHandler;

        if (callbackHandler == null) {
            callbackHandler = new DefaultCallbackHandler(this);
        }

        final OutboundConnectionCache<ConnectorHandler> outboundConnectionCache =
                parentPool.getOutboundConnectionCache();

        do {
            connectExecutor.wasCalled = false;
            underlyingConnectorHandler = outboundConnectionCache.get(
                    new CacheableConnectorInfo(parentPool, connectExecutor,
                    protocol, targetAddress),
                    parentPool.getConnectionFinder());

            /* check whether NEW connection was created, or taken from cache */
            isReusing = !connectExecutor.wasCalled();
            if (isReusing) { // if taken from cache
                //if connection is taken from cache - explicitly notify callback handler
                underlyingConnectorHandler.setCallbackHandler(callbackHandler);
                if (notifyCallbackHandlerPseudoConnect()) {
                    return;
                }

                try {
                    underlyingConnectorHandler.close();
                } catch (IOException e) {
                }

                outboundConnectionCache.close(underlyingConnectorHandler);
                underlyingConnectorHandler = null;
            } else {
                return;
            }
            
        } while (true);
    }

    @Override
    public SelectorHandler getSelectorHandler() {
        final ConnectorHandler localCH = underlyingConnectorHandler;
        return localCH != null ? localCH.getSelectorHandler() : null;
    }

    @Override
    public SelectableChannel getUnderlyingChannel() {
        final ConnectorHandler localCH = underlyingConnectorHandler;
        return localCH != null ? localCH.getUnderlyingChannel() : null;
    }    
    
    public void forceClose() throws IOException {
        parentPool.getOutboundConnectionCache().close(underlyingConnectorHandler);
        underlyingConnectorHandler = null;
    }

    public void close() throws IOException {
        parentPool.getOutboundConnectionCache().release(underlyingConnectorHandler, 0);
        underlyingConnectorHandler = null;
    }

    @Override
    public long read(ByteBuffer byteBuffer, boolean blocking) throws IOException {
        return underlyingConnectorHandler.read(byteBuffer, blocking);
    }

    @Override
    public long write(ByteBuffer byteBuffer, boolean blocking) throws IOException {
        return underlyingConnectorHandler.write(byteBuffer, blocking);
    }
    
    public void finishConnect(SelectionKey key) throws IOException {
        // Call underlying finishConnect only if connection was just established
        if (connectExecutor.wasCalled()) {
            underlyingConnectorHandler.finishConnect(key);
        }
    }

    @Override
    public boolean isConnected() {
        return underlyingConnectorHandler != null && underlyingConnectorHandler.isConnected();
    }

    /**
     * Return <tt>true</tt> if underlying connection was take from cache and going to be
     * reused by this <tt>CacheableConnectorHandler</tt>, otherwise return <tt>false</tt>,
     * if underlying connection was just created and connected.
     *
     * @return <tt>true</tt> if underlying connection was take from cache and going to be
     * reused by this <tt>CacheableConnectorHandler</tt>, otherwise return <tt>false</tt>,
     * if underlying connection was just created and connected.
     */
    public boolean isReusing() {
        return isReusing;
    }
    
    public ConnectorHandler getUnderlyingConnectorHandler() {
        return underlyingConnectorHandler;
    }
    
    private boolean notifyCallbackHandlerPseudoConnect() {
        final SelectorHandler underlyingSelectorHandler =
                underlyingConnectorHandler.getSelectorHandler();
        final SelectionKey key = underlyingSelectorHandler.keyFor(underlyingConnectorHandler.getUnderlyingChannel());
        if (key == null || !key.channel().isOpen()) {
            return false;
        }
        
        final NIOContext context = (NIOContext)parentPool.getController().pollContext();
        context.setSelectionKey(key);
        context.configureOpType(key);
        context.setSelectorHandler(underlyingConnectorHandler.getSelectorHandler());
        key.attach(new CallbackHandlerSelectionKeyAttachment(callbackHandler));
        callbackHandler.onConnect(new IOEvent.DefaultIOEvent<Context>(context));

        return true;
    }

    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue( ByteBuffer buffer ) throws IOException {
        return underlyingConnectorHandler.writeToAsyncQueue( buffer );
    }

    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue( ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler ) throws IOException {
        return underlyingConnectorHandler.writeToAsyncQueue( buffer, callbackHandler );
    }

    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue( ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler, AsyncQueueDataProcessor writePreProcessor ) throws IOException {
        return underlyingConnectorHandler.writeToAsyncQueue( buffer, callbackHandler, writePreProcessor );
    }

    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue( ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler, AsyncQueueDataProcessor writePreProcessor, ByteBufferCloner cloner ) throws IOException {
        return underlyingConnectorHandler.writeToAsyncQueue( buffer, callbackHandler, writePreProcessor, cloner );
    }

    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue( SocketAddress dstAddress, ByteBuffer buffer ) throws IOException {
        return underlyingConnectorHandler.writeToAsyncQueue( dstAddress, buffer );
    }

    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue( SocketAddress dstAddress, ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler ) throws IOException {
        return underlyingConnectorHandler.writeToAsyncQueue( dstAddress, buffer, callbackHandler );
    }

    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue( SocketAddress dstAddress, ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler, AsyncQueueDataProcessor writePreProcessor ) throws IOException {
        return underlyingConnectorHandler.writeToAsyncQueue( dstAddress, buffer, callbackHandler, writePreProcessor );
    }

    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue( SocketAddress dstAddress, ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler, AsyncQueueDataProcessor writePreProcessor, ByteBufferCloner cloner ) throws IOException {
        return underlyingConnectorHandler.writeToAsyncQueue( dstAddress, buffer, callbackHandler, writePreProcessor, cloner );
    }

    @Override
    public Future<AsyncQueueReadUnit> readFromAsyncQueue( ByteBuffer buffer, AsyncReadCallbackHandler callbackHandler ) throws IOException {
        return underlyingConnectorHandler.readFromAsyncQueue( buffer, callbackHandler );
    }

    @Override
    public Future<AsyncQueueReadUnit> readFromAsyncQueue( ByteBuffer buffer, AsyncReadCallbackHandler callbackHandler, AsyncReadCondition condition ) throws IOException {
        return underlyingConnectorHandler.readFromAsyncQueue( buffer, callbackHandler, condition );
    }

    @Override
    public Future<AsyncQueueReadUnit> readFromAsyncQueue( ByteBuffer buffer, AsyncReadCallbackHandler callbackHandler, AsyncReadCondition condition, AsyncQueueDataProcessor readPostProcessor ) throws IOException {
        return underlyingConnectorHandler.readFromAsyncQueue( buffer, callbackHandler, condition, readPostProcessor );
    }

    /**
     * Class is responsible for execution corresponding
     * underlying {@link ConnectorHandler} connect method
     */
    private class ConnectExecutor {
        private int methodNumber;
        private SocketAddress remoteAddress;
        private SocketAddress localAddress;
        private SelectorHandler selectorHandler;
        private CallbackHandler callbackHandler;
        private boolean wasCalled;
        
        public void setConnectorHandler(ConnectorHandler connectorHandler) {
            underlyingConnectorHandler = connectorHandler;
        }
        
        public void setParameters(SocketAddress remoteAddress, CallbackHandler callbackHandler, SelectorHandler selectorHandler) {
            setParameters(remoteAddress, null, callbackHandler, selectorHandler);
            methodNumber = 1;
        }
        
        public void setParameters(SocketAddress remoteAddress, CallbackHandler callbackHandler) {
            setParameters(remoteAddress, null, callbackHandler);
            methodNumber = 2;
        }
        
        public void setParameters(SocketAddress remoteAddress) {
            setParameters(remoteAddress, (SocketAddress) null);
            methodNumber = 3;
        }
        
        public void setParameters(SocketAddress remoteAddress, SocketAddress localAddress, CallbackHandler callbackHandler,SelectorHandler selectorHandler) {
            wasCalled = false;
            this.remoteAddress = remoteAddress;
            this.localAddress = localAddress;
            this.callbackHandler = callbackHandler;
            this.selectorHandler = selectorHandler;
            methodNumber = 4;
        }
        
        public void setParameters(SocketAddress remoteAddress, SocketAddress localAddress, CallbackHandler callbackHandler) {
            setParameters(remoteAddress, localAddress, callbackHandler , null);
            methodNumber = 5;
        }
        
        public void setParameters(SocketAddress remoteAddress, SocketAddress localAddress) {
            setParameters(remoteAddress, localAddress, null);
            methodNumber = 6;
        }
        
        public boolean wasCalled() {
            return wasCalled;
        }
        
        public void invokeProtocolConnect() throws IOException {
            wasCalled = true;
            switch(methodNumber) {
            case 1: underlyingConnectorHandler.connect(remoteAddress,
                    CacheableConnectorHandler.this.callbackHandler,
                    selectorHandler);
            break;
            case 2:
            case 3: underlyingConnectorHandler.connect(remoteAddress,
                    CacheableConnectorHandler.this.callbackHandler);
            break;
            case 4: underlyingConnectorHandler.connect(remoteAddress,
                    localAddress, CacheableConnectorHandler.this.callbackHandler,
                    selectorHandler);
            break;
            case 5:
            case 6: underlyingConnectorHandler.connect(remoteAddress,
                    localAddress, CacheableConnectorHandler.this.callbackHandler);
            break;
            default: throw new IllegalStateException(
                    "Can not find appropriate connect method: " + methodNumber);
            }
        }
    }

    //---------------------- ContactInfo implementation --------------------------------
    private static class CacheableConnectorInfo implements ContactInfo<ConnectorHandler> {
        private SelectorHandler selectorHandler;
        private final CacheableConnectorHandlerPool parentPool;
        private final ConnectExecutor connectExecutor;
        private final Protocol protocol;
        private final SocketAddress targetAddress;

        public CacheableConnectorInfo(CacheableConnectorHandlerPool parentPool,
                ConnectExecutor connectExecutor,
                Protocol protocol, SocketAddress targetAddress) {
            this.parentPool = parentPool;
            this.connectExecutor = connectExecutor;
            this.protocol = protocol;
            this.targetAddress = targetAddress;
        }
        

        public ConnectorHandler createConnection() throws IOException {
            final ConnectorHandler connectorHandler =
                    parentPool.getProtocolConnectorHandlerPool().
                    acquireConnectorHandler(protocol);

            if (connectorHandler == null) {
                throw new IllegalStateException("Can not obtain protocol '" + protocol + "' ConnectorHandler");
            }

            connectExecutor.setConnectorHandler(connectorHandler);
            connectExecutor.invokeProtocolConnect();

            selectorHandler = connectorHandler.getSelectorHandler();
            return connectorHandler;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName());
            sb.append(" targetAddress: ");
            sb.append(targetAddress);
            sb.append(" protocol: ");
            sb.append(protocol);
            sb.append(" hashCode: ");
            sb.append(super.hashCode());
            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CacheableConnectorInfo other = (CacheableConnectorInfo) obj;
            if (this.selectorHandler != null && other.selectorHandler != null) {
                if (this.selectorHandler != other.selectorHandler &&
                        !this.selectorHandler.equals(other.selectorHandler)) {
                    return false;
                }
            }
            if (this.protocol != other.protocol) {
                return false;
            }
            if (this.targetAddress != other.targetAddress &&
                    (this.targetAddress == null ||
                    !this.targetAddress.equals(other.targetAddress))) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 47 * hash + 
                    (this.protocol != null ?
                        this.protocol.hashCode() : 0);
            hash = 47 * hash +
                    (this.targetAddress != null ?
                        this.targetAddress.hashCode() : 0);
            return hash;
        }

        //----- Override hashCode(), equals() as part of ContactInfo implementation -----------

    }
}
