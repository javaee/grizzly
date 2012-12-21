/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Readable;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * Common interface, which represents any kind of connection.
 * 
 * @author Alexey Stashok
 */
public interface Connection<L> extends Readable<L>, Writable<L>,
        Closeable<Connection>, AttributeStorage,
        MonitoringAware<ConnectionProbe> {
    /**
     * Get the {@link Transport}, to which this {@link Connection} belongs to.
     * @return the {@link Transport}, to which this {@link Connection} belongs to.
     */
    Transport getTransport();

    /**
     * Is {@link Connection} open and ready.
     * Returns <tt>true</tt>, if connection is open and ready, or <tt>false</tt>
     * otherwise.
     * 
     * @return <tt>true</tt>, if connection is open and ready, or <tt>false</tt>
     * otherwise.
     */
    boolean isOpen();

    /**
     * Sets the {@link Connection} mode.
     *
     * @param isBlocking the {@link Connection} mode. <tt>true</tt>,
     * if {@link Connection} should operate in blocking mode, or
     * <tt>false</tt> otherwise.
     */
    void configureBlocking(boolean isBlocking);

    /**
     * @return the {@link Connection} mode.
     * <tt>true</tt>, if {@link Connection} is operating in blocking mode, or
     * <tt>false</tt> otherwise.
     */
    boolean isBlocking();

    void configureStandalone(boolean isStandalone);

    boolean isStandalone();

    /**
     * Gets the {@link Processor}, which will process {@link Connection}
     * I/O event.
     * If {@link Processor} is <tt>null</tt>,  - then {@link Transport} will try
     * to get {@link Processor} using {@link Connection}'s
     * {@link ProcessorSelector#select(IOEvent, Connection)}. If
     * {@link ProcessorSelector}, associated withthe {@link Connection} is also
     * <tt>null</tt> - will ask {@link Transport} for a {@link Processor}.
     *
     * @return the default {@link Processor}, which will process
     * {@link Connection} I/O events.
     */
    Processor obtainProcessor(IOEvent ioEvent);
    
    /**
     * Gets the default {@link Processor}, which will process {@link Connection}
     * I/O events.
     * If {@link Processor} is <tt>null</tt>,  - then {@link Transport} will try
     * to get {@link Processor} using {@link Connection}'s
     * {@link ProcessorSelector#select(IOEvent, Connection)}. If
     * {@link ProcessorSelector}, associated withthe {@link Connection} is also
     * <tt>null</tt> - {@link Transport} will try to get {@link Processor}
     * using own settings.
     *
     * @return the default {@link Processor}, which will process
     * {@link Connection} I/O events.
     */
    Processor getProcessor();

    /**
     * Sets the default {@link Processor}, which will process {@link Connection}
     * I/O events.
     * If {@link Processor} is <tt>null</tt>,  - then {@link Transport} will try
     * to get {@link Processor} using {@link Connection}'s
     * {@link ProcessorSelector#select(IOEvent, Connection)}. If
     * {@link ProcessorSelector}, associated withthe {@link Connection} is also
     * <tt>null</tt> - {@link Transport} will try to get {@link Processor}
     * using own settings.
     *
     * @param preferableProcessor the default {@link Processor}, which will
     * process {@link Connection} I/O events.
     */
    void setProcessor(
        Processor preferableProcessor);

    /**
     * Gets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process {@link Connection} I/O events, in case if
     * this {@link Connection}'s {@link Processor} is <tt>null</tt>.
     *
     * @return the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process {@link Connection} I/O events, in case if
     * this {@link Connection}'s {@link Processor} is <tt>null</tt>.
     */
    ProcessorSelector getProcessorSelector();
    
    /**
     * Sets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process {@link Connection} I/O events, in case if
     * this {@link Connection}'s {@link Processor} is <tt>null</tt>.
     *
     * @param preferableProcessorSelector the default {@link ProcessorSelector},
     * which will be used to get {@link Processor} to process {@link Connection}
     * I/O events, in case if this {@link Connection}'s {@link Processor}
     * is <tt>null</tt>.
     */
    void setProcessorSelector(
        ProcessorSelector preferableProcessorSelector);

    /**
     * Returns the {@link Processor} state associated with this <tt>Connection</tt>.
     * @param processor {@link Processor}
     * 
     * @return the {@link Processor} state associated with this <tt>Connection</tt>.
     */
    <E> E obtainProcessorState(Processor processor,
            NullaryFunction<E> factory);
    
    /**
     * Get the connection peer address
     * @return the connection peer address
     */
    L getPeerAddress();
    
    /**
     * Get the connection local address
     * @return the connection local address
     */
    L getLocalAddress();

    /**
     * Close the {@link Connection}
     *
     * @return {@link Future}, which could be checked in case, if close operation
     *         will be run asynchronously
     */
    @Override
    GrizzlyFuture<Connection> close();

    /**
     * Close the {@link Connection}
     *
     * @param completionHandler {@link CompletionHandler} to be called, when
     *  the connection is closed.
     */
    @Override
    void close(CompletionHandler<Connection> completionHandler);

    /**
     * Close the {@link Connection} silently, no notification required on
     * completion or failure.
     */
    void closeSilently();

    /**
     * Get the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Connection}.
     * The value less or equal to zero will be ignored.
     *
     * @return the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Connection}.
     */
    int getReadBufferSize();

    /**
     * Set the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Connection}.
     * The value less or equal to zero will be ignored.
     *
     * @param readBufferSize the default size of {@link Buffer}s, which will
     * be allocated for reading data from {@link Connection}.
     */
    void setReadBufferSize(int readBufferSize);

    /**
     * Get the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Connection}.
     *
     * @return the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Connection}.
     */
    int getWriteBufferSize();

    /**
     * Set the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Connection}.
     *
     * @param writeBufferSize the default size of {@link Buffer}s, which will
     * be allocated for writing data to {@link Connection}.
     */
    void setWriteBufferSize(int writeBufferSize);

    /**
     * Get the max size (in bytes) of asynchronous write queue associated
     * with connection.
     * 
     * @return the max size (in bytes) of asynchronous write queue associated
     * with connection.
     * 
     * @since 2.2
     */
    public int getMaxAsyncWriteQueueSize();

    /**
     * Set the max size (in bytes) of asynchronous write queue associated
     * with connection.
     * 
     * @param maxAsyncWriteQueueSize the max size (in bytes) of asynchronous
     * write queue associated with connection.
     * 
     * @since 2.2
     */
    public void setMaxAsyncWriteQueueSize(int maxAsyncWriteQueueSize);
    
    long getReadTimeout(TimeUnit timeUnit);

    void setReadTimeout(long timeout, TimeUnit timeUnit);

    long getWriteTimeout(TimeUnit timeUnit);

    void setWriteTimeout(long timeout, TimeUnit timeUnit);

    public void simulateIOEvent(final IOEvent ioEvent) throws IOException;

    public void enableIOEvent(final IOEvent ioEvent) throws IOException;

    public void disableIOEvent(final IOEvent ioEvent) throws IOException;
    
    /**
     * @return the <tt>Connection</tt> monitoring configuration {@link MonitoringConfig}.
     */
    @Override
    MonitoringConfig<ConnectionProbe> getMonitoringConfig();

    /**
     * Add the {@link CloseListener}, which will be notified once <tt>Connection</tt>
     * will be closed.
     * 
     * @param closeListener {@link CloseListener}.
     */
    void addCloseListener(CloseListener closeListener);

    /**
     * Remove the {@link CloseListener}.
     *
     * @param closeListener {@link CloseListener}.
     */
    boolean removeCloseListener(CloseListener closeListener);
    
    /**
     * Method gets invoked, when error occur during the <tt>Connection</tt> lifecycle.
     *
     * @param error {@link Throwable}.
     */
    void notifyConnectionError(Throwable error);
    
    public static enum CloseType {
        LOCALLY, REMOTELY
    }

    /**
     * The listener, which is used to be notified, when <tt>Connection</tt> gets closed.
     */
    public interface CloseListener {
        void onClosed(Connection connection, CloseType type) throws IOException;    
    }    
}
