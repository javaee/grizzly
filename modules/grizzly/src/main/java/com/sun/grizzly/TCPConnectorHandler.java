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

import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.async.AsyncQueueReadUnit;
import com.sun.grizzly.async.AsyncReadCallbackHandler;
import com.sun.grizzly.async.AsyncReadCondition;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.async.ByteBufferCloner;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.OutputWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
     * A blocking {@link InputStream} that use a pool of Selector
     * to execute a blocking read operation.
     */
    private InputReader inputStream;
    
    
    /**
     * IsConnected Latch related
     */
    private CountDownLatch isConnectedLatch;
    
    
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
     * @param callbackHandler the handler invoked by its associated {@link SelectorHandler} when
     *        a non blocking operation is ready to be handled. When null, all 
     *        read and write operation will be delegated to the default
     *        {@link ProtocolChain} and its list of {@link ProtocolFilter} 
     *        . When null, this {@link ConnectorHandler} will create an instance of {@link DefaultCallbackHandler}.
     * @throws java.io.IOException
     */
    public void connect(SocketAddress remoteAddress,
            CallbackHandler callbackHandler) throws IOException {
        
        connect(remoteAddress,null,callbackHandler);
    }
    
    
    /**
     * Connect to hostname:port. When an aysnchronous event happens (e.g
     * OP_READ or OP_WRITE), the {@link Controller} will invoke
     * the CallBackHandler.
     * @param remoteAddress remote address to connect
     * @param localAddress local address to bind
     * @param callbackHandler the handler invoked by its associated {@link SelectorHandler} when
     *        a non blocking operation is ready to be handled. When null, all 
     *        read and write operation will be delegated to the default
     *        {@link ProtocolChain} and its list of {@link ProtocolFilter} 
     *        . When null, this {@link ConnectorHandler} will create an instance of {@link DefaultCallbackHandler}.
     * @throws java.io.IOException
     */
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress,
            CallbackHandler callbackHandler) throws IOException {
        
        if (controller == null){
            throw new IllegalStateException("Controller cannot be null");
        }
        
        connect(remoteAddress,localAddress,callbackHandler,
                (TCPSelectorHandler)controller.getSelectorHandler(protocol()));
    }
    
    
    /**
     * Connect to hostname:port. When an aysnchronous event happens (e.g
     * OP_READ or OP_WRITE), the {@link Controller} will invoke
     * the CallBackHandler.
     * @param remoteAddress remote address to connect
     * @param callbackHandler the handler invoked by its associated {@link SelectorHandler} when
     *        a non blocking operation is ready to be handled. When null, all 
     *        read and write operation will be delegated to the default
     *        {@link ProtocolChain} and its list of {@link ProtocolFilter} 
     *        . When null, this {@link ConnectorHandler} will create an instance of {@link DefaultCallbackHandler}.
     * @param selectorHandler an instance of SelectorHandler.
     * @throws java.io.IOException
     */
    public void connect(SocketAddress remoteAddress,
            CallbackHandler callbackHandler,
            TCPSelectorHandler selectorHandler) throws IOException {
        
        connect(remoteAddress,null,callbackHandler,selectorHandler);
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
        
        synchronized(this) {
            // Wait for the onConnect to be invoked.
            isConnectedLatch = new CountDownLatch(1);

            selectorHandler.connect(remoteAddress, localAddress,
                    callbackHandler);
        }
        inputStream = new InputReader();
        
        try {
            isConnectedLatch.await(connectionTimeout, TimeUnit.MILLISECONDS);
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
     * @throws java.io.IOException
     */
    public void connect(SocketAddress remoteAddress)
            throws IOException {
        connect(remoteAddress,(SocketAddress)null);
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
        
        connect(remoteAddress,localAddress,callbackHandler,
                (TCPSelectorHandler)controller.getSelectorHandler(protocol()));
    }


    /**
     * Read bytes. If blocking is set to <tt>true</tt>, a pool of temporary
     * {@link Selector} will be used to read bytes.
     * @param byteBuffer The byteBuffer to store bytes.
     * @param blocking <tt>true</tt> if a a pool of temporary Selector
     *        is required to handle a blocking read.
     * @return number of bytes read
     * @throws java.io.IOException
     */
    public long read(ByteBuffer byteBuffer, boolean blocking) throws IOException {
        if (!isConnected){
            throw new NotYetConnectedException();
        }
        
        SelectionKey key = underlyingChannel.keyFor(selectorHandler.getSelector());
        if (blocking){
            inputStream.setSelectionKey(key);
            return inputStream.read(byteBuffer);
        } else {
            if (callbackHandler == null){
                throw new IllegalStateException
                        ("Non blocking read needs a CallbackHandler");
            }
            int nRead = -1;
            
            try{
                nRead = ((SocketChannel) underlyingChannel).read(byteBuffer);
            } catch (IOException ex){
                nRead = -1;
                throw ex;
            } finally {
                if (nRead == -1){
                    SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                    if (skh instanceof BaseSelectionKeyHandler){                  
                        ((DefaultSelectionKeyHandler)skh).notifyRemotlyClose(key);                            
                    } 
                }
            }
            
            if (nRead == 0){
                selectorHandler.register(key,SelectionKey.OP_READ);
            }
            return nRead;
        }
    }
    
    
    /**
     * Writes bytes. If blocking is set to <tt>true</tt>, a pool of temporary
     * {@link Selector} will be used to writes bytes.
     * @param byteBuffer The byteBuffer to write.
     * @param blocking <tt>true</tt> if a a pool of temporary Selector
     *        is required to handle a blocking write.
     * @return number of bytes written
     * @throws java.io.IOException
     */
    public long write(ByteBuffer byteBuffer, boolean blocking) throws IOException {
        if (!isConnected){
            throw new NotYetConnectedException();
        }
        
        if (blocking){
            return OutputWriter.flushChannel(underlyingChannel, byteBuffer);
        } else {
            
            if (callbackHandler == null){
                throw new IllegalStateException
                        ("Non blocking write needs a CallbackHandler");
            }
            
            SelectionKey key = underlyingChannel.keyFor(selectorHandler.getSelector());
            int nWrite = 1;
            int totalWriteBytes = 0;
            try{
                while (nWrite > 0 && byteBuffer.hasRemaining()){
                    nWrite = ((SocketChannel) underlyingChannel).write(byteBuffer);
                    totalWriteBytes += nWrite;
                }
            } catch (IOException ex){
                nWrite = -1;
                throw ex;
            } finally {
                if (nWrite == -1){
                    SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                    if (skh instanceof BaseSelectionKeyHandler){                  
                        ((DefaultSelectionKeyHandler)skh).notifyRemotlyClose(key);                            
                    }                 
                }
            } 
            
            if (totalWriteBytes == 0 && byteBuffer.hasRemaining()){
                selectorHandler.register(key,SelectionKey.OP_WRITE);
            }
            return totalWriteBytes;
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler) throws IOException {
        return readFromAsyncQueue(buffer, callbackHandler, null);
    }

    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler, 
            AsyncReadCondition condition) throws IOException {
        return readFromAsyncQueue(buffer, callbackHandler, condition, null);
    }

    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler, 
            AsyncReadCondition condition, 
            AsyncQueueDataProcessor readPostProcessor) throws IOException {
        return selectorHandler.getAsyncQueueReader().read(
                underlyingChannel.keyFor(selectorHandler.getSelector()), buffer,
                callbackHandler, condition, readPostProcessor);
    }

    
    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer)
            throws IOException {
        return writeToAsyncQueue(buffer, null);
    }


    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler) throws IOException {
        return writeToAsyncQueue(buffer, callbackHandler, null);
    }


    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler,
            AsyncQueueDataProcessor writePreProcessor) throws IOException {
        return writeToAsyncQueue(buffer, callbackHandler, writePreProcessor,
                null);
    }

    
    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler,
            AsyncQueueDataProcessor writePreProcessor,
            ByteBufferCloner cloner) throws IOException {
        return selectorHandler.getAsyncQueueWriter().write(
                underlyingChannel.keyFor(selectorHandler.getSelector()), buffer,
                callbackHandler, writePreProcessor, cloner);
    }

    
    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer)
            throws IOException {
        return writeToAsyncQueue(dstAddress, buffer, null);
    }


    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler) throws IOException {
        return writeToAsyncQueue(dstAddress, buffer, callbackHandler, null);
    }


    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler, 
            AsyncQueueDataProcessor writePreProcessor) throws IOException {
        return writeToAsyncQueue(dstAddress, buffer, callbackHandler,
                writePreProcessor, null);
    }

    
    /**
     * {@inheritDoc}
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler, 
            AsyncQueueDataProcessor writePreProcessor, ByteBufferCloner cloner)
            throws IOException {
        return selectorHandler.getAsyncQueueWriter().write(
                underlyingChannel.keyFor(selectorHandler.getSelector()), dstAddress,
                buffer, callbackHandler, writePreProcessor, cloner);
    }

    
    /**
     * Close the underlying connection.
     */
    public void close() throws IOException{
        if (underlyingChannel != null){
            if (selectorHandler != null){
                SelectionKey key =
                        underlyingChannel.keyFor(selectorHandler.getSelector());
                
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
        try{
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
        } catch (IOException ex){
            throw ex;
        } finally {
            synchronized(this) {
                isConnectedLatch.countDown();
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
                    "setSoLinger exception ",ex);
        }
        
        try{
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (SocketException ex){
            Controller.logger().log(Level.WARNING,
                    "setTcpNoDelay exception ",ex);
        }
        
        try{
            socket.setReuseAddress(reuseAddress);
        } catch (SocketException ex){
            Controller.logger().log(Level.WARNING,
                    "setReuseAddress exception ",ex);
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
