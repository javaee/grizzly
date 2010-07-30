/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */
package com.sun.grizzly.http.server;

import com.sun.grizzly.ConnectionProbe;
import com.sun.grizzly.MonitoringAware;
import com.sun.grizzly.TransportProbe;
import com.sun.grizzly.http.HttpProbe;
import com.sun.grizzly.http.server.filecache.FileCacheProbe;
import com.sun.grizzly.memory.MemoryProbe;
import com.sun.grizzly.utils.ArraySet;

/**
 * Grizzly web server monitoring config.
 * 
 * @author Alexey Stashok
 */
public final class MonitoringConfig {
    private final MonitoringAware<MemoryProbe> memoryConfig =
            new InternalMonitoringAware<MemoryProbe>(MemoryProbe.class);

    private final MonitoringAware<TransportProbe> transportConfig =
            new InternalMonitoringAware<TransportProbe>(TransportProbe.class);

    private final MonitoringAware<ConnectionProbe> connectionConfig =
            new InternalMonitoringAware<ConnectionProbe>(ConnectionProbe.class);

    private final MonitoringAware<FileCacheProbe> fileCacheConfig =
            new InternalMonitoringAware<FileCacheProbe>(FileCacheProbe.class);

    private final MonitoringAware<HttpProbe> httpConfig =
            new InternalMonitoringAware<HttpProbe>(HttpProbe.class);

    private final MonitoringAware<WebServerProbe> webServerConfig =
            new InternalMonitoringAware<WebServerProbe>(WebServerProbe.class);

    /**
     * Get the memory monitoring config.
     *
     * @return the memory monitoring config.
     */
    public MonitoringAware<MemoryProbe> getMemoryConfig() {
        return memoryConfig;
    }

    /**
     * Get the connection monitoring config.
     *
     * @return the connection monitoring config.
     */
    public MonitoringAware<ConnectionProbe> getConnectionConfig() {
        return connectionConfig;
    }

    /**
     * Get the transport monitoring config.
     *
     * @return the transport monitoring config.
     */
    public MonitoringAware<TransportProbe> getTransportConfig() {
        return transportConfig;
    }

    /**
     * Get the file cache monitoring config.
     *
     * @return the file cache monitoring config.
     */
    public MonitoringAware<FileCacheProbe> getFileCacheConfig() {
        return fileCacheConfig;
    }

    /**
     * Get the http monitoring config.
     *
     * @return the http monitoring config.
     */
    public MonitoringAware<HttpProbe> getHttpConfig() {
        return httpConfig;
    }

    /**
     * Get the web server monitoring config.
     *
     * @return the web server monitoring config.
     */
    public MonitoringAware<WebServerProbe> getWebServerConfig() {
        return webServerConfig;
    }

    /**
     * Config container
     * 
     * @param <E> probe type
     */
    private static class InternalMonitoringAware<E> implements MonitoringAware<E> {
        private final Class<E> clazz;

        public InternalMonitoringAware(Class<E> clazz) {
            this.clazz = clazz;
        }

        private final ArraySet<E> monitoringProbes =
                new ArraySet<E>();

        /**
         * {@inheritDoc}
         */
        @Override
        public void addProbes(E... probes) {
            monitoringProbes.add(probes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean removeProbes(E... probes) {
            return monitoringProbes.remove(probes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public E[] getProbes() {
            return monitoringProbes.obtainArrayCopy(clazz);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clearProbes() {
            monitoringProbes.clear();
        }
    }
}
