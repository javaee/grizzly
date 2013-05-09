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

package org.glassfish.grizzly.http.server.jmx;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.HttpServerProbe;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;

/**
 * JMX management object for the {@link org.glassfish.grizzly.http.server.HttpServerFilter}.
 *
 * @since 2.0
 */
@ManagedObject
@Description("The HttpServerFilter is the entity responsible for providing and processing higher level abstractions based on HTTP protocol.")
public class HttpServerFilter extends JmxObject {

    private final org.glassfish.grizzly.http.server.HttpServerFilter httpServerFilter;

    private final AtomicLong receivedCount = new AtomicLong();
    private final AtomicLong completedCount = new AtomicLong();
    private final AtomicInteger suspendCount = new AtomicInteger();
    private final AtomicLong timedOutCount = new AtomicLong();
    private final AtomicLong cancelledCount = new AtomicLong();

    private final HttpServerProbe probe = new JmxWebServerProbe();

    // ------------------------------------------------------------ Constructors


    public HttpServerFilter(org.glassfish.grizzly.http.server.HttpServerFilter httpServerFilter) {
        this.httpServerFilter = httpServerFilter;
    }


    // -------------------------------------------------- Methods from JxmObject


    @Override
    public String getJmxName() {
        return "HttpServerFilter";
    }

    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        httpServerFilter.getMonitoringConfig().addProbes(probe);
    }

    @Override
    protected void onDeregister(GrizzlyJmxManager mom) {
        httpServerFilter.getMonitoringConfig().removeProbes(probe);
    }


    // -------------------------------------------------------------- Attributes


    /**
     * @return the number of requests this {@link org.glassfish.grizzly.http.server.HttpServerFilter}
     *  has received.
     */
    @ManagedAttribute(id="requests-received-count")
    @Description("The total number of requests received.")
    public long getRequestsReceivedCount() {
        return receivedCount.get();
    }


    /**
     * @return the number of requests this {@link org.glassfish.grizzly.http.server.HttpServerFilter}
     *  has completed servicing.
     */
    @ManagedAttribute(id="requests-completed-count")
    @Description("The total number of requests that have been successfully completed.")
    public long getRequestsCompletedCount() {
        return completedCount.get();
    }


    /**
     * @return the number of requests currently suspended.
     */
    @ManagedAttribute(id="current-suspended-request-count")
    @Description("The current number of requests that are suspended to be processed at a later point in time.")
    public int getRequestsSuspendedCount() {
        return suspendCount.get();
    }


    /**
     * @return the number of suspended requests that have timed out.
     */
    @ManagedAttribute(id="requests-timed-out-count")
    @Description("The total number of suspended requests that have been timed out.")
    public long getRequestsTimedOutCount() {
        return timedOutCount.get();
    }


    /**
     * @return the number of requests suspended requests that have been
     *  cancelled.
     */
    @ManagedAttribute(id="requests-cancelled-count")
    @Description("The total number of suspended requests that have been cancelled.")
    public long getRequestsCancelledCount() {
        return cancelledCount.get();
    }


    // ---------------------------------------------------------- Nested Classes


    private final class JmxWebServerProbe extends HttpServerProbe.Adapter {


        // ----------------------------------------- Methods from HttpServerProbe


        @Override
        public void onRequestReceiveEvent(org.glassfish.grizzly.http.server.HttpServerFilter filter, Connection connection, Request request) {
            receivedCount.incrementAndGet();
        }

        @Override
        public void onRequestCompleteEvent(org.glassfish.grizzly.http.server.HttpServerFilter filter, Connection connection, Response response) {
            completedCount.incrementAndGet();
        }

        @Override
        public void onRequestSuspendEvent(org.glassfish.grizzly.http.server.HttpServerFilter filter, Connection connection, Request request) {
            suspendCount.incrementAndGet();
        }

        @Override
        public void onRequestResumeEvent(org.glassfish.grizzly.http.server.HttpServerFilter filter, Connection connection, Request request) {
            if (suspendCount.get() > 0) {
                suspendCount.decrementAndGet();
            }
        }

        @Override
        public void onRequestTimeoutEvent(org.glassfish.grizzly.http.server.HttpServerFilter filter, Connection connection, Request request) {
            timedOutCount.incrementAndGet();
            if (suspendCount.get() > 0) {
                suspendCount.decrementAndGet();
            }
        }

        @Override
        public void onRequestCancelEvent(org.glassfish.grizzly.http.server.HttpServerFilter filter, Connection connection, Request request) {
            cancelledCount.incrementAndGet();
            if (suspendCount.get() > 0) {
                suspendCount.decrementAndGet();
            }
        }

    } // END JmxWebServerProbe
    
}
