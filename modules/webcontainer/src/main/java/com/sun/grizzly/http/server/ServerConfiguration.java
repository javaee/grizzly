/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.http.server;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TODO: Documentation
 */
public class ServerConfiguration {

    private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger(-1);

    /*
     * The directory from which static resources will be served from.
     */
    private String docRoot = null;


    // Non-exposed

    private Map<GrizzlyAdapter, String[]> adapters = new LinkedHashMap<GrizzlyAdapter, String[]>();

    private static final String[] ROOT_MAPPING = {"/"};

    private final WebServerMonitoringConfig monitoringConfig = new WebServerMonitoringConfig();

    private String name;

    private final GrizzlyWebServer instance;

    private boolean jmxEnabled;
    
    // ------------------------------------------------------------ Constructors


    ServerConfiguration(GrizzlyWebServer instance) {
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
     * Adds the specified {@link com.sun.grizzly.http.server.GrizzlyAdapter}
     * with its associated mapping(s). Requests will be dispatched to a
     * {@link com.sun.grizzly.http.server.GrizzlyAdapter} based on these mapping
     * values.
     *
     * @param grizzlyAdapter a {@link com.sun.grizzly.http.server.GrizzlyAdapter}
     * @param mapping        context path mapping information.
     */
    public void addGrizzlyAdapter(GrizzlyAdapter grizzlyAdapter,
                                  String... mapping) {
        if (mapping == null) {
            mapping = ROOT_MAPPING;
        }

        adapters.put(grizzlyAdapter, mapping);
    }

    /**
     *
     * Removes the specified {@link com.sun.grizzly.http.server.GrizzlyAdapter}.
     *
     * @return <tt>true</tt>, if the operation was successful, otherwise
     *  <tt>false</tt>
     */
    public boolean removeGrizzlyAdapter(GrizzlyAdapter grizzlyAdapter) {
        return (adapters.remove(grizzlyAdapter) != null);
    }


    /**
     * @return the {@link GrizzlyAdapter} to be used by this server instance.
     *  This may be a single {@link GrizzlyAdapter} or a composite of multiple
     *  {@link GrizzlyAdapter} instances wrapped by a {@link GrizzlyAdapterChain}.
     */
    protected GrizzlyAdapter buildAdapter() {

        if (adapters.isEmpty()) {
            return new GrizzlyAdapter(docRoot) {
                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) {
                }
            };
        }

        final int adaptersNum = adapters.size();

        if (adaptersNum == 1) {
            GrizzlyAdapter adapter = adapters.keySet().iterator().next();
            if (adapter.getDocRoot() == null) {
                adapter.setDocRoot(docRoot);
            }
            
            return adapter;
        }

        GrizzlyAdapterChain adapterChain = new GrizzlyAdapterChain();
        adapterChain.setDocRoot(docRoot);

        for (Map.Entry<GrizzlyAdapter, String[]> adapterRecord : adapters.entrySet()) {
            final GrizzlyAdapter adapter = adapterRecord.getKey();
            final String[] mappings = adapterRecord.getValue();

            if (adapter.getDocRoot() == null) {
                adapter.setDocRoot(docRoot);
            }
            
            adapterChain.addGrizzlyAdapter(adapter, mappings);
        }

        return adapterChain;
    }

    /**
     * Get the web server monitoring config.
     * 
     * @return the web server monitoring config.
     */
    public WebServerMonitoringConfig getMonitoringConfig() {
        return monitoringConfig;
    }


    /**
     * @return the logical name of this {@link GrizzlyWebServer} instance.
     *  If no name is explicitly specified, the default value will
     *  be <code>GrizzlyWebServer</code>.  If there is more than once
     *  {@link GrizzlyWebServer} per virtual machine, the server name will
     *  be <code>GrizzlyWebServer-[(instance count - 1)].
     */
    public String getName() {
        if (name == null) {
            if (!instance.isStarted()) {
                return null;
            } else {
                final int count = INSTANCE_COUNT.incrementAndGet();
                name = ((count == 0) ? "GrizzlyWebServer" : "GrizzlyWebServer-" + count);
            }
        }
        return name;
    }

    /**
     * Sets the logical name of this {@link GrizzlyWebServer} instance.
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
     *  {@link GrizzlyWebServer}.  If <code>true</code> the {@link GrizzlyWebServer}
     *  management object will be registered at the root of the JMX tree
     *  with the name of <code>[instance-name]</code> where instance name is
     *  the value returned by {@link #getName}.
     */
    public boolean isJmxEnabled() {
        return jmxEnabled;
    }


    /**
     * Enables <code>JMX</code> for this {@link GrizzlyWebServer}.  This value
     * can be changed at runtime.
     *
     * @param jmxEnabled <code>true</code> to enable <code>JMX</code> otherwise
     *  <code>false</code>
     */
    public void setJmxEnabled(boolean jmxEnabled) {

        if (instance.isStarted()) {
            if (jmxEnabled) {
                instance.enableJMX();
            } else {
                instance.disableJMX();
            }
        }
        this.jmxEnabled = jmxEnabled;

    }
    
} // END ServerConfiguration
