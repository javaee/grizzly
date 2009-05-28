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

package com.sun.grizzly.nio;

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
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.grizzly.Connection;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.asyncqueue.AsyncQueue;
import com.sun.grizzly.asyncqueue.AsyncReadQueueRecord;
import com.sun.grizzly.asyncqueue.AsyncWriteQueueRecord;
import com.sun.grizzly.attributes.IndexedAttributeHolder;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;

/**
 * Common {@link Connection} implementation for Java NIO <tt>Connection</tt>s.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractNIOConnection implements NIOConnection {
    protected final NIOTransport transport;

    protected int readBufferSize;
    protected int writeBufferSize;

    protected SelectorRunner selectorRunner;
    protected SelectableChannel channel;
    protected SelectionKey selectionKey;
    
    protected Processor processor;
    protected ProcessorSelector processorSelector;
    
    protected final AttributeHolder attributes;

    protected final AsyncQueue<AsyncReadQueueRecord> asyncReadQueue;
    protected final AsyncQueue<AsyncWriteQueueRecord> asyncWriteQueue;
    
    protected AtomicBoolean isClosed = new AtomicBoolean(false);

    protected boolean isBlocking;

    public AbstractNIOConnection(NIOTransport transport) {
        this.transport = transport;
        asyncReadQueue = new AsyncQueue<AsyncReadQueueRecord>();
        asyncWriteQueue = new AsyncQueue<AsyncWriteQueueRecord>();
        
        attributes = new IndexedAttributeHolder(transport.getAttributeBuilder());
    }

    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    public boolean isBlocking() {
        return isBlocking;
    }

    public Transport getTransport() {
        return transport;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    public SelectorRunner getSelectorRunner() {
        return selectorRunner;
    }

    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        this.selectorRunner = selectorRunner;
    }

    public SelectableChannel getChannel() {
        return channel;
    }

    protected void setChannel(SelectableChannel channel) {
        this.channel = channel;
    }

    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    protected void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        setChannel(selectionKey.channel());
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setProcessor(
            Processor preferableProcessor) {
        this.processor = preferableProcessor;
    }

    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

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

    public AttributeHolder getAttributes() {
        return attributes;
    }

    public AttributeHolder obtainAttributes() {
        return attributes;
    }

    @Override
    public Future<ReadResult<Buffer, SocketAddress>> read() throws IOException {
        return read(null);
    }

    @Override
    public Future<ReadResult<Buffer, SocketAddress>> read(Buffer buffer)
            throws IOException {
        return read(buffer, null);
    }

    @Override
    public Future<ReadResult<Buffer, SocketAddress>> read(Buffer buffer,
            CompletionHandler<ReadResult<Buffer, SocketAddress>> completionHandler)
            throws IOException {
        return read(buffer, completionHandler, null);
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(Buffer buffer)
            throws IOException {
        return write(null, buffer);
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(Buffer buffer,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler)
            throws IOException {
        return write((SocketAddress) null, buffer, completionHandler);
    }

    @Override
    public Future<WriteResult<Buffer, SocketAddress>> write(
            SocketAddress dstAddress, Buffer buffer) throws IOException {
        return write(dstAddress, buffer, null);
    }    

    public boolean isOpen() {
        return channel != null && channel.isOpen() && !isClosed.get();
    }

    public void close() throws IOException {
        if (!isClosed.getAndSet(true)) {
            preClose();
            StreamWriter writer = getStreamWriter();
            if (writer != null) {
                writer.close();
            }

            StreamReader reader = getStreamReader();
            if (reader != null) {
                reader.close();
            }
            
            ((AbstractNIOTransport) transport).closeConnection(this);
        }
    }

    protected abstract void preClose();

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
