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

import java.io.IOException;

/**
 * The {@link IOEvent} life-cycle listener, which will be notified about changes
 * in {@link IOEvent} processing statuses.
 * 
 * @author Alexey Stashok
 */
public interface IOEventLifeCycleListener {
    /**
     * {@link IOEvent} processing suspended.
     *
     * @param context
     * @throws IOException
     */
    void onContextSuspend(Context context) throws IOException;

    /**
     * {@link IOEvent} processing resumed.
     *
     * @param context
     * @throws IOException
     */
    void onContextResume(Context context) throws IOException;

    /**
     * Processing switched to the manual IOEvent control.
     * {@link Connection#enableIOEvent(org.glassfish.grizzly.IOEvent)} or
     * {@link Connection#disableIOEvent(org.glassfish.grizzly.IOEvent)} might be
     * explicitly called.
     * 
     * @param context
     */
    void onContextManualIOEventControl(final Context context) throws IOException;

    /**
     * Reregister {@link IOEvent} interest.
     *
     * @param context
     * @throws IOException
     */
    void onReregister(Context context) throws IOException;

    /**
     * {@link IOEvent} processing completed.
     * 
     * @param context 
     * @throws IOException
     */
    void onComplete(Context context, Object data) throws IOException;

    /**
     * Detaching {@link IOEvent} processing out of this {@link Context}.
     *
     * @param context
     * @throws IOException
     */
    void onLeave(Context context) throws IOException;

    /**
     * Terminate {@link IOEvent} processing in this thread, but it's going to
     * be continued later.
     *
     * @param context
     * @throws IOException
     * 
     * @deprecated will never be invoked
     */
    void onTerminate(Context context) throws IOException;

    /**
     * Re-run {@link IOEvent} processing.
     *
     * @param context original {@link Context} to be rerun
     * @param newContext new context, which will replace original {@link Context}
     * @throws IOException
     */
    void onRerun(Context context, Context newContext) throws IOException;

    /**
     * Error occurred during {@link IOEvent} processing.
     *
     * @param context
     */
    void onError(Context context, Object description) throws IOException;

    /**
     * {@link IOEvent} wasn't processed.
     *
     * @param context
     */
    void onNotRun(Context context) throws IOException;

    /**
     * Empty {@link IOEventLifeCycleListener} implementation.
     */
    class Adapter implements IOEventLifeCycleListener {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onContextSuspend(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onContextResume(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onComplete(Context context, Object data) throws IOException {
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

        /**
         * {@inheritDoc}
         */
        @Override
        public void onContextManualIOEventControl(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onReregister(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onLeave(Context context) throws IOException {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onRerun(Context context, Context newContext) throws IOException {
        }
    }
}
