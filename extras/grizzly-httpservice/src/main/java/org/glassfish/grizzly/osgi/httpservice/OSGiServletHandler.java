/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.osgi.httpservice;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.osgi.httpservice.util.Logger;
import org.glassfish.grizzly.servlet.FilterChainInvoker;
import org.glassfish.grizzly.servlet.FilterConfigImpl;
import org.glassfish.grizzly.servlet.ServletConfigImpl;
import org.glassfish.grizzly.servlet.WebappContext;
import org.osgi.service.http.HttpContext;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.lang.String;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

import org.glassfish.grizzly.servlet.ServletHandler;

/**
 * OSGi customized {@link ServletHandler}.
 *
 * @author Hubert Iwaniuk
 */
public class OSGiServletHandler extends ServletHandler implements OSGiHandler {
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private HttpContext httpContext;
    private Logger logger;
    private String servletPath;
    private FilterChainFactory filterChainFactory;

    public OSGiServletHandler(final Servlet servlet,
                              final HttpContext httpContext,
                              final HashMap<String, String> servletInitParams,
                              final Logger logger) {
        super(createServletConfig(new OSGiServletContext(httpContext, logger), servletInitParams));
        //noinspection AccessingNonPublicFieldOfAnotherObject
        super.servletInstance = servlet;
        this.httpContext = httpContext;
        this.logger = logger;
        filterChainFactory = new FilterChainFactory(servlet, (OSGiServletContext) getServletCtx());
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
    protected FilterChainInvoker getFilterChain(Request request) {
       return filterChainFactory.createFilterChain();
    }


    protected void addFilter(final Filter filter,
                             final String name,
                             final Map<String,String> initParams) {
        try {
            filterChainFactory.addFilter(filter, name, initParams);
        } catch (Exception e) {
            logger.error(e.toString(), e);
        }

    }

//    @Override
//    protected void setPathData(Request from, HttpServletRequestImpl to) {
//        to.setServletPath(getServletPath());
//    }

    // --------------------------------------------------------- Private Methods


    private static FilterConfig createFilterConfig(final WebappContext ctx,
                                                   final String name,
                                                   final Map<String,String> initParams) {
        final OSGiFilterConfig config = new OSGiFilterConfig(ctx);
        config.setFilterName(name);
        config.setInitParameters(initParams);
        return config;

    }


    private static ServletConfigImpl createServletConfig(final OSGiServletContext ctx,
                                                         final Map<String,String> params) {

        final OSGiServletConfig config = new OSGiServletConfig(ctx);
        config.setInitParameters(params);
        return config;
    }


    // ---------------------------------------------------------- Nested Classes


    private static final class OSGiFilterConfig extends FilterConfigImpl {

        public OSGiFilterConfig(WebappContext servletContext) {
            super(servletContext);
        }

        @Override
        protected void setFilterName(String filterName) {
            super.setFilterName(filterName);
        }

        @Override
        protected void setInitParameters(Map<String, String> initParameters) {
            super.setInitParameters(initParameters);
        }
    }

    private static final class OSGiServletConfig extends ServletConfigImpl {

        protected OSGiServletConfig(WebappContext servletContextImpl) {
            super(servletContextImpl);
        }

        @Override
        protected void setInitParameters(Map<String, String> parameters) {
            super.setInitParameters(parameters);
        }
    }

    private static final class FilterChainImpl implements FilterChain, FilterChainInvoker {

        private static final java.util.logging.Logger LOGGER = Grizzly.logger(FilterChainImpl.class);

        /**
         * The servlet instance to be executed by this chain.
         */
        private final Servlet servlet;
        private final OSGiServletContext ctx;

        private Filter[] filters;
        private int filterCount;


        /**
         * The int which is used to maintain the current position
         * in the filter chain.
         */
        private int pos;

        public FilterChainImpl(final Servlet servlet,
                               final OSGiServletContext ctx,
                               final Filter[] filters,
                               final int filterCount) {

            this.servlet = servlet;
            this.ctx = ctx;
            this.filters = filters;
            this.filterCount = filterCount;

        }

        // ---------------------------------------------------- FilterChain Methods


        public void invokeFilterChain(final ServletRequest request,
                                      final ServletResponse response)
        throws IOException, ServletException {

            ServletRequestEvent event =
                    new ServletRequestEvent(ctx, request);
            try {
                requestInitialized(event);
                pos = 0;
                doFilter(request, response);
            } finally {
                requestDestroyed(event);
            }

        }


        /**
         * Invoke the next filter in this chain, passing the specified request
         * and response.  If there are no more filters in this chain, invoke
         * the <code>service()</code> method of the servlet itself.
         *
         * @param request  The servlet request we are processing
         * @param response The servlet response we are creating
         * @throws java.io.IOException            if an input/output error occurs
         * @throws javax.servlet.ServletException if a servlet exception occurs
         */
        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {

            // Call the next filter if there is one
            if (pos < filterCount) {

                Filter filter = filters[pos++];

                try {
                    filter.doFilter(request, response, this);
                } catch (Exception e) {
                    throw new ServletException(e);
                }

                return;
            }

            try {
                if (servlet != null) {
                    servlet.service(request, response);
                }

            } catch (Exception e) {
                throw new ServletException(e);
            }

        }

        // --------------------------------------------------------- Private Methods

        private void requestDestroyed(ServletRequestEvent event) {
            // TODO don't create the event unless necessary
            final EventListener[] listeners = ctx.getEventListeners();
            for (int i = 0, len = listeners.length; i < len; i++) {
                if (listeners[i] instanceof ServletRequestListener) {
                    try {
                        ((ServletRequestListener) listeners[i]).requestDestroyed(event);
                    } catch (Throwable t) {
                        if (LOGGER.isLoggable(Level.WARNING)) {
                            LOGGER.log(Level.WARNING,
                                    LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_DESTROYED_ERROR("requestDestroyed", "ServletRequestListener", listeners[i].getClass().getName()),
                                    t);
                        }
                    }
                }
            }

        }

        private void requestInitialized(ServletRequestEvent event) {
            final EventListener[] listeners = ctx.getEventListeners();
            for (int i = 0, len = listeners.length; i < len; i++) {
                if (listeners[i] instanceof ServletRequestListener) {
                    try {
                        ((ServletRequestListener) listeners[i]).requestDestroyed(event);
                    } catch (Throwable t) {
                        if (LOGGER.isLoggable(Level.WARNING)) {
                            LOGGER.log(Level.WARNING,
                                    LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_INITIALIZED_ERROR("requestDestroyed", "ServletRequestListener", listeners[i].getClass().getName()),
                                    t);
                        }
                    }
                }
            }
        }

    } // END FilterChainImpl

    private static final class FilterChainFactory {

        private final Servlet servlet;
        private final OSGiServletContext servletContext;
        private final ReentrantReadWriteLock lock =
                new ReentrantReadWriteLock();
        private Filter[] filters = new Filter[0];
        private int filterCount;

        /**
         * Initializes a new {@link FilterChainFactory}.
         *
         * @param servlet
         * @param servletContext
         */
        FilterChainFactory(Servlet servlet, OSGiServletContext servletContext) {
            super();
            this.servlet = servlet;
            this.servletContext = servletContext;
        }

        protected void addFilter(final Filter filter,
                                     final String name,
                                     final Map<String,String> initParams)
        throws ServletException {
            filter.init(createFilterConfig(servletContext, name, initParams));
            final Lock writeLock = lock.writeLock();
            try {
                writeLock.lock();
                if (filterCount == filters.length) {
                    Filter[] newFilters =
                            new Filter[filterCount + 4];
                    System.arraycopy(filters, 0, newFilters, 0, filterCount);
                    filters = newFilters;
                }

                filters[filterCount++] = filter;
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * Construct and return a FilterChain implementation that will wrap the execution of the specified servlet instance. If we should
         * not execute a filter chain at all, return <code>null</code>.
         */
        FilterChainImpl createFilterChain() {
            if (filterCount == 0) {
                return null;
            }
            final Lock readLock = lock.readLock();
            try {
                readLock.lock();
                return new FilterChainImpl(servlet,
                        servletContext,
                        filters,
                        filterCount);
            } finally {
                readLock.unlock();
            }
        }
    }

}
