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
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.attributes.NullaryFunction;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;

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

    /**
     * Gets the {@link Processor}, which will process I/O events occurred on the
     * {@link Connection}.
     * If the {@link Connection} haven't been assigned specific {@link Processor} -
     * then {@link Transport}'s default {@link Processor} will be returned.
     *
     * @return the {@link Processor}, which will process I/O events occurred on the
     * {@link Connection}.
     */
    Processor getProcessor();

    /**
     * Sets the default {@link Processor}, which will process {@link Connection}
     * I/O events.
     * If {@link Processor} is <tt>null</tt>,  - then {@link Transport} will try
     * to get {@link Processor} using {@link Connection}'s
     * {@link ProcessorSelector#select(Event, Connection)}. If
     * {@link ProcessorSelector}, associated with the {@link Connection} is also
     * <tt>null</tt> - {@link Transport} will try to get {@link Processor}
     * using own settings.
     *
     * @param preferableProcessor the default {@link Processor}, which will
     * process {@link Connection} I/O events.
     */
    void setProcessor(
        Processor preferableProcessor);

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

    long getBlockingReadTimeout(TimeUnit timeUnit);

    void setBlockingReadTimeout(long timeout, TimeUnit timeUnit);

    long getBlockingWriteTimeout(TimeUnit timeUnit);

    void setBlockingWriteTimeout(long timeout, TimeUnit timeUnit);

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
