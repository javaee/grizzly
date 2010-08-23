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
import com.sun.grizzly.util.InputReader;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Client side interface used to implement non blocking client operation.
 * Implementation of this class must make sure the following methods are
 * invoked in that order:
 * <p><pre><code>
 * (1) connect()
 * (2) read() or write().
 * </code></pre></p>
 *
 * @author Jeanfrancois Arcand
 */
public class UDPConnectorHandler
        extends AbstractConnectorHandler<UDPSelectorHandler, CallbackHandler> {

    /**
     * IsConnected Latch related
     */
    protected volatile CountDownLatch isConnectedLatch;
    
    
    /**
     * Are we creating a controller every run.
     */
    private boolean isStandalone = false;
    
    
    public UDPConnectorHandler() {
        protocol(Protocol.UDP);
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
     */
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress,
            CallbackHandler callbackHandler,
            UDPSelectorHandler selectorHandler) throws IOException {
        
        if (isConnected){
            throw new AlreadyConnectedException();
        }
        
        if (controller == null){
            throw new IllegalStateException("Controller cannot be null");
        }
        
        if (selectorHandler == null){
            throw new IllegalStateException("Controller cannot be null");
        }
        
        this.selectorHandler = selectorHandler;
        if (callbackHandler == null){
            callbackHandler = new DefaultCallbackHandler(this);
        } else {
            this.callbackHandler = callbackHandler;
        }
        
        // Wait for the onConnect to be invoked.
        isConnectedLatch = new CountDownLatch(1);

        selectorHandler.connect(remoteAddress, localAddress, callbackHandler);
        
        try {
            isConnectedLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new IOException(ex.getMessage());
        }
    }
    
    
    /**
     * Connect to hostname:port. Internally an instance of Controller and
     * its default SelectorHandler will be created everytime this method is
     * called. This method should be used only and only if no external
     * Controller has been initialized.
     * @param remoteAddress remote address to connect
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
            controller.setSelectorHandler(new UDPSelectorHandler(true));
            
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
            callbackHandler = new DefaultCallbackHandler(this, false);
            controller.executeUsingKernelExecutor();
            
            try {
                latch.await();
            } catch (InterruptedException ex) {
            }
        }
        
        connect(remoteAddress,localAddress,callbackHandler);
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public long read(ByteBuffer byteBuffer, boolean blocking) throws IOException {
        if (blocking){
            if (inputStream == null) {
                inputStream = new InputReader();
            }
            inputStream.setChannelType(InputReader.ChannelType.DatagramChannel);
        }
        return super.read( byteBuffer, blocking );
    }
    

    /**
     * Receive bytes.
     * @param byteBuffer The byteBuffer to store bytes.
     * @param socketAddress
     * @return number bytes sent
     * @throws java.io.IOException
     */
    public long send(ByteBuffer byteBuffer, SocketAddress socketAddress)
    throws IOException {
        if (!isConnected){
            throw new NotYetConnectedException();
        }
        
        if (callbackHandler == null){
            throw new IllegalStateException
                    ("Non blocking read needs a CallbackHandler");
        }
        
        return ((DatagramChannel) underlyingChannel).send(byteBuffer,socketAddress);
    }
    
    
    /**
     * Receive bytes.
     * @param byteBuffer The byteBuffer to store bytes.
     * @return {@link SocketAddress}
     * @throws java.io.IOException
     */
    public SocketAddress receive(ByteBuffer byteBuffer) throws IOException {
        if (!isConnected){
            throw new NotYetConnectedException();
        }
        
        if (callbackHandler == null){
            throw new IllegalStateException
                    ("Non blocking read needs a CallbackHandler");
        }
        
        SocketAddress socketAddress =
                ((DatagramChannel) underlyingChannel).receive(byteBuffer);
        return socketAddress;
    }
    
    
    /**
     * Close the underlying connection.
     */
    public void close() throws IOException{
        if (underlyingChannel != null){
            if (selectorHandler != null){
                SelectionKey key =
                        selectorHandler.keyFor(underlyingChannel);
                
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
    }
    
    
    /**
     * Finish handling the OP_CONNECT interest ops.
     */
    public void finishConnect(SelectionKey key) throws IOException {
        if (Controller.logger().isLoggable(Level.FINE)) {
            Controller.logger().log(Level.FINE, "Finish connect");
        }
        
        final DatagramChannel datagramChannel = (DatagramChannel)key.channel();
        underlyingChannel = datagramChannel;
        isConnected = datagramChannel.isConnected();
        if (isConnectedLatch != null) {
            isConnectedLatch.countDown();
        }
    }
    
    
    /**
     * A token decribing the protocol supported by an implementation of this
     * interface
     */
    @Override
    public Controller.Protocol protocol(){
        return Controller.Protocol.UDP;
    }
}
