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
}
