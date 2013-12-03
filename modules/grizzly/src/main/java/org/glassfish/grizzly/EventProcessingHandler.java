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

import java.io.IOException;

/**
 * The {@link Event} processing handler, which will be notified about changes
 * in {@link Event} processing statuses.
 * 
 * @author Alexey Stashok
 */
public interface EventProcessingHandler {
    /**
     * {@link Event} processing suspended.
     *
     * @param context the {@link Context} for the current event.
     * @throws IOException if an error occurs processing the event.
     */
    public void onSuspend(Context context) throws IOException;

    /**
     * {@link Event} processing resumed.
     *
     * @param context the {@link Context} for the current event.
     * @throws IOException if an error occurs processing the event.
     */
    public void onResume(Context context) throws IOException;

    /**
     * {@link Event} processing completed.
     * 
     * @param context the {@link Context} for the current event.
     * @throws IOException if an error occurs processing the event.
     */
    public void onComplete(Context context) throws IOException;

    /**
     * Terminate {@link Event} processing in this thread, but it's going to
     * be continued later.
     *
     * @param context the {@link Context} for the current event.
     * @throws IOException if an error occurs processing the event.
     */
    public void onTerminate(Context context) throws IOException;

    /**
     * Error occurred during {@link Event} processing.
     *
     * @param context the {@link Context} for the current event.
     * @throws IOException if an error occurs processing the event.
     */
    public void onError(Context context, Object description) throws IOException;

    /**
     * {@link Event} wasn't processed.
     *
     * @param context the {@link Context} for the current event.
     * @throws IOException if an error occurs processing the event.
     */
    public void onNotRun(Context context) throws IOException;
    
    /**
     * Empty {@link EventProcessingHandler} implementation.
     */
    public class Adapter implements EventProcessingHandler {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSuspend(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onResume(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onComplete(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTerminate(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onError(Context context, Object description) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onNotRun(Context context) throws IOException {
        }
    }
    
}
