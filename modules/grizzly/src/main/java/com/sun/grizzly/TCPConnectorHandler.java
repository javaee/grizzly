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

package com.sun.grizzly;

import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.util.FutureImpl;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.InputReader;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
/**
 * Non blocking TCP Connector Handler. The recommended way to use this class
 * is by creating an external Controller and share the same SelectorHandler
 * instance.
 * <p>
 * Recommended
 * -----------
 * </p><p><pre><code>
 * Controller controller = new Controller();
 * // new TCPSelectorHandler(true) means the Selector will be used only
 * // for client operation (OP_READ, OP_WRITE, OP_CONNECT).
 * TCPSelectorHandler tcpSelectorHandler = new TCPSelectorHandler(true);
 * controller.setSelectorHandler(tcpSelectorHandler);
 * TCPConnectorHandler tcpConnectorHandler = new TCPConnectorHandler();
 * tcpConnectorHandler.connect(localhost,port, new CallbackHandler(){...},
 *                             tcpSelectorHandler);
 * TCPConnectorHandler tcpConnectorHandler2 = new TCPConnectorHandler();
 * tcpConnectorHandler2.connect(localhost,port, new CallbackHandler(){...},
 *                             tcpSelectorHandler);
 * </code></pre></p><p>
 * Not recommended (but still works)
 * ---------------------------------
 * </p><p><pre><code>
 * TCPConnectorHandler tcpConnectorHandler = new TCPConnectorHandler();
 * tcpConnectorHandler.connect(localhost,port);
 * </code></pre></p><p>
 *
 * Internally, a new Controller will be created everytime connect(localhost,port)
 * is invoked, which has an impact on performance.
 *
 * @author Jeanfrancois Arcand
 */
