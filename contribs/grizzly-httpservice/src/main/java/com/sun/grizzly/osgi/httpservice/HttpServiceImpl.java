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

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.osgi.httpservice.util.Logger;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Grizzly OSGi HttpService implementation.
 *
 * @author Hubert Iwaniuk
 * @since Jan 20, 2009
 */
public class HttpServiceImpl implements HttpService {
    private static final HashMap<String, GrizzlyAdapter> aliasesRegistered = new HashMap<String, GrizzlyAdapter>(16, 0.75f);
    private static final HashMap<String, Servlet> registeredServlets = new HashMap<String, Servlet>(16, 0.75f);
    private static final ReadWriteLock GLOBAL_CONF_LOCK = new ReentrantReadWriteLock();
    private final HashMap<HttpContext, LinkedList<OSGiServletAdapter>> contextToServletAdapterMap;
    private final ConcurrentLinkedQueue<String> localyRegisteredAliases;
    private final GrizzlyWebServer ws;
    private final Logger logger;
    private final Bundle bundle;

    /**
     * {@link HttpService} constructor.
     *
     * @param bundle {@link Bundle} that got this instance of {@link HttpService}.
     * @param ws     {@link GrizzlyWebServer} instance to be used by this {@link HttpService}.
     * @param logger {@link Logger} utility to be used here.
     */
    public HttpServiceImpl(
        Bundle bundle, final GrizzlyWebServer ws, final Logger logger) {
        this.bundle = bundle;
        this.ws = ws;
        this.logger = logger;
        contextToServletAdapterMap = new HashMap<HttpContext, LinkedList<OSGiServletAdapter>>(16, 0.75f);
        localyRegisteredAliases = new ConcurrentLinkedQueue<String>();
    }

    /** {@inheritDoc} */
    public HttpContext createDefaultHttpContext() {
        // TODO: check spec if this can be cached for each bundle
        return new HttpContextImpl(bundle);
    }

