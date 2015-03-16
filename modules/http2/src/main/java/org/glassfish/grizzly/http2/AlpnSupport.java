/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ICloseType;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.npn.AlpnClientNegotiator;
import org.glassfish.grizzly.npn.AlpnServerNegotiator;
import org.glassfish.grizzly.npn.NegotiationSupport;
import org.glassfish.grizzly.ssl.SSLBaseFilter;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLUtils;

/**
 * Grizzly TLS Next Protocol Negotiation support class.
 * 
 */
public class AlpnSupport {
    private final static Logger LOGGER = Grizzly.logger(AlpnSupport.class);

    private final static Map<SSLEngine, Connection> SSL_TO_CONNECTION_MAP =
            new WeakHashMap<SSLEngine, Connection>();
    
    private static final AlpnSupport INSTANCE;
    
    static {
        
        boolean isExtensionFound = false;
        try {
            ClassLoader.getSystemClassLoader().loadClass("sun.security.ssl.GrizzlyNPN");
            isExtensionFound = true;
        } catch (Throwable e) {
            LOGGER.log(Level.FINE, "TLS ALPN extension is not found:", e);
        }
        
        INSTANCE = isExtensionFound ? new AlpnSupport() : null;
    }

    public static boolean isEnabled() {
        return INSTANCE != null;
    }

    public static AlpnSupport getInstance() {
        if (!isEnabled()) {
            throw new IllegalStateException("TLS ALPN is disabled");
        }
        
        return INSTANCE;
    }

    public static Connection getConnection(final SSLEngine engine) {
        synchronized (SSL_TO_CONNECTION_MAP) {
            return SSL_TO_CONNECTION_MAP.get(engine);
        }
    }
    
    private static void setConnection(final SSLEngine engine,
            final Connection connection) {
        synchronized (SSL_TO_CONNECTION_MAP) {
            SSL_TO_CONNECTION_MAP.put(engine, connection);
        }
    }

    private final Map<Object, AlpnServerNegotiator> serverSideNegotiators =
            new WeakHashMap<Object, AlpnServerNegotiator>();
    private final ReadWriteLock serverSideLock = new ReentrantReadWriteLock();
    
    private final Map<Object, AlpnClientNegotiator> clientSideNegotiators =
            new WeakHashMap<Object, AlpnClientNegotiator>();
    private final ReadWriteLock clientSideLock = new ReentrantReadWriteLock();

    private final SSLFilter.HandshakeListener handshakeListener = 
            new SSLFilter.HandshakeListener() {

        @Override
        public void onStart(final Connection connection) {
            final SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);
            assert sslEngine != null;
            
            if (sslEngine.getUseClientMode()) {
                AlpnClientNegotiator negotiator;
                clientSideLock.readLock().lock();
                
                try {
                    negotiator = clientSideNegotiators.get(connection);
                    if (negotiator == null) {
                        negotiator = clientSideNegotiators.get(connection.getTransport());
                    }
                } finally {
                    clientSideLock.readLock().unlock();
                }
                
                if (negotiator != null) {
                    // add a CloseListener to ensure we remove the
                    // negotiator associated with this SSLEngine
                    connection.addCloseListener(new CloseListener() {
                        @Override
                        public void onClosed(Closeable closeable, ICloseType type) throws IOException {
                            NegotiationSupport.removeClientNegotiator(sslEngine);
                        }
                    });
                    setConnection(sslEngine, connection);
                    NegotiationSupport.addNegotiator(sslEngine, negotiator);
                }
            } else {
                AlpnServerNegotiator negotiator;
                serverSideLock.readLock().lock();
                
                try {
                    negotiator = serverSideNegotiators.get(connection);
                    if (negotiator == null) {
                        negotiator = serverSideNegotiators.get(connection.getTransport());
                    }
                } finally {
                    serverSideLock.readLock().unlock();
                }
                
                if (negotiator != null) {

                    // add a CloseListener to ensure we remove the
                    // negotiator associated with this SSLEngine
                    connection.addCloseListener(new CloseListener() {
                        @Override
                        public void onClosed(Closeable closeable, ICloseType type) throws IOException {
                            NegotiationSupport.removeServerNegotiator(sslEngine);
                        }
                    });
                    setConnection(sslEngine, connection);
                    NegotiationSupport.addNegotiator(sslEngine, negotiator);
                }
            }
            
        }

        @Override
        public void onComplete(final Connection connection) {
        }

        @Override
        public void onFailure(Connection connection, Throwable t) {
        }
    };
    
    private AlpnSupport() {
    }    
    
    public void configure(final SSLBaseFilter sslFilter) {
        sslFilter.addHandshakeListener(handshakeListener);
    }
    
    public void setServerSideNegotiator(final Transport transport,
            final AlpnServerNegotiator negotiator) {
        putServerSideNegotiator(transport, negotiator);
    }
    
    public void setServerSideNegotiator(final Connection connection,
            final AlpnServerNegotiator negotiator) {
        putServerSideNegotiator(connection, negotiator);
    }

    
    public void setClientSideNegotiator(final Transport transport,
            final AlpnClientNegotiator negotiator) {
        putClientSideNegotiator(transport, negotiator);
    }

    public void setClientSideNegotiator(final Connection connection,
            final AlpnClientNegotiator negotiator) {
        putClientSideNegotiator(connection, negotiator);
    }

    private void putServerSideNegotiator(final Object object,
            final AlpnServerNegotiator negotiator) {
        serverSideLock.writeLock().lock();

        try {
            serverSideNegotiators.put(object, negotiator);
        } finally {
            serverSideLock.writeLock().unlock();
        }
    }

    private void putClientSideNegotiator(final Object object,
            final AlpnClientNegotiator negotiator) {
        clientSideLock.writeLock().lock();

        try {
            clientSideNegotiators.put(object, negotiator);
        } finally {
            clientSideLock.writeLock().unlock();
        }
    }

}
