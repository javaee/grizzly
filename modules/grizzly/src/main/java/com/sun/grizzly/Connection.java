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

package com.sun.grizzly;

import com.sun.grizzly.monitoring.MonitoringAware;
import java.io.IOException;
import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.monitoring.MonitoringConfig;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Common interface, which represents any kind of connection.
 * 
 * @author Alexey Stashok
 */
public interface Connection<L> extends Readable<L>, Writable<L>, Closeable,
        AttributeStorage, MonitoringAware<ConnectionProbe> {
    /**
     * Get the {@link Transport}, to which this {@link Connection} belongs to.
     * @return the {@link Transport}, to which this {@link Connection} belongs to.
     */
    public Transport getTransport();

    /**
     * Is {@link Connection} open and ready.
     * Returns <tt>true</tt>, if connection is open and ready, or <tt>false</tt>
     * otherwise.
     * 
     * @return <tt>true</tt>, if connection is open and ready, or <tt>false</tt>
     * otherwise.
     */
    public boolean isOpen();

    /**
     * Returns the {@link Connection} mode.
     * <tt>true</tt>, if {@link Connection} is operating in blocking mode, or
     * <tt>false</tt> otherwise.
     *
     * @return the {@link Connection} mode.
     * <tt>true</tt>, if {@link Connection} is operating in blocking mode, or
     * <tt>false</tt> otherwise.
     */
    public void configureBlocking(boolean isBlocking);

    /**
     * Sets the {@link Connection} mode.
     *
     * @param isBlocking the {@link Connection} mode. <tt>true</tt>,
     * if {@link Connection} should operate in blocking mode, or
     * <tt>false</tt> otherwise.
     */
    public boolean isBlocking();

    public void configureStandalone(boolean isStandalone);

    public boolean isStandalone();

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
    public Processor obtainProcessor(IOEvent ioEvent);
    
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
    public Processor getProcessor();

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
    public void setProcessor(
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
    public ProcessorSelector getProcessorSelector();
    
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
    public void setProcessorSelector(
            ProcessorSelector preferableProcessorSelector);

    /**
     * Get the connection peer address
     * @return the connection peer address
     */
    public L getPeerAddress();
    
    /**
     * Get the connection local address
     * @return the connection local address
     */
    public L getLocalAddress();

    /**
     * Close the {@link Connection}
     *
     * @return {@link Future}, which could be checked in case, if close operation
     *         will be run asynchronously
     * @throws java.io.IOException, if I/O error was detected
     * during {@link Connection} closing.
     */
    @Override
    public GrizzlyFuture close() throws IOException;

    /**
     * Get the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Connection}.
     *
     * @return the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Connection}.
     */
    public int getReadBufferSize();

    /**
     * Set the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Connection}.
     *
     * @param readBufferSize the default size of {@link Buffer}s, which will
     * be allocated for reading data from {@link Connection}.
     */
    public void setReadBufferSize(int readBufferSize);

    /**
     * Get the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Connection}.
     *
     * @return the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Connection}.
     */
    public int getWriteBufferSize();

    /**
     * Set the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Connection}.
     *
     * @param writeBufferSize the default size of {@link Buffer}s, which will
     * be allocated for writing data to {@link Connection}.
     */
    public void setWriteBufferSize(int writeBufferSize);

    public long getReadTimeout(TimeUnit timeUnit);

    public void setReadTimeout(long timeout, TimeUnit timeUnit);

    public long getWriteTimeout(TimeUnit timeUnit);

    public void setWriteTimeout(long timeout, TimeUnit timeUnit);

    /**
     * Get the <tt>Connection</tt> monitoring configuration {@link MonitoringConfig}.
     *
     * @param the <tt>Connection</tt> monitoring configuration {@link MonitoringConfig}.
     */
    @Override
    public MonitoringConfig<ConnectionProbe> getMonitoringConfig();

    /**
     * Add the {@link CloseListener}, which will be notified once <tt>Connection</tt>
     * will be closed.
     * 
     * @param closeListener {@link CloseListener}.
     */
    public void addCloseListener(CloseListener closeListener);

    /**
     * Remove the {@link CloseListener}.
     *
     * @param closeListener {@link CloseListener}.
     */
    public boolean removeCloseListener(CloseListener closeListener);
    
    /**
     * Method gets invoked, when error occur during the <tt>Connection</tt> lifecycle.
     *
     * @param error {@link Throwable}.
     */
    public void notifyConnectionError(Throwable error);
        
    /**
     * The listener, which is used to be notified, when <tt>Connection</tt> gets closed.
     */
    public static interface CloseListener {
        public void onClosed(Connection connection) throws IOException;
    }
}
