/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Future;

/**
 * Socket based client side connector.
 * <tt>SocketConnectorHandler</tt> is responsible for creating and initializing
 * {@link Connection}, and optionally connect is to a specific local/remote
 * address.
 * 
 * @author Alexey Stashok
 */
public interface SocketConnectorHandler {
    /**
     * Creates, initializes and connects socket to the specific remote host
     * and port and returns {@link Connection}, representing socket.
     * 
     * @param host remote host to connect to.
     * @param port remote port to connect to.
     * @return {@link Future} of connect operation, which could be used to get
     * resulting {@link Connection}.
     * 
     * @throws java.io.IOException
     */
    public Future<Connection> connect(String host, int port) throws IOException;
    
    /**
     * Creates, initializes and connects socket to the specific
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @return {@link Future} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public Future<Connection> connect(SocketAddress remoteAddress)
            throws IOException;

    /**
     * Creates, initializes and connects socket to the specific
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param completionHandler {@link CompletionHandler}.
     * @return {@link Future} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public Future<Connection> connect(SocketAddress remoteAddress,
            CompletionHandler<Connection> completionHandler) throws IOException;

    /**
     * Creates, initializes socket, binds it to the specific local and remote
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param localAddress local address to bind socket to.
     * @return {@link Future} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public abstract Future<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws IOException;

    /**
     * Creates, initializes socket, binds it to the specific local and remote
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param localAddress local address to bind socket to.
     * @param completionHandler {@link CompletionHandler}.
     * @return {@link Future} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public abstract Future<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler) throws IOException;

    /**
     * Get the default {@link Processor} to process {@link IOEvent}, occuring
     * on connection phase.
     * 
     * @return the default {@link Processor} to process {@link IOEvent},
     * occuring on connection phase.
     */
    public Processor getProcessor();

    /**
     * Set the default {@link Processor} to process {@link IOEvent}, occuring
     * on connection phase.
     *
     * @param defaultProcessor the default {@link Processor} to process
     * {@link IOEvent}, occuring on connection phase.
     */
    public void setProcessor(Processor defaultProcessor);

    /**
     * Gets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process I/O events, occuring on connection phase.
     *
     * @return the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process I/O events, occuring on connection phase.
     */
    public ProcessorSelector getProcessorSelector();

    /**
     * Sets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process I/O events, occuring on connection phase.
     *
     * @param defaultProcessorSelector the default {@link ProcessorSelector},
     * which will be used to get {@link Processor} to process I/O events,
     * occuring on connection phase.
     */
    public void setProcessorSelector(ProcessorSelector defaultProcessorSelector);

    /**
     * Add the {@link ConnectionProbe}, which will be notified about
     * <tt>Connection</tt> lifecycle events.
     *
     * @param probe the {@link ConnectionProbe}.
     */
    public void addMonitoringProbe(ConnectionProbe probe);

    /**
     * Remove the {@link ConnectionProbe}.
     *
     * @param probe the {@link ConnectionProbe}.
     */
    public boolean removeMonitoringProbe(ConnectionProbe probe);

    /**
     * Get the {@link ConnectionProbe}, which are registered on the <tt>Connection</tt>.
     * Please note, it's not appropriate to modify the returned array's content.
     * Please use {@link #addMonitoringProbe(com.sun.grizzly.ConnectionProbe)} and
     * {@link #removeMonitoringProbe(com.sun.grizzly.ConnectionProbe)} instead.
     *
     * @return the {@link ConnectionProbe}, which are registered on the <tt>Connection</tt>.
     */
    public ConnectionProbe[] getMonitoringProbes();
}