public class TCPConnectorHandler extends
        AbstractConnectorHandler<TCPSelectorHandler, CallbackHandler> {

    /**
     * default TCP channel connection timeout in milliseconds
     */
    private static final int DEFAULT_CONNECTION_TIMEOUT = 30 * 1000;
    

    /**
     * IsConnected future
     */
    private volatile FutureImpl<Boolean> isConnectedFuture;
    
    
    /**
     * Are we creating a controller every run.
     */
    private boolean isStandalone = false;
    
    
    /**
     * The socket tcpDelay.
     * 
     * Default value for tcpNoDelay.
     */
    protected boolean tcpNoDelay = true;
    
    
    /**
     * The socket reuseAddress
     */
    protected boolean reuseAddress = true;
    
    
    /**
     * The socket linger.
     */
    protected int linger = -1;    
    
    /**
     * Connection timeout is milliseconds
     */
    protected int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    public TCPConnectorHandler() {
        protocol(Protocol.TCP);
    }
        
    
    /**
     * Connect to hostname:port. When an aysnchronous event happens (e.g
     * OP_READ or OP_WRITE), the {@link Controller} will invoke
     * the CallBackHandler.
     * @param remoteAddress remote address to connect
     * @param localAddress local address to bin
     * @param callbackHandler the handler invoked by its associated {@link SelectorHandler} when
     *        a non blocking operation is ready to be handled. When null, all 
     *        read and write operation will be delegated to the default
     *        {@link ProtocolChain} and its list of {@link ProtocolFilter} 
     *        . When null, this {@link ConnectorHandler} will create an instance of {@link DefaultCallbackHandler}.
     * @param selectorHandler an instance of SelectorHandler.
     * @throws java.io.IOException
     */
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress,
            CallbackHandler callbackHandler,
            TCPSelectorHandler selectorHandler) throws IOException {
        
        if (isConnected){
            throw new AlreadyConnectedException();
        }
        
        if (controller == null){
            throw new IllegalStateException("Controller cannot be null");
        }
        
        if (selectorHandler == null){
            throw new IllegalStateException("SelectorHandler cannot be null");
        }
        
        this.selectorHandler = selectorHandler;
        
        if (callbackHandler == null){
            callbackHandler = new DefaultCallbackHandler(this); 
        } else {
            this.callbackHandler = callbackHandler;
        }
        
            // Wait for the onConnect to be invoked.
        isConnectedFuture = new FutureImpl<Boolean>();

        selectorHandler.connect(remoteAddress, localAddress,
                callbackHandler);
        inputStream = new InputReader();
        
        try {
            isConnectedFuture.get(connectionTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new IOException(e.getMessage());
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
        }

            throw new IOException("Unexpected exception during connect. " +
                    cause.getClass().getName() + ": " + cause.getMessage());
        } catch (TimeoutException e) {
            throw new IOException("Connection timeout");
    }
    }
    
    
    /**
     * Connect to hostname:port. Internally an instance of Controller and
     * its default SelectorHandler will be created everytime this method is
     * called. This method should be used only and only if no external
     * Controller has been initialized.
     * @param remoteAddress remote address to connect
     * @throws java.io.IOException
     * @param localAddress local address to bin
     */
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress)
            throws IOException {
        
        if (isConnected){
            throw new AlreadyConnectedException();
        }
        
        if (controller == null){
            isStandalone = true;
            controller = new Controller();
            controller.setSelectorHandler(new TCPSelectorHandler(true));
            
            final CountDownLatch latch = new CountDownLatch(1);
            controller.addStateListener(new ControllerStateListenerAdapter() {
                @Override
                public void onReady() {
                    latch.countDown();
                }
                
                @Override
                public void onException(Throwable e) {
                    latch.countDown();
                }
            });

            callbackHandler = new DefaultCallbackHandler(this,false);            
            controller.executeUsingKernelExecutor();
            
            try {
                latch.await();
            } catch (InterruptedException ex) {
            }
        }
        
        if (null == callbackHandler) {
            callbackHandler = new DefaultCallbackHandler(this);
        }
        
        connect(remoteAddress,localAddress,callbackHandler);
    }

    
    /**
     * Close the underlying connection.
     */
    public void close() throws IOException{
        if (underlyingChannel != null){
            if (selectorHandler != null){
                SelectionKey key = selectorHandler.keyFor(underlyingChannel);
                
                if (key == null) return;
                
                selectorHandler.getSelectionKeyHandler().cancel(key);
            } else {
                underlyingChannel.close();
            }
        }
        
        if (controller != null && isStandalone){
            controller.stop();
            controller = null;
        }
        
        isStandalone = false;
        isConnected = false;
        connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    }

    
    /**
     * Finish handling the OP_CONNECT interest ops.
     * @param key - a {@link SelectionKey}
     */
    public void finishConnect(SelectionKey key) throws IOException{
        Throwable error = null;
        
        try {
            if (Controller.logger().isLoggable(Level.FINE)) {
                Controller.logger().log(Level.FINE, "Finish connect");
            }
            
            final SocketChannel socketChannel = (SocketChannel) key.channel();
            underlyingChannel = socketChannel;
            socketChannel.finishConnect();
            isConnected = socketChannel.isConnected();
            configureChannel(socketChannel);
            
            if (Controller.logger().isLoggable(Level.FINE)) {
                Controller.logger().log(Level.FINE, "isConnected: " + isConnected);
            }
        } catch (Throwable e) {
            error = e;
        } finally {
            if (error == null) {
                isConnectedFuture.setResult(Boolean.TRUE);
            } else {
                isConnectedFuture.setException(error);
                if (error instanceof IOException) {
                    throw (IOException) error;
        }

                throw new IOException("Unexpected exception during connect. " +
                        error.getClass().getName() + ": " + error.getMessage());
    }
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    public void configureChannel(SelectableChannel channel) throws IOException{
        Socket socket = ((SocketChannel) channel).socket();

        try{
            if(linger >= 0 ) {
                socket.setSoLinger( true, linger);
            }
        } catch (SocketException ex){
            Controller.logger().log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_CONNECTOR_HANDLER_LINGER_EXCEPTION(), ex);
        }
        
        try{
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (SocketException ex){
            Controller.logger().log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_CONNECTOR_HANDLER_TCPNODELAY_EXCEPTION(), ex);
        }
        
        try{
            socket.setReuseAddress(reuseAddress);
        } catch (SocketException ex){
            Controller.logger().log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_CONNECTOR_HANDLER_REUSEADDRESS_EXCEPTION(), ex);
        }
    }
    
    
    /**
     * A token decribing the protocol supported by an implementation of this
     * interface
     * @return this {@link ConnectorHandler}'s protocol
     */
    @Override
    public final Controller.Protocol protocol(){
        return Controller.Protocol.TCP;
    }
    
    
    /**
     * Return the tcpNoDelay value used by the underlying accepted Sockets.
     * 
     * Also see setTcpNoDelay(boolean tcpNoDelay)
     */
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }
    
    
    /**
     * Enable (true) or disable (false) the underlying Socket's
     * tcpNoDelay.
     * 
     * Default value for tcpNoDelay is disabled (set to false).
     * 
     * Disabled by default since enabling tcpNoDelay for most applications
     * can cause packets to appear to arrive in a fragmented fashion where it 
     * takes multiple OP_READ events (i.e. multiple calls to read small 
     * messages). The common behaviour seen when this occurs is that often times
     * a small number of bytes, as small as 1 byte at a time is read per OP_READ
     * event dispatch.  This results in a large number of system calls to
     * read(), system calls to enable and disable interest ops and potentially
     * a large number of thread context switches between a thread doing the
     * Select(ing) and a worker thread doing the read.
     * 
     * The Connector side should also set tcpNoDelay the same as it is set here 
     * whenever possible.
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }
    
    
    /**
     * @see java.net.Socket#setLinger()
     * @return the linger value.
     */
    public int getLinger() {
        return linger;
    }
    
    
    /**
     * @see java.net.Socket#setLinger()
     */    
    public void setLinger(int linger) {
        this.linger = linger;
    }

    
    /**
     * Get TCP channel connection timeout in milliseconds
     * @return TCP channel connection timeout in milliseconds
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    
    /**
     * Set TCP channel connection timeout in milliseconds
     * @param connectionTimeout TCP channel connection timeout in milliseconds
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    
    /**
     * @see java.net.Socket#setReuseAddress()
     */      
    public boolean isReuseAddress() {
        return reuseAddress;
    }
    
    
    /**
     * @see java.net.Socket#setReuseAddress()
     */      
    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }
}
