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

package com.sun.grizzly.http.server.jmx;

import com.sun.grizzly.Connection;
import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;
import com.sun.grizzly.http.server.WebServerProbe;
import com.sun.grizzly.monitoring.jmx.GrizzlyJmxManager;
import com.sun.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@ManagedObject
@Description("Grizzly Web Server Filter")
public class WebServerFilter extends JmxObject {

    private final com.sun.grizzly.http.server.WebServerFilter webServerFilter;

    private final AtomicLong receivedCount = new AtomicLong();
    private final AtomicLong completedCount = new AtomicLong();
    private final AtomicInteger suspendCount = new AtomicInteger();
    private final AtomicLong timedOutCount = new AtomicLong();
    private final AtomicLong cancelledCount = new AtomicLong();

    private final WebServerProbe probe = new JmxWebServerProbe();

    // ------------------------------------------------------------ Constructors


    public WebServerFilter(com.sun.grizzly.http.server.WebServerFilter webServerFilter) {
        this.webServerFilter = webServerFilter;
    }


    // -------------------------------------------------- Methods from JxmObject


    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        webServerFilter.getMonitoringConfig().addProbes(probe);
    }

    @Override
    protected void onUnregister(GrizzlyJmxManager mom) {
        webServerFilter.getMonitoringConfig().removeProbes(probe);
    }


    // -------------------------------------------------------------- Attributes


    /**
     * @return the number of requests this {@link com.sun.grizzly.http.server.WebServerFilter}
     *  has received.
     */
    @ManagedAttribute(id="requests-received-count")
    public long getRequestsReceivedCount() {
        return receivedCount.get();
    }


    /**
     * @return the number of requests this {@link com.sun.grizzly.http.server.WebServerFilter}
     *  has completed servicing.
     */
    @ManagedAttribute(id="requests-completed-count")
    public long getRequestsCompletedCount() {
        return completedCount.get();
    }


    /**
     * @return the number of requests currently suspended.
     */
    @ManagedAttribute(id="current-suspended-request-count")
    public int getRequestsSuspendedCount() {
        return suspendCount.get();
    }


    /**
     * @return the number of suspended requests that have timed out.
     */
    @ManagedAttribute(id="requests-timed-out-count")
    public long getRequestsTimedOutCount() {
        return timedOutCount.get();
    }


    /**
     * @return the number of requests suspended requests that have been
     *  cancelled.
     */
    @ManagedAttribute(id="requests-cancelled-count")
    public long getRequestsCancelledCount() {
        return cancelledCount.get();
    }


    // ---------------------------------------------------------- Nested Classes


    private final class JmxWebServerProbe implements WebServerProbe {


        // ----------------------------------------- Methods from WebServerProbe


        @Override
        public void onRequestReceiveEvent(com.sun.grizzly.http.server.WebServerFilter filter, Connection connection, GrizzlyRequest request) {
            receivedCount.incrementAndGet();
        }

        @Override
        public void onRequestCompleteEvent(com.sun.grizzly.http.server.WebServerFilter filter, Connection connection, GrizzlyResponse response) {
            completedCount.incrementAndGet();
        }

        @Override
        public void onRequestSuspendEvent(com.sun.grizzly.http.server.WebServerFilter filter, Connection connection, GrizzlyRequest request) {
            suspendCount.incrementAndGet();
        }

        @Override
        public void onRequestResumeEvent(com.sun.grizzly.http.server.WebServerFilter filter, Connection connection, GrizzlyRequest request) {
            suspendCount.decrementAndGet();
        }

        @Override
        public void onRequestTimeoutEvent(com.sun.grizzly.http.server.WebServerFilter filter, Connection connection, GrizzlyRequest request) {
            timedOutCount.incrementAndGet();
            suspendCount.decrementAndGet();
        }

        @Override
        public void onRequestCancelEvent(com.sun.grizzly.http.server.WebServerFilter filter, Connection connection, GrizzlyRequest request) {
            cancelledCount.incrementAndGet();
            suspendCount.decrementAndGet();
        }

    } // END JmxWebServerProbe
    
}
