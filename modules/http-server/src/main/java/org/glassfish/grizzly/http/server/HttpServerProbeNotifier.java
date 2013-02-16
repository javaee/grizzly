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

import org.glassfish.grizzly.Connection;

/**
 * Utility class, which has notification methods for different
 * {@link HttpServerProbe} events.
 *
 * @author Alexey Stashok
 */
final class HttpServerProbeNotifier {
    /**
     * Notify registered {@link HttpServerProbe}s about the "request received" event.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request received {@link Request}.
     */
    static void notifyRequestReceive(final HttpServerFilter filter,
            final Connection connection, final Request request) {

        final HttpServerProbe[] probes = filter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpServerProbe probe : probes) {
                probe.onRequestReceiveEvent(filter, connection, request);
            }
        }
    }

    /**
     * Notify registered {@link HttpServerProbe}s about the "request completed" event.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param response {@link Response}.
     */
    static void notifyRequestComplete(final HttpServerFilter filter,
            final Connection connection, final Response response) {

        final HttpServerProbe[] probes = filter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpServerProbe probe : probes) {
                probe.onRequestCompleteEvent(filter, connection, response);
            }
        }
    }

    /**
     * Notify registered {@link HttpServerProbe}s about the "request suspended" event.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request {@link Request}.
     */
    static void notifyRequestSuspend(final HttpServerFilter filter,
            final Connection connection, final Request request) {

        final HttpServerProbe[] probes = filter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpServerProbe probe : probes) {
                probe.onRequestSuspendEvent(filter, connection, request);
            }
        }
    }

    /**
     * Notify registered {@link HttpServerProbe}s about the "request resumed" event.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request {@link Request}.
     */
    static void notifyRequestResume(final HttpServerFilter filter,
            final Connection connection, final Request request) {

        final HttpServerProbe[] probes = filter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpServerProbe probe : probes) {
                probe.onRequestResumeEvent(filter, connection, request);
            }
        }
    }

    /**
     * Notify registered {@link HttpServerProbe}s about the "request timeout after suspend" event.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request  {@link Request}.
     */
    static void notifyRequestTimeout(final HttpServerFilter filter,
            final Connection connection, final Request request) {

        final HttpServerProbe[] probes = filter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpServerProbe probe : probes) {
                probe.onRequestTimeoutEvent(filter, connection, request);
            }
        }
    }

    /**
     * Notify registered {@link HttpServerProbe}s about the "request canceled after suspend" event.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request  {@link Request}.
     */
    static void notifyRequestCancel(final HttpServerFilter filter,
            final Connection connection, final Request request) {

        final HttpServerProbe[] probes = filter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpServerProbe probe : probes) {
                probe.onRequestCancelEvent(filter, connection, request);
            }
        }
    }
    
    /**
     * Notify registered {@link HttpServerProbe}s before invoking
     * {@link HttpHandler#service(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response)}.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param response {@link Response}.
     * @param httpHandler {@link HttpHandler}.
     */
    static void notifyBeforeService(final HttpServerFilter filter,
            final Connection connection, final Request request,
            final HttpHandler httpHandler) {

        final HttpServerProbe[] probes = filter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpServerProbe probe : probes) {
                probe.onBeforeServiceEvent(filter, connection, request, httpHandler);
            }
        }
    }    
}
