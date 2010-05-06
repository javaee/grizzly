/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 *   The contents of this file are subject to the terms of either the GNU
 *   General Public License Version 2 only ("GPL") or the Common Development
 *   and Distribution License("CDDL") (collectively, the "License").  You
 *   may not use this file except in compliance with the License. You can obtain
 *   a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 *   or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 *   language governing permissions and limitations under the License.
 *
 *   When distributing the software, include this License Header Notice in each
 *   file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *   Sun designates this particular file as subject to the "Classpath" exception
 *   as provided by Sun in the GPL Version 2 section of the License file that
 *   accompanied this code.  If applicable, add the following below the License
 *   Header, with the fields enclosed by brackets [] replaced by your own
 *   identifying information: "Portions Copyrighted [year]
 *   [name of copyright owner]"
 *
 *   Contributor(s):
 *
 *   If you wish your version of this file to be governed by only the CDDL or
 *   only the GPL Version 2, indicate your decision by adding "[Contributor]
 *   elects to include this software in this distribution under the [CDDL or GPL
 *   Version 2] license."  If you don't indicate a single choice of license, a
 *   recipient has the option to distribute your version of this file under
 *   either the CDDL, the GPL Version 2 or to extend the choice of license to
 *   its licensees as provided above.  However, if you add GPL Version 2 code
 *   and therefore, elected the GPL Version 2 license, then the option applies
 *   only if the new code is made subject to such option by the copyright
 *   holder.
 *
 */
package com.sun.grizzly.config.dom;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jvnet.hk2.component.Injectable;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBean;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.DuckTyped;
import org.jvnet.hk2.config.types.PropertyBag;

/**
 * Binds protocol to a specific endpoint to listen on
 */
@Configured
public interface NetworkListener extends ConfigBeanProxy, Injectable, PropertyBag {
    /**
     * IP address to listen on
     */
    @Attribute(defaultValue = "0.0.0.0")
    @NetworkAddress
    String getAddress();

    void setAddress(String value);

    /**
     * If false, a configured listener, is disabled
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getEnabled();

    void setEnabled(String enabled);

    /**
     * If true, a jk listener is enabled
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getJkEnabled();

    void setJkEnabled(String enabled);

    /**
     * Network-listener name, which could be used as reference
     */
    @Attribute(required = true, key = true)
    String getName();

    void setName(String value);

    /**
     * Port to listen on
     */
    @Attribute(required = true, dataType = Integer.class)
    String getPort();

    void setPort(String value);

    @DuckTyped
    Protocol findProtocol();

    @DuckTyped
    Protocol findHttpProtocol();

    /**
     * Reference to a protocol
     */
    @Attribute(required = true)
    String getProtocol();

    void setProtocol(String value);

    @DuckTyped
    ThreadPool findThreadPool();

    /**
     * Reference to a thread-pool, defined earlier in the document.
     */
    @Attribute
    String getThreadPool();

    void setThreadPool(String value);

    @DuckTyped
    Transport findTransport();

    /**
     * Reference to a low-level transport
     */
    @Attribute(required = true)
    String getTransport();

    void setTransport(String value);

    class Duck {
        public static Protocol findProtocol(NetworkListener listener) {
            return listener.getParent().getParent(NetworkConfig.class).findProtocol(listener.getProtocol());
        }

        public static Protocol findHttpProtocol(NetworkListener listener) {
            return findHttpProtocol(new HashSet<String>(), findProtocol(listener));
        }

        private static Protocol findHttpProtocol(Set<String> tray, Protocol protocol) {
            if(protocol == null) {
                return null;
            }
            
            final String protocolName = protocol.getName();
            if (tray.contains(protocolName)) {
                throw new IllegalStateException("Loop found in Protocol definition. Protocol name: " + protocol.getName());
            }

            if (protocol.getHttp() != null) {
                return protocol;
            } else if (protocol.getPortUnification() != null) {
                Habitat habitat = ConfigBean.unwrap(protocol).getHabitat();
                final List<ProtocolFinder> finders = protocol.getPortUnification().getProtocolFinder();
                tray.add(protocolName);

                try {
                    Protocol foundHttpProtocol = null;
                    for (ProtocolFinder finder : finders) {
                        final Protocol subProtocol = finder.findProtocol();
                        if (subProtocol != null) {
                            final Protocol httpProtocol = findHttpProtocol(tray, subProtocol);
                            if (httpProtocol != null) {
                                if (foundHttpProtocol == null) {
                                    foundHttpProtocol = httpProtocol;
                                } else {
                                    throw new IllegalStateException("Port unification allows only one \"<http>\" definition");
                                }
                            }
                        }
                    }

                    return foundHttpProtocol;
                } finally {
                    tray.remove(protocolName);
                }
            }

            return null;
        }

        public static ThreadPool findThreadPool(NetworkListener listener) {
            List<ThreadPool> list = listener.getParent(NetworkListeners.class).getThreadPool();
            if(list == null) {
                list = ConfigBean.unwrap(listener.getParent().getParent().getParent()).nodeByTypeElements(ThreadPool.class);
            }
            for (ThreadPool pool : list) {
                if(listener.getThreadPool().equals(pool.getName())) {
                    return pool;
                }
            }
            return null;
        }

        public static Transport findTransport(NetworkListener listener) {
            List<Transport> list = listener.getParent().getParent(NetworkConfig.class).getTransports().getTransport();
            for (Transport transport : list) {
                if(listener.getTransport().equals(transport.getName())) {
                    return transport;
                }
            }
            return null;
        }
    }
}
