/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

/**
 * Monitoring probe providing callbacks that may be invoked by Grizzly {@link KeepAlive}.
 *
 * @author Alexey Stashok
 *
 * @since 2.0
 */
public interface KeepAliveProbe {

    /**
     * Method will be called, when new keep-alive HTTP connection is getting established.
     * This method is getting invoked, when 1st HTTP request processing completes,
     * but the Connection will be kept alive to process next HTTP request.
     *
     * @param connection {@link Connection}, the event belongs to.
     */
    void onConnectionAcceptEvent(Connection connection);

    /**
     * Method will be called, when HTTP request comes on a kept alive connection.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param requestNumber HTTP request number, being processed on the given keep-alive connection.
     */
    void onHitEvent(Connection connection, int requestNumber);

    /**
     * Method will be called, when the Connection could be used in the keep alive mode,
     * but due to KeepAlive config limitations it will be closed.
     *
     * @param connection {@link Connection}, the event belongs to.
     */
    void onRefuseEvent(Connection connection);

    /**
     * Method will be called, when the keep alive Connection idle timeout expired.
     *
     * @param connection {@link Connection}, the event belongs to.
     */
    void onTimeoutEvent(Connection connection);


    // ---------------------------------------------------------- Nested Classes


    /**
     * {@link KeepAliveProbe} adapter that provides no-op implementations for
     * all interface methods allowing easy extension by the developer.
     *
     * @since 2.1.9
     */
    @SuppressWarnings("UnusedDeclaration")
    class Adapter implements KeepAliveProbe {


        // ----------------------------------------- Methods from KeepAliveProbe

        /**
         * {@inheritDoc}
         */
        @Override
        public void onConnectionAcceptEvent(Connection connection) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onHitEvent(Connection connection, int requestNumber) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onRefuseEvent(Connection connection) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTimeoutEvent(Connection connection) {}

    } // END Adapter
}
