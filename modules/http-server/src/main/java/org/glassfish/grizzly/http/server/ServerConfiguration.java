/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;


import java.util.Collections;
import java.util.HashMap;
import org.glassfish.grizzly.http.server.jmxbase.JmxEventListener;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Configuration options for a particular {@link HttpServer} instance.
 */
public class ServerConfiguration extends ServerFilterConfiguration {

    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(-1);

    private static final HttpHandlerRegistration[] ROOT_MAPPING = {
        HttpHandlerRegistration.ROOT
    };

    // Non-exposed

    final Map<HttpHandler, HttpHandlerRegistration[]> handlers =
            DataStructures.<HttpHandler, HttpHandlerRegistration[]>getConcurrentMap();
    private final Map<HttpHandler, HttpHandlerRegistration[]> unmodifiableHandlers =
            Collections.unmodifiableMap(handlers);
    final List<HttpHandler> orderedHandlers =
            new LinkedList<HttpHandler>();

    private final Set<JmxEventListener> jmxEventListeners = new CopyOnWriteArraySet<JmxEventListener>();

    private final HttpServerMonitoringConfig monitoringConfig = new HttpServerMonitoringConfig();

    private String name;

    final HttpServer instance;

    private boolean jmxEnabled;

    // flag, which enables/disables payload support for HTTP methods,
    // for which HTTP spec doesn't clearly state whether they support payload.
    // Known "undefined" methods are: GET, HEAD, DELETE
    private boolean allowPayloadForUndefinedHttpMethods;
    
    /**
     * The maximum request payload remainder (in bytes) HttpServerFilter will try
     * to swallow after HTTP request processing is over in order to keep the
     * connection alive. If the remainder is too large - HttpServerFilter will
     * close the connection.
     */
    private long maxPayloadRemainderToSkip = -1;
    
    final Object handlersSync = new Object();
    
    // ------------------------------------------------------------ Constructors


    ServerConfiguration(HttpServer instance) {
        this.instance = instance;
    }


    // ---------------------------------------------------------- Public Methods

    /**
     * Adds the specified {@link HttpHandler} as a root handler.
     *
     * @param httpHandler a {@link HttpHandler}
     */
    public void addHttpHandler(final HttpHandler httpHandler) {
        addHttpHandler(httpHandler, ROOT_MAPPING);
    }

    /**
     * Adds the specified {@link HttpHandler}
     * with its associated mapping(s). Requests will be dispatched to a
     * {@link HttpHandler} based on these mapping
     * values.
     *
     * @param httpHandler a {@link HttpHandler}
     * @param mappings    context path mapping information.
     */
    public void addHttpHandler(final HttpHandler httpHandler,
            final String... mappings) {
        if (mappings == null || mappings.length == 0) {
            addHttpHandler(httpHandler, ROOT_MAPPING);
            return;
        }
        
        final HttpHandlerRegistration[] registrations =
                new HttpHandlerRegistration[mappings.length];
        
        for (int i = 0; i < mappings.length; i++) {
            registrations[i] = HttpHandlerRegistration.fromString(mappings[i]);
        }
        
        addHttpHandler(httpHandler, registrations);
    }

    /**
     * Adds the specified {@link HttpHandler}
     * with its associated mapping(s). Requests will be dispatched to a
     * {@link HttpHandler} based on these mapping
     * values.
     *
     * @param httpHandler a {@link HttpHandler}
     * @param mapping        context path mapping information.
     */
    public void addHttpHandler(final HttpHandler httpHandler,
            HttpHandlerRegistration... mapping) {
        synchronized (handlersSync) {
            if (mapping == null || mapping.length == 0) {
                mapping = ROOT_MAPPING;
            }

            if (handlers.put(httpHandler, mapping) != null) {
                orderedHandlers.remove(httpHandler);
            }

            orderedHandlers.add(httpHandler);
            instance.onAddHttpHandler(httpHandler, mapping);
        }
    }
    
    /**
     *
     * Removes the specified {@link HttpHandler}.
     *
     * @return <tt>true</tt>, if the operation was successful, otherwise
     *  <tt>false</tt>
     */
    public synchronized boolean removeHttpHandler(final HttpHandler httpHandler) {
        synchronized (handlersSync) {
            final boolean result = handlers.remove(httpHandler) != null;
            if (result) {
                orderedHandlers.remove(httpHandler);
                instance.onRemoveHttpHandler(httpHandler);
            }

            return result;
        }
    }

    /**
     *
     * Returns the {@link HttpHandler} map.
     * Please note, the returned map is read-only.
     *
     * @return the {@link HttpHandler} map.
     * @deprecated please use {@link #getHttpHandlersMap()}
     */
    public Map<HttpHandler, String[]> getHttpHandlers() {
        final Map<HttpHandler, String[]> map = new HashMap<HttpHandler, String[]>(
                unmodifiableHandlers.size());
        
        for (Map.Entry<HttpHandler, HttpHandlerRegistration[]> entry : unmodifiableHandlers.entrySet()) {
            final HttpHandlerRegistration[] regs = entry.getValue();
            final String[] strRegs = new String[regs.length];
            
            for (int i = 0; i < regs.length; i++) {
                final String contextPath = regs[i].getContextPath();
                final String urlPattern = regs[i].getUrlPattern();
                
                if (contextPath == null) {
                    strRegs[i] = urlPattern;
                } else if (urlPattern == null) {
                    strRegs[i] = contextPath;
                } else if (contextPath.endsWith("/") && urlPattern.startsWith("/")) {
                    strRegs[i] = contextPath.substring(0, contextPath.length() - 1) + urlPattern;
                } else {
                    strRegs[i] = contextPath + urlPattern;
                }
            }
            
            map.put(entry.getKey(), strRegs);
        }
        
        return Collections.unmodifiableMap(map);
    }

