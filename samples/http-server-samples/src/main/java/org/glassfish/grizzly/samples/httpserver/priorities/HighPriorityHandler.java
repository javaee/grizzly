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
package org.glassfish.grizzly.samples.httpserver.priorities;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.RequestExecutorProvider;
import org.glassfish.grizzly.http.server.Response;

/**
 * The high-priority {@link HttpHandler} we want to be processed as fast as possible.
 * 
 * @author Alexey Stashok
 */
public class HighPriorityHandler extends HttpHandler {

    private final RequestExecutorProvider executorProvider;
    
    public HighPriorityHandler(final ExecutorService threadPool) {
        this.executorProvider = new RequestExecutorProvider() {

            @Override
            public Executor getExecutor(Request request) {
                return threadPool;
            }
        };
    }
    
    /**
     * @return the {@link RequestExecutorProvider} to be used to 
     * call {@link HighPriorityHandler#service(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response)}.
     */
    @Override
    public RequestExecutorProvider getRequestExecutorProvider() {
        return executorProvider;
    }

    @Override
    public void service(final Request request, final Response response)
            throws Exception {
        response.getWriter().write(Thread.currentThread().getName() + ": executing high priority task");
    }    
}
