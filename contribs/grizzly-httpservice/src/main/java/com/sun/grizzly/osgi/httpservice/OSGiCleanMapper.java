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

import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.osgi.service.http.HttpContext;

import javax.servlet.Servlet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Context mapper.
 * Supports complex context.
 *
 * @author Hubert Iwaniuk
 */
class OSGiCleanMapper {

    private static final ReentrantLock lock = new ReentrantLock();
    private static final TreeSet<String> aliasTree = new TreeSet<String>();
    private static final Map<String, GrizzlyAdapter> registrations = new HashMap<String, GrizzlyAdapter>(16);
    private static final Set<Servlet> registeredServlets = new HashSet<Servlet>(16);

    private Set<String> localAliases = new HashSet<String>(4);
    private HashMap<HttpContext, ArrayList<OSGiServletAdapter>> contextServletAdapterMap =
            new HashMap<HttpContext, ArrayList<OSGiServletAdapter>>(3);

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
     * @return <code>true</code> iff alias has been registered, else <code>false</code>.
     */
    public static boolean containsAlias(String alias) {
        return aliasTree.contains(alias);
    }

    /**
     * Checks if {@link Servlet} has been registered.
     *
     * @param servlet Servlet instance to check.
     * @return <code>true</code> iff alias has been registered, else <code>false</code>.
     */
    public static boolean contaisServlet(Servlet servlet) {
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
     * Looksup {@link GrizzlyAdapter} registered under alias.
     *
     * @param alias Registered alias.
     * @return {@link GrizzlyAdapter} registered under alias.
     */
    static GrizzlyAdapter getAdapter(String alias) {
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
            GrizzlyAdapter adapter = registrations.remove(alias);
            adapter.destroy();

            // local cleanup
            localAliases.remove(alias);
        } else {
            // already gone
        }
    }

    /**
     * Add {@link com.sun.grizzly.tcp.http11.GrizzlyAdapter}.
     * <p/>
     *
     * @param alias   Registration alias.
     * @param adapter Adapter handling requests for <code>alias</code>.
     */
    public void addGrizzlyAdapter(String alias, GrizzlyAdapter adapter) {
        if (containsAlias(alias)) {
            // should not happend, alias should be checked before.
            // TODO: signal it some how
        } else {
            registerAliasAdapter(alias, adapter);
            if (adapter instanceof OSGiServletAdapter) {
                registeredServlets.add(((OSGiServletAdapter) adapter).getServletInstance());
            }
            localAliases.add(alias);
        }
    }

    /**
     * Checks if alias was registered by calling bundle.
     *
     * @param alias Alias to check for local registration.
     * @return <code>true</code> iff alias was registered localy, else <code>false</code>.
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
            GrizzlyAdapter adapter = getAdapter(alias);
            if (adapter instanceof OSGiServletAdapter) {
                ((OSGiGrizzlyAdapter) adapter).getRemovalLock().lock();
                try {
                    Servlet servlet = ((OSGiServletAdapter) adapter).getServletInstance();
                    registeredServlets.remove(servlet);
                    if (callDestroyOnServlet) {
                        servlet.destroy();
                    }
                } finally {
                    ((OSGiGrizzlyAdapter) adapter).getRemovalLock().unlock();
                }
            }
        }
        recycleRegistrationData(alias);
    }

    /**
     * Gets localy registered aliases.
     *
     * @return Unmodidiable {@link Set} of localy registered aliases.
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
     * @return <code>true</code> iff httpContext has been registered.
     */
    public boolean containsContext(HttpContext httpContext) {
        return contextServletAdapterMap.containsKey(httpContext);
    }

    public List<OSGiServletAdapter> getContext(HttpContext httpContext) {
        return contextServletAdapterMap.get(httpContext);
    }

    public void addContext(HttpContext httpContext, ArrayList<OSGiServletAdapter> servletAdapters) {
        contextServletAdapterMap.put(httpContext, servletAdapters);
    }

    private static boolean registerAliasAdapter(String alias, GrizzlyAdapter adapter) {
        boolean wasNew = aliasTree.add(alias);
        if (wasNew) {
            registrations.put(alias, adapter);
        } else {
            // TODO already registered, wtf
        }
        return wasNew;
    }
}
