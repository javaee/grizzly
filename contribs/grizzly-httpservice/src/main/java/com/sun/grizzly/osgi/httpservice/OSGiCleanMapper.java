/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 */
package com.sun.grizzly.osgi.httpservice;

import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import org.osgi.service.http.HttpContext;

import javax.servlet.Servlet;
import java.util.*;
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

    public static String map(String resource) {
        String result;
        String match = resource;
        while (true) {
            int i = match.lastIndexOf('/');
            if (i == -1) {
                result = null;
                break;
            } else {
                if (i == 0)
                    match = "/";
                else
                    match = resource.substring(0, i);
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

    public static boolean contaisServlet(Servlet servlet) {
        return registeredServlets.contains(servlet);
    }

    public static boolean registerAliasAdapter(String alias, GrizzlyAdapter adapter) {
        boolean wasNew = aliasTree.add(alias);
        if (wasNew) {
            registrations.put(alias, adapter);
        } else {
            // TODO already registered, wtf
        }
        return wasNew;
    }

    public static GrizzlyAdapter removeAdapter(String alias) {
        return registrations.remove(alias);
    }

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
            removeAlias(alias);
            GrizzlyAdapter adapter = removeAdapter(alias);
            adapter.destroy();

            // local cleanup
            removeLocalAlias(alias);
        } else {
            // already gone
        }
    }

    public static boolean removeAlias(String s) {
        return aliasTree.remove(s);
    }

    public static boolean containsAlias(String s) {
        return aliasTree.contains(s);
    }

    public static void addServlet(Servlet servlet) {
        registeredServlets.add(servlet);
    }

    public static void removeServlet(Servlet servlet) {
        registeredServlets.remove(servlet);
    }

    public boolean isLocalyRegisteredAlias(String alias) {
        return localAliases.contains(alias);
    }

    public void addLocalAlias(String alias) {
        localAliases.add(alias);
    }

    public boolean removeLocalAlias(String alias) {
        return localAliases.remove(alias);
    }

    public Set<String> getLocalAliases() {
        return Collections.unmodifiableSet(localAliases);
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
                    removeServlet(servlet);
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

    public boolean containsContext(HttpContext httpContext) {
        return contextServletAdapterMap.containsKey(httpContext);
    }

    public List<OSGiServletAdapter> getContext(HttpContext httpContext) {
        return contextServletAdapterMap.get(httpContext);
    }

    public void addContext(HttpContext httpContext, ArrayList<OSGiServletAdapter> servletAdapters) {
        contextServletAdapterMap.put(httpContext, servletAdapters);
    }

    public static ReentrantLock getLock() {
        return lock;
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
            // shold not happend, alias shouls be checked before.
            // TODO: signal it some how
        } else {
            registerAliasAdapter(alias, adapter);
            if (adapter instanceof OSGiServletAdapter) {
                addServlet(((OSGiServletAdapter) adapter).getServletInstance());
            }
            addLocalAlias(alias);
        }
    }
}
