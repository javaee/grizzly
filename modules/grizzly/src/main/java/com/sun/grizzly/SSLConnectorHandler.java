/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.async.AsyncQueueReadUnit;
import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.async.AsyncReadCallbackHandler;
import com.sun.grizzly.async.AsyncReadCondition;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import com.sun.grizzly.async.ByteBufferCloner;
import com.sun.grizzly.util.FutureImpl;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.SSLOutputWriter;
import com.sun.grizzly.util.SSLUtils;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;

/**
 * <p>
 * Non blocking SSL Connector Handler. The recommended way to use this class
 * is by creating an external Controller and share the same SelectorHandler
 * instance.
 * </p><p>
 * Recommended
 * -----------
 * </p><p><pre><code>
 * Controller controller = new Controller();
 * // new SSLSelectorHandler(true) means the Selector will be used only
 * // for client operation (OP_READ, OP_WRITE, OP_CONNECT).
 * SSLSelectorHandler sslSelectorHandler = new SSLSelectorHandler(true);
 * controller.setSelectorHandler(sslSelectorHandler);
 * SSLConnectorHandler sslConnectorHandler = new SSLConnectorHandler();
 * sslConnectorHandler.connect(localhost,port, new SSLCallbackHandler(){...},
 *                             sslSelectorHandler);
 * SSLConnectorHandler sslConnectorHandler2 = new SSLConnectorHandler();
 * sslConnectorHandler2.connect(localhost,port, new SSLCallbackHandler(){...},
 *                             sslSelectorHandler);
 * </code></pre></p><p>
 * Not recommended (but still works)
 * ---------------------------------
 * </p><p><pre><code>
 * SSLConnectorHandler sslConnectorHandler = new SSLConnectorHandler();
 * sslConnectorHandler.connect(localhost,port);
 *
 * Internally, an new Controller will be created everytime connect(localhost,port)
 * is invoked, which has an impact on performance.
 *
 * As common comment: developer should be very careful if dealing directly with
 * <code>SSLConnectorHandler</code>'s underlying socket channel! In most cases
 * there is no need to do this, but use read, write methods provided
 * by <code>SSLConnectorHandler</code>
 * </code></pre></p>
 *
 * @author Alexey Stashok
 * @author Jeanfrancois Arcand
 */
