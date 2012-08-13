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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glassfish.grizzly.servlet;

import java.util.List;
import java.util.Map;
import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.util.Globals;

/**
 * <code>FilterChainFactory</code> is responsible for building a {@link javax.servlet.FilterChain}
 * instance with the Filters that need to be invoked for a particular request URI.
 *
 * TODO: We should look into how to cache these.  They currently are re-built
 *  on each request.
 *
 * @since 2.2
 */
public class FilterChainFactory {

//    private final Collection<FilterRegistration> registrations;
    private final WebappContext ctx;


    // ------------------------------------------------------------ Constructors


    public FilterChainFactory(final WebappContext ctx) {

        this.ctx = ctx;
//        this.registrations = registrations;

    }


    // ---------------------------------------------------------- Public Methods

    /**
     * Construct and return a FilterChain implementation that will wrap the
     * execution of the specified servlet instance.  If we should not execute
     * a filter chain at all, return <code>null</code>.
     *
     * @param request The servlet request we are processing
     * @param servlet The servlet instance to be wrapped
     */
    public FilterChainImpl createFilterChain(final ServletRequest request,
                                             final Servlet servlet,
                                             final DispatcherType dispatcherType) {

        return buildFilterChain(servlet, getRequestPath(request), dispatcherType);

    }

    /**
     * Construct and return a FilterChain implementation that will wrap the
     * execution of the specified servlet instance.  If we should not execute
     * a filter chain at all, return <code>null</code>.
     *
     * @param request The servlet request we are processing
     * @param servlet The servlet instance to be wrapped
     */
    public FilterChainImpl createFilterChain(final Request request,
                                             final Servlet servlet,
                                             final DispatcherType dispatcherType) {

        return buildFilterChain(servlet, getRequestPath(request), dispatcherType);


    }



    // -------------------------------------------------------- Private Methods
    private FilterChainImpl buildFilterChain(final Servlet servlet,
            final String requestPath,
            final DispatcherType dispatcherType) {
        // If there is no servlet to execute, return null
        if (servlet == null) {
            return (null);
        }

        // Create and initialize a filter chain object
        FilterChainImpl filterChain = new FilterChainImpl(servlet, ctx);

        final Map<String, ? extends FilterRegistration> registrations =
                ctx.getFilterRegistrations();

        // If there are no filter mappings, we are done
        if (registrations.isEmpty()) {
            return filterChain;
        }
        
        final List<FilterMap> filterMaps = ctx.getFilterMaps();

        // Add the relevant path-mapped filters to this filter chain
        for (final FilterMap filterMap : filterMaps) {
            if (!filterMap.getDispatcherTypes().contains(dispatcherType)) {
                continue;
            }
            
            if (!matchFiltersURL(filterMap, requestPath)) {
                continue;
            }
            
            filterChain.addFilter(registrations.get(filterMap.getFilterName()));
        }

        // Add filters that match on servlet name second
        String servletName = servlet.getServletConfig().getServletName();
        for (final FilterMap filterMap : filterMaps) {
            if (!filterMap.getDispatcherTypes().contains(dispatcherType)) {
                continue;
            }
            
            if (!matchFiltersServlet(filterMap, servletName)) {
                continue;
            }
            
            filterChain.addFilter(registrations.get(filterMap.getFilterName()));
        }

        // Return the completed filter chain
        return filterChain;
    }


    private String getRequestPath(ServletRequest request) {
        // get the dispatcher type
        String requestPath = null;
        Object attribute = request.getAttribute(
            Globals.DISPATCHER_REQUEST_PATH_ATTR);
        if (attribute != null) {
            requestPath = attribute.toString();
        }
        return requestPath;
    }

    private String getRequestPath(Request request) {
        // get the dispatcher type
        String requestPath = null;
        Object attribute = request.getAttribute(
            Globals.DISPATCHER_REQUEST_PATH_ATTR);
        if (attribute != null) {
            requestPath = attribute.toString();
        }
        return requestPath;
    }

    /**
     * Return <code>true</code> if the context-relative request path matches the
     * requirements of the specified filter mapping; otherwise, return
     * <code>null</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    /* SJSWS 6324431
     private boolean matchFiltersURL(FilterMap filterMap, String requestPath) {
     */
    // START SJSWS 6324431
    private boolean matchFiltersURL(FilterMap filterMap, String requestPath) {
        // END SJSWS 6324431

        if (requestPath == null) {
            return false;
        }

        // Match on context relative request path
        String testPath = filterMap.getURLPattern();
        if (testPath == null) {
            return false;
        }

        // Case 1 - Exact Match
        if (testPath.equals(requestPath)) {
            return true;
        }

        // Case 2 - Path Match ("/.../*")
        if (testPath.equals("/*")) {
            return true;
        }
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0,
                    testPath.length() - 2)) {
                if (requestPath.length() == (testPath.length() - 2)) {
                    return true;
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return true;
                }
            }
            return false;
        }

        // Case 3 - Extension Match
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash)
                    && (period != requestPath.length() - 1)
                    && ((requestPath.length() - period)
                    == (testPath.length() - 1))) {
                return (testPath.regionMatches(2, requestPath, period + 1,
                        testPath.length() - 2));
            }
        }

        // Case 4 - "Default" Match
        return false; // NOTE - Not relevant for selecting filters

    }


    /**
     * Return <code>true</code> if the specified servlet name matches
     * the requirements of the specified filter mapping; otherwise
     * return <code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param servletName Servlet name being checked
     */
    private boolean matchFiltersServlet(FilterMap filterMap, 
                                        String servletName) {

        if (servletName == null) {
            return false;
        } else {
            if (servletName.equals(filterMap.getServletName())
                    || "*".equals(filterMap.getServletName())) {
                return true;
            } else {
                return false;
            }
        }
    }

}
