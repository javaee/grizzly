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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.glassfish.grizzly.osgi.httpservice.util.Logger;
import org.osgi.service.http.HttpContext;

import javax.servlet.Servlet;
import java.util.concurrent.locks.ReentrantLock;
import org.glassfish.grizzly.http.server.HttpHandler;

/**
 * Context mapper.
 * Supports complex context.
 *
 * @author Hubert Iwaniuk
 */
class OSGiCleanMapper {

    private static final ReentrantLock lock = new ReentrantLock();
    private static final TreeSet<String> aliasTree = new TreeSet<String>();
    private static final Map<String, HttpHandler> registrations = new HashMap<String, HttpHandler>(16);
    private static final Set<Servlet> registeredServlets = new HashSet<Servlet>(16);

    private final Set<String> localAliases = new HashSet<String>(4);
    private final HashMap<HttpContext, List<OSGiServletHandler>> contextServletHandlerMap =
            new HashMap<HttpContext, List<OSGiServletHandler>>(3);
    private final Logger logger;

    protected final Map<HttpContext, OSGiServletContext> httpContextToServletContextMap =
                new HashMap<HttpContext, OSGiServletContext>();


    // ------------------------------------------------------------ Constructors


    protected OSGiCleanMapper(final Logger logger) {
        this.logger = logger;
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * Performs mapping of requested URI to registered alias if any.
     * <p/>
     * Works in two modes:
     * <ul>
     * <li>Full match - Checks for full match of resource (cutAfterSlash == false),</li>
     * <li>Reducing match - Checks {@link String#substring(int, int)} (0, {@link String#lastIndexOf(String)} ('/'))
     * for match (cutAfterSlash == true).</li>
     * </ul>
     *
     * @param resource      Resource to be mapped.
     * @param cutAfterSlash Should cut off after last '/' before looking up.
     * @return First matching alias, or <code>null</code> if no match has been found.
     */
    public static String map(String resource, boolean cutAfterSlash) {
        String result;
        String match = resource;
        while (true) {
            int i = 0;
            if (cutAfterSlash) {
                i = match.lastIndexOf('/');
                if (i == -1) {
                    result = null;
                    break;
                } else {
                    if (i == 0)
                        match = "/";
                    else
                        match = resource.substring(0, i);
                }
            }
            if (containsAlias(match)) {
                result = match;
                break;
            } else if (i == 0) {
                result = null;
                break;
            }
        }
        return result;
    }

    /**
     * Checks if alias has been registered.
     *
     * @param alias Alias to check.
     * @return <code>true</code> if alias has been registered, else <code>false</code>.
     */
    public static boolean containsAlias(String alias) {
        return aliasTree.contains(alias);
    }

    /**
     * Checks if {@link Servlet} has been registered.
     *
     * @param servlet Servlet instance to check.
     * @return <code>true</code> if alias has been registered, else <code>false</code>.
     */
    public static boolean containsServlet(Servlet servlet) {
        return registeredServlets.contains(servlet);
    }

    /**
     * Gets mappers {@link ReentrantLock}.
     * <p/>
     * This {@link java.util.concurrent.locks.Lock} should protect mappers state.
     *
     * @return {@link java.util.concurrent.locks.Lock} to protect operations on mapper.
     */
    public static ReentrantLock getLock() {
        return lock;
    }

    /**
     * Looks up {@link HttpHandler} registered under alias.
     *
     * @param alias Registered alias.
     * @return {@link HttpHandler} registered under alias.
     */
    static HttpHandler getHttpHandler(String alias) {
        return registrations.get(alias);
    }

    /**
     * Remove registration information for internal book keeping.
     *
     * @param alias Alias to unregister.
     */
    public void recycleRegistrationData(String alias) {
        if (containsAlias(alias)) {
            // global cleanup
            aliasTree.remove(alias);
            HttpHandler handler = registrations.remove(alias);
            handler.destroy();

            // local cleanup
            localAliases.remove(alias);
        }
    }

    /**
     * Add {@link HttpHandler}.
     * <p/>
     *
     * @param alias   Registration alias.
     * @param handler HttpHandler handling requests for <code>alias</code>.
     */
    public void addHttpHandler(String alias, HttpHandler handler) {
        if (!containsAlias(alias)) {
            registerAliasHandler(alias, handler);
            if (handler instanceof OSGiServletHandler) {
                registeredServlets.add(((OSGiServletHandler) handler).getServletInstance());
            }
            localAliases.add(alias);
        }
    }

    /**
     * Checks if alias was registered by calling bundle.
     *
     * @param alias Alias to check for local registration.
     * @return <code>true</code> if alias was registered locally, else <code>false</code>.
     */
    public boolean isLocalyRegisteredAlias(String alias) {
        return localAliases.contains(alias);
    }

    /**
     * Executes unregistering of <code>alias</code> optionally calling {@link javax.servlet.Servlet#destroy()}.
     *
     * @param alias                Alias to unregister.
     * @param callDestroyOnServlet If <code>true</code> call {@link javax.servlet.Servlet#destroy()}, else don't call.
     */
    public void doUnregister(String alias, boolean callDestroyOnServlet) {
        if (containsAlias(alias)) {
            HttpHandler httpHandler = getHttpHandler(alias);
            if (httpHandler instanceof OSGiServletHandler) {
                ((OSGiHandler) httpHandler).getRemovalLock().lock();
                try {
                    Servlet servlet = ((OSGiServletHandler) httpHandler).getServletInstance();
                    registeredServlets.remove(servlet);
                    if (callDestroyOnServlet) {
                        servlet.destroy();
                    }
                } finally {
                    ((OSGiHandler) httpHandler).getRemovalLock().unlock();
                }
            }
        }
        recycleRegistrationData(alias);
    }

    /**
     * Gets locally registered aliases.
     *
     * @return Unmodifiable {@link Set} of locally registered aliases.
     */
    public Set<String> getLocalAliases() {
        return Collections.unmodifiableSet(localAliases);
    }

    /**
     * Gets all registered aliases.
     *
     * @return {@link Set} of all registered aliases.
     */
    /*package*/ static Set<String> getAllAliases() {
        return aliasTree;
    }
    /**
     * Checks if {@link HttpContext} has been registered..
     *
     * @param httpContext Context to check.
     * @return <code>true</code> if httpContext has been registered.
     */
    public boolean containsContext(HttpContext httpContext) {
        return contextServletHandlerMap.containsKey(httpContext);
    }

    public List<OSGiServletHandler> getContext(HttpContext httpContext) {
        return contextServletHandlerMap.get(httpContext);
    }

    public void addContext(final HttpContext httpContext,
            final List<OSGiServletHandler> servletHandlers) {
        addContext(httpContext, null, servletHandlers);
    }

    public void addContext(final HttpContext httpContext,
            OSGiServletContext servletCtx,
            final List<OSGiServletHandler> servletHandlers) {
        if (servletCtx == null) {
            servletCtx = new OSGiServletContext(httpContext, logger);
        }
        
        contextServletHandlerMap.put(httpContext, servletHandlers);
        httpContextToServletContextMap.put(httpContext, servletCtx);
    }
    
    public OSGiServletContext getServletContext(final HttpContext httpContext) {
        return httpContextToServletContextMap.get(httpContext);
    }

    private static boolean registerAliasHandler(String alias, HttpHandler httpHandler) {
        boolean wasNew = aliasTree.add(alias);
        if (wasNew) {
            registrations.put(alias, httpHandler);
        }
        return wasNew;
    }
}
