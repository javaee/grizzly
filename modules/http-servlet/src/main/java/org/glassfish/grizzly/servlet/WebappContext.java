/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.SessionManager;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.http.server.util.ClassLoaderUtil;
import org.glassfish.grizzly.http.server.util.DispatcherHelper;
import org.glassfish.grizzly.http.server.util.Enumerator;
import org.glassfish.grizzly.http.server.util.Mapper;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.http.util.MimeType;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.localization.LogMessages;

import javax.servlet.Filter;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.SingleThreadModel;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpUpgradeHandler;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * <p>
 * This class acts as the foundation for registering listeners, servlets, and
 * filters in an embedded environment.
 * </p>
 *
 * <p>
 * Additionally, this class implements the the requirements set forth by the
 * Servlet 2.5 specification of {@link ServletContext}, however, it also exposes
 * the dynamic registration API of Servlet 3.0.
 * </p>
 *
 * <p>
 *     TODO: Provide code examples once the api firms up a bit.
 * </p>
 *
 * @since 2.2
 */
public class WebappContext implements ServletContext {

    private static final Logger LOGGER =
            Grizzly.logger(WebappContext.class);

    private static final Map<WebappContext, HttpServer> DEPLOYED_APPS =
            new HashMap<WebappContext, HttpServer>();

    private static final Set<SessionTrackingMode> DEFAULT_SESSION_TRACKING_MODES =
        EnumSet.of(SessionTrackingMode.COOKIE);
    
    /* Servlet major/minor versions */
    private static final int MAJOR_VERSION = 3;
    private static final int MINOR_VERSION = 1;

    /* Logical name, path spec, and filesystem path of this application */
    private final String displayName;
    private final String contextPath;
    private final String basePath;

    /* Servlet context initialization parameters */
    private final Map<String,String> contextInitParams =
            new LinkedHashMap<String, String>(8, 1.0f);

    /**
     * The security roles for this application
     */
    private final List<String> securityRoles = new ArrayList<String>();

    /* Registrations */
    protected final Map<String, ServletRegistration> servletRegistrations =
            new HashMap<String,ServletRegistration>(8, 1.0f);
    
    protected final Map<String, FilterRegistration> filterRegistrations =
            new LinkedHashMap<String,FilterRegistration>(4, 1.0f);
    protected final Map<String, FilterRegistration> unmodifiableFilterRegistrations =
            Collections.unmodifiableMap(filterRegistrations);
            

    /* SessionManager that will be used for the ServletHandlers */
    private SessionManager sessionManager = ServletSessionManager.instance();

    /* ServletHandlers backing the registrations */
    private Set<ServletHandler> servletHandlers;

    /* Listeners */
    private final Set<EventListener> eventListenerInstances =
            new LinkedHashSet<EventListener>(4, 1.0f);  // TODO - wire this in
    private EventListener[] eventListeners = new EventListener[0];

    /* Application start/stop state */
    protected boolean deployed;

    /* Factory for creating FilterChainImpl instances */
    final private FilterChainFactory filterChainFactory;

    /* Servlet context attributes */
    private final ConcurrentMap<String,Object> attributes =
            DataStructures.getConcurrentMap(16, 0.75f, 64);

    /* Server name; used in the Server entity header */
    private volatile String serverInfo = "Grizzly " + Grizzly.getDotedVersion();


    /* Thread local data used during request dispatch */
    /* TODO: seems like this may cause a leak - when is this ever cleared? */
    private final ThreadLocal<DispatchData> dispatchData = new ThreadLocal<DispatchData>();

    /* Request dispatcher helper class */
    private DispatcherHelper dispatcherHelper;

    private ClassLoader webappClassLoader;

    /**
     * Session cookie config
     */
    private javax.servlet.SessionCookieConfig sessionCookieConfig;
    
    private Set<SessionTrackingMode> sessionTrackingModes;
    
    /**
     * Destroy listener to be registered on {@link ServletHandler} to make sure
     * we undeploy entire application when {@link ServletHandler#destroy()} is invoked.
     */
    private final Runnable onDestroyListener = new Runnable() {
        @Override
        public void run() {
            if (deployed) {
                undeploy();
            }
        }
    };

    /**
     * The list of filter mappings for this application, in the order
     * they were defined in the deployment descriptor.
     */
    private final List<FilterMap> filterMaps = new ArrayList<FilterMap>();
    
    // ------------------------------------------------------------ Constructors

    protected WebappContext() {
        displayName = "";
        contextPath = "";
        basePath = "";
        filterChainFactory = new FilterChainFactory(this);
    }

    /**
     * <p>
     * Creates a simple <code>WebappContext</code> with the root being "/".
     * </p>
     *
     * @param displayName
     */
    public WebappContext(final String displayName) {
        this(displayName, "");
    }

    public WebappContext(final String displayName, final String contextPath) {
        this(displayName, contextPath, ".");
    }


    public WebappContext(final String displayName,
                         final String contextPath,
                         final String basePath) {

        if (displayName == null || displayName.length() == 0) {
            throw new IllegalArgumentException("'displayName' cannot be null or zero-length");
        }
        if (contextPath == null) {
            throw new IllegalArgumentException("'contextPath' cannot be null");
        }
        if (contextPath.length() > 0) {
            if (contextPath.charAt(0) != '/') {
                throw new IllegalArgumentException("'contextPath' must start with a forward slash");
            }
            if (!contextPath.equals("/")) {
                if (contextPath.charAt(contextPath.length() - 1) == '/') {
                    throw new IllegalArgumentException("'contextPath' must not end with a forward slash");
                }
            }
        }
        this.displayName = displayName;
        this.contextPath = contextPath;
        try {
            this.basePath = new File(basePath).getCanonicalPath();
        } catch (IOException ioe) {
            throw new IllegalArgumentException("Unable to resolve path: " + basePath);
        }
        filterChainFactory = new FilterChainFactory(this);
        Mapper.setAllowReplacement(true);

    }


    // ---------------------------------------------------------- Public Methods


