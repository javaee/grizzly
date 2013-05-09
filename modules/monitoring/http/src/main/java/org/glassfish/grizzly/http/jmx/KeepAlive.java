/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.jmx;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.GenericCloseListener;
import org.glassfish.grizzly.http.KeepAliveProbe;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;

/**
 * JMX management object for {@link org.glassfish.grizzly.http.KeepAlive}.
 *
 * @since 2.0
 */
@ManagedObject
@Description("The configuration for HTTP keep-alive connections.")
public class KeepAlive extends JmxObject {
    /**
     * The {@link org.glassfish.grizzly.http.KeepAlive} being managed.
     */
    private final org.glassfish.grizzly.http.KeepAlive keepAlive;

    /**
     * The number of live keep-alive connections.
     */
    private final AtomicInteger keepAliveConnectionsCount = new AtomicInteger();

    /**
     * The number of requests processed on a keep-alive connections.
     */
    private final AtomicInteger keepAliveHitsCount = new AtomicInteger();

    /**
     * The number of times keep-alive mode was refused.
     */
    private final AtomicInteger keepAliveRefusesCount = new AtomicInteger();

    /**
     * The number of times idle keep-alive connections were closed by timeout.
     */
    private final AtomicInteger keepAliveTimeoutsCount = new AtomicInteger();

    /**
     * The {@link JMXKeepAliveProbe} used to track keep-alive statistics.
     */
    private final JMXKeepAliveProbe keepAliveProbe = new JMXKeepAliveProbe();
    
    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new JMX managed KeepAlive for the specified
     * {@link org.glassfish.grizzly.http.KeepAlive} instance.
     *
     * @param keepAlive the {@link org.glassfish.grizzly.http.KeepAlive}
     *  to manage.
     */
    public KeepAlive(org.glassfish.grizzly.http.KeepAlive keepAlive) {
        this.keepAlive = keepAlive;
    }

    // -------------------------------------------------- Methods from JmxObject


    /**
     * {@inheritDoc}
     */
    @Override
    public String getJmxName() {
        return "Keep-Alive";
    }

    /**
     * <p>
     * {@inheritDoc}
     * </p>
     *
     * <p>
     * When invoked, this method will add a {@link KeepAliveProbe} to track
     * statistics.
     * </p>
     */
    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        keepAlive.getMonitoringConfig().addProbes(keepAliveProbe);
    }

    /**
     * <p>
     * {@inheritDoc}
     * </p>
     *
     * <p>
     * When invoked, this method will remove the {@link KeepAliveProbe} added
     * by the {@link #onRegister(GrizzlyJmxManager, GmbalMBean)}
     * call.
     * </p>
     */
    @Override
    protected void onDeregister(GrizzlyJmxManager mom) {
        keepAlive.getMonitoringConfig().removeProbes(keepAliveProbe);
    }

    // --------------------------------------------------- Keep Alive Properties


    /**
     * @see org.glassfish.grizzly.http.KeepAlive#getIdleTimeoutInSeconds()
     */
    @ManagedAttribute(id="idle-timeout-seconds")
    @Description("The time period keep-alive connection may stay idle")
    public int getIdleTimeoutInSeconds() {
        return keepAlive.getIdleTimeoutInSeconds();
    }

    /**
     * @see org.glassfish.grizzly.http.KeepAlive#getMaxRequestsCount()
     */
    @ManagedAttribute(id="max-requests-count")
    @Description("the max number of HTTP requests allowed to be processed on one keep-alive connection")
    public int getMaxRequestsCount() {
        return keepAlive.getMaxRequestsCount();
    }

    /**
     * @return the number live keep-alive connections.
     */
    @ManagedAttribute(id="live-connections-count")
    @Description("The number of live keep-alive connections")
    public int getConnectionsCount() {
        return keepAliveConnectionsCount.get();
    }

    /**
     * @return the number of requests processed on a keep-alive connections.
     */
    @ManagedAttribute(id="hits-count")
    @Description("The number of requests processed on a keep-alive connections.")
    public int getHitsCount() {
        return keepAliveHitsCount.get();
    }

    /**
     * @return the number of times keep-alive mode was refused.
     */
    @ManagedAttribute(id="refuses-count")
    @Description("The number of times keep-alive mode was refused.")
    public int getRefusesCount() {
        return keepAliveRefusesCount.get();
    }

    /**
     * @return the number of times idle keep-alive connections were closed by timeout.
     */
    @ManagedAttribute(id="timeouts-count")
    @Description("The number of times idle keep-alive connections were closed by timeout.")
    public int getTimeoutsCount() {
        return keepAliveTimeoutsCount.get();
    }

    // ---------------------------------------------------------- Nested Classes


    /**
     * JMX statistic gathering {@link KeepAliveProbe}.
     */
    private final class JMXKeepAliveProbe implements KeepAliveProbe {

        @Override
        public void onConnectionAcceptEvent(Connection connection) {
            keepAliveConnectionsCount.incrementAndGet();
            connection.addCloseListener(new GenericCloseListener() {

                @Override
                public void onClosed(final Closeable closeable,
                        final CloseType closeType) throws IOException {
                    keepAliveConnectionsCount.decrementAndGet();
                }
            });
        }

        @Override
        public void onHitEvent(Connection connection, int requestCounter) {
            keepAliveHitsCount.incrementAndGet();
        }

        @Override
        public void onRefuseEvent(Connection connection) {
            keepAliveRefusesCount.incrementAndGet();
        }

        @Override
        public void onTimeoutEvent(Connection connection) {
            keepAliveTimeoutsCount.incrementAndGet();
        }


        // ----------------------------------------- Methods from KeepAliveProbe


    } // END JMXKeepAliveProbe
}
