/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.server.jmx;

import com.sun.grizzly.Connection;
import com.sun.grizzly.http.server.KeepAliveProbe;
import com.sun.grizzly.monitoring.jmx.GrizzlyJmxManager;
import com.sun.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedObject;

/**
 * JMX management object for {@link com.sun.grizzly.http.server.KeepAlive}.
 *
 * @since 2.0
 */
@ManagedObject
@Description("The configuration for HTTP keep-alive connections.")
public class KeepAlive extends JmxObject {
    /**
     * The {@link com.sun.grizzly.http.server.KeepAlive} being managed.
     */
    private final com.sun.grizzly.http.server.KeepAlive keepAlive;


    /**
     * The {@link JMXKeepAliveProbe} used to track keep-alive statistics.
     */
    private final JMXKeepAliveProbe keepAliveProbe = new JMXKeepAliveProbe();
    
    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new JMX managed KeepAlive for the specified
     * {@link com.sun.grizzly.http.server.KeepAlive} instance.
     *
     * @param keepAlive the {@link com.sun.grizzly.http.server.KeepAlive}
     *  to manage.
     */
    public KeepAlive(com.sun.grizzly.http.server.KeepAlive keepAlive) {
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
     * When invoked, this method will add a {@link FileCacheProbe} to track
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
     * When invoked, this method will remove the {@link FileCacheProbe} added
     * by the {@link #onRegister(com.sun.grizzly.monitoring.jmx.GrizzlyJmxManager, org.glassfish.gmbal.GmbalMBean)}
     * call.
     * </p>
     */
    @Override
    protected void onUnregister(GrizzlyJmxManager mom) {
        keepAlive.getMonitoringConfig().removeProbes(keepAliveProbe);
    }

    // ---------------------------------------------------------- Nested Classes


    /**
     * JMX statistic gathering {@link KeepAliveProbe}.
     */
    private final class JMXKeepAliveProbe implements KeepAliveProbe {

        @Override
        public void onConnectionAcceptEvent(Connection connection) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void onConnectionCloseEvent(Connection connection) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void onHitEvent(Connection connection, int requestCounter) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void onRefuseEvent(Connection connection) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void onTimeoutEvent(Connection connection) {
            throw new UnsupportedOperationException("Not supported yet.");
        }


        // ----------------------------------------- Methods from KeepAliveProbe


    } // END JMXFileCacheProbe
}