    /**
     *
     * @param targetServer
     */
    public synchronized void deploy(final HttpServer targetServer) {
        if (!deployed) {

            if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO,
                               "Starting application [{0}] ...",
                            displayName);
                }
            boolean error = false;
            try {
                webappClassLoader =
                        ClassLoaderUtil.createURLClassLoader(
                                new File(getBasePath()).getCanonicalPath());
                initializeListeners();
                contextInitialized();
                initServlets(targetServer);
                initFilters();
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO,
                               "Application [{0}] is ready to service requests.  Root: [{1}].",
                               new Object[] {displayName, contextPath});
                }
                DEPLOYED_APPS.put(this, targetServer);
                deployed = true;
            } catch (Exception e) {
                error = true;
                if (LOGGER.isLoggable(Level.SEVERE)) {
                    LOGGER.log(Level.SEVERE,
                               "[" + displayName + "] Exception deploying application.  See stack trace for details.",
                               e);
                }
            } finally {
                if (error) {
                    undeploy();
                }
            }
            // TODO : DO SOMETHING
        }
    }

    /**
     *
     */
    public synchronized void undeploy() {
        try {
            if (deployed) {
                deployed = false;
                
                final HttpServer server = DEPLOYED_APPS.remove(this);
                destoryServlets(server);

                // destroy filter instances
                destroyFilters();

                // invoke servlet context listeners
                contextDestroyed();
            }
        } catch (Exception e) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE,
                        "[" + displayName + "] Exception undeploying application.  See stack trace for details.",
                        e);
            }
        }
    }

    /**
     *
     * @param name
     * @param value
     */
    public void addContextInitParameter(final String name,
                                        final String value) {
        if (!deployed) {
            contextInitParams.put(name, value);
        }

    }

    /**
     *
     * @param name
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void removeContextInitParameter(final String name) {

        if (!deployed) {
            contextInitParams.remove(name);
        }

    }

    /**
     *
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void clearContextInitParameters() {

        if (!deployed) {
            contextInitParams.clear();
        }

    }


    // --------------------------------------------- Methods from ServletContext


    /**
     * Adds the filter with the given name and class type to this servlet
     * context.
     *
     * <p>The registered filter may be further configured via the returned
     * {@link FilterRegistration} object.
     *
     * <p>If this WebappContext already contains a preliminary
     * FilterRegistration for a filter with the given <tt>filterName</tt>,
     * it will be completed (by assigning the name of the given
     * <tt>filterClass</tt> to it) and returned.
     *
     * @param filterName the name of the filter
     * @param filterClass the class object from which the filter will be
     * instantiated
     *
     * @return a FilterRegistration object that may be used to further
     * configure the registered filter, or <tt>null</tt> if this
     * WebappContext already contains a complete FilterRegistration for a
     * filter with the given <tt>filterName</tt> 
     *
     * @throws IllegalStateException if this WebappContext has already
     * been initialized
     */
    @Override
    public FilterRegistration addFilter(final String filterName,
                                        final Class<? extends Filter> filterClass) {

        if (deployed) {
            throw new IllegalArgumentException("WebappContext has already been deployed");
        }
        if (filterName == null) {
            throw new IllegalArgumentException("'filterName' cannot be null");
        }
        if (filterClass == null) {
            throw new IllegalArgumentException("'filterClass' cannot be null");
        }

        FilterRegistration registration = filterRegistrations.get(filterName);
        if (registration == null) {
            registration = new FilterRegistration(this, filterName, filterClass);
            filterRegistrations.put(filterName, registration);
        } else {
            if (registration.filterClass != filterClass) {
                registration.filter = null;
                registration.filterClass = filterClass;
                registration.className = filterClass.getName();
            }
        }
        return registration;
    }

    /**
     * Registers the given filter instance with this WebappContext
     * under the given <tt>filterName</tt>.
     *
     * <p>The registered filter may be further configured via the returned
     * {@link FilterRegistration} object.
     *
     * <p>If this WebappContext already contains a preliminary
     * FilterRegistration for a filter with the given <tt>filterName</tt>,
     * it will be completed (by assigning the class name of the given filter
     * instance to it) and returned.
     *
     * @param filterName the name of the filter
     * @param filter the filter instance to register
     *
     * @return a FilterRegistration object that may be used to further
     * configure the given filter, or <tt>null</tt> if this
     * WebappContext already contains a complete FilterRegistration for a
     * filter with the given <tt>filterName</tt> or if the same filter
     * instance has already been registered with this or another
     * WebappContext in the same container
     *
     * @throws IllegalStateException if this WebappContext has already
     * been initialized
     *
     * @since Servlet 3.0
     */
    @Override
    public FilterRegistration addFilter(final String filterName,
                                        final Filter filter) {
        if (deployed) {
            throw new IllegalArgumentException("WebappContext has already been deployed");
        }
        if (filterName == null) {
            throw new IllegalArgumentException("'filterName' cannot be null");
        }
        if (filter == null) {
            throw new IllegalArgumentException("'filter' cannot be null");
        }

        FilterRegistration registration = filterRegistrations.get(filterName);
        if (registration == null) {
            registration = new FilterRegistration(this, filterName, filter);
            filterRegistrations.put(filterName, registration);
        } else {
            if (registration.filter != filter) {
                registration.filter = filter;
                registration.filterClass = filter.getClass();
                registration.className = filter.getClass().getName();
            }
        }
        return registration;
    }

    /**
     * Adds the filter with the given name and class name to this servlet
     * context.
     *
     * <p>The registered filter may be further configured via the returned
     * {@link FilterRegistration} object.
     *
     * <p>The specified <tt>className</tt> will be loaded using the 
     * classloader associated with the application represented by this
     * WebappContext.
     *
     * <p>If this WebappContext already contains a preliminary
     * FilterRegistration for a filter with the given <tt>filterName</tt>,
     * it will be completed (by assigning the given <tt>className</tt> to it)
     * and returned.
     *
     * @param filterName the name of the filter
     * @param className the fully qualified class name of the filter
     *
     * @return a FilterRegistration object that may be used to further
     * configure the registered filter, or <tt>null</tt> if this
     * WebappContext already contains a complete FilterRegistration for
     * a filter with the given <tt>filterName</tt> 
     *
     * @throws IllegalStateException if this WebappContext has already
     * been initialized
     */
    @Override
    public FilterRegistration addFilter(final String filterName,
                                        final String className) {
        if (deployed) {
            throw new IllegalArgumentException("WebappContext has already been deployed");
        }
        if (filterName == null) {
            throw new IllegalArgumentException("'filterName' cannot be null");
        }
        if (className == null) {
            throw new IllegalArgumentException("'className' cannot be null");
        }

        FilterRegistration registration = filterRegistrations.get(filterName);
        if (registration == null) {
            registration = new FilterRegistration(this, filterName, className);
            filterRegistrations.put(filterName, registration);
        } else {
            if (!registration.className.equals(className)) {
                registration.className = className;
                registration.filterClass = null;
                registration.filter = null;
            }
        }
        return registration;
    }

    /**
     * Adds the servlet with the given name and class type to this servlet
     * context.
     *
     * <p>The registered servlet may be further configured via the returned
     * {@link ServletRegistration} object.
     *
     * <p>If this WebappContext already contains a preliminary
     * ServletRegistration for a servlet with the given <tt>servletName</tt>,
     * it will be completed (by assigning the name of the given
     * <tt>servletClass</tt> to it) and returned.
     *
     *
     * @param servletName the name of the servlet
     * @param servletClass the class object from which the servlet will be
     * instantiated
     *
     * @return a ServletRegistration object that may be used to further
     * configure the registered servlet, or <tt>null</tt> if this
     * WebappContext already contains a complete ServletRegistration for
     * the given <tt>servletName</tt> 
     *
     * @throws IllegalStateException if this WebappContext has already
     * been initialized
     *
     */
    @Override
    public ServletRegistration addServlet(final String servletName,
                                          final Class<? extends Servlet> servletClass) {
        if (deployed) {
            throw new IllegalArgumentException("WebappContext has already been deployed");
        }
        if (servletName == null) {
            throw new IllegalArgumentException("'servletName' cannot be null");
        }
        if (servletClass == null) {
            throw new IllegalArgumentException("'servletClass' cannot be null");
        }

        ServletRegistration registration = servletRegistrations.get(servletName);
        if (registration == null) {
            registration = new ServletRegistration(this, servletName, servletClass);
            servletRegistrations.put(servletName, registration);
        } else {
            if (registration.servletClass != servletClass) {
                registration.servlet = null;
                registration.servletClass = servletClass;
                registration.className = servletClass.getName();
            }
        }
        return registration;
    }

    /**
     * Registers the given servlet instance with this WebappContext
     * under the given <tt>servletName</tt>.
     *
     * <p>The registered servlet may be further configured via the returned
     * {@link ServletRegistration} object.
     *
     * <p>If this WebappContext already contains a preliminary
     * ServletRegistration for a servlet with the given <tt>servletName</tt>,
     * it will be completed (by assigning the class name of the given servlet
     * instance to it) and returned.
     *
     * @param servletName the name of the servlet
     * @param servlet the servlet instance to register
     *
     * @return a ServletRegistration object that may be used to further
     * configure the given servlet, or <tt>null</tt> if this
     * WebappContext already contains a complete ServletRegistration for a
     * servlet with the given <tt>servletName</tt> or if the same servlet
     * instance has already been registered with this or another
     * WebappContext in the same container
     *
     * @throws IllegalStateException if this WebappContext has already
     * been initialized
     *
     * @throws IllegalArgumentException if the given servlet instance 
     * implements {@link javax.servlet.SingleThreadModel}
     */
    @SuppressWarnings({"deprecation"})
    @Override
    public ServletRegistration addServlet(final String servletName,
                                          final Servlet servlet) {
        if (deployed) {
            throw new IllegalArgumentException("WebappContext has already been deployed");
        }
        if (servletName == null) {
            throw new IllegalArgumentException("'servletName' cannot be null");
        }
        if (servlet == null) {
            throw new IllegalArgumentException("'servlet' cannot be null");
        }
        if (servlet instanceof SingleThreadModel) {
            throw new IllegalArgumentException("SingleThreadModel Servlet instances are not allowed.");
        }

        ServletRegistration registration = servletRegistrations.get(servletName);
        if (registration == null) {
            registration = new ServletRegistration(this, servletName, servlet);
            servletRegistrations.put(servletName, registration);
        } else {
            if (registration.servlet != servlet) {
                registration.servlet = servlet;
                registration.servletClass = servlet.getClass();
                registration.className = servlet.getClass().getName();
            }
        }
        return registration;
    }

    /**
     * Adds the servlet with the given name and class name to this servlet
     * context.
     *
     * <p>The registered servlet may be further configured via the returned
     * {@link ServletRegistration} object.
     *
     * <p>The specified <tt>className</tt> will be loaded using the 
     * classloader associated with the application represented by this
     * WebappContext.
     *
     * <p>If this WebappContext already contains a preliminary
     * ServletRegistration for a servlet with the given <tt>servletName</tt>,
     * it will be completed (by assigning the given <tt>className</tt> to it)
     * and returned.
     *
     *
     * @param servletName the name of the servlet
     * @param className the fully qualified class name of the servlet
     *
     * @return a ServletRegistration object that may be used to further
     * configure the registered servlet, or <tt>null</tt> if this
     * WebappContext already contains a complete ServletRegistration for
     * a servlet with the given <tt>servletName</tt> 
     *
     * @throws IllegalStateException if this WebappContext has already
     * been initialized
     */
    @Override
    public ServletRegistration addServlet(final String servletName,
                                          final String className) {
        if (deployed) {
            throw new IllegalArgumentException("WebappContext has already been deployed");
        }
        if (servletName == null) {
            throw new IllegalArgumentException("'servletName' cannot be null");
        }
        if (className == null) {
            throw new IllegalArgumentException("'className' cannot be null");
        }

        ServletRegistration registration = servletRegistrations.get(servletName);
        if (registration == null) {
            registration = new ServletRegistration(this, servletName, className);
            servletRegistrations.put(servletName, registration);
        } else {
            if (!registration.className.equals(className)) {
                registration.servlet = null;
                registration.servletClass = null;
                registration.className = className;
            }
        }
        return registration;
    }

    /**
     * Gets the FilterRegistration corresponding to the filter with the
     * given <tt>filterName</tt>.
     *
     * @return the (complete or preliminary) FilterRegistration for the
     * filter with the given <tt>filterName</tt>, or null if no
     * FilterRegistration exists under that name
     */
    @Override
    public FilterRegistration getFilterRegistration(final String name) {
        if (name == null) {
            return null;
        }
        return filterRegistrations.get(name);
    }

    /**
     * Gets a (possibly empty) Map of the FilterRegistration
     * objects (keyed by filter name) corresponding to all filters
     * registered with this WebappContext.
     *
     * <p>The returned Map includes the FilterRegistration objects
     * corresponding to all declared and annotated filters, as well as the
     * FilterRegistration objects corresponding to all filters that have
     * been added via one of the <tt>addFilter</tt> methods.
     *
     * <p>Any changes to the returned Map must not affect this
     * WebappContext.
     *
     * @return Map of the (complete and preliminary) FilterRegistration
     * objects corresponding to all filters currently registered with this
     * WebappContext
     */
    @Override
    public Map<String,? extends FilterRegistration> getFilterRegistrations() {
        return unmodifiableFilterRegistrations;
    }

    /**
     * Gets the ServletRegistration corresponding to the servlet with the
     * given <tt>servletName</tt>.
     *
     * @return the (complete or preliminary) ServletRegistration for the
     * servlet with the given <tt>servletName</tt>, or null if no
     * ServletRegistration exists under that name
     */
    @Override
    public ServletRegistration getServletRegistration(final String name) {
        if (name == null) {
            return null;
        }
        return servletRegistrations.get(name);
    }

    /**
     * Gets a (possibly empty) Map of the ServletRegistration
     * objects (keyed by servlet name) corresponding to all servlets
     * registered with this WebappContext.
     *
     * <p>The returned Map includes the ServletRegistration objects
     * corresponding to all declared and annotated servlets, as well as the
     * ServletRegistration objects corresponding to all servlets that have
     * been added via one of the <tt>addServlet</tt> methods.
     *
     * <p>If permitted, any changes to the returned Map must not affect this
     * WebappContext.
     *
     * @return Map of the (complete and preliminary) ServletRegistration
     * objects corresponding to all servlets currently registered with this
     * WebappContext
     *
     * @since Servlet 3.0
     */
    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return Collections.unmodifiableMap(servletRegistrations);
    }

    /**
     * Adds the given listener class to this WebappContext.
     *
     * <p>The given listener must be an instance of one or more of the
     * following interfaces:
     * <ul>
     * <li>{@link ServletContextAttributeListener}</tt>
     * <li>{@link javax.servlet.ServletRequestListener}</tt>
     * <li>{@link javax.servlet.ServletRequestAttributeListener}</tt>
     * <li>{@link javax.servlet.http.HttpSessionListener}</tt>
     * <li>{@link javax.servlet.http.HttpSessionAttributeListener}</tt>
     * </ul>
     *
     * <p>If the given listener is an instance of a listener interface whose
     * invocation order corresponds to the declaration order (i.e., if it
     * is an instance of {@link javax.servlet.ServletRequestListener},
     * {@link ServletContextListener}, or
     * {@link javax.servlet.http.HttpSessionListener}),
     * then the listener will be added to the end of the ordered list of
     * listeners of that interface.
     *
     * @throws IllegalArgumentException if the given listener is not
     * an instance of any of the above interfaces
     *
     * @throws IllegalStateException if this WebappContext has already
     * been initialized
     */
    @Override
    public void addListener(final Class<? extends EventListener> listenerClass) {
        if (deployed) {
            throw new IllegalStateException("WebappContext has already been deployed");
        }
        
        if (listenerClass == null) {
            throw new IllegalArgumentException("'listener' cannot be null");
        }
        
        try {
            addListener(createEventListenerInstance(listenerClass));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Adds the listener with the given class name to this WebappContext.
     *
     * <p>The class with the given name will be loaded using the
     * classloader associated with the application represented by this
     * WebappContext, and must implement one or more of the following
     * interfaces:
     * <ul>
     * <li>{@link ServletContextAttributeListener}</tt>
     * <li>{@link javax.servlet.ServletRequestListener}</tt>
     * <li>{@link javax.servlet.ServletRequestAttributeListener}</tt>
     * <li>{@link javax.servlet.http.HttpSessionListener}</tt>
     * <li>{@link javax.servlet.http.HttpSessionAttributeListener}</tt>
     * </ul>
     *
     * <p>As part of this method call, the container must load the class
     * with the specified class name to ensure that it implements one of 
     * the required interfaces.
     *
     * <p>If the class with the given name implements a listener interface
     * whose invocation order corresponds to the declaration order (i.e.,
     * if it implements {@link javax.servlet.ServletRequestListener},
     * {@link ServletContextListener}, or
     * {@link javax.servlet.http.HttpSessionListener}),
     * then the new listener will be added to the end of the ordered list of
     * listeners of that interface.
     *
     * @param className the fully qualified class name of the listener
     *
     * @throws IllegalArgumentException if the class with the given name
     * does not implement any of the above interfaces
     *
     * @throws IllegalStateException if this WebappContext has already
     * been initialized
     */
    @Override
    public void addListener(String className) {
        if (deployed) {
            throw new IllegalStateException("WebappContext has already been deployed");
        }

        if (className == null) {
            throw new IllegalArgumentException("'className' cannot be null");
        }

        try {
            addListener(createEventListenerInstance(className));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends EventListener> void addListener(T eventListener) {
        if (deployed) {
            throw new IllegalStateException("WebappContext has already been deployed");
        }
        
        eventListenerInstances.add(eventListener);
    }
    


    @SuppressWarnings("unchecked")
    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        try {
            return (T) createServletInstance(clazz);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
        try {
            return (T) createFilterInstance(clazz);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
        try {
            return (T) createEventListenerInstance(clazz);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }


    @Override
    public void declareRoles(String... roleNames) {
        if (deployed) {
            throw new IllegalStateException("WebappContext has already been deployed");
        }
        
        securityRoles.addAll(Arrays.asList(roleNames));
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getContextPath() {
        return contextPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServletContext getContext(String uri) {
        // Validate the format of the specified argument
        if (uri == null || !uri.startsWith("/")) {
            return null;
        }
        if (dispatcherHelper == null) {
            return null;
        }

        // Use the thread local URI and mapping data
        DispatchData dd = dispatchData.get();
        if (dd == null) {
            dd = new DispatchData();
            dispatchData.set(dd);
        } else {
            dd.recycle();
        }
        DataChunk uriDC = dd.uriDC;
        // Retrieve the thread local mapping data
        MappingData mappingData = dd.mappingData;

        try {
            uriDC.setString(uri);
            dispatcherHelper.mapPath(null, uriDC, mappingData);
            if (mappingData.context == null) {
                return null;
            }
        } catch (Exception e) {
            // Should never happen
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "Error during mapping", e);
            }
            return null;
        }

        if (!(mappingData.context instanceof ServletHandler)) {
            return null;
        }
        ServletHandler context = (ServletHandler) mappingData.context;
        return context.getServletCtx();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEffectiveMajorVersion() {
        return MAJOR_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEffectiveMinorVersion() {
        return MINOR_VERSION;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getMimeType(String file) {
        if (file == null) {
            return (null);
        }
        int period = file.lastIndexOf(".");
        if (period < 0) {
            return (null);
        }
        String extension = file.substring(period + 1);
        if (extension.length() < 1) {
            return (null);
        }
        return MimeType.get(extension);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getResourcePaths(String path) {
        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(path);
        }

        path = normalize(path);
        if (path == null) {
            return (null);
        }

        File[] files = new File(basePath, path).listFiles();

        Set<String> set = Collections.emptySet();
        if (files != null) {
            set = new HashSet<String>(files.length);
            for (File f : files) {
                try {
                    String canonicalPath = f.getCanonicalPath();

                    // add a trailing "/" if a folder
                    if (f.isDirectory()) {
                        canonicalPath = canonicalPath + "/";
                    }

                    canonicalPath = canonicalPath.substring(
                            canonicalPath.indexOf(basePath) + basePath.length());
                    set.add(canonicalPath.replace("\\", "/"));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return set;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL getResource(String path) throws MalformedURLException {
        if (path == null || !path.startsWith("/")) {
            throw new MalformedURLException(path);
        }

        path = normalize(path);
        if (path == null) {
            return (null);
        }

        // Help the UrlClassLoader, which is not able to load resources
        // that contains '//'
        if (path.length() > 1) {
            path = path.substring(1);
        }

        return Thread.currentThread().getContextClassLoader().getResource(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getResourceAsStream(String path) {
        String pathLocal = normalize(path);
        if (pathLocal == null) {
            return (null);
        }

        // Help the UrlClassLoader, which is not able to load resources
        // that contains '//'
        if (pathLocal.length() > 1) {
            pathLocal = pathLocal.substring(1);
        }

        return Thread.currentThread().getContextClassLoader().getResourceAsStream(pathLocal);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (dispatcherHelper == null) {
            return null;
        }
        if (!path.startsWith("/") && !path.isEmpty()) {
            throw new IllegalArgumentException("Path " + path + " does not start with ''/'' and is not empty");
        }

        // Get query string
        String queryString = null;
        int pos = path.indexOf('?');
        if (pos >= 0) {
            queryString = path.substring(pos + 1);
            path = path.substring(0, pos);
        }
        path = normalize(path);
        if (path == null)
            return null;

        // Use the thread local URI and mapping data
        DispatchData dd = dispatchData.get();
        if (dd == null) {
            dd = new DispatchData();
            dispatchData.set(dd);
        } else {
            dd.recycle();
        }
        DataChunk uriDC = dd.uriDC;
        // Retrieve the thread local mapping data
        MappingData mappingData = dd.mappingData;

        try {
            if (contextPath.length() == 1 && contextPath.charAt(0) == '/') {
                uriDC.setString(path);
            } else {
                uriDC.setString(contextPath + path);
            }
            dispatcherHelper.mapPath(null, uriDC, mappingData);
            if (mappingData.wrapper == null) {
                return null;
            }
        } catch (Exception e) {
            // Should never happen
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "Error during mapping", e);
            }
            return null;
        }

        if (!(mappingData.wrapper instanceof ServletHandler)) {
            return null;
        }
        ServletHandler wrapper = (ServletHandler) mappingData.wrapper;
        String wrapperPath = mappingData.wrapperPath.toString();
        String pathInfo = mappingData.pathInfo.toString();

        // Construct a RequestDispatcher to process this request
        return new ApplicationDispatcher(wrapper,
                                         uriDC.toString(),
                                         wrapperPath,
                                         pathInfo,
                                         queryString,
                                         null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        // Validate the name argument
        if (name == null)
            return null;
        if (dispatcherHelper == null) {
            return null;
        }

        // Use the thread local URI and mapping data
        DispatchData dd = dispatchData.get();
        if (dd == null) {
            dd = new DispatchData();
            dispatchData.set(dd);
        } else {
            dd.recycle();
        }
        DataChunk servletNameDC = dd.servletNameDC;
        // Retrieve the thread local mapping data
        MappingData mappingData = dd.mappingData;
        // Map the name
        servletNameDC.setString(name);

        try {
            dispatcherHelper.mapName(servletNameDC, mappingData);
            if (!(mappingData.wrapper instanceof ServletHandler)) {
                return null;
            } else {
                final ServletHandler h = (ServletHandler) mappingData.wrapper;
                if (!contextPath.equals(h.getContextPath())) {
                    return null;
                }
            }

        } catch (Exception e) {
            // Should never happen
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "Error during mapping", e);
            }
            return null;
        }

        ServletHandler wrapper = (ServletHandler) mappingData.wrapper;
        // Construct a RequestDispatcher to process this request
        return new ApplicationDispatcher(wrapper, null, null, null, null, name);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated
     */
    @Override
    @Deprecated
    public Servlet getServlet(String name) throws ServletException {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated
     */
    @Override
    @Deprecated
    public Enumeration<Servlet> getServlets() {
        return new Enumerator<Servlet>(Collections.<Servlet>emptyList());
    }

    /**
     *
     * {@inheritDoc}
     * @deprecated
     */
    @Override
    @Deprecated
    public Enumeration<String> getServletNames() {
        return new Enumerator<String>(Collections.<String>emptyList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(String message) {
        LOGGER.log(Level.INFO, String.format("[%s] %s", displayName, message));
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated
     */
    @Override
    @Deprecated
    public void log(Exception e, String message) {
        log(message, e);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(String message, Throwable throwable) {
        LOGGER.log(Level.INFO, String.format("[%s] %s", displayName, message), throwable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getRealPath(String path) {
        if (path == null) {
            return null;
        }

        return new File(basePath, path).getAbsolutePath();
    }

    @Override
    public String getVirtualServerName() {
        return "server";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServerInfo() {
        return serverInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getInitParameter(String name) {
        return contextInitParams.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        return new Enumerator<String>(contextInitParams.keySet());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setInitParameter(String name, String value) {
        if (!deployed) {
            contextInitParams.put(name, value);
            return true;
        }
        
        return false;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return new Enumerator<String>(attributes.keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttribute(String name, Object value) {
        // Name cannot be null
        if (name == null) {
            throw new IllegalArgumentException("Cannot be null");
        }

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        Object oldValue = attributes.put(name, value);

        ServletContextAttributeEvent event = null;
        for (int i = 0, len = eventListeners.length; i < len; i++) {
            if (!(eventListeners[i] instanceof ServletContextAttributeListener)) {
                continue;
            }
            ServletContextAttributeListener listener =
                    (ServletContextAttributeListener) eventListeners[i];
            try {
                if (event == null) {
                    if (oldValue != null) {
                        event =
                                new ServletContextAttributeEvent(this,
                                name, oldValue);
                    } else {
                        event =
                                new ServletContextAttributeEvent(this,
                                name, value);
                    }

                }
                if (oldValue != null) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_ATTRIBUTE_LISTENER_ADD_ERROR("ServletContextAttributeListener", listener.getClass().getName()),
                            t);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(String name) {
       Object value = attributes.remove(name);
        if (value == null) {
            return;
        }

        ServletContextAttributeEvent event = null;
        for (int i = 0, len = eventListeners.length; i < len; i++) {
            if (!(eventListeners[i] instanceof ServletContextAttributeListener)) {
                continue;
            }
            ServletContextAttributeListener listener =
                    (ServletContextAttributeListener) eventListeners[i];
            try {
                if (event == null) {
                    event = new ServletContextAttributeEvent(this, name, value);

                }
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_ATTRIBUTE_LISTENER_REMOVE_ERROR("ServletContextAttributeListener", listener.getClass().getName()),
                            t);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServletContextName() {
        return displayName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public javax.servlet.SessionCookieConfig getSessionCookieConfig() {
        if (sessionCookieConfig == null) {
            sessionCookieConfig = new SessionCookieConfig(this);
        }
        return sessionCookieConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        if (sessionTrackingModes.contains(SessionTrackingMode.SSL)) {
            throw new IllegalArgumentException("SSL tracking mode is not supported");
        }

        if (deployed) {
            throw new IllegalArgumentException("WebappContext has already been deployed");
        }

        this.sessionTrackingModes =
            Collections.unmodifiableSet(sessionTrackingModes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return DEFAULT_SESSION_TRACKING_MODES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return (sessionTrackingModes != null ? sessionTrackingModes :
            DEFAULT_SESSION_TRACKING_MODES);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    

    // ------------------------------------------------------- Protected Methods


    /**
     * Return a context-relative path, beginning with a "/", that represents
     * the canonical version of the specified path after ".." and "." elements
     * are resolved out.  If the specified path attempts to go outside the
     * boundaries of the current context (i.e. too many ".." path elements
     * are present), return <code>null</code> instead.
     *
     * @param path Path to be normalized
     */
    protected String normalize(String path) {

        if (path == null) {
            return null;
        }

        String normalized = path;

        // Normalize the slashes and add leading slash if necessary
        if (normalized.indexOf('\\') >= 0) {
            normalized = normalized.replace('\\', '/');
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0) {
                break;
            }
            if (index == 0) {
                return (null);  // Trying to go outside our context
            }
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2)
                    + normalized.substring(index + 3);
        }

        // Return the normalized path that we have completed
        return (normalized);

    }

    /**
     *
     * @return
     */
    protected String getBasePath() {
        return basePath;
    }

    /**
     *
     * @param dispatcherHelper
     */
    protected void setDispatcherHelper( DispatcherHelper dispatcherHelper ) {
        this.dispatcherHelper = dispatcherHelper;
    }

    /**
     * Sets the {@link SessionManager} that should be used by this
     * {@link WebappContext}. The default is an instance of
     * {@link ServletSessionManager}
     *
     * @param sessionManager an implementation of SessionManager
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     *
     * @return
     */
    protected EventListener[] getEventListeners() {
        return eventListeners;
    }

    /**
     * Add a filter mapping to this Context.
     *
     * @param filterMap The filter mapping to be added
     *
     * @param isMatchAfter true if the given filter mapping should be matched
     * against requests after any declared filter mappings of this servlet
     * context, and false if it is supposed to be matched before any declared
     * filter mappings of this servlet context
     *
     * @exception IllegalArgumentException if the specified filter name
     *  does not match an existing filter definition, or the filter mapping
     *  is malformed
     *
     */
    protected void addFilterMap(FilterMap filterMap, boolean isMatchAfter) {

        // Validate the proposed filter mapping
        String filterName = filterMap.getFilterName();
        String servletName = filterMap.getServletName();
        String urlPattern = filterMap.getURLPattern();
        if (null == filterRegistrations.get(filterName)) {
            throw new IllegalArgumentException
                ("Filter mapping specifies an unknown filter name: " + filterName);
        }
        if ((servletName == null) && (urlPattern == null)) {
            throw new IllegalArgumentException
                ("Filter mapping must specify either a <url-pattern> or a <servlet-name>");
        }
        if ((servletName != null) && (urlPattern != null)) {
            throw new IllegalArgumentException
                ("Filter mapping must specify either a <url-pattern> or a <servlet-name>");
        }
        // Because filter-pattern is new in 2.3, no need to adjust
        // for 2.2 backwards compatibility
        if ((urlPattern != null) && !validateURLPattern(urlPattern)) {
            throw new IllegalArgumentException
                ("Invalid <url-pattern> {0} in filter mapping: " + urlPattern);
        }

        // Add this filter mapping to our registered set
        if (isMatchAfter) {
            filterMaps.add(filterMap);
        } else {
            filterMaps.add(0, filterMap);
        }

//        if (notifyContainerListeners) {
//            fireContainerEvent("addFilterMap", filterMap);
//        }
    }
    
    protected List<FilterMap> getFilterMaps() {
        return filterMaps;
    }
    
    /**
     * Removes any filter mappings from this Context.
     */
    protected void removeFilterMaps() {
        // Inform interested listeners
//        if (notifyContainerListeners) {
//            Iterator<FilterMap> i = filterMaps.iterator();
//            while (i.hasNext()) {
//                fireContainerEvent("removeFilterMap", i.next());
//            }
//        }
        filterMaps.clear();
    }    
    /**
     * Gets the current servlet name mappings of the Filter with
     * the given name.
     */
    protected Collection<String> getServletNameFilterMappings(String filterName) {
        HashSet<String> mappings = new HashSet<String>();
        synchronized (filterMaps) {
            for (FilterMap fm : filterMaps) {
                if (filterName.equals(fm.getFilterName()) &&
                        fm.getServletName() != null) {
                    mappings.add(fm.getServletName());
                }
            }
        }
        return mappings;
    }

    /**
     * Gets the current URL pattern mappings of the Filter with the given
     * name.
     */
    protected Collection<String> getUrlPatternFilterMappings(String filterName) {
        HashSet<String> mappings = new HashSet<String>();
        synchronized (filterMaps) {
            for (FilterMap fm : filterMaps) {
                if (filterName.equals(fm.getFilterName()) &&
                        fm.getURLPattern() != null) {
                    mappings.add(fm.getURLPattern());
                }
            }
        }
        return mappings;
    }

    protected FilterChainFactory getFilterChainFactory() {
        return filterChainFactory;
    }

    @SuppressWarnings("UnusedDeclaration")
    protected void unregisterFilter(final Filter f) {
        synchronized (filterRegistrations) {
            for (Iterator<FilterRegistration> i = filterRegistrations.values().iterator(); i.hasNext();) {
                final FilterRegistration registration = i.next();
                if (registration.filter == f) {
                    for (Iterator<FilterMap> fmi = filterMaps.iterator(); fmi.hasNext(); ) {
                        FilterMap fm = fmi.next();
                        if (fm.getFilterName().equals(registration.name)) {
                            fmi.remove();
                        }
                    }
                    f.destroy();
                    i.remove();
                }
            }
        }
    }

    protected void unregisterAllFilters() {
        destroyFilters();
    }


    // --------------------------------------------------------- Private Methods

    protected void destroyFilters() {
        for (final FilterRegistration registration : filterRegistrations.values()) {
            registration.filter.destroy();
        }
        
        removeFilterMaps();
    }

    /**
     *
     * @param server
     */
    private void destoryServlets(HttpServer server) {
        if (servletHandlers != null && !servletHandlers.isEmpty()) {
            ServerConfiguration config = server.getServerConfiguration();
            for (final ServletHandler handler : servletHandlers) {
                config.removeHttpHandler(handler);
            }
        }
    }

    /**
     *
     */
    private void initializeListeners() throws ServletException {
        if (!eventListenerInstances.isEmpty()) {
            eventListeners = eventListenerInstances.toArray(
                    new EventListener[eventListenerInstances.size()]);
        }
    }

    /**
     *
     * @param server
     */
    private void initServlets(final HttpServer server) throws ServletException {

        boolean defaultMappingAdded = false;
        if (!servletRegistrations.isEmpty()) {
            final ServerConfiguration serverConfig = server.getServerConfiguration();
            servletHandlers = new LinkedHashSet<ServletHandler>(servletRegistrations.size(), 1.0f);
            final LinkedList<ServletRegistration> sortedRegistrations =
                    new LinkedList<ServletRegistration>(servletRegistrations.values());
            Collections.sort(sortedRegistrations);
            for (final ServletRegistration registration : sortedRegistrations) {
                ServletHandler servletHandler;
                final ServletConfigImpl sConfig =
                        createServletConfig(registration);

                if (registration.servlet != null) {
                    try {
                        registration.servlet.init(sConfig);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else if (registration.loadOnStartup >= 0) {
                    try {
                        Servlet servletInstance = createServletInstance(registration);
                        LOGGER.log(Level.INFO, "Loading Servlet: {0}", servletInstance.getClass().getName());
                        servletInstance.init(sConfig);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                servletHandler = new ServletHandler(sConfig);
                servletHandler.setServletInstance(registration.servlet);
                servletHandler.setServletClass(registration.servletClass);
                servletHandler.setServletClassName(registration.className);
                servletHandler.setSessionManager(sessionManager);
                servletHandler.setContextPath(contextPath);
                servletHandler.setFilterChainFactory(filterChainFactory);
                servletHandler.setExpectationHandler(registration.expectationHandler);
                servletHandler.addOnDestroyListener(onDestroyListener);
                servletHandler.setClassLoader(webappClassLoader);

                final String[] patterns = registration.urlPatterns.getArray();
                if (patterns != null && patterns.length > 0) {
                    final String[] mappings = new String[patterns.length];
                    
                    for (int i = 0; i < patterns.length; i++) {
                        final String pattern = patterns[i];
                        
                        if (pattern.length() == 0 || "/".equals(pattern)) {
                            defaultMappingAdded = true;
                        }
                        
                        mappings[i] = updateMappings(servletHandler, pattern);
                    }
                                
                    serverConfig.addHttpHandler(servletHandler, mappings);
                } else {
                    serverConfig.addHttpHandler(servletHandler,
                            updateMappings(servletHandler, ""));
                }
                servletHandlers.add(servletHandler);
                if (LOGGER.isLoggable(Level.INFO)) {
                    String p = ((patterns == null)
                                        ? ""
                                        : Arrays.toString(patterns));
                    LOGGER.log(Level.INFO,
                            "[{0}] Servlet [{1}] registered for url pattern(s) [{2}].",
                            new Object[]{
                                    displayName,
                                    registration.className,
                                    p});
                }
            }
        }
        if (!defaultMappingAdded) {
            // add the default servlet
            registerDefaultServlet(server);
        }
    }

    private void registerDefaultServlet(HttpServer server) {
        final ServerConfiguration serverConfig = server.getServerConfiguration();
        Map<HttpHandler,String[]> handlers = serverConfig.getHttpHandlers();
        for (final Map.Entry<HttpHandler,String[]> entry : handlers.entrySet()) {
            final HttpHandler h = entry.getKey();
            if (!(h instanceof StaticHttpHandler)) {
                continue;
            }
            String[] mappings = entry.getValue();
            for (final String mapping : mappings) {
                if ("/".equals(mapping)) {
                    final DefaultServlet s = new DefaultServlet(((StaticHttpHandler) h).getDocRoots());
                    final ServletRegistration registration =
                            addServlet("DefaultServlet", s);
                    registration.addMapping("/");
                    final ServletConfigImpl sConfig =
                                            createServletConfig(registration);
                    try {
                        s.init(sConfig);
                    } catch (ServletException ignored) {
                        // shouldn't happen
                    }
                    final ServletHandler servletHandler = new ServletHandler(sConfig);
                    servletHandler.setServletInstance(registration.servlet);
                    servletHandler.setServletClass(registration.servletClass);
                    servletHandler.setServletClassName(registration.className);
                    servletHandler.setContextPath(contextPath);
                    servletHandler.setFilterChainFactory(filterChainFactory);
                    servletHandler.setExpectationHandler(registration.expectationHandler);
                    servletHandler.addOnDestroyListener(onDestroyListener);
                    
                    serverConfig.addHttpHandler(servletHandler,
                                                    updateMappings(servletHandler,
                                                            "/"));
                    if (servletHandlers == null) {
                        servletHandlers = new LinkedHashSet<ServletHandler>(1, 1.0f);
                    }
                    servletHandlers.add(servletHandler);
                }
            }
        }
    }

    /**
     *
     */
    @SuppressWarnings({"unchecked"})
    private void initFilters() {
        if (!filterRegistrations.isEmpty()) {
            for (final FilterRegistration registration : filterRegistrations.values()) {
                try {
                    Filter f = registration.filter;
                    if (f == null) {
                        f = createFilterInstance(registration);
                    }

                    final FilterConfigImpl filterConfig =
                            createFilterConfig(registration);
                    registration.filter = f;
                    f.init(filterConfig);
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.log(Level.INFO,
                                   "[{0}] Filter [{1}] registered for url pattern(s) [{2}] and servlet name(s) [{3}]",
                                   new Object[] {
                                           displayName,
                                        registration.className,
                                        registration.getUrlPatternMappings(),
                                        registration.getServletNameMappings()});
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     *
     * @param handler
     * @param mapping
     * @return
     */
    private static String updateMappings(ServletHandler handler, String mapping) {

        String mappingLocal = mapping;
        if (mappingLocal.length() == 0) {
            mappingLocal = "/";
        } else {
            if (mappingLocal.charAt(0) == '*') {
                mappingLocal = "/" + mapping;
            }
            if (mappingLocal.indexOf("//", 1) != -1) {
                mappingLocal = mappingLocal.replaceAll("//", "/");
            }
        }
        String contextPath = handler.getContextPath();
        contextPath = ((contextPath.length() == 0) ? "/" : contextPath);
        return (contextPath + mappingLocal);

    }

    // --------------------------------------------------------- Private Methods

    /**
     *
     * @param registration
     * @return
     */
    private FilterConfigImpl createFilterConfig(final FilterRegistration registration) {
        final FilterConfigImpl fConfig = new FilterConfigImpl(this);
        fConfig.setFilterName(registration.getName());
        if (!registration.initParameters.isEmpty()) {
            fConfig.setInitParameters(registration.initParameters);
        }
        return fConfig;
    }


    /**
     *
     * @param registration
     * @return
     */
    private ServletConfigImpl createServletConfig(final ServletRegistration registration) {
        final ServletConfigImpl sConfig = new ServletConfigImpl(this);
        sConfig.setServletName(registration.getName());
        if (!registration.initParameters.isEmpty()) {
            sConfig.setInitParameters(registration.initParameters);
        }
        return sConfig;

    }


    /**
     *
     */
    private void contextInitialized() {
        ServletContextEvent event = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(webappClassLoader);
        for (int i = 0, len = eventListeners.length; i < len; i++) {
            if (!(eventListeners[i] instanceof ServletContextListener)) {
                continue;
            }
            ServletContextListener listener =
                    (ServletContextListener) eventListeners[i];
            if (event == null) {
                event = new ServletContextEvent(this);
            }
            try {
                listener.contextInitialized(event);
            } catch (Throwable t) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_INITIALIZED_ERROR("contextInitialized", "ServletContextListener", listener.getClass().getName()),
                            t);
                }
            }
        }
        Thread.currentThread().setContextClassLoader(loader);
    }

    /**
     *
     */
    private void contextDestroyed() {
        ServletContextEvent event = null;
        for (int i = 0, len = eventListeners.length; i < len; i++) {
            if (!(eventListeners[i] instanceof ServletContextListener)) {
                continue;
            }
            ServletContextListener listener =
                    (ServletContextListener) eventListeners[i];
            if (event == null) {
                event = new ServletContextEvent(this);
            }
            try {
                listener.contextDestroyed(event);
            } catch (Throwable t) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_DESTROYED_ERROR("contextDestroyed", "ServletContextListener", listener.getClass().getName()),
                            t);
                }
            }
        }
    }
    
    /**
     * Instantiates the given Servlet class.
     *
     * @return the new Servlet instance
     */
    protected Servlet createServletInstance(final ServletRegistration registration)
            throws Exception {
        String servletClassName = registration.className;
        Class<? extends Servlet> servletClass = registration.servletClass;
        if (servletClassName != null) {
            return (Servlet) ClassLoaderUtil.load(servletClassName);
        } else {
            return createServletInstance(servletClass);
        }
    }

    /**
     * Instantiates the given Servlet class.
     *
     * @return the new Servlet instance
     */
    protected Servlet createServletInstance(Class<? extends Servlet> servletClass)
            throws Exception {
        return servletClass.newInstance();
    }
    
    /**
     * Instantiates the given Filter class.
     *
     * @return the new Filter instance
     */
    protected Filter createFilterInstance(final FilterRegistration registration)
            throws Exception {
        String filterClassName = registration.className;
        Class<? extends Filter> filterClass = registration.filterClass;
        if (filterClassName != null) {
            return (Filter) ClassLoaderUtil.load(filterClassName);
        } else {
            return createFilterInstance(filterClass);
        }
    }

    /**
     * Instantiates the given Filter class.
     *
     * @return the new Filter instance
     */
    protected Filter createFilterInstance(Class<? extends Filter> filterClass)
            throws Exception {
        return filterClass.newInstance();
    }
    
    /**
     * Instantiates the given EventListener class.
     *
     * @return the new EventListener instance
     */
    protected EventListener createEventListenerInstance(Class<? extends EventListener> eventListenerClass)
            throws Exception {
        return eventListenerClass.newInstance();
    }
    
    /**
     * Instantiates the given EventListener class.
     *
     * @return the new EventListener instance
     */
    protected EventListener createEventListenerInstance(String eventListenerClassname)
            throws Exception {
        return (EventListener) ClassLoaderUtil.load(eventListenerClassname);
    }

    /**
     * Instantiates the given HttpUpgradeHandler class.
     *
     * @param clazz
     * @param <T>
     * @return a new T instance
     * @throws Exception
     */
    public <T extends HttpUpgradeHandler> T createHttpUpgradeHandlerInstance(Class<T> clazz)
            throws Exception {
        return clazz.newInstance();
    }
    
    /**
     * Validate the syntax of a proposed <code>&lt;url-pattern&gt;</code>
     * for conformance with specification requirements.
     *
     * @param urlPattern URL pattern to be validated
     */
    protected boolean validateURLPattern(String urlPattern) {
        if (urlPattern == null) {
            return false;
        }
        if (urlPattern.isEmpty()) {
            return true;
        }
        if (urlPattern.indexOf('\n') >= 0 || urlPattern.indexOf('\r') >= 0) {
            LOGGER.log(Level.WARNING, "The URL pattern ''{0}'' contains a CR or LF and so can never be matched", urlPattern);
            return false;
        }
        if (urlPattern.startsWith("*.")) {
            if (urlPattern.indexOf('/') < 0) {
                checkUnusualURLPattern(urlPattern);
                return true;
            } else
                return false;
        }
        if ( (urlPattern.startsWith("/")) &&
                (!urlPattern.contains("*."))) {
            checkUnusualURLPattern(urlPattern);
            return true;
        } else
            return false;

    }
    
    /**
     * Check for unusual but valid <code>&lt;url-pattern&gt;</code>s.
     * See Bugzilla 34805, 43079 &amp; 43080
     */
    private void checkUnusualURLPattern(String urlPattern) {
        if (LOGGER.isLoggable(Level.INFO)) {
            if(urlPattern.endsWith("*") && (urlPattern.length() < 2 ||
                    urlPattern.charAt(urlPattern.length()-2) != '/')) {
                LOGGER.log(Level.INFO,"Suspicious url pattern: \"{0}" + "\"" +
                        " in context - see" +
                        " section SRV.11.2 of the Servlet specification" , urlPattern);
            }
        }
    }    
    
    // ---------------------------------------------------------- Nested Classes


    /**
     * Internal class used as thread-local storage when doing path
     * mapping during dispatch.
     */
    private static final class DispatchData {

        public final DataChunk uriDC;
        public final DataChunk servletNameDC;
        public final MappingData mappingData;

        public DispatchData() {
            uriDC = DataChunk.newInstance();
            servletNameDC = DataChunk.newInstance();
            mappingData = new MappingData();
        }

        public void recycle() {
            uriDC.recycle();
            servletNameDC.recycle();
            mappingData.recycle();
        }

    } // END DispatchData

}
