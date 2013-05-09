/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.http.server.jmxbase.JmxEventListener;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration options for a particular {@link HttpServer} instance.
 */
public class ServerConfiguration extends ServerFilterConfiguration {

    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(-1);

    private static final String[] ROOT_MAPPING = {"/"};

    // Non-exposed

    final Map<HttpHandler, String[]> handlers =
            new ConcurrentHashMap<HttpHandler, String[]>();
    private final Map<HttpHandler, String[]> unmodifiableHandlers =
            Collections.unmodifiableMap(handlers);
    final List<HttpHandler> orderedHandlers =
            new LinkedList<HttpHandler>();

    private Set<JmxEventListener> jmxEventListeners = new CopyOnWriteArraySet<JmxEventListener>();

    private final HttpServerMonitoringConfig monitoringConfig = new HttpServerMonitoringConfig();

    private String name;

    final HttpServer instance;

    private boolean jmxEnabled;

    final Object handlersSync = new Object();
    
    // ------------------------------------------------------------ Constructors


    ServerConfiguration(HttpServer instance) {
        this.instance = instance;
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * Adds the specified {@link HttpHandler}
     * with its associated mapping(s). Requests will be dispatched to a
     * {@link HttpHandler} based on these mapping
     * values.
     *
     * @param httpHandler a {@link HttpHandler}
     * @param mapping        context path mapping information.
     */
    public void addHttpHandler(final HttpHandler httpHandler, String... mapping) {
        synchronized (handlersSync) {
            if (mapping == null) {
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
     */
    public Map<HttpHandler, String[]> getHttpHandlers() {
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

} // END ServerConfiguration
