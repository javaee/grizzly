/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.servlet;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.localization.LogMessages;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.EventListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of <code>javax.servlet.FilterChain</code> used to manage
 * the execution of a set of filters for a particular request.  When the
 * set of defined filters has all been executed, the next call to
 * <code>doFilter()</code> will execute the servlet's <code>service()</code>
 * method itself.
 *
 * @author Craig R. McClanahan
 */
final class FilterChainImpl implements FilterChain, FilterChainInvoker {

    private static final Logger LOGGER = Grizzly.logger(FilterChainImpl.class);

    /**
     * The servlet instance to be executed by this chain.
     */
    private final Servlet servlet;
    private final WebappContext ctx;

    private final Object lock = new Object();
    private int n;

    private FilterRegistration[] filters = new FilterRegistration[0];

    /**
     * The int which is used to maintain the current position
     * in the filter chain.
     */
    private int pos;

    public FilterChainImpl(final Servlet servlet,
                           final WebappContext ctx) {

        this.servlet = servlet;
        this.ctx = ctx;
    }

    // ---------------------------------------------------- FilterChain Methods


    public void invokeFilterChain(ServletRequest request, ServletResponse response)
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
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception java.io.IOException if an input/output error occurs
     * @exception javax.servlet.ServletException if a servlet exception occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
            throws IOException, ServletException {

        // Call the next filter if there is one
        if (pos < n) {

            FilterRegistration registration = filters[pos++];

            try {
                Filter filter = registration.filter;
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

    // ------------------------------------------------------- Protected Methods


    protected void addFilter(final FilterRegistration filterRegistration) {
        synchronized (lock) {
            if (n == filters.length) {
                FilterRegistration[] newFilters =
                        new FilterRegistration[n + 4];
                System.arraycopy(filters, 0, newFilters, 0, n);
                filters = newFilters;
            }

            filters[n++] = filterRegistration;
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
                    ((ServletRequestListener) listeners[i]).requestInitialized(event);
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



}
