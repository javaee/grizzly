/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

/**
 * Monitoring probe providing callbacks that may be invoked by Grizzly
 * {@link Connection} implementations.
 *
 * @author Alexey Stashok
 *
 * @since 2.0
 */
public interface ConnectionProbe {

    /**
     * Method will be called, when server side connection gets bound.
     *
     * @param connection {@link Connection}, the event belongs to.
     */
    public void onBindEvent(Connection connection);

    /**
     * Method will be called, when server side connection gets accepted.
     *
     * @param serverConnection server {@link Connection}, the event belongs to.
     * @param clientConnection new client {@link Connection}.
     */
    public void onAcceptEvent(Connection serverConnection, Connection clientConnection);

    /**
     * Method will be called, when client side connection gets connected (opened).
     *
     * @param connection {@link Connection}, the event belongs to.
     */
    public void onConnectEvent(Connection connection);

    /**
     * Method will be called, when the {@link Connection} has read data.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param data {@link Buffer}, where the data gets read.
     * @param size the data size.
     */
    public void onReadEvent(Connection connection, Buffer data, int size);

    /**
     * Method will be called, when the {@link Connection} has written data.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param data {@link Buffer}, where the data gets writen.
     * @param size the data size.
     */
    public void onWriteEvent(Connection connection, Buffer data, long size);
    
    /**
     * Method will be called, when error occurs on the {@link Connection}.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param error error
     */
    public void onErrorEvent(Connection connection, Throwable error);

    /**
     * Method will be called, when {@link Connection} gets closed.
     *
     * @param connection {@link Connection}, the event belongs to.
     */
    public void onCloseEvent(Connection connection);

    /**
     * Method will be called, when {@link IOEvent} for the specific
     * {@link Connection} gets ready.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param ioEvent {@link IOEvent}.
     */
    public void onIOEventReadyEvent(Connection connection, IOEvent ioEvent);

    /**
     * Method will be called, when {@link IOEvent} for the specific
     * {@link Connection} gets enabled.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param ioEvent {@link IOEvent}.
     */
    public void onIOEventEnableEvent(Connection connection, IOEvent ioEvent);

    /**
     * Method will be called, when {@link IOEvent} for the specific
     * {@link Connection} gets disabled.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param ioEvent {@link IOEvent}.
     */
    public void onIOEventDisableEvent(Connection connection, IOEvent ioEvent);


    // ---------------------------------------------------------- Nested Classes

    /**
     * {@link ConnectionProbe} adapter that provides no-op implementations for
     * all interface methods allowing easy extension by the developer.
     *
     * @since 2.1.9
     */
    @SuppressWarnings("UnusedDeclaration")
    public static class Adapter implements ConnectionProbe {


        // ---------------------------------------- Methods from ConnectionProbe

        /**
         * {@inheritDoc}
         */
        @Override
        public void onBindEvent(Connection connection) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onAcceptEvent(Connection serverConnection, Connection clientConnection) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onConnectEvent(Connection connection) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReadEvent(Connection connection, Buffer data, int size) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onWriteEvent(Connection connection, Buffer data, long size) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onErrorEvent(Connection connection, Throwable error) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCloseEvent(Connection connection) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onIOEventReadyEvent(Connection connection, IOEvent ioEvent) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onIOEventEnableEvent(Connection connection, IOEvent ioEvent) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onIOEventDisableEvent(Connection connection, IOEvent ioEvent) {}

    } // END Adapter

}
