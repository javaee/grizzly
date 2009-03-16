/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */

package com.sun.grizzly;

import com.sun.grizzly.SelectionKeyOP.ConnectSelectionKeyOP;
import com.sun.grizzly.async.UDPAsyncQueueReader;
import com.sun.grizzly.async.UDPAsyncQueueWriter;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.State;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
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
            try{
                isShutDown.set(false);

                connectorInstanceHandler = new ConnectorInstanceHandler.
                        ConcurrentQueueDelegateCIH(
                        getConnectorInstanceHandlerDelegate());
                               
                datagramChannel = DatagramChannel.open();
                selector = Selector.open();
                if (role != Role.CLIENT){
                    datagramSocket = datagramChannel.socket();
                    datagramSocket.setReuseAddress(reuseAddress);
                    if (inet == null)
                        datagramSocket.bind(new InetSocketAddress(port));
                    else
                        datagramSocket.bind(new InetSocketAddress(inet,port));

                    datagramChannel.configureBlocking(false);
                    datagramChannel.register( selector, SelectionKey.OP_READ );
                                            
                    datagramSocket.setSoTimeout(serverTimeout); 
                } 
                ctx.getController().notifyReady();
            } catch (SocketException ex){
                throw new BindException(ex.getMessage() + ": " + port);
            }
   
        } else {
            processPendingOperations(ctx);
        }
    }

    
    /**
     * Register a CallBackHandler to this Selector.
     *
     * @param remoteAddress remote address to connect
     * @param localAddress local address to bin
     * @param callbackHandler {@link CallbackHandler}
     * @throws java.io.IOException
     */
    @Override
    protected void connect(SocketAddress remoteAddress, SocketAddress localAddress,
            CallbackHandler callbackHandler) throws IOException {

        DatagramChannel newDatagramChannel = DatagramChannel.open();
        newDatagramChannel.socket().setReuseAddress(reuseAddress);
        if (localAddress != null) {
            newDatagramChannel.socket().bind(localAddress);
        }

        newDatagramChannel.configureBlocking(false);

        SelectionKeyOP.ConnectSelectionKeyOP keyOP = new ConnectSelectionKeyOP();

        keyOP.setOp(SelectionKey.OP_CONNECT);
        keyOP.setChannel(newDatagramChannel);
        keyOP.setRemoteAddress(remoteAddress);
        keyOP.setCallbackHandler(callbackHandler);
        opToRegister.offer(keyOP);
        selector.wakeup();
    }

    /**
     * Handle new OP_CONNECT ops.
     */
    @Override
    protected void onConnectOp(Context ctx, 
            SelectionKeyOP.ConnectSelectionKeyOP selectionKeyOp) throws IOException {
        DatagramChannel newDatagramChannel = (DatagramChannel) selectionKeyOp.getChannel();
        SocketAddress remoteAddress = selectionKeyOp.getRemoteAddress();
        CallbackHandler callbackHandler = selectionKeyOp.getCallbackHandler();

        CallbackHandlerSelectionKeyAttachment attachment =
                CallbackHandlerSelectionKeyAttachment.create(callbackHandler);

        SelectionKey key = newDatagramChannel.register(selector,
                SelectionKey.OP_READ | SelectionKey.OP_WRITE, attachment);
        attachment.associateKey(key);

        try {
            newDatagramChannel.connect(remoteAddress);
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
        // If shutdown was called for this SelectorHandler
        if (isShutDown.getAndSet(true)) return;

        stateHolder.setState(State.STOPPED);
        
        try {
            if ( datagramSocket != null )
                datagramSocket.close();
        } catch (Throwable ex){
            Controller.logger().log(Level.SEVERE,
                    "closeSocketException",ex);
        }

        try{
            if ( datagramChannel != null)
                datagramChannel.close();
        } catch (Throwable ex){
            Controller.logger().log(Level.SEVERE,
                    "closeSocketException",ex);
        }

        try{
            if ( selector != null)
                selector.close();
        } catch (Throwable ex){
            Controller.logger().log(Level.SEVERE,
                    "closeSocketException",ex);
        }
        
        if (asyncQueueReader != null) {
            asyncQueueReader.close();
            asyncQueueReader = null;
        }

        if (asyncQueueWriter != null) {
            asyncQueueWriter.close();
            asyncQueueWriter = null;
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
