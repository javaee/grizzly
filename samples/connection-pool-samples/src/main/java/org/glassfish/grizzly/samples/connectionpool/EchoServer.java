/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.samples.connectionpool;

import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.StringFilter;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * The simple echo server implementation,
 * which is used to test client-side connection pool.
 */
public class EchoServer {
    // the address to bind the server to
    private final SocketAddress endpointAddress;
    
    // internal transport
    private Transport transport;
    // true, if the server is running, or false otherwise
    private boolean isRunning;

    public EchoServer(SocketAddress endpointAddress) {
        this.endpointAddress = endpointAddress;
    }

    /**
     * Returns the {@link SocketAddress} the server is bound to.
     */
    public SocketAddress getEndpointAddress() {
        return endpointAddress;
    }
    
    /**
     * Starts the server.
     */
    public void start() throws IOException {
        if (isRunning) {
            return;
        }
        
        isRunning = true;
        
        final FilterChain filterChain = FilterChainBuilder.stateless()
                .add(new TransportFilter())
                .add(new StringFilter(Charsets.UTF8_CHARSET))
                .add(new EchoFilter())
                .build();
        
        final TCPNIOTransport tcpTransport = TCPNIOTransportBuilder.newInstance()
                .setProcessor(filterChain)
                .build();

        transport = tcpTransport;
        
        tcpTransport.bind(endpointAddress);
        tcpTransport.start();
    }
    
    /**
     * Stops the server.
     */
    public void stop() throws IOException {
        if (!isRunning) {
            return;
        }
        
        final Transport localTransport = transport;
        transport = null;

        localTransport.shutdownNow();
    }
}
