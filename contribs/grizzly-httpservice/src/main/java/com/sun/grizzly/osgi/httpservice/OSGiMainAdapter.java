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

import com.sun.grizzly.osgi.httpservice.util.Logger;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * OSGi Main Adapter.
 * <p/>
 * Dispatching adapter.
 * Grizzly integration.
 * <p/>
 * Responsibilities:
 * <ul>
 * <li>Manages registration data.</li>
 * <li>Dispatching {@link GrizzlyAdapter#service(GrizzlyRequest, GrizzlyResponse)} method call to registered
 * {@link GrizzlyAdapter}s.</li>
 * </ul>
 *
 * @author Hubert Iwaniuk
 */
public class OSGiMainAdapter extends GrizzlyAdapter implements OSGiGrizzlyAdapter {
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Logger logger;
    private Bundle bundle;
    private OSGiCleanMapper mapper;

    /**
     * Constructor.
     * @param logger Logger utility.
     * @param bundle Bundle that we create if for, for local data reference.
     */
    public OSGiMainAdapter(Logger logger, Bundle bundle) {
        this.logger = logger;
        this.bundle = bundle;
        this.mapper = new OSGiCleanMapper();
    }

    /**
     * Service method dispatching to registered handlers.
     * <p/>
     * {@inheritDoc}
     */
    public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
        boolean invoked = false;
        String alias = request.getDecodedRequestURI();
        String originalAlias = alias;
        logger.debug("Serviceing URI: " + alias);
        // first lookup needs to be done for full match.
        boolean cutOff = false;
        while (true) {
            logger.debug("CutOff: " + cutOff + ", alias: " + alias);
            alias = OSGiCleanMapper.map(alias, cutOff);
            if (alias == null) {
                if (cutOff) {
                    // not found
                    break;
                } else {
                    // switching to reducing mapping mode (removing after last '/' and searching)
                    logger.debug("Swithcing to reducing mapping mode.");
                    cutOff = true;
                    alias = originalAlias;
                }
            } else {
                GrizzlyAdapter adapter = OSGiCleanMapper.getAdapter(alias);

                ((OSGiGrizzlyAdapter) adapter).getProcessingLock().lock();
                try {
                    adapter.service(request, response);
                } finally {
                    ((OSGiGrizzlyAdapter) adapter).getProcessingLock().unlock();
                }
                invoked = true;
                if (response.getStatus() != 404) {
                    break;
                } else if ("/".equals(alias)) {
                    // 404 in "/", cutoff algo will not escape this one.
                    break;
                } else if (!cutOff){
                    // not found and haven't run in cutoff mode
                    cutOff = true;
                }
            }
        }
        if (!invoked) {
            response.getResponse().setStatus(404);
            try {
                customizedErrorPage(request.getRequest(), response.getResponse());
            } catch (Exception e) {
                logger.warn("Failed to commit 404 status.", e);
            }
        }
    }

    /**
     * Registers {@link com.sun.grizzly.osgi.httpservice.OSGiServletAdapter} in OSGi Http Service.
     * <p/>
     * Keeps truck of all registrations, takes care of thread safety.
     *
     * @param alias       Alias to register, if wrong value than throws {@link org.osgi.service.http.NamespaceException}.
     * @param servlet     Servlet to register under alias, if fails to {@link javax.servlet.Servlet#init(javax.servlet.ServletConfig)}
     *                    throws {@link javax.servlet.ServletException}.
     * @param initparams  Initial parameters to populate {@link javax.servlet.ServletContext} with.
     * @param context     OSGi {@link org.osgi.service.http.HttpContext}, provides mime handling, security and bundle specific resource access.
     * @param httpService Used to {@link HttpService#createDefaultHttpContext()} if needed.
     * @throws org.osgi.service.http.NamespaceException
     *                                        If alias was invalid or already registered.
     * @throws javax.servlet.ServletException If {@link javax.servlet.Servlet#init(javax.servlet.ServletConfig)} fails.
     */
    public void registerServletAdapter(String alias, Servlet servlet, Dictionary initparams, HttpContext context,
                                       HttpService httpService)
            throws NamespaceException, ServletException {

        ReentrantLock lock = OSGiCleanMapper.getLock();
        lock.lock();
        try {
            validateAlias4RegOk(alias);
            validateServlet4RegOk(servlet);

            if (context == null) {
                logger.debug("No HttpContext provided, creating default");
                context = httpService.createDefaultHttpContext();
            }

            OSGiServletAdapter servletAdapter =
                    findOrCreateOSGiServletAdapter(servlet, context, initparams);
            servletAdapter.setServletPath(alias);

            logger.debug("Initializing Servlet been registered");
            servletAdapter.startServlet(); // this might throw ServletException, throw it to offending bundle.

            mapper.addGrizzlyAdapter(alias, servletAdapter);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Registers {@link OSGiResourceAdapter} in OSGi Http Service.
     * <p/>
     * Keeps truck of all registrations, takes care of thread safety.
     *
     * @param alias          Alias to register, if wrong value than throws {@link NamespaceException}.
     * @param context        OSGi {@link HttpContext}, provides mime handling, security and bundle specific resource access.
     * @param internalPrefix Prefix to map request for this alias to.
     * @param httpService Used to {@link HttpService#createDefaultHttpContext()} if needed.
     * @throws NamespaceException If alias was invalid or already registered.
     */
    public void registerResourceAdapter(String alias, HttpContext context, String internalPrefix,
                                        HttpService httpService)
            throws NamespaceException {

        ReentrantLock lock = OSGiCleanMapper.getLock();
        lock.lock();
        try {
            validateAlias4RegOk(alias);

            if (context == null) {
                logger.debug("No HttpContext provided, creating default");
                context = httpService.createDefaultHttpContext();
            }
            if (internalPrefix == null) {
                internalPrefix = "";
            }

            mapper.addGrizzlyAdapter(alias, new OSGiResourceAdapter(alias, internalPrefix, context, logger));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Unregisters previously registered alias.
     * <p/>
     * Keeps truck of all registrations, takes care of thread safety.
     *
     * @param alias       Alias to unregister, if not owning alias {@link IllegalArgumentException} is thrown.
     * @throws IllegalArgumentException If alias was not registered by calling bundle.
     */
    public void unregisterAlias(String alias) {

        ReentrantLock lock = OSGiCleanMapper.getLock();
        lock.lock();
        try {
            if (mapper.isLocalyRegisteredAlias(alias)) {
                mapper.doUnregister(alias, true);
            } else {
                logger.warn(
                        new StringBuilder(128).append("Bundle: ").append(bundle)
                                .append(" tried to unregister not owned alias '").append(alias)
                                .append('\'').toString());
                throw new IllegalArgumentException(
                        new StringBuilder(64).append("Alias '").append(alias)
                                .append("' was not registered by you.").toString());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Unregisters all <code>alias</code>es registered by owning bundle.
     */
    public void uregisterAllLocal() {
        logger.info("Unregistering all aliases registered by owning bundle");

        ReentrantLock lock = OSGiCleanMapper.getLock();
        lock.lock();
        try {
            for (String alias : mapper.getLocalAliases()) {
                logger.debug(new StringBuilder().append("Unregistering '").append(alias).append("'").toString());
                // remember not to call Servlet.destroy() owning bundle might be stopped already.
                mapper.doUnregister(alias, false);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Part of Shutdown sequence.
     * Unregister and clean up.
     */
    public void unregisterAll() {
        logger.info("Unregistering all registered aliases");

        ReentrantLock lock = OSGiCleanMapper.getLock();
        lock.lock();
        try {
            Set<String> aliases = OSGiCleanMapper.getAllAliases();
            while (!aliases.isEmpty()) {
                String alias = ((TreeSet<String>) aliases).first();
                logger.debug(new StringBuilder().append("Unregistering '").append(alias).append("'").toString());
                // remember not to call Servlet.destroy() owning bundle might be stopped already.
                mapper.doUnregister(alias, false);
            }
        } finally {
            lock.unlock();
        }
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

    /**
     * Chek if <code>alias</code> has been already registered.
     *
     * @param alias Alias to check.
     * @throws NamespaceException If <code>alias</code> has been registered.
     */
    private void validateAlias4RegOk(String alias) throws NamespaceException {
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
        if (OSGiCleanMapper.containsAlias(alias)) {
            String msg = "Alias: '" + alias + "', already registered";
            logger.warn(msg);
            throw new NamespaceException(msg);
        }
    }

    /**
     * Check if <code>servlet</code> has been already registered.
     * <p/>
     * An instance of {@link Servlet} can be registed only once, so in case of servlet been registered before will throw
     * {@link ServletException} as specified in OSGI HttpService Spec.
     *
     * @param servlet {@link Servlet} to check if can be registered.
     * @throws ServletException Iff <code>servlet</code> has been registered before.
     */
    private void validateServlet4RegOk(Servlet servlet) throws ServletException {
        if (OSGiCleanMapper.contaisServlet(servlet)) {
            String msg = new StringBuilder(64).append("Servlet: '").append(servlet).append("', already registered.")
                    .toString();
            logger.warn(msg);
            throw new ServletException(msg);
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
     * @return Found or created {@link OSGiServletAdapter}.
     */
    private OSGiServletAdapter findOrCreateOSGiServletAdapter(
            Servlet servlet, HttpContext httpContext, Dictionary initparams) {
        OSGiServletAdapter osgiServletAdapter;

        if (mapper.containsContext(httpContext)) {
            logger.debug("Reusing ServletAdapter");
            // new servlet adapter for same configuration, different servlet and alias
            List<OSGiServletAdapter> servletAdapters = mapper.getContext(httpContext);
            osgiServletAdapter = servletAdapters.get(0).newServletAdapter(servlet);
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
            ArrayList<OSGiServletAdapter> servletAdapters = new ArrayList<OSGiServletAdapter>(1);
            servletAdapters.add(osgiServletAdapter);
            mapper.addContext(httpContext, servletAdapters);
        }
        osgiServletAdapter.addFilter(new OSGiAuthFilter(httpContext), "AuthorisationFilter", new HashMap(0));
        return osgiServletAdapter;
    }
}
