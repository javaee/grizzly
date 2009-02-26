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

package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.nio.AbstractNIOTransport;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.SocketAcceptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Future;

/**
 * UDP NIO transport implementation
 * 
 * @author Alexey Stashok
 */
public class UDPNIOTransport extends AbstractNIOTransport implements SocketAcceptor {
    
    private static final String DEFAULT_TRANSPORT_NAME = "UDPNIOTransport";

    public UDPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    public UDPNIOTransport(String name) {
        super(name);
    }

    /**
     * {@inheritDoc}
     */
    public void bind(int port) throws IOException {
        bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    public void bind(String host, int port) throws IOException {
        bind(host, port, 50);
    }

    /**
     * {@inheritDoc}
     */
    public void bind(String host, int port, int backlog) throws IOException {
        bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    public void bind(SocketAddress socketAddress) throws IOException {
        bind(socketAddress, 4096);
    }

    /**
     * {@inheritDoc}
     */
    public void bind(SocketAddress socketAddress, int backlog) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Future<Connection> accept() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void closeConnection(Connection connection) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void start() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void stop() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void pause() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void resume() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void fireIOEvent(IOEvent ioEvent, Connection connection) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
