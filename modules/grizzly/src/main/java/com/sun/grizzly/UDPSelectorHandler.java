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

import com.sun.grizzly.async.UDPAsyncQueueReader;
import com.sun.grizzly.async.UDPAsyncQueueWriter;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.Utils;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Callable;
import java.util.logging.Level;

/**
 * A SelectorHandler handles all java.nio.channels.Selector operations. 
 * One or more instance of a Selector are handled by SelectorHandler. 
 * The logic for processing of SelectionKey interest (OP_ACCEPT,OP_READ, etc.)
 * is usually defined using an instance of SelectorHandler.
 *
 * This class represent a UDP implementation of a SelectorHandler. 
 * This class first bind a datagramSocketChannel to a UDP port and then start 
 * waiting for NIO events. 
 *
 * @author Jeanfrancois Arcand
 */
public class UDPSelectorHandler extends TCPSelectorHandler {
 
    private final static String NOT_SUPPORTED = 
            "Not supported by this SelectorHandler";
   
    /**
     * The datagramSocket instance.
     */
    protected DatagramSocket datagramSocket;
    
    
    /**
     * The DatagramChannel.
     */
    protected DatagramChannel datagramChannel;    
 
 
    public UDPSelectorHandler() {
        this(Role.CLIENT_SERVER);
    }
    
    
    public UDPSelectorHandler(boolean isClient) {
        this(boolean2Role(isClient));
    }

    
    public UDPSelectorHandler(Role role) {
        super(role);
    }


    @Override
    public void copyTo(Copyable copy) {
        super.copyTo(copy);
        UDPSelectorHandler copyHandler = (UDPSelectorHandler) copy;
        copyHandler.datagramSocket = datagramSocket;
        copyHandler.datagramChannel= datagramChannel;
    }

    /**
     * Before invoking Selector.select(), make sure the ServerScoketChannel
     * has been created. If true, then register all SelectionKey to the Selector.
     */
    @Override
    public void preSelect(Context ctx) throws IOException {

        if (asyncQueueReader == null) {
            asyncQueueReader = new UDPAsyncQueueReader(this);
        }

        if (asyncQueueWriter == null) {
            asyncQueueWriter = new UDPAsyncQueueWriter(this);
        }
        
        if (selector == null){
            initSelector(ctx);
        } else {
            processPendingOperations(ctx);
        }
    }

    private void initSelector(Context ctx) throws IOException{
        try{
            isShutDown.set(false);

            connectorInstanceHandler = new ConnectorInstanceHandler.
                    ConcurrentQueueDelegateCIH(
                    getConnectorInstanceHandlerDelegate());

            selector = Utils.openSelector();
            if (role != Role.CLIENT){
                datagramChannel = DatagramChannel.open();
                datagramSocket = datagramChannel.socket();
                if( receiveBufferSize > 0 ) {
                    try {
                        datagramSocket.setReceiveBufferSize( receiveBufferSize );
                    } catch( SocketException se ) {
                        if( logger.isLoggable( Level.FINE ) )
                            logger.log( Level.FINE, "setReceiveBufferSize exception ", se );
                    } catch( IllegalArgumentException iae ) {
                        if( logger.isLoggable( Level.FINE ) )
                            logger.log( Level.FINE, "setReceiveBufferSize exception ", iae );
                    }
                }
                if( sendBufferSize > 0 ) {
                    try {
                        datagramSocket.setSendBufferSize( sendBufferSize );
                    } catch( SocketException se ) {
                        if( logger.isLoggable( Level.FINE ) )
                            logger.log( Level.FINE, "setSendBufferSize exception ", se );
                    } catch( IllegalArgumentException iae ) {
                        if( logger.isLoggable( Level.FINE ) )
                            logger.log( Level.FINE, "setSendBufferSize exception ", iae );
                    }
                }
                datagramSocket.setReuseAddress(reuseAddress);
                if (inet == null) {
                    portRange.bind(datagramSocket);
                } else {
                    portRange.bind(datagramSocket, inet);
                }

                datagramChannel.configureBlocking(false);
                datagramChannel.register( selector, SelectionKey.OP_READ );

                datagramSocket.setSoTimeout(serverTimeout);

                port = datagramSocket.getLocalPort();
                inet = datagramSocket.getLocalAddress();
            }
            ctx.getController().notifyReady();
        } catch (SocketException ex){
            throw new BindException(ex.getMessage() + ": " + port);
        }
    }

    @Override
    protected SelectableChannel getSelectableChannel( SocketAddress remoteAddress, SocketAddress localAddress ) throws IOException {
        DatagramChannel newDatagramChannel = DatagramChannel.open();
        DatagramSocket newDatagramSocket = newDatagramChannel.socket();
        if( receiveBufferSize > 0 ) {
            try {
                newDatagramSocket.setReceiveBufferSize( receiveBufferSize );
            } catch( SocketException se ) {
                if( logger.isLoggable( Level.FINE ) )
                    logger.log( Level.FINE, "setReceiveBufferSize exception ", se );
            } catch( IllegalArgumentException iae ) {
                if( logger.isLoggable( Level.FINE ) )
                    logger.log( Level.FINE, "setReceiveBufferSize exception ", iae );
            }
        }
        if( sendBufferSize > 0 ) {
            try {
                newDatagramSocket.setSendBufferSize( sendBufferSize );
            } catch( SocketException se ) {
                if( logger.isLoggable( Level.FINE ) )
                    logger.log( Level.FINE, "setSendBufferSize exception ", se );
            } catch( IllegalArgumentException iae ) {
                if( logger.isLoggable( Level.FINE ) )
                    logger.log( Level.FINE, "setSendBufferSize exception ", iae );
            }
        }
        newDatagramSocket.setReuseAddress( reuseAddress );
        if( localAddress != null )
            newDatagramSocket.bind( localAddress );
        newDatagramChannel.configureBlocking( false );
        return newDatagramChannel;
    }