    /**
     *
     * Returns the {@link HttpHandler} map.
     * Please note, the returned map is read-only.
     *
     * @return the {@link HttpHandler} map.
     */
    public Map<HttpHandler, HttpHandlerRegistration[]> getHttpHandlersWithMapping() {
        return unmodifiableHandlers;
    }
    
    /**
     * Get the web server monitoring config.
     * 
     * @return the web server monitoring config.
     */
    public HttpServerMonitoringConfig getMonitoringConfig() {
        return monitoringConfig;
    }


    /**
     * @return the logical name of this {@link HttpServer} instance.
     *  If no name is explicitly specified, the default value will
     *  be <code>HttpServer</code>.  If there is more than once
     *  {@link HttpServer} per virtual machine, the server name will
     *  be <code>HttpServer-[(instance count - 1)].
     */
    public String getName() {
        if (name == null) {
            if (!instance.isStarted()) {
                return null;
            } else {
                final int count = INSTANCE_COUNT.incrementAndGet();
                name = count == 0 ? "HttpServer" : "HttpServer-" + count;
            }
        }
        return name;
    }

    /**
     * Sets the logical name of this {@link HttpServer} instance.
     * The logical name cannot be changed after the server has been started.
     *
     * @param name server name
     */
    public void setName(String name) {
        if (!instance.isStarted()) {
            this.name = name;
        }
    }


    /**
     * @return <code>true</code> if <code>JMX</code> has been enabled for this
     *  {@link HttpServer}.  If <code>true</code> the {@link HttpServer}
     *  management object will be registered at the root of the JMX tree
     *  with the name of <code>[instance-name]</code> where instance name is
     *  the value returned by {@link #getName}.
     */
    public boolean isJmxEnabled() {
        return jmxEnabled;
    }


    /**
     * Enables <code>JMX</code> for this {@link HttpServer}.  This value
     * can be changed at runtime.
     *
     * @param jmxEnabled <code>true</code> to enable <code>JMX</code> otherwise
     *  <code>false</code>
     */
    public void setJmxEnabled(boolean jmxEnabled) {

        this.jmxEnabled = jmxEnabled;
        if (instance.isStarted()) {
            if (jmxEnabled) {
                instance.enableJMX();
                if (!jmxEventListeners.isEmpty()) {
                    for (final JmxEventListener l : jmxEventListeners) {
                        l.jmxEnabled();
                    }
                }
            } else {
                if (!jmxEventListeners.isEmpty()) {
                    for (final JmxEventListener l : jmxEventListeners) {
                        l.jmxDisabled();
                    }
                }
                instance.disableJMX();
            }
        }

    }


    /**
     * Add a {@link JmxEventListener} which will be notified when the
     * {@link HttpServer} is started and JMX was enabled prior to starting
     * or if the {@link HttpServer} was started with JMX disabled, but
     * JMX was enabled at a later point in time.
     *
     * @param listener the {@link JmxEventListener} to add.
     */
    public void addJmxEventListener(final JmxEventListener listener) {

        if (listener != null) {
            jmxEventListeners.add(listener);
        }

    }


    /**
     * Removes the specified {@link JmxEventListener}.
     *
     * @param listener the {@link JmxEventListener} to remove.
     */
    public void removeJmxEventListener(final JmxEventListener listener) {

        if (listener != null) {
            jmxEventListeners.remove(listener);
        }

    }


    /**
     * @return an {@link Iterator} of all registered {@link JmxEventListener}s.
     */
    public Set<JmxEventListener> getJmxEventListeners() {

        return jmxEventListeners;
        
    }
    
    /**
     * The flag, which enables/disables payload support for HTTP methods,
     * for which HTTP spec doesn't clearly state whether they support payload.
     * Known "undefined" methods are: GET, HEAD, DELETE.
     * 
     * @return <tt>true</tt> if "undefined" methods support payload, or <tt>false</tt> otherwise
     * @since 2.3.12
     */
    public boolean isAllowPayloadForUndefinedHttpMethods() {
        return allowPayloadForUndefinedHttpMethods;
    }

    /**
     * The flag, which enables/disables payload support for HTTP methods,
     * for which HTTP spec doesn't clearly state whether they support payload.
     * Known "undefined" methods are: GET, HEAD, DELETE.
     * 
     * @param allowPayloadForUndefinedHttpMethods <tt>true</tt> if "undefined" methods support payload, or <tt>false</tt> otherwise
     * @since 2.3.12
     */
    public void setAllowPayloadForUndefinedHttpMethods(boolean allowPayloadForUndefinedHttpMethods) {
        this.allowPayloadForUndefinedHttpMethods = allowPayloadForUndefinedHttpMethods;
    }
    
    /**
     * @return the maximum request payload remainder (in bytes) HttpServerFilter
     * will try to swallow after HTTP request processing is over in order to
     * keep the connection alive. If the remainder is too large - the connection
     * will be closed. <tt>-1</tt> means no limits will be applied.
     * 
     * @since 2.3.13
     */
    public long getMaxPayloadRemainderToSkip() {
        return maxPayloadRemainderToSkip;
    }

    /**
     * Set the maximum request payload remainder (in bytes) HttpServerFilter
     * will try to swallow after HTTP request processing is over in order to
     * keep the connection alive. If the remainder is too large - the connection
     * will be closed. <tt>-1</tt> means no limits will be applied.
     * 
     * @param maxPayloadRemainderToSkip
     * @since 2.3.13
     */
    public void setMaxPayloadRemainderToSkip(long maxPayloadRemainderToSkip) {
        this.maxPayloadRemainderToSkip = maxPayloadRemainderToSkip;
    }    
} // END ServerConfiguration
