/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.npn;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLUtils;

/**
 * Grizzly TLS Next Protocol Negotiation support class.
 * 
 * Current implementation serves as a Grizzly adapter of Jetty
 * org.eclipse.jetty.npn.NextProtoNego implementation, and requires OpenJDK 7+.
 * 
 * In order to enable TLS Next Protocol Negotiation it's required to download corresponding
 * Jetty npn_boot.jar (http://wiki.eclipse.org/Jetty/Feature/NPN#Versions).
 * There are 2 options npn_boot.jar might be used:
 * 1) Specify this jar using java -Xbootclasspath parameter (http://wiki.eclipse.org/Jetty/Feature/NPN#Starting_the_JVM).
 * 2) Copy npn_boot.jar to the JDK endorsed folder (check system property "java.endorsed.dirs").
 */
public class NextProtoNegSupport {
    private final static Logger LOGGER = Grizzly.logger(NextProtoNegSupport.class);
    
    private static final NextProtoNegSupport INSTANCE;
    
    static {
        
        boolean isExtensionFound = false;
        try {
            ClassLoader.getSystemClassLoader().loadClass("sun.security.ssl.NextProtoNegoExtension");
            isExtensionFound = true;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "TLS Next Protocol Negotiation extension is not found:", e);
        }
        
        INSTANCE = isExtensionFound ? new NextProtoNegSupport() : null;
    }

    public static boolean isEnabled() {
        return INSTANCE != null;
    }

    public static NextProtoNegSupport getInstance() {
        if (!isEnabled()) {
            throw new IllegalStateException("TLS Next Protocol Negotiation is disabled");
        }
        
        return INSTANCE;
    }
    
    private final Map<Object, ServerSideNegotiator> serverSideNegotiators = 
            new WeakHashMap<Object, ServerSideNegotiator>();
    private final ReadWriteLock serverSideLock = new ReentrantReadWriteLock();
    
    private final Map<Object, ClientSideNegotiator> clientSideNegotiators = 
            new WeakHashMap<Object, ClientSideNegotiator>();
    private final ReadWriteLock clientSideLock = new ReentrantReadWriteLock();

    private final SSLFilter.HandshakeListener handshakeListener = 
            new SSLFilter.HandshakeListener() {

        @Override
        public void onStart(final Connection connection) {
            final SSLEngine sslEngine = SSLUtils.getSSLEngine(connection);
            assert sslEngine != null;
            
            if (sslEngine.getUseClientMode()) {
                ClientSideNegotiator negotiator;
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
                    JettyBridge.putClientProvider(connection, sslEngine, negotiator);
                }
            } else {
                ServerSideNegotiator negotiator;
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
                    JettyBridge.putServerProvider(connection, sslEngine, negotiator);
                }
            }
            
        }

        @Override
        public void onComplete(final Connection connection) {
        }
    };
    
    private NextProtoNegSupport() {
    }    
    
    public void configure(final SSLFilter sslFilter) {
        sslFilter.addHandshakeListener(handshakeListener);
    }
    
    public void setServerSideNegotiator(final Transport transport,
            final ServerSideNegotiator negotiator) {
        putServerSideNegotiator(transport, negotiator);
    }
    
    public void setServerSideNegotiator(final Connection connection,
            final ServerSideNegotiator negotiator) {
        putServerSideNegotiator(connection, negotiator);
    }

    
    public void setClientSideNegotiator(final Transport transport,
            final ClientSideNegotiator negotiator) {
        putClientSideNegotiator(transport, negotiator);
    }

    public void setClientSideNegotiator(final Connection connection,
            final ClientSideNegotiator negotiator) {
        putClientSideNegotiator(connection, negotiator);
    }

    private void putServerSideNegotiator(final Object object,
            final ServerSideNegotiator negotiator) {
        serverSideLock.writeLock().lock();

        try {
            serverSideNegotiators.put(object, negotiator);
        } finally {
            serverSideLock.writeLock().unlock();
        }
    }

    private void putClientSideNegotiator(final Object object,
            final ClientSideNegotiator negotiator) {
        clientSideLock.writeLock().lock();

        try {
            clientSideNegotiators.put(object, negotiator);
        } finally {
            clientSideLock.writeLock().unlock();
        }
    }
    
    public interface ServerSideNegotiator {
        public List<String> supportedProtocols(Connection connection);
        public void onSuccess(Connection connection, String protocol);
        public void onNoDeal(Connection connection);
    }

    public interface ClientSideNegotiator {
        public boolean wantNegotiate(Connection connection);
        public String selectProtocol(Connection connection, List<String> protocols);
        public void onNoDeal(Connection connection);
    }
    
    private static class JettyBridge {

        public static void putServerProvider(final Connection connection,
                final SSLEngine sslEngine,
                final ServerSideNegotiator negotiator) {
            org.eclipse.jetty.npn.NextProtoNego.put(sslEngine,
                    new org.eclipse.jetty.npn.NextProtoNego.ServerProvider() {
                        @Override
                        public void unsupported() {
                            negotiator.onNoDeal(connection);
                        }

                        @Override
                        public List<String> protocols() {
                            return negotiator.supportedProtocols(connection);
                        }

                        @Override
                        public void protocolSelected(final String protocol) {
                            negotiator.onSuccess(connection, protocol);
                        }
                    });
        }

        public static void putClientProvider(final Connection connection,
                final SSLEngine sslEngine,
                final ClientSideNegotiator negotiator) {
            org.eclipse.jetty.npn.NextProtoNego.put(sslEngine,
                    new org.eclipse.jetty.npn.NextProtoNego.ClientProvider() {
                        @Override
                        public boolean supports() {
                            return negotiator.wantNegotiate(connection);
                        }

                        @Override
                        public void unsupported() {
                            negotiator.onNoDeal(connection);
                        }

                        @Override
                        public String selectProtocol(List<String> protocols) {
                            return negotiator.selectProtocol(connection, protocols);
                        }
                    });
        }
    }
}
