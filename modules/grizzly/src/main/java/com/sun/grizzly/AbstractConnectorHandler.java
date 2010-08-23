/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.async.AsyncQueueReadUnit;
import com.sun.grizzly.async.AsyncReadCallbackHandler;
import com.sun.grizzly.async.AsyncReadCondition;
import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import com.sun.grizzly.async.ByteBufferCloner;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.OutputWriter;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * Abstract {@link ConnectorHandler} definition, which preimplements common
 * getter/setter methods.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractConnectorHandler<E extends SelectorHandler,
        K extends CallbackHandler> implements ConnectorHandler<E, K> {
    /**
     * The <tt>ConnectorHandler</tt> {@link Protocol}.
     */
    protected Protocol protocol;

    /**
     * The internal Controller used (in case not specified).
     */
    protected Controller controller;

    /**
     * The underlying SelectorHandler used to mange SelectionKeys.
     */
    protected E selectorHandler;
    

    /**
     * A {@link CallbackHandler} handler invoked by the SelectorHandler
     * when a non blocking operation is ready to be processed.
     */
    protected K callbackHandler;


    /**
     * The connection's SelectableChannel.
     */
    protected SelectableChannel underlyingChannel;

    /**
     * Is the connection established.
     */
    protected volatile boolean isConnected;

    /**
     * A blocking {@link java.io.InputStream} that use a pool of Selector
     * to execute a blocking read operation.
     */
    protected InputReader inputStream;
    
    /**
     * Get the <tt>ConnectorHandler</tt> {@link Protocol}.
     * @return the <tt>ConnectorHandler</tt> {@link Protocol}.
     */
    public Protocol protocol() {
        return protocol;
    }

    /**
     * Set the <tt>ConnectorHandler</tt> {@link Protocol}.
     * @param protocol the <tt>ConnectorHandler</tt> {@link Protocol}.
     */
    public void protocol(Protocol protocol) {
        this.protocol = protocol;
    }

    /**
     * Set the {@link Controller} to use with this instance.
     * @param controller the {@link Controller} to use with this instance.
     */
    public void setController(Controller controller) {
        this.controller = controller;
    }

    /**
     * Return the  {@link Controller}
     * @return the  {@link Controller}
     */
    public Controller getController() {
        return controller;
    }

    /**
     * Return the associated {@link SelectorHandler}.
     * @return the associated {@link SelectorHandler}.
     */
    public E getSelectorHandler() {
        return selectorHandler;
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
    public void connect(SocketAddress remoteAddress, K callbackHandler) throws IOException {
        connect(remoteAddress,null,callbackHandler);
    }

    /**
     * Connect to hostname:port. Internally an instance of Controller and
     * its default SelectorHandler will be created everytime this method is
     * called. This method should be used only and only if no external
     * Controller has been initialized.
     * @param remoteAddress remote address to connect
     * @throws java.io.IOException
     */
    public void connect(SocketAddress remoteAddress) throws IOException {
        connect(remoteAddress,(SocketAddress)null);
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
    public void connect(SocketAddress remoteAddress, K callbackHandler, E selectorHandler) throws IOException {
        connect(remoteAddress,null,callbackHandler,selectorHandler);
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
    @SuppressWarnings("unchecked")
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress, K callbackHandler) throws IOException {
        if( controller == null )
            throw new IllegalStateException("Controller cannot be null");
        if( protocol == null )
            throw new IllegalStateException("Protocol cannot be null");
        SelectorHandler selectorHandler = controller.getSelectorHandler( protocol );
        if( controller.getReadThreadsCount() > 0 &&
            controller.multiReadThreadSelectorHandler.supportsClient( selectorHandler ) ) {
            if( controller.multiReadThreadSelectorHandler == null )
                throw new IllegalStateException("ComplexSelectorHandler cannot be null");
            ReadController auxController = controller.multiReadThreadSelectorHandler.nextController();
            SelectorHandler relativeSelectorHandler = auxController.getSelectorHandlerClone( selectorHandler );
            if( relativeSelectorHandler == null ) {
                relativeSelectorHandler = auxController.getSelectorHandler( selectorHandler.protocol() );
                if( relativeSelectorHandler == null )
                    throw new IOException( "Can not get correct SelectorHandler" );
            }
            selectorHandler = relativeSelectorHandler;
        }
        connect(remoteAddress, localAddress, callbackHandler, (E)selectorHandler);
    }

    /**
     * Set the associated {@link SelectorHandler}
     * @param selectorHandler the associated {@link SelectorHandler}.
     */
    public void setSelectorHandler(E selectorHandler) {
        this.selectorHandler = selectorHandler;
    }
    
    /**
     * Return the current {@link SelectableChannel} used.
     * @return the current {@link SelectableChannel} used.
     */
    public SelectableChannel getUnderlyingChannel() {
        return underlyingChannel;
    }

    /**
     * Set the {@link SelectableChannel}.
     * @param underlyingChannel the {@link SelectableChannel} to use.
     */
    public void setUnderlyingChannel(SelectableChannel underlyingChannel) {
        this.underlyingChannel = underlyingChannel;
    }


    /**
     * Return the {@link CallbackHandler}.
     * @return the {@link CallbackHandler}.
     */
    public K getCallbackHandler() {
        return callbackHandler;
    }

    /**
     * Set the {@link CallbackHandler}.
     * @param callbackHandler the {@link CallbackHandler}.
     */
    public void setCallbackHandler(K callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    /**
     * Is the underlying channel connected.
     * @return <tt>true</tt> if connected, otherwise <tt>false</tt>
     */
    public boolean isConnected(){
        return isConnected && underlyingChannel != null && underlyingChannel.isOpen();
    }

    /**
     * Read bytes. If blocking is set to <tt>true</tt>, a pool of temporary
     * {@link java.nio.channels.Selector} will be used to read bytes.
     * @param byteBuffer The byteBuffer to store bytes.
     * @param blocking <tt>true</tt> if a a pool of temporary Selector
     *        is required to handle a blocking read.
     * @return number of bytes read
     * @throws java.io.IOException
     */
    public long read( ByteBuffer byteBuffer, boolean blocking) throws IOException {
        if (!isConnected){
            throw new NotYetConnectedException();
        }

        SelectionKey key = selectorHandler.keyFor(underlyingChannel);
        int nRead = -1;
        if (blocking){
            inputStream.setSelectionKey(key);
            try{
                nRead = inputStream.read(byteBuffer);
            } catch (IOException ex){
                nRead = -1;
                throw ex;
            } finally {
                if (nRead == -1){
                    SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                    if (skh instanceof BaseSelectionKeyHandler){
                        ((BaseSelectionKeyHandler)skh).notifyRemotlyClose(key);
                    }
                }
            }
            return nRead;
        } else {
            if (callbackHandler == null){
                throw new IllegalStateException
                        ("Non blocking read needs a CallbackHandler");
            }
            try{
                nRead = ((ReadableByteChannel)underlyingChannel).read(byteBuffer);
            } catch (IOException ex){
                nRead = -1;
                throw ex;
            } finally {
                if (nRead == -1){
                    SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                    if (skh instanceof BaseSelectionKeyHandler){
                        ((BaseSelectionKeyHandler)skh).notifyRemotlyClose(key);
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
     * {@link java.nio.channels.Selector} will be used to writes bytes.
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
            SelectionKey key = selectorHandler.keyFor(underlyingChannel);
            int nWrite = 1;
            int totalWriteBytes = 0;
            try{
                while (nWrite > 0 && byteBuffer.hasRemaining()){
                    nWrite = ((WritableByteChannel)underlyingChannel).write(byteBuffer);
                    totalWriteBytes += nWrite;
                }
            } catch (IOException ex){
                nWrite = -1;
                throw ex;
            } finally {
                if (nWrite == -1){
                    SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                    if (skh instanceof BaseSelectionKeyHandler){
                        ((BaseSelectionKeyHandler)skh).notifyRemotlyClose(key);
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
                selectorHandler.keyFor(underlyingChannel), buffer,
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
                selectorHandler.keyFor(underlyingChannel), buffer,
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
                selectorHandler.keyFor(underlyingChannel), dstAddress,
                buffer, callbackHandler, writePreProcessor, cloner);
    }
}
