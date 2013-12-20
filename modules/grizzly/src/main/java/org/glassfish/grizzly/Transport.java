/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.grizzly.utils.StateHolder;

/**
 * Transport interface describes the transport unit used in Grizzly.
 *
 * Transport implementation could operate over TCP, UDP or other custom
 * protocol, using blocking, NIO or NIO.2 Java API.
 *
 * @author Alexey Stashok
 */
public interface Transport extends MonitoringAware<TransportProbe> {

    /**
     * The default read buffer size.
     */
    static final int DEFAULT_READ_BUFFER_SIZE = -1;

    /**
     * The default write buffer size.
     */
    static final int DEFAULT_WRITE_BUFFER_SIZE = -1;

    /**
     * Default read timeout in seconds.
     */
    public static final int DEFAULT_READ_TIMEOUT = 30;

    /**
     * Default write timeout in seconds.
     */
    public static final int DEFAULT_WRITE_TIMEOUT = 30;

    
    enum State {STARTING, STARTED, PAUSING, PAUSED, STOPPING, STOPPED}

    /**
     * Gets the {@link Transport} name.
     * 
     * @return the {@link Transport} name.
     */
    String getName();

    /**
     * Sets the {@link Transport} name.
     *
     * @param name the {@link Transport} name.
     */
    void setName(String name);

    /**
     * Return the {@link Transport} state controller. Using the state controller,
     * it is possible to get/set the {@link Transport} state in thread-safe manner.
     * 
     * @return {@link StateHolder} state controller.
     */
    StateHolder<State> getState();

    /**
     * Returns the {@link Transport} mode.
     * <tt>true</tt>, if {@link Transport} is operating in blocking mode, or
     * <tt>false</tt> otherwise.
     * Specific {@link Transport} {@link Connection}s may override this setting
     * by {@link Connection#isBlocking()}.
     * 
     * @return the {@link Transport} mode.
     * <tt>true</tt>, if {@link Transport} is operating in blocking mode, or
     * <tt>false</tt> otherwise.
     */
    boolean isBlocking();

    /**
     * Sets the {@link Transport} mode.
     * Specific {@link Transport} {@link Connection}s may override this setting
     * by {@link Connection#configureBlocking(boolean)}.
     *
     * @param isBlocking the {@link Transport} mode. <tt>true</tt>,
     * if {@link Transport} should operate in blocking mode, or
     * <tt>false</tt> otherwise.
     */
    void configureBlocking(boolean isBlocking);

    /**
     * Gets the default {@link FilterChain}, which will process {@link Connection}
     * events in case, if {@link Connection} doesn't have own
     * {@link FilterChain} preferences.
     * 
     * @return the default {@link FilterChain}, which will process
     * {@link Connection} events, if one doesn't have
     * own {@link FilterChain} preferences.
     */
    FilterChain getFilterChain();

    /**
     * Sets the default {@link FilterChain}, which will process {@link Connection}
     * events in case, if {@link Connection} doesn't have own
     * {@link FilterChain} preferences.
     *
     * @param filterChain the default {@link FilterChain}, which will process
     * {@link Connection} events, if one doesn't have own
     * {@link FilterChain} preferences.
     */
    void setFilterChain(FilterChain filterChain);

    /**
     * Get the {@link Transport} associated {@link MemoryManager}, which will
     * be used by the {@link Transport}, its {@link Connection}s and by during
     * processing I/O events, occurred on {@link Connection}s.
     * 
     * @return the {@link Transport} associated {@link MemoryManager},
     * which will be used by the {@link Transport}, its {@link Connection}s
     * and by during processing I/O events, occurred on {@link Connection}s.
     */
    MemoryManager getMemoryManager();

    /**
     * Set the {@link Transport} associated {@link MemoryManager}, which will
     * be used by the {@link Transport}, its {@link Connection}s and by during
     * processing I/O events, occurred on {@link Connection}s.
     *
     * @param memoryManager the {@link Transport} associated
     * {@link MemoryManager}, which will be used by the {@link Transport},
     * its {@link Connection}s and by during processing I/O events, occurred
     * on {@link Connection}s.
     */
    void setMemoryManager(MemoryManager memoryManager);

    /**
     * Get the {@link IOStrategy} implementation, which will be used by
     * {@link Transport} to process {@link Event}.
     * {@link IOStrategy} is responsible for choosing the way, how I/O event
     * will be processed: using current {@link Thread}, worker {@link Thread};
     * or make any other decisions.
     * 
     * @return the {@link IOStrategy} implementation, which will be used by
     * {@link Transport} to process {@link Event}.
     */
    IOStrategy getIOStrategy();

