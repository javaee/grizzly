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

package org.glassfish.grizzly;

import java.io.Closeable;
import java.io.IOException;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.attributes.AttributeStorage;

/**
 * Common interface, which represents any kind of connection.
 * 
 * @author Alexey Stashok
 */
public interface Connection<L> extends Closeable, AttributeStorage {
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
     * @throws java.io.IOException, if I/O error was detected
     * during {@link Connection} closing.
     */
    public void close() throws IOException;

    /**
     * Get the {@link Connection} {@link StreamReader}, to read data from the
     * {@link Connection}.
     * 
     * @return the {@link Connection} {@link StreamReader}, to read data from the
     * {@link Connection}.
     */
    public StreamReader getStreamReader();

    /**
     * Get the {@link Connection} {@link StreamWriter}, to write data to the
     * {@link Connection}.
     *
     * @return the {@link Connection} {@link StreamWriter}, to write data to the
     * {@link Connection}.
     */
    public StreamWriter getStreamWriter();

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
}
