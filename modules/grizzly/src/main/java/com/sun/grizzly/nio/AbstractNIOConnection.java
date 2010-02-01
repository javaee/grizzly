/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.nio;

import com.sun.grizzly.Transformer;
import java.util.concurrent.Future;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Processor;
import com.sun.grizzly.ProcessorSelector;
import com.sun.grizzly.Transport;
import com.sun.grizzly.attributes.AttributeHolder;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.grizzly.Connection;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.StandaloneProcessor;
import com.sun.grizzly.StandaloneProcessorSelector;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.asyncqueue.AsyncQueue;
import com.sun.grizzly.asyncqueue.AsyncReadQueueRecord;
import com.sun.grizzly.asyncqueue.AsyncWriteQueueRecord;
import com.sun.grizzly.attributes.IndexedAttributeHolder;

/**
 * Common {@link Connection} implementation for Java NIO <tt>Connection</tt>s.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractNIOConnection implements NIOConnection {
    protected final NIOTransport transport;

    protected volatile int readBufferSize;
    protected volatile int writeBufferSize;

    protected volatile long readTimeoutMillis = 30000;
    protected volatile long writeTimeoutMillis = 30000;

    protected SelectorRunner selectorRunner;
    protected SelectableChannel channel;
    protected SelectionKey selectionKey;
    
    protected volatile Processor processor;
    protected volatile ProcessorSelector processorSelector;
    
    protected final AttributeHolder attributes;

    protected final AsyncQueue<AsyncReadQueueRecord> asyncReadQueue;
    protected final AsyncQueue<AsyncWriteQueueRecord> asyncWriteQueue;
    
    protected final AtomicBoolean isClosed = new AtomicBoolean(false);

    protected volatile boolean isBlocking;

    protected volatile boolean isStandalone;

    public AbstractNIOConnection(NIOTransport transport) {
        this.transport = transport;
        asyncReadQueue = AsyncQueue.<AsyncReadQueueRecord>createSafeAsyncQueue();
        asyncWriteQueue = AsyncQueue.<AsyncWriteQueueRecord>createSafeAsyncQueue();
        
        attributes = new IndexedAttributeHolder(transport.getAttributeBuilder());
    }

    @Override
    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    @Override
    public boolean isBlocking() {
        return isBlocking;
    }

    @Override
    public synchronized void configureStandalone(boolean isStandalone) {
        if (this.isStandalone != isStandalone) {
            this.isStandalone = isStandalone;
            if (isStandalone) {
                processor = StandaloneProcessor.INSTANCE;
                processorSelector = StandaloneProcessorSelector.INSTANCE;
            } else {
                processor = transport.getProcessor();
                processorSelector = transport.getProcessorSelector();
            }
        }
    }

    @Override
    public boolean isStandalone() {
        return isStandalone;
    }

    @Override
    public Transport getTransport() {
        return transport;
    }

    @Override
    public int getReadBufferSize() {
        return readBufferSize;
    }

    @Override
    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    @Override
    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    @Override
    public long getReadTimeout(TimeUnit timeUnit) {
        return timeUnit.convert(readTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setReadTimeout(long timeout, TimeUnit timeUnit) {
        readTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
    }

    @Override
    public long getWriteTimeout(TimeUnit timeUnit) {
        return timeUnit.convert(writeTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setWriteTimeout(long timeout, TimeUnit timeUnit) {
        writeTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
    }

    @Override
    public SelectorRunner getSelectorRunner() {
        return selectorRunner;
    }

    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        this.selectorRunner = selectorRunner;
    }

    @Override
    public SelectableChannel getChannel() {
        return channel;
    }

    protected void setChannel(SelectableChannel channel) {
        this.channel = channel;
    }

    @Override
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    protected void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        setChannel(selectionKey.channel());
    }

    @Override
    public Processor getProcessor() {
        return processor;
    }

    @Override
    public void setProcessor(
            Processor preferableProcessor) {
        this.processor = preferableProcessor;
    }

    @Override
    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    @Override
    public void setProcessorSelector(
            ProcessorSelector preferableProcessorSelector) {
        this.processorSelector =
                preferableProcessorSelector;
    }

    public AsyncQueue<AsyncReadQueueRecord> getAsyncReadQueue() {
        return asyncReadQueue;
    }

    public AsyncQueue<AsyncWriteQueueRecord> getAsyncWriteQueue() {
        return asyncWriteQueue;
    }

    @Override
    public AttributeHolder getAttributes() {
        return attributes;
    }

    @Override
    public Future<ReadResult<Buffer, SocketAddress>> read() throws IOException {
        return read(null, null, null, null);
    }

    @Override
    public Future<ReadResult<Buffer, SocketAddress>> read(Buffer buffer)
            throws IOException {
        return read(buffer, null, null, null);
    }

    @Override
    public Future<ReadResult<Buffer, SocketAddress>> read(Buffer buffer,
            CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler) throws IOException {
        return read(buffer, completionHandler, null, null);
    }

    @Override
    public <M> Future<ReadResult<M, SocketAddress>> read(M message,
            CompletionHandler<ReadResult<M, SocketAddress>> completionHandler,
            Transformer<Buffer, M> transformer) throws IOException {
        
        return read(message, completionHandler, transformer, null);
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(Buffer buffer)
            throws IOException {
        return write(null, buffer, null, null);
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler)
            throws IOException {
        return write(null, buffer, completionHandler, null);
    }

    @Override
    public <M> Future<WriteResult<M, SocketAddress>> write(M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler,
            Transformer<M, Buffer> transformer) throws IOException {

        return write(null, message, completionHandler, transformer);
    }



    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(
            SocketAddress dstAddress, Buffer buffer) throws IOException {
        return write(dstAddress, buffer, null, null);
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(SocketAddress dstAddress,
            Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler) throws IOException {
        return write(dstAddress, buffer, completionHandler, null);
    }


    @Override
    public boolean isOpen() {
        return channel != null && channel.isOpen() && !isClosed.get();
    }

    @Override
    public void close() throws IOException {
        if (!isClosed.getAndSet(true)) {
            preClose();
            ((AbstractNIOTransport) transport).closeConnection(this);
        }
    }

    protected abstract void preClose();

    @Override
    public void enableIOEvent(IOEvent ioEvent) throws IOException {
        final SelectionKeyHandler selectionKeyHandler =
                transport.getSelectionKeyHandler();
        final int interest =
                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent);

        if (interest == 0) return;

        final SelectorHandler selectorHandler = transport.getSelectorHandler();

        selectorHandler.registerKey(selectorRunner, selectionKey,
                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent));
    }

    @Override
    public void disableIOEvent(IOEvent ioEvent) throws IOException {
        final SelectionKeyHandler selectionKeyHandler =
                transport.getSelectionKeyHandler();
        final int interest =
                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent);

        if (interest == 0) return;

        final SelectorHandler selectorHandler = transport.getSelectorHandler();

        selectorHandler.unregisterKey(selectorRunner, selectionKey, interest);
    }
}