    /**
     * Set the {@link IOStrategy} implementation, which will be used by
     * {@link Transport} to process {@link Event}.
     * {@link IOStrategy} is responsible for choosing the way, how I/O event
     * will be processed: using current {@link Thread}, worker {@link Thread};
     * or make any other decisions.
     *
     * @param IOStrategy the {@link IOStrategy} implementation, which will be used
     * by {@link Transport} to process {@link Event}.
     */
    void setIOStrategy(IOStrategy IOStrategy);

    /**
     * Get a thread pool, which will run Event processing
     * (depending on Transport {@link IOStrategy}) to let kernel threads continue
     * their job.
     *
     * @return {@link ExecutorService} transport worker thread pool.
     */
    ExecutorService getWorkerThreadPool();


    /**
     * @return {@link ExecutorService} responsible for running Transport internal
     * tasks. For example {@link org.glassfish.grizzly.nio.SelectorRunner}
     *  threads for NIO.
     */
    ExecutorService getKernelThreadPool();

    /**
     * Set a thread pool, which will run Event processing
     * (depending on Transport {@link IOStrategy}) to let kernel threads continue
     * their job.
     *
     * @param threadPool {@link ExecutorService} transport worker thread pool.
     */
    void setWorkerThreadPool(ExecutorService threadPool);

    /**
     * Set a thread pool which will run Transport internal tasks. For example
     * {@link org.glassfish.grizzly.nio.SelectorRunner} threads for NIO.
     *
     * @param threadPool {@link ExecutorService} for {@link org.glassfish.grizzly.nio.SelectorRunner}s
     */
    void setKernelThreadPool(ExecutorService threadPool);


    /**
     * Set the {@link ThreadPoolConfig} to be used by the Transport internal
     * thread pool.
     *
     * @param kernelConfig kernel thread
     *  pool configuration.
     */
    void setKernelThreadPoolConfig(final ThreadPoolConfig kernelConfig);

    /**
     * Set the {@link ThreadPoolConfig} to be used by the worker thread pool.
     *
     * @param workerConfig worker thread pool configuration.
     */
    void setWorkerThreadPoolConfig(final ThreadPoolConfig workerConfig);

    /**
     * @return the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} which will run the
     *  {@link org.glassfish.grizzly.Transport}'s internal tasks.
     *  For example
     *  {@link org.glassfish.grizzly.nio.SelectorRunner}s for NIO.
     */
    ThreadPoolConfig getKernelThreadPoolConfig();

    /**
     * @return the {@link ThreadPoolConfig} that will be used to construct the
     *  {@link java.util.concurrent.ExecutorService} for <code>IOStrategies</code>
     *  that require worker threads.  Depending on the {@link IOStrategy} being
     *  used, this may return <code>null</code>.
     */
    ThreadPoolConfig getWorkerThreadPoolConfig();

    /**
     * Get {@link Transport} associated {@link AttributeBuilder}, which will
     * be used by {@link Transport} and its {@link Connection}s to store custom
     * {@link Attribute}s.
     * 
     * @return {@link Transport} associated {@link AttributeBuilder}, which will
     * be used by {@link Transport} and its {@link Connection}s to store custom
     * {@link Attribute}s.
     */
    AttributeBuilder getAttributeBuilder();

    /**
     * Set {@link Transport} associated {@link AttributeBuilder}, which will
     * be used by {@link Transport} and its {@link Connection}s to store custom
     * {@link Attribute}s.
     *
     * @param attributeBuilder {@link Transport} associated
     * {@link AttributeBuilder}, which will be used by {@link Transport} and
     * its {@link Connection}s to store custom {@link Attribute}s.
     */
    void setAttributeBuilder(AttributeBuilder attributeBuilder);

    /**
     * Starts the transport
     * 
     * @throws IOException
     */
    void start() throws IOException;

    /**
     * Gracefully stops the transport accepting new connections and allows
     * existing work to complete before finalizing the shutdown.  This method
     * will wait indefinitely for all interested parties to signal it is safe
     * to terminate the transport.   Invoke {@link #shutdownNow()} to terminate
     * the transport if the graceful shutdown is taking too long.
     *
     * @return a {@link GrizzlyFuture} which will return the stopped transport.
     * @see GracefulShutdownListener
     * @since 2.3.5
     */
    GrizzlyFuture<Transport> shutdown();

    /**
     * Gracefully stops the transport accepting new connections and allows
     * existing work to complete before finalizing the shutdown.  This method
     * will wait for the specified time for all interested parties to signal it
     * is safe to terminate the transport.  If the timeout is exceeded, the
     * transport will be terminated forcefully.
     *
     * @param gracePeriod the grace period for a graceful shutdown before the
     *                    transport is forcibly terminated.  If gracePeriod
     *                    is zero or less, then there is no time limit for
     *                    the shutdown.
     * @param timeUnit    the {@link TimeUnit} of the specified grace period.
     * @return a {@link GrizzlyFuture} which will return the stopped transport.
     * @since 2.3.5
     */
    GrizzlyFuture<Transport> shutdown(final long gracePeriod,
                                      final TimeUnit timeUnit);

