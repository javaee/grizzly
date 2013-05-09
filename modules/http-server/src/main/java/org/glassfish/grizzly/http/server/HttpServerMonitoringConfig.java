/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.TransportProbe;
import org.glassfish.grizzly.http.HttpProbe;
import org.glassfish.grizzly.http.server.filecache.FileCacheProbe;
import org.glassfish.grizzly.memory.MemoryProbe;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;

/**
 * Grizzly web server monitoring config.
 * 
 * @author Alexey Stashok
 */
public final class HttpServerMonitoringConfig {
    private final DefaultMonitoringConfig<MemoryProbe> memoryConfig =
            new DefaultMonitoringConfig<MemoryProbe>(MemoryProbe.class);

    private final DefaultMonitoringConfig<TransportProbe> transportConfig =
            new DefaultMonitoringConfig<TransportProbe>(TransportProbe.class);

    private final DefaultMonitoringConfig<ConnectionProbe> connectionConfig =
            new DefaultMonitoringConfig<ConnectionProbe>(ConnectionProbe.class);

    private final DefaultMonitoringConfig<ThreadPoolProbe> threadPoolConfig =
            new DefaultMonitoringConfig<ThreadPoolProbe>(ThreadPoolProbe.class);

    private final DefaultMonitoringConfig<FileCacheProbe> fileCacheConfig =
            new DefaultMonitoringConfig<FileCacheProbe>(FileCacheProbe.class);

    private final DefaultMonitoringConfig<HttpProbe> httpConfig =
            new DefaultMonitoringConfig<HttpProbe>(HttpProbe.class);

    private final DefaultMonitoringConfig<HttpServerProbe> webServerConfig =
            new DefaultMonitoringConfig<HttpServerProbe>(HttpServerProbe.class);

    /**
     * Get the memory monitoring config.
     *
     * @return the memory monitoring config.
     */
    public MonitoringConfig<MemoryProbe> getMemoryConfig() {
        return memoryConfig;
    }

    /**
     * Get the connection monitoring config.
     *
     * @return the connection monitoring config.
     */
    public MonitoringConfig<ConnectionProbe> getConnectionConfig() {
        return connectionConfig;
    }

    /**
     * Get the thread pool monitoring config.
     *
     * @return the thread pool monitoring config.
     */
    public MonitoringConfig<ThreadPoolProbe> getThreadPoolConfig() {
        return threadPoolConfig;
    }

    /**
     * Get the transport monitoring config.
     *
     * @return the transport monitoring config.
     */
    public MonitoringConfig<TransportProbe> getTransportConfig() {
        return transportConfig;
    }

    /**
     * Get the file cache monitoring config.
     *
     * @return the file cache monitoring config.
     */
    public MonitoringConfig<FileCacheProbe> getFileCacheConfig() {
        return fileCacheConfig;
    }

    /**
     * Get the http monitoring config.
     *
     * @return the http monitoring config.
     */
    public MonitoringConfig<HttpProbe> getHttpConfig() {
        return httpConfig;
    }

    /**
     * Get the web server monitoring config.
     *
     * @return the web server monitoring config.
     */
    public MonitoringConfig<HttpServerProbe> getWebServerConfig() {
        return webServerConfig;
    }
}
