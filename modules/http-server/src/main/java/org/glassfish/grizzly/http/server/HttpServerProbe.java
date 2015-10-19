/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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
 * Monitoring probe providing callbacks that may be invoked by Grizzly {@link HttpServerFilter}.
 *
 * @author Alexey Stashok
 *
 * @since 2.0
 */
public interface HttpServerProbe {
    /**
     * Method will be called, when new {@link Request} will come.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request received {@link Request}.
     */
    void onRequestReceiveEvent(HttpServerFilter filter,
                               Connection connection, Request request);

    /**
     * Method will be called, when {@link Request} processing will be completed.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param response sent {@link Response}.
     */
    void onRequestCompleteEvent(HttpServerFilter filter,
                                Connection connection, Response response);

    /**
     * Method will be called, when {@link Request} processing is suspended.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request {@link Request}.
     */
    void onRequestSuspendEvent(HttpServerFilter filter,
                               Connection connection, Request request);

    /**
     * Method will be called, when {@link Request} processing is resumed.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request {@link Request}.
     */
    void onRequestResumeEvent(HttpServerFilter filter,
                              Connection connection, Request request);

    /**
     * Method will be called, when {@link Request} processing is timeout
     * after suspend.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request {@link Request}.
     */
    void onRequestTimeoutEvent(HttpServerFilter filter,
                               Connection connection, Request request);

    /**
     * Method will be called, when {@link Request} processing is cancelled
     * after suspend.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request {@link Request}.
     */
    void onRequestCancelEvent(HttpServerFilter filter,
                              Connection connection, Request request);

    /**
     * Method will be called, before invoking
     * {@link HttpHandler#service(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response)}.
     *
     * @param filter {@link HttpServerFilter}, the event belongs to.
     * @param connection {@link Connection}, the event belongs to.
     * @param request received {@link Request}.
     * @param httpHandler {@link HttpHandler} to be invoked.
     */
    void onBeforeServiceEvent(HttpServerFilter filter,
                              Connection connection, Request request, HttpHandler httpHandler);
    
    // ---------------------------------------------------------- Nested Classes


    /**
     * {@link HttpServerProbe} adapter that provides no-op implementations for
     * all interface methods allowing easy extension by the developer.
     *
     * @since 2.1.9
     */
    @SuppressWarnings("UnusedDeclaration")
    class Adapter implements HttpServerProbe {


        // ---------------------------------------- Methods from HttpServerProbe

        /**
         * {@inheritDoc}
         */
        @Override
        public void onRequestReceiveEvent(HttpServerFilter filter, Connection connection, Request request) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onRequestCompleteEvent(HttpServerFilter filter, Connection connection, Response response) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onRequestSuspendEvent(HttpServerFilter filter, Connection connection, Request request) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onRequestResumeEvent(HttpServerFilter filter, Connection connection, Request request) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onRequestTimeoutEvent(HttpServerFilter filter, Connection connection, Request request) {}


        @Override
        public void onRequestCancelEvent(HttpServerFilter filter, Connection connection, Request request) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onBeforeServiceEvent(HttpServerFilter filter, Connection connection, Request request, HttpHandler httpHandler) {}
    }
}
