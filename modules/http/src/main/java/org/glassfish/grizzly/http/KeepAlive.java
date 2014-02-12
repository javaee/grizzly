/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.DefaultMonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringUtils;

/**
 * Web container configuration for keep-alive HTTP connections.
 * 
 * @author Alexey Stashok
 */
public final class KeepAlive implements MonitoringAware<KeepAliveProbe> {
    /**
     * Keep alive probes
     */
    protected final DefaultMonitoringConfig<KeepAliveProbe> monitoringConfig =
            new DefaultMonitoringConfig<KeepAliveProbe>(KeepAliveProbe.class) {

        @Override
        public Object createManagementObject() {
            return createJmxManagementObject();
        }

    };
    
    /**
     * The number int seconds a connection may be idle before being timed out.
     */
    private int idleTimeoutInSeconds = Constants.KEEP_ALIVE_TIMEOUT_IN_SECONDS;

    /**
     * The max number of HTTP requests allowed to be processed on one keep-alive connection.
     */
    private int maxRequestsCount = Constants.DEFAULT_MAX_KEEP_ALIVE;

    public KeepAlive() {
    }

    /**
     * The copy constructor.
     * @param keepAlive
     */
    public KeepAlive(final KeepAlive keepAlive) {
        this.idleTimeoutInSeconds = keepAlive.idleTimeoutInSeconds;
        this.maxRequestsCount = keepAlive.maxRequestsCount;
    }


    /**
     * @return the number in seconds a connection may be idle before being
     *  timed out.
     */
    public int getIdleTimeoutInSeconds() {

        return idleTimeoutInSeconds;

    }


    /**
     * <p>
     * Configures idle connection timeout behavior.
     * </p>
     *
     * @param idleTimeoutInSeconds the number in seconds a connection may
     *  be idle before being timed out.  Values less than zero are considered as FOREVER.
     */
    public void setIdleTimeoutInSeconds(final int idleTimeoutInSeconds) {

        if (idleTimeoutInSeconds < 0) {
            this.idleTimeoutInSeconds = -1;
        } else {
            this.idleTimeoutInSeconds = idleTimeoutInSeconds;
        }

    }

    /**
     * @return the max number of HTTP requests allowed to be processed on one keep-alive connection.
     */
    public int getMaxRequestsCount() {
        return maxRequestsCount;
    }

    /**
     * <p>
     * Configures the max number of HTTP requests allowed to be processed on one keep-alive connection.
     * </p>
     *
     * @param maxRequestsCount the max number of HTTP requests allowed to be
     * processed on one keep-alive connection. Values less than zero are considered as UNLIMITED.
     */
    public void setMaxRequestsCount(int maxRequestsCount) {
        this.maxRequestsCount = maxRequestsCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<KeepAliveProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    protected Object createJmxManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.http.jmx.KeepAlive", this, KeepAlive.class);
    }

    /**
     * Notify registered {@link KeepAliveProbe}s about the "keep-alive connection accepted" event.
     *
     * @param keepAlive the <tt>KeepAlive</tt> event occurred on.
     * @param connection {@link Connection} been accepted.
     */
    protected static void notifyProbesConnectionAccepted(
            final KeepAlive keepAlive, final Connection connection) {
        final KeepAliveProbe[] probes =
                keepAlive.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (KeepAliveProbe probe : probes) {
                probe.onConnectionAcceptEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link KeepAliveProbe}s about the "keep-alive connection hit" event.
     *
     * @param keepAlive the <tt>KeepAlive</tt> event occurred on.
     * @param connection {@link Connection} been hit.
     * @param requestNumber the request number being processed on the given {@link Connection}.
     */
    protected static void notifyProbesHit(
            final KeepAlive keepAlive, final Connection connection,
            final int requestNumber) {
        
        final KeepAliveProbe[] probes =
                keepAlive.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (KeepAliveProbe probe : probes) {
                probe.onHitEvent(connection, requestNumber);
            }
        }
    }

    /**
     * Notify registered {@link KeepAliveProbe}s about the "keep-alive connection refused" event.
     *
     * @param keepAlive the <tt>KeepAlive</tt> event occurred on.
     * @param connection {@link Connection} been refused.
     */
    protected static void notifyProbesRefused(
            final KeepAlive keepAlive, final Connection connection) {

        final KeepAliveProbe[] probes =
                keepAlive.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (KeepAliveProbe probe : probes) {
                probe.onRefuseEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link KeepAliveProbe}s about the "keep-alive connection timeout" event.
     *
     * @param keepAlive the <tt>KeepAlive</tt> event occurred on.
     * @param connection {@link Connection} been timeout.
     */
    protected static void notifyProbesTimeout(
            final KeepAlive keepAlive, final Connection connection) {

        final KeepAliveProbe[] probes =
                keepAlive.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (KeepAliveProbe probe : probes) {
                probe.onTimeoutEvent(connection);
            }
        }
    }

}
