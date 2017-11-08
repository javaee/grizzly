/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.osgi.httpservice;

import org.glassfish.grizzly.osgi.httpservice.util.Logger;
import org.glassfish.grizzly.servlet.FilterChainFactory;
import org.glassfish.grizzly.servlet.ServletConfigImpl;
import org.glassfish.grizzly.servlet.WebappContext;
import org.osgi.service.http.HttpContext;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.lang.String;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.glassfish.grizzly.servlet.ServletHandler;

/**
 * OSGi customized {@link ServletHandler}.
 *
 * @author Hubert Iwaniuk
 */
public class OSGiServletHandler extends ServletHandler implements OSGiHandler {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private HttpContext httpContext;
    private final Logger logger;
    private String servletPath;

    public OSGiServletHandler(final Servlet servlet,
                              final HttpContext httpContext,
                              final OSGiServletContext servletContext,
                              final HashMap<String, String> servletInitParams,
                              final Logger logger) {
        super(createServletConfig(servletContext, servletInitParams));
        //noinspection AccessingNonPublicFieldOfAnotherObject
        super.servletInstance = servlet;
        this.httpContext = httpContext;
        this.logger = logger;
    }

    private OSGiServletHandler(final ServletConfigImpl servletConfig,
                               final Logger logger) {
        super(servletConfig);
        this.logger = logger;

    }

    public OSGiServletHandler newServletHandler(Servlet servlet) {
        OSGiServletHandler servletHandler =
                new OSGiServletHandler(getServletConfig(), logger);

        servletHandler.setServletInstance(servlet);
        servletHandler.setServletPath(getServletPath());
        servletHandler.setFilterChainFactory(filterChainFactory);
        //noinspection AccessingNonPublicFieldOfAnotherObject
        servletHandler.httpContext = httpContext;
        return servletHandler;
    }

    /**
     * Starts {@link Servlet} instance of this {@link OSGiServletHandler}.
     *
     * @throws ServletException If {@link Servlet} startup failed.
     */
    public void startServlet() throws ServletException {
        configureServletEnv();
        servletInstance.init(getServletConfig());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReentrantReadWriteLock.ReadLock getProcessingLock() {
        return lock.readLock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReentrantReadWriteLock.WriteLock getRemovalLock() {
        return lock.writeLock();
    }

    protected void setServletPath(final String path) {
        this.servletPath = path;
    }

    protected String getServletPath() {
        return servletPath;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }


    // ------------------------------------------------------- Protected Methods


    @Override
    protected void setFilterChainFactory(FilterChainFactory filterChainFactory) {
        super.setFilterChainFactory(filterChainFactory);
    }


    // --------------------------------------------------------- Private Methods



    private static ServletConfigImpl createServletConfig(final OSGiServletContext ctx,
                                                         final Map<String,String> params) {

        final OSGiServletConfig config = new OSGiServletConfig(ctx);
        config.setInitParameters(params);
        return config;
    }


    // ---------------------------------------------------------- Nested Classes


    private static final class OSGiServletConfig extends ServletConfigImpl {

        protected OSGiServletConfig(WebappContext servletContextImpl) {
            super(servletContextImpl);
        }

        @Override
        protected void setInitParameters(Map<String, String> parameters) {
            super.setInitParameters(parameters);
        }
    }

}
