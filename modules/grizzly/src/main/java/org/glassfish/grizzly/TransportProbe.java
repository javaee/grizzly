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

package org.glassfish.grizzly;

/**
 * Monitoring probe providing callbacks that may be invoked by Grizzly
 * {@link Transport} implementations.
 *
 * @author Alexey Stashok
 *
 * @since 2.0
 */
public interface TransportProbe {

    /**
     * Method will be called before starting the {@link Transport}.
     *
     * @param transport {@link Transport}, the event belongs to.
     *
     * @since 3.0
     */
    void onBeforeStartEvent(Transport transport);

    /**
     * Method will be called when the {@link Transport} has been started.
     *
     * @param transport {@link Transport}, the event belongs to.
     */
    void onStartEvent(Transport transport);

    /**
     * Method will be called before stopping the {@link Transport}.
     *
     * @param transport {@link Transport}, the event belongs to.
     *
     * @since 3.0
     */
    void onBeforeStopEvent(Transport transport);

    /**
     * Method will be called when the {@link Transport} has been stopped.
     *
     * @param transport {@link Transport}, the event belongs to.
     */
    void onStopEvent(Transport transport);

    /**
     * Method will be called before pausing the {@link Transport}.
     *
     * @param transport {@link Transport}, the event belongs to.
     *
     * @since 3.0
     */
    void onBeforePauseEvent(Transport transport);

    /**
     * Method will be called when the {@link Transport} is paused.
     *
     * @param transport {@link Transport}, the event belongs to.
     */
    void onPauseEvent(Transport transport);

    /**
     * Method will be called before resuming the {@link Transport}.
     *
     * @param transport {@link Transport}, the event belongs to.
     *
     * @since 3.0
     */
    void onBeforeResumeEvent(Transport transport);

    /**
     * Method will be called, when the {@link Transport} gets resumed.
     *
     * @param transport {@link Transport}, the event belongs to.
     */
    void onResumeEvent(Transport transport);

    /**
     * Method will be called, when the {@link Transport} configuration gets changed.
     *
     * @param transport {@link Transport}, the event belongs to.
     */
    void onConfigChangeEvent(Transport transport);
    
    /**
     * Method will be called, when error occurs on the {@link Transport}.
     *
     * @param transport {@link Transport}, the event belongs to.
     * @param error error
     */
    void onErrorEvent(Transport transport, Throwable error);


    // ---------------------------------------------------------- Nested Classes

    /**
     * {@link TransportProbe} adapter that provides no-op implementations for
     * all interface methods allowing easy extension by the developer.
     *
     * @since 2.1.9
     */
    @SuppressWarnings("UnusedDeclaration")
    class Adapter implements TransportProbe {


        // ----------------------------------------- Methods from TransportProbe


        /**
         * {@inheritDoc}
         */
        @Override
        public void onBeforeStartEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onStartEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onBeforeStopEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onStopEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onBeforePauseEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPauseEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onBeforeResumeEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onResumeEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onConfigChangeEvent(Transport transport) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onErrorEvent(Transport transport, Throwable error) {}

    } // END Adapter

}