    /**
     * Handle new OP_CONNECT ops.
     */
    @Override
    protected void onConnectOp(Context ctx, 
            ConnectChannelOperation selectionKeyOp) throws IOException {
        DatagramChannel datagramChannel = (DatagramChannel) selectionKeyOp.getChannel();
        SocketAddress remoteAddress = selectionKeyOp.getRemoteAddress();
        CallbackHandler callbackHandler = selectionKeyOp.getCallbackHandler();

        CallbackHandlerSelectionKeyAttachment attachment =
                new CallbackHandlerSelectionKeyAttachment(callbackHandler);

        SelectionKey key = datagramChannel.register(selector,
                SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment);
        attachment.associateKey(key);

        try {
            datagramChannel.connect(remoteAddress);
        } catch(Exception e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Exception occured when tried to connect datagram channel", e);
            }
        }
        
        onConnectInterest(key, ctx);
    }
  
    
    /**
     * Shuntdown this instance by closing its Selector and associated channels.
     */
    @Override
    public void shutdown(){
        super.shutdown();
        try {
            if ( datagramSocket != null ) {
                datagramSocket.close();
                datagramSocket = null;
            }
        } catch (Throwable ex){
            Controller.logger().log(Level.SEVERE,
                    "closeSocketException",ex);
        }

        try{
            if ( datagramChannel != null) {
                datagramChannel.close();
                datagramChannel = null;
            }
        } catch (Throwable ex){
            Controller.logger().log(Level.SEVERE,
                    "closeSocketException",ex);
        }
    }


    /**
     * Handle OP_ACCEPT. Not used for UPD.
     */
    @Override
    public boolean onAcceptInterest(SelectionKey key, Context ctx) throws IOException{
        return false;
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends SelectionKeyHandler> getPreferredSelectionKeyHandler() {
        return BaseSelectionKeyHandler.class;
    }

    
    /**
     * A token describing the protocol supported by an implementation of this
     * interface
     */
    @Override
    public Controller.Protocol protocol(){
        return Controller.Protocol.UDP;
    }

    @Override
    public int getPortLowLevel() {
        if (datagramSocket != null) {
            return datagramSocket.getLocalPort();
        }
        
        return -1;
    }

    /**
     *
     * @param sndBuffSize
     */
    public void setSocketNativeSendBufferSize(int sendBuffSize) throws SocketException{
        datagramSocket.setSendBufferSize(sendBuffSize);
    }

    /**
     *
     * @param sndBuffSize
     */
    public void setSocketNativeReceiveBufferSize(int recBuffSize) throws SocketException{
        datagramSocket.setReceiveBufferSize(recBuffSize);        
    }

    /**
     *
     * @return
     * @throws java.net.SocketException
     */
    public int getSocketNativeReceiveBufferSize() throws SocketException{
        return datagramSocket.getReceiveBufferSize();
    }

    /**
     * 
     * @return
     * @throws java.net.SocketException
     */
    public int getSocketNativeSendBufferSize() throws SocketException{
        return datagramSocket.getSendBufferSize();
    }


    @Override
    public int getSsBackLog() {
        throw new IllegalStateException(NOT_SUPPORTED);
    }

    
    @Override
    public void setSsBackLog(int ssBackLog) {
        throw new IllegalStateException(NOT_SUPPORTED);
    }
    
    
    @Override
    public boolean isTcpNoDelay() {
        throw new IllegalStateException(NOT_SUPPORTED);
    }

    
    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
        throw new IllegalStateException(NOT_SUPPORTED);
    }
    
    
    @Override
    public int getLinger() {
       throw new IllegalStateException(NOT_SUPPORTED);
    }

    
    @Override
    public void setLinger(int linger) {
        throw new IllegalStateException(NOT_SUPPORTED);
    }    
    
    
    @Override
    public int getSocketTimeout() {
        throw new IllegalStateException(NOT_SUPPORTED);
    }

    
    @Override
    public void setSocketTimeout(int socketTimeout) {
        throw new IllegalStateException(NOT_SUPPORTED);
    }    

    @Override
    public void closeChannel(SelectableChannel channel) {
        try{
            channel.close();
        } catch (IOException ex){
            ; // LOG ME
        }
        
        if (asyncQueueReader != null) {
            asyncQueueReader.onClose(channel);
        }

        if (asyncQueueWriter != null) {
            asyncQueueWriter.onClose(channel);
        }
    }
    
    //--------------- ConnectorInstanceHandler -----------------------------
    @Override
    protected Callable<ConnectorHandler> getConnectorInstanceHandlerDelegate() {
        return new Callable<ConnectorHandler>() {
            public ConnectorHandler call() throws Exception {
                return new UDPConnectorHandler();
            }
        };
    }
}
