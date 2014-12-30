/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * Common interface, which represents any kind of connection.
 * 
 * @param <L> the Connection address type
 * 
 * @author Alexey Stashok
 */
public interface Connection<L> extends Readable<L>, Writeable<L>,
        Closeable, AttributeStorage,
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
    @Override
    boolean isOpen();

    /**
     * Checks if this <tt>Connection</tt> is open and ready to be used.
     * If this <tt>Connection</tt> is closed - this method throws
     * {@link IOException} giving the reason why this <tt>Connection</tt>
     * was closed.
     * 
     * @throws IOException 
     */
    @Override
    void assertOpen() throws IOException;
    
    /**
     * Returns {@link CloseReason} if this <tt>Connection</tt> has been closed,
     * or <tt>null</tt> otherwise.
     * 
     * @return {@link CloseReason} if this <tt>Connection</tt> has been closed,
     * or <tt>null</tt> otherwise
     */
    CloseReason getCloseReason();
    
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

    @Deprecated
    void configureStandalone(boolean isStandalone);

    @Deprecated
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
     * @param ioEvent
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
     * @param <E>
     * @param processor {@link Processor}
     * @param factory
     * 
     * @return the {@link Processor} state associated with this <tt>Connection</tt>.
     */
    <E> E obtainProcessorState(Processor processor,
            NullaryFunction<E> factory);
    
    /**
     * Executes the {@link Runnable} in the thread, responsible for running
     * the given type of event on this <tt>Connection</tt>.
     * The thread will be chosen based on {@link #getTransport() Transport}
     * settings, especially current I/O strategy.
     * 
     * @param event
     * @param runnable 
     */
    void executeInEventThread(IOEvent event, Runnable runnable);
    
    /**
     * @return an associated {@link MemoryManager}. It's a shortcut for {@link #getTransport()#getMemoryManager()}
     * @since 2.3.18
     */
    MemoryManager<?> getMemoryManager();
    
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
    
    /**
     * Returns the current value for the blocking read timeout converted to the
     * provided {@link TimeUnit} specification.  If this value hasn't been
     * explicitly set, it will default to {@value #DEFAULT_READ_TIMEOUT} seconds.
     *
     * @param timeUnit the {@link TimeUnit} to convert the returned result to.
     *
     * @since 2.3
     */
    long getReadTimeout(TimeUnit timeUnit);

    /**
     * Specifies the timeout for the blocking reads.  This may be overridden on
     * a per-connection basis.
     * A value of zero or less effectively disables the timeout.
     *
     * @param timeout the new timeout value
     * @param timeUnit the {@TimeUnit} specification of the provided value.
     *
     * @see Connection#setReadTimeout(long, java.util.concurrent.TimeUnit)
     *
     * @since 2.3
     */
    void setReadTimeout(long timeout, TimeUnit timeUnit);

    /**
     * Returns the current value for the blocking write timeout converted to the
     * provided {@link TimeUnit} specification.  If this value hasn't been
     * explicitly set, it will default to {@value #DEFAULT_WRITE_TIMEOUT} seconds.
     *
     * @param timeUnit the {@link TimeUnit} to convert the returned result to.
     *
     * @since 2.3
     */
    long getWriteTimeout(TimeUnit timeUnit);

    /**
     * Specifies the timeout for the blocking writes.  This may be overridden on
     * a per-connection basis.
     * A value of zero or less effectively disables the timeout.
     *
     * @param timeout  the new timeout value
     * @param timeUnit the {@TimeUnit} specification of the provided value.
     *
     * @see Connection#setWriteTimeout(long, java.util.concurrent.TimeUnit)
     *
     * @since 2.3
     */
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
     * Close the {@link Connection} silently, no notification required on
     * completion or failure.
     */
    @Override
    public void terminateSilently();

    /**
     * Close the {@link Connection}
     *
     * @return {@link Future}, which could be checked in case, if close operation
     *         will be run asynchronously
     */
    @Override
    public GrizzlyFuture<Closeable> terminate();
    
    /**
     * Closes the <tt>Connection</tt> and provides the reason description.
     * 
     * This method is similar to {@link #terminateSilently()}, but additionally
     * provides the reason why the <tt>Connection</tt> will be closed.
     * 
     * @param reason 
     */
    @Override
    void terminateWithReason(IOException reason);
    
    /**
     * Gracefully close the {@link Connection}
     *
     * @return {@link Future}, which could be checked in case, if close operation
     *         will be run asynchronously
     */
    @Override
    GrizzlyFuture<Closeable> close();

    /**
     * Gracefully close the {@link Connection}
     *
     * @param completionHandler {@link CompletionHandler} to be called, when
     *  the connection is closed.
     * 
     * @deprecated use {@link #close()} with the following {@link GrizzlyFuture#addCompletionHandler(org.glassfish.grizzly.CompletionHandler)}.
     */
    @Override
    void close(CompletionHandler<Closeable> completionHandler);

    /**
     * Gracefully close the {@link Connection} silently, no notification required on
     * completion or failure.
     */
    @Override
    void closeSilently();

    /**
     * Gracefully closes the <tt>Connection</tt> and provides the reason description.
     * 
     * This method is similar to {@link #closeSilently()}, but additionally
     * provides the reason why the <tt>Connection</tt> will be closed.
     * 
     * @param reason 
     */
    @Override
    void closeWithReason(IOException reason);
    
    /**
     * Add the {@link CloseListener}, which will be notified once <tt>Connection</tt>
     * will be closed.
     * 
     * @param closeListener {@link CloseListener}.
     *
     * @since 2.3
     */
    @Override
    void addCloseListener(org.glassfish.grizzly.CloseListener closeListener);

    /**
     * Remove the {@link CloseListener}.
     *
     * @param closeListener {@link CloseListener}.
     *
     * @since 2.3
     */
    @Override
    boolean removeCloseListener(org.glassfish.grizzly.CloseListener closeListener);

    /**
     * Add the {@link CloseListener}, which will be notified once <tt>Connection</tt>
     * will be closed.
     * @param closeListener {@link CloseListener}
     *
     * @deprecated use {@link #addCloseListener(org.glassfish.grizzly.CloseListener)}
     */
    @Deprecated
    void addCloseListener(CloseListener closeListener);

    /**
     * Remove the {@link CloseListener}.
     *
     * @param closeListener {@link CloseListener}.
     *
     * @deprecated use {@link #removeCloseListener(org.glassfish.grizzly.CloseListener)}
     */
    @Deprecated
    boolean removeCloseListener(CloseListener closeListener);
    
    /**
     * Method gets invoked, when error occur during the <tt>Connection</tt> lifecycle.
     *
     * @param error {@link Throwable}.
     */
    void notifyConnectionError(Throwable error);


    // ------------------------------------------------------------------- Nested Classes

    /**
     * This interface will be removed in 3.0.
     *
     * @deprecated use {@link org.glassfish.grizzly.CloseListener}
     *
     * @see GenericCloseListener
     */
    @Deprecated
    public static interface CloseListener extends org.glassfish.grizzly.CloseListener<Connection, CloseType> {

        @Override
        void onClosed(Connection connection, CloseType type) throws IOException;

    }

    /**
     * This enum will be removed in 3.0.
     *
     * @deprecated use {@link org.glassfish.grizzly.CloseType}
     */
    @Deprecated
    public enum CloseType implements ICloseType {
        LOCALLY, REMOTELY
    }

}
