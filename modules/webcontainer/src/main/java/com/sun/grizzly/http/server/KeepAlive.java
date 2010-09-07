/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.server;

import com.sun.grizzly.monitoring.jmx.AbstractJmxMonitoringConfig;
import com.sun.grizzly.monitoring.jmx.JmxMonitoringAware;
import com.sun.grizzly.monitoring.jmx.JmxMonitoringConfig;
import com.sun.grizzly.monitoring.jmx.JmxObject;

/**
 * Web container configuration for keep-alive HTTP connections.
 * 
 * @author Alexey Stashok
 */
public final class KeepAlive implements JmxMonitoringAware<KeepAliveProbe> {
    /**
     * Keep alive probes
     */
    protected final AbstractJmxMonitoringConfig<KeepAliveProbe> monitoringConfig =
            new AbstractJmxMonitoringConfig<KeepAliveProbe>(KeepAliveProbe.class) {

        @Override
        public JmxObject createManagementObject() {
            return createJmxManagementObject();
        }

    };
    
    /**
     * The number int seconds a connection may be idle before being timed out.
     */
    private int timeoutInSeconds = Constants.KEEP_ALIVE_TIMEOUT_IN_SECONDS;




    /**
     * @return the number in seconds a connection may be idle before being
     *  timed out.
     */
    public int getTimeoutInSeconds() {

        return timeoutInSeconds;

    }


    /**
     * <p>
     * Configures idle connection timeout behavior.
     * </p>
     *
     * @param keepAliveTimeoutInSeconds the number in seconds a connection may
     *  be idle before being timed out.  Values less than zero are considered as FOREVER.
     */
    public void setTimeoutInSeconds(final int keepAliveTimeoutInSeconds) {

        if (keepAliveTimeoutInSeconds < 0) {
            timeoutInSeconds = -1;
        } else {
            timeoutInSeconds = keepAliveTimeoutInSeconds;
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JmxMonitoringConfig<KeepAliveProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    protected JmxObject createJmxManagementObject() {
        return new com.sun.grizzly.http.server.jmx.KeepAlive(this);
    }
}