    /** {@inheritDoc} */
    public void registerServlet(
        final String alias, final Servlet servlet, final Dictionary initparams, HttpContext httpContext)
        throws ServletException, NamespaceException {
        HttpContext context = httpContext;

        logger.info(
            new StringBuilder(128).append("Registering servlet: ").append(servlet).append(", under: ").append(alias)
                .append(", with: ").append(initparams).append(" and context: ").append(context).toString());

        try {
            GLOBAL_CONF_LOCK.writeLock().lock();
            validateAlias(alias);
            validateServlet(servlet);

            if (context == null) {
                logger.debug("No HttpContext provided, creating default");
                context = createDefaultHttpContext();
            }

            OSGiServletAdapter servletAdapter = findOrCreateOSGiServletAdapter(servlet, context, initparams);

            logger.debug("Initializing Servlet been registered");
            servletAdapter.startServlet(); // this might throw ServletException, throw it to offending bundle.

            ws.addGrizzlyAdapter(servletAdapter, new String[]{alias});
            storeRegistrationData(alias, servlet, servletAdapter);
        } finally {
            GLOBAL_CONF_LOCK.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    public void registerResources(
        final String alias, String prefix, HttpContext httpContext) throws NamespaceException {
        HttpContext context = httpContext;
        String internalPrefix = prefix;

        logger.info(
            new StringBuilder(128).append("Registering resource: alias: ").append(alias).append(", prefix: ")
                .append(internalPrefix).append(" and context: ").append(context).toString());

        try {
            GLOBAL_CONF_LOCK.writeLock().lock();
            validateAlias(alias);

            if (context == null) {
                if (internalPrefix == null) {
                    internalPrefix = "";
                }

                logger.debug("No HttpContext provided, creating default");
                context = createDefaultHttpContext();
            }

            OSGiResourceAdapter adapter = new OSGiResourceAdapter(alias, internalPrefix, context, logger);

            ws.addGrizzlyAdapter(adapter, new String[]{alias});
            storeRegistrationData(alias, adapter);
        } finally {
            GLOBAL_CONF_LOCK.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    public void unregister(final String alias) {
        logger.info(new StringBuilder(32).append("Unregistering alias: ").append(alias).toString());
        try {
            GLOBAL_CONF_LOCK.writeLock().lock();
            if (localyRegisteredAliases.contains(alias)) {
                doUnregister(alias, true);
            } else {
                logger.warn(
                    new StringBuilder(128).append("Bundle: ").append(bundle)
                        .append(" tried to unregister not owned alias '").append(alias).append('\'').toString());
                throw new IllegalArgumentException(
                    new StringBuilder(64).append("Alias '").append(alias)
                        .append("' was not registered by you.").toString());
            }
        } finally {
            GLOBAL_CONF_LOCK.writeLock().unlock();
        }
    }

    /** Unregisters all <code>alias</code>es registered by owning bundle. */
    public void uregisterAllLocal() {
        logger.info("Unregistering all aliases registered by owning bundle");
        try {
            GLOBAL_CONF_LOCK.writeLock().lock();
            for (String alias : localyRegisteredAliases) {
                logger.debug(new StringBuilder().append("Unregistering '").append(alias).append("'").toString());
                // remember not to call Servlet.destroy() owning bundle might be stopped already.
                doUnregister(alias, false);
            }
        } finally {
            GLOBAL_CONF_LOCK.writeLock().unlock();
        }
    }

    /**
     * Executes unregistering of <code>alias</code> optionally calling {@link Servlet#destroy()}.
     *
     * @param alias                Alias to unregister.
     * @param callDestroyOnServlet If <code>true</code> call {@link Servlet#destroy()}, else don't call.
     */
    private void doUnregister(String alias, boolean callDestroyOnServlet) {
        GrizzlyAdapter adapter = aliasesRegistered.get(alias);
        if (adapter instanceof OSGiServletAdapter) {
            Servlet servlet = registeredServlets.get(alias);
            if (callDestroyOnServlet) {
                servlet.destroy();
            }
        }
        ws.removeGrizzlyAdapter(adapter);
        removeRegistrationData(alias);
    }

    /**
     * Remove registration information for internal book keeping.
     *
     * @param alias Alias to unregister.
     */
    private void removeRegistrationData(String alias) {
        GrizzlyAdapter adapter = aliasesRegistered.remove(alias);
        if (adapter instanceof OSGiServletAdapter) {
            OSGiServletAdapter o = (OSGiServletAdapter) adapter;
            HttpContext context = o.getHttpContext();
            LinkedList<OSGiServletAdapter> servletAdapters = contextToServletAdapterMap.get(context);
            OSGiServletAdapter servletAdapter = servletAdapters.poll();
            if (servletAdapter != null) {
                servletAdapter.destroy();
            }
            if (servletAdapters.isEmpty()) {
                contextToServletAdapterMap.remove(context);
            }
            registeredServlets.remove(alias);
        } else if (adapter instanceof OSGiResourceAdapter) {
            OSGiResourceAdapter resourceAdapter = (OSGiResourceAdapter) adapter;
            resourceAdapter.destroy();
        }
        localyRegisteredAliases.remove(alias);
    }

    /**
     * Store registration information for internal book keeping.
     *
     * @param alias   Registered alias.
     * @param servlet Registered {@link Servlet}.
     * @param adapter Registered {@link GrizzlyAdapter}.
     */
    private void storeRegistrationData(
        String alias, Servlet servlet, GrizzlyAdapter adapter) {
        aliasesRegistered.put(alias, adapter);
        registeredServlets.put(alias, servlet);
        localyRegisteredAliases.add(alias);
    }

    /**
     * Store registration information for internal book keeping.
     *
     * @param alias   Registered alias.
     * @param adapter Registered {@link GrizzlyAdapter}.
     */
    private void storeRegistrationData(String alias, GrizzlyAdapter adapter) {
        aliasesRegistered.put(alias, adapter);
        localyRegisteredAliases.add(alias);
    }

    /**
     * Check if <code>servlet</code> has been already registered.
     * <p/>
     * Same instance of {@link Servlet} can be registed only once, so in case of servlet been registered before will
     * throw {@link ServletException} as specified in OSGI HttpService Spec.
     *
     * @param servlet {@link Servlet} to check if hasn'r been registered.
     *
     * @throws ServletException Iff <code>servlet</code> has been registered before.
     */
    private void validateServlet(Servlet servlet) throws ServletException {
        if (registeredServlets.containsValue(servlet)) {
            String msg = new StringBuilder(64).append("Servlet: '").append(servlet).append("', already registered.")
                .toString();
            logger.warn(msg);
            throw new ServletException(msg);
        }
    }

    /**
     * Chek if <code>alias</code> has been already registered.
     *
     * @param alias Alias to check.
     *
     * @throws NamespaceException If <code>alias</code> has been registered.
     */
    private void validateAlias(String alias) throws NamespaceException {
        if (!alias.startsWith("/")) {
            // have to start with "/"
            String msg = new StringBuilder(64).append("Invalid alias '").append(alias)
                .append("', have to start with '/'.").toString();
            logger.warn(msg);
            throw new NamespaceException(msg);
        }
        if (alias.length() > 1 && alias.endsWith("/")) {
            // if longer than "/", should not end with "/"
            String msg = new StringBuilder(64).append("Alias '").append(alias)
                .append("' can't and with '/' with exception to alias '/'.").toString();
            logger.warn(msg);
            throw new NamespaceException(msg);
        }
        if (aliasesRegistered.containsKey(alias)) {
            String msg = "Alias: '" + alias + "', already registered";
            logger.warn(msg);
            throw new NamespaceException(msg);
        }
    }

    /**
     * Looks up {@link OSGiServletAdapter}.
     * <p/>
     * If is already registered for <code>httpContext</code> than create new instance based on already registered. Else
     * Create new one.
     * <p/>
     *
     * @param servlet     {@link Servlet} been registered.
     * @param httpContext {@link HttpContext} used for registration.
     * @param initparams  Init parameters that will be visible in {@link javax.servlet.ServletContext}.
     *
     * @return Found or created {@link OSGiServletAdapter}.
     */
    private OSGiServletAdapter findOrCreateOSGiServletAdapter(
        Servlet servlet, HttpContext httpContext, Dictionary initparams) {
        OSGiServletAdapter osgiServletAdapter;
        if (contextToServletAdapterMap.containsKey(httpContext)) {
            logger.debug("Reusing ServletAdapter");
            // new servlet adapter for same configuration, different servlet and alias
            LinkedList<OSGiServletAdapter> servletAdapters = contextToServletAdapterMap.get(httpContext);
            osgiServletAdapter = servletAdapters.peek().newServletAdapter(servlet);
            servletAdapters.add(osgiServletAdapter);
        } else {
            logger.debug("Creating new ServletAdapter");
            HashMap<String, String> params;
            if (initparams != null) {
                params = new HashMap<String, String>(initparams.size());
                Enumeration names = initparams.keys();
                while (names.hasMoreElements()) {
                    String name = (String) names.nextElement();
                    params.put(name, (String) initparams.get(name));
                }
            } else {
                params = new HashMap<String, String>(0);
            }
            osgiServletAdapter = new OSGiServletAdapter(servlet, httpContext, params, logger);
            LinkedList<OSGiServletAdapter> servletAdapters = new LinkedList<OSGiServletAdapter>();
            servletAdapters.add(osgiServletAdapter);
            contextToServletAdapterMap.put(httpContext, servletAdapters);
        }
        osgiServletAdapter.addFilter(new OSGiAuthFilter(httpContext), "AuthorisationFilter", new HashMap(0));
        return osgiServletAdapter;
    }

}
