/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Executor;
import org.glassfish.grizzly.threadpool.Threads;

/**
 * An implementation of this interface will be  responsible for executing
 * user's code in {@link HttpHandler#service(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response)}
 * and notifying {@link ReadHandler}, {@link WriteHandler} registered by the
 * user.
 *
 * @author Alexey Stashok
 */
public interface RequestExecutorProvider {

    /**
     * Returns the {@link Executor} to execute user's code associated with the
     * {@link Request} processing.
     * 
     * @param request {@link Request}
     * @return the {@link Executor} to execute user's code associated with the
     * {@link Request} processing, or <tt>null</tt> if the {@link Request} has
     * to be executed on the current {@link Thread}
     */
    Executor getExecutor(final Request request);

    /**
     * The {@link RequestExecutorProvider} implementation, which always returns
     * <tt>null</tt> to force the user code to be executed on the current {@link Thread}.
     */
    public static class SameThreadProvider implements RequestExecutorProvider {

        @Override
        public Executor getExecutor(final Request request) {
            return null;
        }
    }

    /**
     * The {@link RequestExecutorProvider} implementation, which checks if the
     * current {@link Thread} is a service {@link Thread} (see {@link Threads#isService()}).
     * If the current {@link Thread} is a service {@link Thread} - the
     * implementation returns a worker thread pool associated with the {@link Request},
     * or, if the current {@link Thread} is not a service {@link Thread} - <tt>null</tt>
     * will be return to force the user code to be executed on the current {@link Thread}.
     */
    public static class WorkerThreadProvider implements RequestExecutorProvider {

        @Override
        public Executor getExecutor(final Request request) {
            if (!Threads.isService()) {
                return null; // Execute in the current thread
            }

            return request.getContext().getConnection().getTransport().getWorkerThreadPool();
        }
    }
}