public class SSLConnectorHandler
        extends AbstractConnectorHandler<SSLSelectorHandler, SSLCallbackHandler> {

    /**
     * Default Logger.
     */
    private static Logger logger = Logger.getLogger("grizzly");
    
    /*
     * An empty ByteBuffer used for handshaking
     */
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    
    /**
     * Input buffer for reading encrypted data from channel
     */
    private ByteBuffer securedInputBuffer;
    
    /**
     * Output buffer, which contains encrypted data ready for writing to channel
     */
    private ByteBuffer securedOutputBuffer;
    
    /**
     * Buffer, where application data could be written during a asynchronous handshaking.
     * It is set when user application calls: SSLConnectorHandler.handshake(appDataBuffer)
     * and references appDataBuffer.
     */
    private ByteBuffer asyncHandshakeBuffer;
    
    /**
     * Is the handshake phase completed
     */
    private volatile boolean isHandshakeDone;

    
    /**
     * IsConnected future
     */
    private volatile FutureImpl<Boolean> isConnectedFuture;
    
    /**
     * Are we creating a controller every run.
     */
    private boolean isStandalone = false;
    
    /**
     * Is async handshake in progress
     */
    private boolean isProcessingAsyncHandshake;
    
    /**
     * Result of last{@link SSLEngine} operation
     */
    private SSLEngineResult sslLastOperationResult;
    
    /**
     * Current handshake status
     */
    private SSLEngineResult.HandshakeStatus handshakeStatus;
    
    /**
     * Current{@link SSLEngine} status
     */
    private SSLEngineResult.Status sslEngineStatus = null;
    
    
    /**
     * Are we creating a controller every run.
     */
    private boolean delegateSSLTasks;
    
    /**
     * Connector's{@link SSLEngine}
     */
    private SSLEngine sslEngine;
    
    /**
     * Connector's {@link SSLContext}
     */
    private SSLContext sslContext;
    
    /**
     * SSL read postprocessor for <code>AsyncQueueReadable</code>
     */
    private final AsyncQueueDataProcessor sslReadPostProcessor;

    /**
     * SSL write preprocessor for <code>AsyncQueueWritable</code>
     */
    private final AsyncQueueDataProcessor sslWritePreProcessor;
    
    /**
     * Connector's write mode
     */
    private boolean isAsyncWriteQueueMode;
    
    /**
     * Connector's read mode
     */
    private boolean isAsyncReadQueueMode;

    public SSLConnectorHandler() {
        this(SSLConfig.DEFAULT_CONFIG.createSSLContext());
    }
    
    public SSLConnectorHandler(SSLConfig sslConfig) {
        this(sslConfig.createSSLContext());
    }
    
    public SSLConnectorHandler(SSLContext sslContext) {
        this.sslContext = sslContext;
        sslReadPostProcessor = new SSLReadPostProcessor();
        sslWritePreProcessor = new SSLWritePreProcessor();
        protocol(Protocol.TLS);
    }
    
    public boolean getDelegateSSLTasks() {
        return delegateSSLTasks;
    }
    
    public void setDelegateSSLTasks(boolean delegateSSLTasks) {
        this.delegateSSLTasks = delegateSSLTasks;
    }
    
    /**
     * Connect to hostname:port. When an aysnchronous event happens (e.g
     * OP_READ or OP_WRITE), the {@link Controller} will invoke
     * the {@link CallbackHandler}.
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
            SSLCallbackHandler callbackHandler,
            SSLSelectorHandler selectorHandler) throws IOException {
        if (isConnected) {
            throw new AlreadyConnectedException();
        }
        
        if (controller == null) {
            throw new IllegalStateException("Controller cannot be null");
        }
        
        if (selectorHandler == null) {
            throw new IllegalStateException("SelectorHandler cannot be null");
        }
        
        this.selectorHandler = selectorHandler;
        if (callbackHandler == null){
            this.callbackHandler = new DefaultCallbackHandler(this);
        } else {
            this.callbackHandler = callbackHandler;
        }
        
        // Wait for the onConnect to be invoked.
        isConnectedFuture = new FutureImpl<Boolean>();
        
        selectorHandler.connect(remoteAddress, localAddress, 
                new SSLInternalCallbackHandler());
        
        try {
            isConnectedFuture.get(30, TimeUnit.SECONDS);
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
        if (isConnected) {
            throw new AlreadyConnectedException();
        }
        
        if (controller == null) {
            isStandalone = true;
            controller = new Controller();
            controller.setSelectorHandler(new SSLSelectorHandler(true));
            
            final CountDownLatch latch = new CountDownLatch(1);
            controller.addStateListener(new ControllerStateListenerAdapter() {
                @Override
                public void onReady() {
                    latch.countDown();
                }
                
                @Override
                public void onException(Throwable e) {
                    if (latch.getCount() > 0) {
                        logger.log(Level.SEVERE, "Error occured on Controller startup: ", e);
                    }
                    
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
        
        connect(remoteAddress, localAddress, callbackHandler);
    }
    
    /**
     * Initiate SSL handshake phase.
     * Handshake is required to be done after connection established.
     *
     * @param byteBuffer Application {@link ByteBuffer}, where application data
     * will be stored
     * @param blocking true, if handshake should be done in blocking mode, for non-blocking false
     * @return If blocking parameter is true - method should always return true if handshake is done,
     * or throw IOException otherwise. For non-blocking mode method returns true if handshake is done, or false
     * if handshake will be completed in non-blocking manner.
     * If False returned - <code>SSLConnectorHandler</code> will call callbackHandler.onHandshake() to notify
     * about finishing handshake phase.
     * @throws java.io.IOException if some error occurs during processing I/O operations/
     */
    public boolean handshake(ByteBuffer byteBuffer, boolean blocking)
            throws IOException {
        sslEngine.beginHandshake();
        handshakeStatus = sslEngine.getHandshakeStatus();
        
        if (blocking) {
            SSLUtils.doHandshake(underlyingChannel, byteBuffer, securedInputBuffer,
                    securedOutputBuffer, sslEngine, handshakeStatus);
            securedOutputBuffer.limit(securedOutputBuffer.position());
            finishHandshake();
            
            // Sync should be always done
            return true;
        } else {
            return doAsyncHandshake(byteBuffer);
        }
    }
    
    /**
     * Read bytes. If blocking is set to <tt>true</tt>, a pool of temporary
     * {@link Selector} will be used to read bytes.
     * @param byteBuffer The byteBuffer to store bytes.
     * @param blocking <tt>true</tt> if a a pool of temporary Selector
     *        is required to handle a blocking read.
     * @return number of bytes read from a channel.
     * Be careful, because return value represents the length of encrypted data,
     * which was read from a channel. Don't use return value to determine the
     * availability of a decrypted data to process, but use byteBuffer.remaining().
     * @throws java.io.IOException
     */
    @Override
    public long read(ByteBuffer byteBuffer, boolean blocking)
            throws IOException {
        if (!isConnected) {
            throw new NotYetConnectedException();
        }
        
        if (blocking) {
            return SSLUtils.doSecureRead(underlyingChannel, sslEngine,
                    byteBuffer, securedInputBuffer).bytesRead;
        } else {
            isAsyncReadQueueMode = false;
            int nRead = doReadAsync(byteBuffer);
            
            if (nRead == 0) {
                registerSelectionKeyFor(SelectionKey.OP_READ);
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
     * @return number of bytes written on a channel.
     * Be careful, as non-crypted data is passed, but crypted data is written
     * on channel. Don't use return value to determine the
     * number of bytes from original buffer, which were written.
     * @throws java.io.IOException
     */
    @Override
    public long write(ByteBuffer byteBuffer, boolean blocking)
            throws IOException {
        if (!isConnected) {
            throw new NotYetConnectedException();
        }
        
        if (blocking) {
            long nWrite = SSLOutputWriter.flushChannel(underlyingChannel,
                    byteBuffer, securedOutputBuffer, sslEngine);
            // Mark securedOutputBuffer as empty
            securedOutputBuffer.position(securedOutputBuffer.limit());
            return nWrite;
        } else {
            if (callbackHandler == null) {
                throw new IllegalStateException("Non blocking write needs a CallbackHandler");
            }
            
            isAsyncWriteQueueMode = false;
            int nWrite = 1;
            int totalWrite = 0;
            
            while (nWrite > 0 &&
                    (byteBuffer.hasRemaining() ||
                    securedOutputBuffer.hasRemaining())) {
                nWrite = doWriteAsync(byteBuffer);
                totalWrite += nWrite;
            }
            
            if (byteBuffer.hasRemaining() ||
                    securedOutputBuffer.hasRemaining()) {
                registerSelectionKeyFor(SelectionKey.OP_WRITE);
            }
            
            return totalWrite;
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler,
            AsyncReadCondition condition,
            AsyncQueueDataProcessor readPostProcessor) throws IOException {
        isAsyncReadQueueMode = true;
        return super.readFromAsyncQueue( buffer,
                                         callbackHandler,
                                         condition,
                                         readPostProcessor != null ? readPostProcessor : obtainSSLReadPostProcessor() );
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler,
            AsyncQueueDataProcessor writePreProcessor,
            ByteBufferCloner cloner) throws IOException {
        isAsyncWriteQueueMode = true;
        return super.writeToAsyncQueue( buffer,
                                        callbackHandler,
                                        writePreProcessor != null ? writePreProcessor : obtainSSLWritePreProcessor(),
                                        cloner );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler, 
            AsyncQueueDataProcessor writePreProcessor, ByteBufferCloner cloner)
            throws IOException {
        isAsyncWriteQueueMode = true;
        return super.writeToAsyncQueue( dstAddress,
                                        buffer,
                                        callbackHandler,
                                        writePreProcessor != null ? writePreProcessor : obtainSSLWritePreProcessor(),
                                        cloner );
    }

    
    /**
     * Close the underlying connection.
     */
    public void close() throws IOException {
        if (logger.isLoggable(Level.FINE)) {
            IOException ioe = new IOException("Logging stacktrace...");
            logger.log(Level.FINE, "Closing SSLConnectorHandler " + this +
                    " Channel: " + underlyingChannel + " engine: " + sslEngine, ioe);
        }

        if (underlyingChannel != null) {
            if (isConnected) {
                try {
                    if (securedOutputBuffer.hasRemaining()) {
                        // if there is something is securedOutputBuffer - flush it
                        OutputWriter.flushChannel(underlyingChannel,
                                securedOutputBuffer);
                    }
                    
                    // Close secure outbound channel and flush data
                    sslEngine.closeOutbound();
                    SSLUtils.wrap(EMPTY_BUFFER, securedOutputBuffer, sslEngine);
                    OutputWriter.flushChannel(underlyingChannel,
                            securedOutputBuffer);
                } catch (IOException e) {
                    logger.log(Level.FINE,
                            "IOException during closing the connector.", e);
                }
            }
            
            if (selectorHandler != null) {
                SelectionKey key =
                        selectorHandler.keyFor(underlyingChannel);
                
                if (key == null) {
                    return;
                }
                selectorHandler.getSelectionKeyHandler().close(key);
            }
            
            underlyingChannel.close();
        }
        
        if (controller != null && isStandalone) {
            controller.stop();
            controller = null;
        }
        
        sslEngine = null;
        asyncHandshakeBuffer = null;
        isStandalone = false;
        isConnected = false;
        isHandshakeDone = false;
    }
    
    
    /**
     * Finish handling the OP_CONNECT interest ops.
     * @param key - a {@link SelectionKey}
     */
    public void finishConnect(SelectionKey key) throws IOException{
        Throwable error = null;

        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Finish connect");
            }

            final SocketChannel socketChannel = (SocketChannel) key.channel();
            underlyingChannel = socketChannel;
            
            socketChannel.finishConnect();
            isConnected = socketChannel.isConnected();
            if (isConnected) {
                initSSLEngineIfRequired();
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
     * Changes SSLConnectorHandler state, after handshake operation is done.
     * Normally should not be called by outside Grizzly, just in case when custom
     * handshake code was used.
     */
    public void finishHandshake() {
        isProcessingAsyncHandshake = false;
        isHandshakeDone = true;
    }
    
    /**
     * A token decribing the protocol supported by an implementation of this
     * interface
     * @return this {@link ConnectorHandler}'s protocol
     */
    @Override
    public Controller.Protocol protocol() {
        return Controller.Protocol.TLS;
    }
    
    
    /**
     * Is the underlying SocketChannel connected.
     * @return <tt>true</tt> if connected, otherwise <tt>false</tt>
     */
    public boolean isHandshakeDone() {
        return isHandshakeDone && !isProcessingAsyncHandshake;
    }

    
    /**
     * Get SSLConnector's {@link SSLContext}
     */
    public SSLContext getSSLContext() {
        return sslContext;
    }
    
    
    /**
     * Set {@link SSLContext}.
     * Use this method to change SSLConnectorHandler configuration.
     * New configuration will become active only after SSLConnector
     * will be closed and connected again.
     */
    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }
    
    
    /**
     * Configure SSLConnectorHandler's SSL settings.
     *
     * Use this method to change SSLConnectorHandler configuration.
     * New configuration will become active only after SSLConnector
     * will be closed and connected again.
     */
    public void configure(SSLConfig sslConfig) {
        this.sslContext = sslConfig.createSSLContext();
    }
    
    /**
     * Returns SSLConnector's{@link SSLEngine}
     * @return{@link SSLEngine}
     */
    public SSLEngine getSSLEngine() {
        return sslEngine;
    }
    
    /**
     * Set{@link SSLEngine}
     * @param sslEngine{@link SSLEngine}
     */
    public void setSSLEngine(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }
    
    /**
     * Returns <code>SSLConnectorHandler</code>'s secured input buffer, it
     * uses for reading data from a socket channel.
     * @return secured input {@link ByteBuffer}
     */
    public ByteBuffer getSecuredInputBuffer() {
        return securedInputBuffer;
    }
    
    /**
     * Returns <code>SSLConnectorHandler</code>'s secured output buffer, it
     * uses for writing data to a socket channel.
     * @return secured output {@link ByteBuffer}
     */
    public ByteBuffer getSecuredOutputBuffer() {
        return securedOutputBuffer;
    }

    
    /**
     * Gets the size of the largest application buffer that may occur when
     * using this session.
     * SSLEngine application data buffers must be large enough to hold the
     * application data from any inbound network application data packet
     * received. Typically, outbound application data buffers can be of any size.
     *
     * (javadoc is taken from SSLSession.getApplicationBufferSize())
     * @return largets application buffer size, which may occur
     */
    public int getApplicationBufferSize() {
        initSSLEngineIfRequired();
        return sslEngine.getSession().getApplicationBufferSize();
    }
        
    /**
     * Read a data from channel in async mode and decrypt
     * @param byteBuffer buffer for decrypted data
     * @return number of bytes read from a channel
     * @throws java.io.IOException
     */
    private int doReadAsync(ByteBuffer byteBuffer) throws IOException {
        // Clear or compact secured input buffer
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "SSLConnectorHandler compactBuffer" +
                    " securedInputBuffer: " + securedInputBuffer);
        }
        clearOrCompactBuffer(securedInputBuffer);
        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "SSLConnectorHandler read" +
                    " securedInputBuffer: " + securedInputBuffer);
        }
        // Read data to secured buffer
        int bytesRead = -1;
        
        try{
            bytesRead = ((SocketChannel) underlyingChannel).read(securedInputBuffer);
        } finally {
           if (bytesRead == -1){
                SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                if (skh instanceof BaseSelectionKeyHandler){                  
                    ((DefaultSelectionKeyHandler)skh).notifyRemotlyClose(getSelectionKey());                            
                }  
            }
        }
        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "SSLConnectorHandler done read" +
                    " securedInputBuffer: " + securedInputBuffer +
                    " bytesRead: " + bytesRead);
        }
        if (bytesRead == -1) {
            try {
                sslEngine.closeInbound();
                // check if there is some secured data still available
                if (securedInputBuffer.position() == 0 ||
                        sslEngineStatus == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    return -1;
                }
            } catch (SSLException e) {
                return -1;
            }
        }
        
        securedInputBuffer.flip();
        
        if (bytesRead == 0 && !securedInputBuffer.hasRemaining()) {
            return 0;
        }
        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "SSLConnectorHandler unwrapall" +
                    " securedInputBuffer: " + securedInputBuffer);
        }
        int bytesProduced = unwrapAll(byteBuffer);
        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "SSLConnectorHandler unwrapall done" +
                    " securedInputBuffer: " + securedInputBuffer,
                    " produced: " + bytesProduced +
                    " status: " + sslEngineStatus);
        }
        if (bytesProduced == 0) {
            if (sslEngineStatus == SSLEngineResult.Status.CLOSED) {
                return -1;
            } else if (sslEngineStatus == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                throw new BufferOverflowException();
            }
        }
        
        return bytesRead;
    }
    
    private int unwrapAll(ByteBuffer byteBuffer) throws SSLException {
        SSLEngineResult result = null;
        int bytesProduced = 0;
        
        do {
            result = sslEngine.unwrap(securedInputBuffer, byteBuffer);
            bytesProduced += result.bytesProduced();
            // During handshake phase several unwrap actions could be executed on read data
        } while (result.getStatus() == SSLEngineResult.Status.OK && 
                (isHandshakeDone || (result.getHandshakeStatus() == 
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP && 
                result.bytesProduced() == 0)));
        
        updateSSLEngineStatus(result);
        return bytesProduced;
    }
    
    /**
     * Write secured data to channel in async mode
     *
     * @param byteBuffer non-crypted data buffer
     * @return number of bytes written on a channel.
     * Be careful, as non-crypted data is passed, but crypted data is written
     * on channel. Don't use return value to determine,
     * number of bytes from original buffer, which were written.
     * @throws java.io.IOException
     */
    private int doWriteAsync(ByteBuffer byteBuffer) throws IOException {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "asyncWrite. securedBuffer: " +
                    securedOutputBuffer);
        }

        if (securedOutputBuffer.hasRemaining() && !flushSecuredOutputBuffer()) {
            return 0;
        }
        
        securedOutputBuffer.clear();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "asyncWrite. wrap sslEngine: " + sslEngine +
                    " securedOutputBuffer: " + securedOutputBuffer +
                    " byteBuffer: " + byteBuffer);
        }
        SSLEngineResult result = SSLUtils.wrap(byteBuffer, securedOutputBuffer, sslEngine);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "asyncWrite. wrap done sslEngine: " + sslEngine +
                    " securedOutputBuffer: " + securedOutputBuffer +
                    " byteBuffer: " + byteBuffer +
                    " status: " + result);
        }
        
        updateSSLEngineStatus(result);
        
        int count = ((SocketChannel) underlyingChannel).write(securedOutputBuffer);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "asyncWrite. written: " + count +
                    " securedOutputBuffer: " + securedOutputBuffer);
        }

        return count;
    }
    
    /**
     * Perform an SSL handshake in async mode.
     * @param byteBuffer The application {@link ByteBuffer}
     *
     * @throws IOException if the handshake fail.
     */
    private boolean doAsyncHandshake(ByteBuffer byteBuffer) throws IOException {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "SSLConnectorHandler.doAsyncHandshake");
        }

        SSLEngineResult result;
        isProcessingAsyncHandshake = true;
        asyncHandshakeBuffer = byteBuffer;
        while (handshakeStatus != HandshakeStatus.FINISHED) {
            switch (handshakeStatus) {
                case NEED_WRAP:
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "SSLConnectorHandler NEED_WRAP sslEngine: " + sslEngine +
                                " securedBuffer: " + securedOutputBuffer);
                    }
                    result = SSLUtils.wrap(EMPTY_BUFFER, securedOutputBuffer, sslEngine);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "SSLConnectorHandler NEED_WRAP done sslEngine: " + sslEngine +
                                " securedBuffer: " + securedOutputBuffer +
                                " result: " + result +
                                " handshakeStatus: " + result.getHandshakeStatus());
                    }
                    updateSSLEngineStatus(result);
                    switch (result.getStatus()) {
                        case OK:
                            if (!flushSecuredOutputBuffer()) {
                                if (logger.isLoggable(Level.FINER)) {
                                    logger.log(Level.FINER,
                                            "SSLConnectorHandler pending secured buffer flush: " + sslEngine +
                                            " securedBuffer: " + securedOutputBuffer);
                                }
                                return false;
                            }
                            break;
                        default:
                            throw new IOException("Handshaking error: " + result.getStatus());
                    }
                    
                    if (handshakeStatus != HandshakeStatus.NEED_UNWRAP) {
                        break;
                    }
                case NEED_UNWRAP:
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "SSLConnectorHandler NEED_UNWRAP sslEngine: " + sslEngine +
                                " byteBuffer: " + byteBuffer);
                    }
                    int bytesRead = doReadAsync(byteBuffer);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "SSLConnectorHandler NEED_UNWRAP done sslEngine: " + sslEngine +
                                " byteBuffer: " + byteBuffer +
                                " bytesRead: " + bytesRead +
                                " handshakeStatus: " + handshakeStatus);
                    }
                    if (bytesRead == -1) {
                        try {
                            sslEngine.closeInbound();
                        } catch (IOException e) {
                            logger.log(Level.FINE, "Exception occured when closing sslEngine inbound.", e);
                        }
                        
                        throw new EOFException("Connection closed");
                    } else if (bytesRead == 0 && sslLastOperationResult.bytesConsumed() == 0) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "SSLConnectorHandler NEED_UNWRAP reregister key sslEngine: " + sslEngine);
                        }
                        registerSelectionKeyFor(SelectionKey.OP_READ);
                        return false;
                    }
                    
                    if (handshakeStatus != HandshakeStatus.NEED_TASK) {
                        break;
                    }
                case NEED_TASK:
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "SSLConnectorHandler NEED_TASK sslEngine: " + sslEngine);
                    }
                    handshakeStatus = executeDelegatedTask();
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "SSLConnectorHandler NEED_TASK sslEngine: " + sslEngine +
                                " handshakeStatus: " + handshakeStatus);
                    }
                    break;
                default:
                    throw new RuntimeException("Invalid Handshaking State" + handshakeStatus);
            }
        }
        
        if (isProcessingAsyncHandshake) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "SSLConnectorHandler finishHandshake sslEngine: " + sslEngine);
            }
            finishHandshake();
        }
        
        asyncHandshakeBuffer = null;
        return true;
    }
    
    /**
     * Complete hanshakes operations.
     * @return SSLEngineResult.HandshakeStatus
     */
    private SSLEngineResult.HandshakeStatus executeDelegatedTask() {
        Runnable runnable;
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
        
        return sslEngine.getHandshakeStatus();
    }

    /**
     * Update <code>SSLConnectorHandler</code> internal status with
     * last{@link SSLEngine} operation result.
     *
     * @param result last{@link SSLEngine} operation result
     */
    private void updateSSLEngineStatus(SSLEngineResult result) {
        sslLastOperationResult = result;
        sslEngineStatus = result.getStatus();
        handshakeStatus = result.getHandshakeStatus();
    }
    
    /**
     * Clears buffer if there is no info available, or compact buffer otherwise.
     * @param buffer byte byffer
     */
    private static void clearOrCompactBuffer(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            buffer.clear();
        } else if (buffer.remaining() < buffer.capacity()) {
            buffer.compact();
        }
    }
    
    /**
     * Gets <code>SSLConnectorHandler</code> {@link SelectionKey}
     * @return {@link SelectionKey}
     */
    private SelectionKey getSelectionKey() {
        return selectorHandler.keyFor(underlyingChannel);
    }
    
    /**
     * Registers <code>SSLConnectorHandler<code>'s {@link SelectionKey}
     * to listen channel operations.
     * @param ops interested channel operations
     */
    private void registerSelectionKeyFor(int ops) {
        SelectionKey key = getSelectionKey();
        selectorHandler.register(key, ops);
    }
    
    /**
     * Flushes as much as possible bytes from the secured output buffer
     * @return true if secured buffer was completely flushed, false otherwise
     */
    private boolean flushSecuredOutputBuffer() throws IOException {
        int nWrite = 1;
        
        try{
            while (nWrite > 0 && securedOutputBuffer.hasRemaining()) {
                nWrite = ((SocketChannel) underlyingChannel).write(securedOutputBuffer);
            }
        } catch (IOException ex){
            nWrite = -1;
            throw ex;
        } finally{
            if (nWrite == -1){
                SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                if (skh instanceof BaseSelectionKeyHandler){                  
                    ((DefaultSelectionKeyHandler)skh).notifyRemotlyClose(getSelectionKey());                            
                }                 
            }
        }
        
        if (securedOutputBuffer.hasRemaining()) {
            SelectionKey key = selectorHandler.keyFor(underlyingChannel);
            selectorHandler.register(key, SelectionKey.OP_WRITE);
            
            return false;
        }
        
        return true;
    }
    
    /**
     * Initiate{@link SSLEngine} and related secure buffers
     */
    private void initSSLEngineIfRequired() {
        if (sslEngine == null) {
            sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(true);
        }
            
        int bbSize = sslEngine.getSession().getPacketBufferSize();
        securedInputBuffer = ByteBuffer.allocate(bbSize * 2);
        securedOutputBuffer = ByteBuffer.allocate(bbSize * 2);
        securedOutputBuffer.limit(0);
    }
    
    private AsyncQueueDataProcessor obtainSSLReadPostProcessor() {
        return sslReadPostProcessor;
    }

    private AsyncQueueDataProcessor obtainSSLWritePreProcessor() {
        return sslWritePreProcessor;
    }
    
    /**
     * Internal SSL CallbackHandler, which is able to process properly SSL handshake
     * phase and translate its calls to the custom SSLCallbackHandler.
     */
    private class SSLInternalCallbackHandler implements CallbackHandler {
        public void onConnect(IOEvent ioEvent) {
            callbackHandler.onConnect(ioEvent);
        }

        public void onRead(IOEvent ioEvent) {
            if (!isAsyncReadQueueMode) {
                try {
                    // if processing handshake - pass the data to handshake related code
                    if (isProcessingAsyncHandshake) {
                        if (doAsyncHandshake(asyncHandshakeBuffer)) {
                            callbackHandler.onHandshake(ioEvent);
                        }

                        return;
                    }

                    callbackHandler.onRead(ioEvent);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Exception occured when reading from SSL channel.", e);
                }
            }
        }

        public void onWrite(IOEvent ioEvent) {
            if (!isAsyncWriteQueueMode) {
                try {
                    // check if all the secured data was written, if not -
                    // flush as much as possible.
                    if (!securedOutputBuffer.hasRemaining() || flushSecuredOutputBuffer()) {
                        // if no encrypted data left in buffer - continue processing
                        if (isProcessingAsyncHandshake) {
                            if (doAsyncHandshake(asyncHandshakeBuffer)) {
                                callbackHandler.onHandshake(ioEvent);
                            }

                            return;
                        }

                        callbackHandler.onWrite(ioEvent);
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Exception occured when writing to SSL channel.", e);
                }
            }
        }
    }
    
    /**
     * SSL <code>AsyncQueueDataProcessor</code> for a 
     * <code>TCPAsyncQueueReader</code>
     */
    private class SSLReadPostProcessor implements AsyncQueueDataProcessor {
        public ByteBuffer getInternalByteBuffer() {
            return securedInputBuffer;
        }

        public void process(ByteBuffer byteBuffer) throws SSLException {
            securedInputBuffer.flip();
            unwrapAll(byteBuffer);
            clearOrCompactBuffer(securedInputBuffer);
        }
    }
    
    /**
     * SSL <code>AsyncQueueDataProcessor</code> for a 
     * <code>TCPAsyncQueueWriter</code>
     */
    private class SSLWritePreProcessor implements AsyncQueueDataProcessor {
        public ByteBuffer getInternalByteBuffer() {
            return securedOutputBuffer;
        }

        public void process(ByteBuffer byteBuffer) throws SSLException {
            if (!byteBuffer.hasRemaining() ||
                    securedOutputBuffer.hasRemaining()) return;
            
            securedOutputBuffer.clear();
            SSLEngineResult result = sslEngine.wrap(byteBuffer, securedOutputBuffer);
            updateSSLEngineStatus(result);
            securedOutputBuffer.flip();
        }
    }
}
