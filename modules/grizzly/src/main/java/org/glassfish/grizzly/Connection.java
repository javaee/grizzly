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
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainState;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;

/**
 * Common interface, which represents any kind of connection.
 * 
 * @param <L> the Connection address type
 * 
 * @author Alexey Stashok
 */
public interface Connection<L> extends org.glassfish.grizzly.Readable<L>, Writable<L>,
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
    boolean isOpen();

    /**
     * Checks if this <tt>Connection</tt> is open and ready to be used.
     * If this <tt>Connection</tt> is closed - this method throws
     * {@link IOException} giving the reason why this <tt>Connection</tt>
     * was closed.
     * 
     * @throws IOException 
     */
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

    /**
     * Gets the {@link FilterChain}, which will process events occurred on the
     * {@link Connection}.
     * If the {@link Connection} haven't been assigned specific {@link FilterChain} -
     * then {@link Transport}'s default {@link FilterChain} will be returned.
     *
     * @return the {@link FilterChain}, which will process events occurred on the
     * {@link Connection}.
     */
    FilterChain getFilterChain();

    /**
     * @return {@link FilterChain} associated state, which keeps track of the
     *      incomplete chunks passed along with the {@link NextAction} returned
     *      by {@link Filter}s
     */
    FilterChainState getFilterChainState();
    
    /**
     * Sets the default {@link FilterChain}, which will process {@link Connection}
     * events.
     * If {@link FilterChain} is <tt>null</tt> - the {@link Transport} will
     * try to get {@link FilterChain} using own settings.
     *
     * @param filterChain the default {@link FilterChain}, which will
     * process {@link Connection} events.
     */
    void setFilterChain(FilterChain filterChain);
    
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
     * Returns the current value for the blocking read timeout converted to the
     * provided {@link TimeUnit} specification.  If this value hasn't been
     * explicitly set, it will default to {@value #DEFAULT_READ_TIMEOUT} seconds.
     *
     * @param timeUnit the {@link TimeUnit} to convert the returned result to.
     *
     * @since 3.0
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
     * @since 3.0
     */
    void setBlockingWriteTimeout(long timeout, TimeUnit timeUnit);
    
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
     * This method is similar to {@link #closeSilently()}, but additionally
     * provides the reason why the <tt>Connection</tt> will be closed.
     * 
     * @param reason 
     */
    void terminateWithReason(CloseReason closeReason);
    
    /**
     * Gracefully close the {@link Connection}
     *
     * @return {@link Future}, which could be checked in case, if close operation
     *         will be run asynchronously
     */
    @Override
    GrizzlyFuture<Closeable> close();

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
    void closeWithReason(CloseReason closeReason);
    
    /**
     * Add the {@link CloseListener}, which will be notified once <tt>Connection</tt>
     * will be closed.
     * 
     * @param closeListener {@link CloseListener}.
     */
    @Override
    void addCloseListener(CloseListener closeListener);

    /**
     * Remove the {@link CloseListener}.
     *
     * @param closeListener {@link CloseListener}.
     */
    @Override
    boolean removeCloseListener(CloseListener closeListener);
    
    /**
     * @return the <tt>Connection</tt> monitoring configuration {@link MonitoringConfig}.
     */
    @Override
    MonitoringConfig<ConnectionProbe> getMonitoringConfig();
}
