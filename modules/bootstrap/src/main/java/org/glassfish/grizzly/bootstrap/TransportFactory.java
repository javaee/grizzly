/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.bootstrap;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.nio.transport.*;

import java.io.IOException;
import java.util.logging.Level;

import static org.glassfish.grizzly.bootstrap.Bootstrap.LOGGER;

/**
 * TODO Documentation
 */
class TransportFactory implements InstanceFactory<Transport> {

    private static final ConfigValue DEFAULT_NETWORK_HOST =
            ConfigValueFactory.fromAnyRef("0.0.0.0");
    private static final ConfigValue DEFAULT_NETWORK_PORT =
            ConfigValueFactory.fromAnyRef(0);


    public Transport createFrom(Config config) {
        final ConfigValue name = config.getValue("name");
        if (name == null) {
            throw new IllegalArgumentException("No defined name for transport.");
        }
        final String transportName = (String) name.unwrapped();
        final ConfigValue protocol = config.getValue("protocol");
        if (protocol == null) {
            throw new IllegalArgumentException(
                    String.format("No protocol defined for transport named [%s].",
                                  transportName));
        }
        final String protocolValue = ((String) protocol.unwrapped()).toLowerCase().trim();
        if ("tcp".equals(protocolValue)) {
            return doTcpTransportConfig(transportName, config);
        } else if ("udp".equals(protocolValue)) {
            return doUdpTransportConfig(transportName, config);
        } else {
            throw new IllegalArgumentException(
                    String.format("Unknown protocol type [%s] defined for transport named [%s].",
                                  protocolValue,
                                  transportName));
        }
    }


    // --------------------------------------------------------- Private Methods


    private Transport doTcpTransportConfig(final String name,
                                           final Config config) {
        final TCPNIOTransportBuilder builder = TCPNIOTransportBuilder.newInstance();
        final TCPNIOTransport t = builder.setName(name).build();
        final ConfigValue host =
                BootstrapUtils.get(config, "host", DEFAULT_NETWORK_HOST);
        final ConfigValue port =
                BootstrapUtils.get(config, "port", DEFAULT_NETWORK_PORT);
        try {
            TCPNIOServerConnection c = t.bind((String) host.unwrapped(),
                                              Integer.parseInt(port.render()));
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info(
                        String.format("Binding transport [%s] to [%s]",
                                name,
                                c.getLocalAddress().toString()));
            }
            return t;
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }


    private Transport doUdpTransportConfig(final String name,
                                           final Config config) {
        final UDPNIOTransportBuilder builder = UDPNIOTransportBuilder.newInstance();
        final UDPNIOTransport t = builder.setName(name).build();
        final ConfigValue host =
                BootstrapUtils.get(config, "host", DEFAULT_NETWORK_HOST);
        final ConfigValue port =
                BootstrapUtils.get(config, "port", DEFAULT_NETWORK_PORT);
        try {
            UDPNIOServerConnection c = t.bind((String) host.unwrapped(),
                    Integer.parseInt(port.render()));
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info(
                        String.format("Binding transport [%s] to [%s]",
                                name,
                                c.getLocalAddress().toString()));
            }
            return t;
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }


}
