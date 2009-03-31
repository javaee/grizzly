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

package org.glassfish.grizzly.nio;

import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ProcessorSelector;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.attributes.AttributeHolder;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicBoolean;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.asyncqueue.AsyncQueue;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.asyncqueue.AsyncWriteQueueRecord;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;

/**
 * Common {@link Connection} implementation for Java NIO <tt>Connection</tt>s.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractNIOConnection<A> implements NIOConnection<A> {
    protected NIOTransport transport;

    protected int readBufferSize;
    protected int writeBufferSize;

    protected SelectorRunner selectorRunner;
    protected SelectableChannel channel;
    protected SelectionKey selectionKey;
    
    protected Processor processor;
    protected ProcessorSelector processorSelector;
    
    protected volatile AttributeHolder attributes;

    protected volatile AsyncQueue<AsyncReadQueueRecord> asyncReadQueue;
    protected volatile AsyncQueue<AsyncWriteQueueRecord> asyncWriteQueue;
    
    protected AtomicBoolean isClosed = new AtomicBoolean(false);

    protected boolean isBlocking;

    protected long idleTimeoutMillis = UNLIMITED_IDLE_TIMEOUT;

    protected long lastOperationTime;

    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    public boolean isBlocking() {
        return isBlocking;
    }

    public Transport getTransport() {
        return transport;
    }

    protected void setTransport(NIOTransport transport) {
        this.transport = transport;
    }

    public long getIdleTime(TimeUnit timeunit) {
        return timeunit.convert(idleTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void setIdleTime(long timeout, TimeUnit timeunit) {
        idleTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
    }

    public long getLastOperationTime() {
        return lastOperationTime;
    }

    public void setLastOperationTime(long lastOperationTime) {
        this.lastOperationTime = lastOperationTime;
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

    public AttributeHolder obtainAttributes() {
        if (attributes == null) {
            synchronized(this) {
                if (attributes == null) {
                    attributes = initializeAttributeHolder();
                }
            } 
        }

        return attributes;
    }

    public AsyncQueue<AsyncReadQueueRecord> getAsyncReadQueue() {
        return asyncReadQueue;
    }

    public AsyncQueue<AsyncReadQueueRecord> obtainAsyncReadQueue() {
        if (asyncReadQueue == null) {
            synchronized(this) {
                if (asyncReadQueue == null) {
                    asyncReadQueue = new AsyncQueue<AsyncReadQueueRecord>();
                }
            }
        }

        return asyncReadQueue;
    }

    public AsyncQueue<AsyncWriteQueueRecord> getAsyncWriteQueue() {
        return asyncWriteQueue;
    }

    public AsyncQueue<AsyncWriteQueueRecord> obtainAsyncWriteQueue() {
        if (asyncWriteQueue == null) {
            synchronized(this) {
                if (asyncWriteQueue == null) {
                    asyncWriteQueue = new AsyncQueue<AsyncWriteQueueRecord>();
                }
            }
        }

        return asyncWriteQueue;
    }

    public AttributeHolder getAttributes() {
        return attributes;
    }

    protected void setAttributes(AttributeHolder attributes) {
        this.attributes = attributes;
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

    protected abstract AttributeHolder initializeAttributeHolder();
}
