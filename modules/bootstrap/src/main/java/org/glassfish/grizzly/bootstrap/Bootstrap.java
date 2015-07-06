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
import com.typesafe.config.ConfigFactory;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO Documentation.
 */
public class Bootstrap {

    static final Logger LOGGER = Grizzly.logger(Bootstrap.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            return;
        }
        final String fileName = args[0];
        final File configFile = new File(fileName);
        if (!configFile.exists()) {
            throw new IllegalArgumentException(
                    String.format("Config file, %s, does not exist.", fileName));
        }
        if (!configFile.canRead()) {
            throw new IllegalStateException(
                    String.format("Config file, %s, is not readable.", fileName));
        }

        final Bootstrap bootstrap = new Bootstrap();
        bootstrap.doBootstrap(configFile);

    }


    // --------------------------------------------------------- Private Methods


    private void doBootstrap(final File configFile) {
        final Config config = ConfigFactory.parseFile(configFile);
        final Map<String,Transport> transportsMap;
        config.resolve(); // manually resolve references as parseFile doesn't do this for us.
        Map<String,FilterChain> filterChainsMap = Collections.emptyMap();
        if (config.hasPath("grizzly.filter-chains")) {
            filterChainsMap = getFiltersChainsFromConfig(config);
        }


        if (config.hasPath("grizzly.transports")) {
            transportsMap = getTransportsFromConfig(config);
            for (Transport t : transportsMap.values()) {
                t.setProcessor(filterChainsMap.get(t.getName()));
                t.setKernelThreadPoolConfig(ThreadPoolConfig.defaultConfig().setDaemon(false));
                try {
                    t.start();
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.info(String.format("Transport [%s] started.", t.getName()));
                    }
                } catch (IOException ioe) {
                    throw new IllegalStateException("Unable to start transport");
                }
            }

            // TODO: this will probably need to be moved later
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.info("Stopping service...");
                    }
                    for (Transport t : transportsMap.values()) {
                        try {
                            t.shutdownNow();
                            if (LOGGER.isLoggable(Level.INFO)) {
                                LOGGER.info(String.format("Transport [%s] stopped.", t.getName()));
                            }
                        } catch (IOException ioe) {
                            if (LOGGER.isLoggable(Level.SEVERE)) {
                                LOGGER.severe(
                                        String.format("Unable to terminate transport [%s]: %s",
                                                t.getName(),
                                                ioe.toString()));
                            }
                        }
                    }
                }
            });
        }
    }


    private Map<String,FilterChain> getFiltersChainsFromConfig(final Config config) {
        final List<? extends Config> filterChains = config.getConfigList("grizzly.filter-chains");
        final Map<String,FilterChain> filterChainMap =
                new HashMap<String,FilterChain>(filterChains.size());
        final FilterChainFactory filterChainFactory = new FilterChainFactory();
        for (final Config filterChainConfig : filterChains) {
            final FilterChain filterChain = filterChainFactory.createFrom(filterChainConfig);
            final List<String> transports = filterChainConfig.getStringList("transports");
            for (String transport : transports) {
                filterChainMap.put(transport, filterChain);
            }
        }
        return filterChainMap;
    }

    private Map<String,Transport> getTransportsFromConfig(final Config config) {
        final List<? extends Config> transports = config.getConfigList("grizzly.transports");
        final Map<String,Transport> transportsMap =
                new HashMap<String, Transport>(transports.size());
        final TransportFactory transportFactory = new TransportFactory();
        for (final Config transportConfig : transports) {
            final Transport transport = transportFactory.createFrom(transportConfig);
            transportsMap.put(transport.getName(), transport);
        }
        return transportsMap;
    }

}