    /**
     * Forcibly stops the transport and closes all connections.
     *
     * @throws IOException
     * @since 2.3.5
     */
    void shutdownNow() throws IOException;

    /**
     * Adds a {@link GracefulShutdownListener} which will be called when {@link #shutdown()}
     * is called to enable graceful shutdown of transports.  This allows the
     * owner of the listener to signal that all shutdown tasks are complete and
     * that it's safe to finalize the termination of the transport
     *
     * @param shutdownListener the {@link GracefulShutdownListener}
     * @return <code>true</code> if the listener was successfully registered,
     * otherwise <code>false</code>.  When this method returns <code>false</code>
     * it means one of two things: the transport is stopping or is stopped, or
     * the listener has already been registered.
     * @since 2.3.5
     */
    boolean addShutdownListener(final GracefulShutdownListener shutdownListener);

    /**
     * Pauses the transport
     */
    void pause();
    
    /**
     * Resumes the transport after a pause
     */
    void resume();
    
    /**
     * Fires the {@link Event} on the {@link Connection}
     *
     * @param event event
     * @param connection {@link Connection}, on which we fire the event.
     */
    void fireEvent(Event event, Connection connection);

    /**
     * Fires the {@link Event} on the {@link Connection}
     *
     * @param event service event
     * @param connection {@link Connection}, on which we fire the event.
     * @param listener I/O event life-cycle listener.
     */
    void fireEvent(Event event, Connection connection,
            EventLifeCycleListener listener);

    /**
     * Returns <tt>true</tt>, if this <tt>Transport</tt> is in stopped state,
     *         <tt>false</tt> otherwise.
     * @return <tt>true</tt>, if this <tt>Transport</tt> is in stopped state,
     *         <tt>false</tt> otherwise.
     */
    boolean isStopped();

    boolean isPaused();

    /**
     * Get the monitoring configuration for Transport {@link Connection}s.
     */
    MonitoringConfig<ConnectionProbe> getConnectionMonitoringConfig();

    /**
     * Get the monitoring configuration for Transport thread pool.
     */
    MonitoringConfig<ThreadPoolProbe> getThreadPoolMonitoringConfig();

    /**
     * Get the <tt>Transport</tt> monitoring configuration {@link MonitoringConfig}.
     */
    @Override
    MonitoringConfig<TransportProbe> getMonitoringConfig();

    /**
     * Method gets invoked, when error occur during the <tt>Transport</tt> lifecycle.
     *
     * @param error {@link Throwable}.
     */
    void notifyTransportError(Throwable error);

    /**
     * Returns the current value for the blocking read timeout converted to the
     * provided {@link TimeUnit} specification.  If this value hasn't been
     * explicitly set, it will default to {@value #DEFAULT_READ_TIMEOUT} seconds.
     *
     * @param timeUnit the {@link TimeUnit} to convert the returned result to.
     *
     * @since 2.3
     */
    long getBlockingReadTimeout(TimeUnit timeUnit);

    /**
     * Specifies the timeout for the blocking reads.  This may be overridden on
     * a per-connection basis.
     * A value of zero or less effectively disables the timeout.
     *
     * @param timeout the new timeout value
     * @param timeUnit the {@TimeUnit} specification of the provided value.
     *
     * @see Connection#setBlockingReadTimeout(long, java.util.concurrent.TimeUnit)
     *
     * @since 3.0
     */
    void setBlockingReadTimeout(long timeout, TimeUnit timeUnit);

    /**
     * Returns the current value for the blocking write timeout converted to the
     * provided {@link TimeUnit} specification.  If this value hasn't been
     * explicitly set, it will default to {@value #DEFAULT_WRITE_TIMEOUT} seconds.
     *
     * @param timeUnit the {@link TimeUnit} to convert the returned result to.
     *
     * @since 3.0
     */
    long getBlockingWriteTimeout(TimeUnit timeUnit);

    /**
     * Specifies the timeout for the blocking writes.  This may be overridden on
     * a per-connection basis.
     * A value of zero or less effectively disables the timeout.
     *
     * @param timeout  the new timeout value
     * @param timeUnit the {@TimeUnit} specification of the provided value.
     *
     * @see Connection#setBlockingWriteTimeout(long, java.util.concurrent.TimeUnit)
     *
     * @since 2.3
     */
    void setBlockingWriteTimeout(long timeout, TimeUnit timeUnit);

}
