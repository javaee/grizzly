/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.osgi.httpservice;

import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.http.servlet.ServletContextImpl;
import com.sun.grizzly.osgi.httpservice.util.Logger;
import org.osgi.service.http.HttpContext;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * OSGi customized {@link ServletAdapter}.
 *
 * @author Hubert Iwaniuk
 */
public class OSGiServletAdapter extends ServletAdapter implements OSGiGrizzlyAdapter {
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private HttpContext httpContext;
    private Logger logger;

    public OSGiServletAdapter(Servlet servlet, HttpContext httpContext, HashMap<String, String> initparams,
                              Logger logger) {
        super(".", new OSGiServletContext(httpContext, logger), new HashMap<String,String>(),
                initparams, new ArrayList<String>(0));
        //noinspection AccessingNonPublicFieldOfAnotherObject
        super.servletInstance = servlet;
        this.httpContext = httpContext;
        this.logger = logger;
    }

    private OSGiServletAdapter(String publicDirectory, ServletContextImpl servletCtx,
                               Map<String, String> parameters, List<String> listeners, Logger logger) {
        super(publicDirectory, servletCtx,new HashMap<String,String>(), parameters, listeners);
        this.logger = logger;
    }

    /**
     * {@inheritDoc}
     */
    @Override public OSGiServletAdapter newServletAdapter(Servlet servlet) {
        OSGiServletAdapter sa =
                new OSGiServletAdapter(getRootFolder(), getServletCtx(), getContextParameters(), getListeners(), logger);
        sa.setServletInstance(servlet);
        sa.setServletPath(getServletPath());
        //noinspection AccessingNonPublicFieldOfAnotherObject
        sa.httpContext = httpContext;
        return sa;
    }

    /**
     * Starts {@link Servlet} instance of this {@link OSGiServletAdapter}.
     *
     * @throws ServletException If {@link Servlet} startup failed.
     */
    public void startServlet() throws ServletException {
        configureServletEnv();
        setResourcesContextPath(getContextPath() + getServletPath());
        // always load servlet
        loadServlet();
    }

    /**
     * {@inheritDoc}
     */
    public ReentrantReadWriteLock.ReadLock getProcessingLock() {
        return lock.readLock();
    }

    /**
     * {@inheritDoc}
     */
    public ReentrantReadWriteLock.WriteLock getRemovalLock() {
        return lock.writeLock();
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }
}
