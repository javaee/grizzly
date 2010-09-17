/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.server;


import com.sun.grizzly.Buffer;
import com.sun.grizzly.http.server.io.NIOOutputStream;
import com.sun.grizzly.http.server.jmx.JmxEventListener;
import com.sun.grizzly.http.server.util.HtmlHelper;
import com.sun.grizzly.http.util.HttpStatus;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration options for a particular {@link HttpServer} instance.
 */
public class ServerConfiguration {

    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(-1);

    /*
     * The directory from which static resources will be served from.
     */
    private String docRoot = null;


    // Non-exposed

    private Map<Adapter, String[]> adapters = new LinkedHashMap<Adapter, String[]>();

    private Set<JmxEventListener> jmxEventListeners = new CopyOnWriteArraySet<JmxEventListener>();

    private static final String[] ROOT_MAPPING = {"/"};

    private final HttpServerMonitoringConfig monitoringConfig = new HttpServerMonitoringConfig();

    private String name;

    private String httpServerName = "Grizzly";

    private String httpServerVersion = "2.0";

    private final HttpServer instance;

    private boolean jmxEnabled;
    
    // ------------------------------------------------------------ Constructors


    ServerConfiguration(HttpServer instance) {
        this.instance = instance;
    }


    // ---------------------------------------------------------- Public Methods


    public String getDocRoot() {
        return docRoot;
    }

    public void setDocRoot(String docRoot) {
        this.docRoot = docRoot;
    }


    /**
     * Adds the specified {@link Adapter}
     * with its associated mapping(s). Requests will be dispatched to a
     * {@link Adapter} based on these mapping
     * values.
     *
     * @param adapter a {@link Adapter}
     * @param mapping        context path mapping information.
     */
    public void addAdapter(Adapter adapter,
                                  String... mapping) {
        if (mapping == null) {
            mapping = ROOT_MAPPING;
        }

        adapters.put(adapter, mapping);
    }

    /**
     *
     * Removes the specified {@link Adapter}.
     *
     * @return <tt>true</tt>, if the operation was successful, otherwise
     *  <tt>false</tt>
     */
    public boolean removeAdapter(Adapter adapter) {
        return (adapters.remove(adapter) != null);
    }


    /**
     * @return the {@link Adapter} to be used by this server instance.
     *  This may be a single {@link Adapter} or a composite of multiple
     *  {@link Adapter} instances wrapped by a {@link AdapterChain}.
     */
    protected Adapter buildAdapter() {

        if (adapters.isEmpty()) {
            return new Adapter(docRoot) {
                @SuppressWarnings({"unchecked"})
                @Override
                public void service(Request request, Response response) {
                    try {
                        ByteBuffer b = HtmlHelper.getErrorPage("Not Found",
                                                               "Resource identified by path '" + request.getRequestURI() + "', does not exist.",
                                                               getHttpServerName() + '/' + getHttpServerVersion());
                        MemoryManager mm = request.getContext().getConnection().getTransport().getMemoryManager();
                        Buffer buf = MemoryUtils.wrap(mm, b);
                        NIOOutputStream out = response.getOutputStream();
                        response.setStatus(HttpStatus.NOT_FOUND_404);
                        response.setContentType("text/html");
                        response.setCharacterEncoding("UTF-8");
                        out.write(buf);
                        out.flush();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            };
        }

        final int adaptersNum = adapters.size();

        if (adaptersNum == 1) {
            Adapter adapter = adapters.keySet().iterator().next();
            if (adapter.getDocRoot() == null) {
                adapter.setDocRoot(docRoot);
            }
            
            return adapter;
        }

        AdapterChain adapterChain = new AdapterChain(instance);
        addJmxEventListener(adapterChain);
        adapterChain.setDocRoot(docRoot);

        for (Map.Entry<Adapter, String[]> adapterRecord : adapters.entrySet()) {
            final Adapter adapter = adapterRecord.getKey();
            final String[] mappings = adapterRecord.getValue();

            if (adapter.getDocRoot() == null) {
                adapter.setDocRoot(docRoot);
            }
            
            adapterChain.addAdapter(adapter, mappings);
        }

        return adapterChain;
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
                name = ((count == 0) ? "HttpServer" : "HttpServer-" + count);
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
    public Iterator<JmxEventListener> getJmxEventListeners() {

        return jmxEventListeners.iterator();
        
    }


    /**
     * @return the server name used for headers and default error pages.
     */
    public String getHttpServerName() {

        return httpServerName;

    }


    /**
     * Sets the server name used for HTTP response headers and default generated
     * error pages.  If not value is explicitly set, this value defaults to
     * <code>Grizzly</code>.
     *
     * @param httpServerName server name
     */
    public void setHttpServerName(String httpServerName) {
        this.httpServerName = httpServerName;
    }


    /**
     * @return the version of this server used for headers and default error
     *  pages.
     */
    public String getHttpServerVersion() {

        return httpServerVersion;

    }


    /**
     * Sets the version of the server info sent in HTTP response headers and the
     *  default generated error pages.  If not value is explicitly set, this
     *  value defaults to the current version of the Grizzly runtime.
     *
     * @param httpServerVersion server version
     */
    public void setHttpServerVersion(String httpServerVersion) {

        this.httpServerVersion = httpServerVersion;

    }
    
} // END ServerConfiguration
