/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.http.server.jmx.JmxEventListener;
import com.sun.grizzly.http.server.jmx.Monitorable;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.HttpStatus;
import com.sun.grizzly.http.util.RequestURIRef;
import com.sun.grizzly.http.server.util.Mapper;
import com.sun.grizzly.http.server.util.MappingData;
import com.sun.grizzly.monitoring.jmx.JmxObject;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The AdapterChain class allows the invocation of multiple {@link Adapter}s
 * every time a new HTTP request is ready to be handled. Requests are mapped
 * to their associated {@link Adapter} at runtime using the mapping
 * information configured when invoking the {@link AdapterChain#addGrizzlyAdapter
 * (com.sun.grizzly.http.server.Adapter, java.lang.String[])}
 *
 *
 * Note: This class is <strong>NOT</strong> thread-safe, so make sure synchronization
 *  is performed when dynamically adding and removing {@link Adapter}
 *
 * @author Jeanfrancois Arcand
 */
public class AdapterChain extends Adapter implements JmxEventListener {
    private static final Logger logger = Grizzly.logger(AdapterChain.class);

    protected final static int MAPPING_DATA = 12;
    protected final static int MAPPED_ADAPTER = 13;
    /**
     * The list of {@link Adapter} instance.
     */
    private ConcurrentHashMap<Adapter, String[]> adapters =
            new ConcurrentHashMap<Adapter, String[]>();
    private ConcurrentHashMap<Adapter, JmxObject> monitors =
            new ConcurrentHashMap<Adapter, JmxObject>();
    /**
     * Internal {@link Mapper} used to Map request to their associated {@link Adapter}
     */
    private Mapper mapper;
    /**
     * The default host.
     */
    private final static String LOCAL_HOST = "localhost";
    /**
     * Use the deprecated mechanism.
     */
    private boolean oldMappingAlgorithm = false;

    /**
     * Flag indicating this Adapter has been started.  Any subsequent
     * Adapter instances added to this chain after is has been started
     * will have their start() method invoked.
     */
    private boolean started;

    private final HttpServer gws;


    // ------------------------------------------------------------ Constructors


    public AdapterChain(final HttpServer gws) {
        this.gws = gws;
        mapper = new Mapper(TransportFactory.getInstance().getDefaultMemoryManager());
        mapper.setDefaultHostName(LOCAL_HOST);
        // We will decode it
        setDecodeUrl(false);
    }


    // ------------------------------------------- Methods from JmxEventListener

    @Override
    public void jmxEnabled() {
        for (Entry<Adapter,String[]> entry : adapters.entrySet()) {
            final Adapter adapter = entry.getKey();
            if (adapter instanceof Monitorable) {
                registerJmxForAdapter(adapter);
            }
        }
    }

    @Override
    public void jmxDisabled() {
        for (Entry<Adapter,String[]> entry : adapters.entrySet()) {
            final Adapter adapter = entry.getKey();
            if (adapter instanceof Monitorable) {
                deregisterJmxForAdapter(adapter);
            }
        }
    }


    // ---------------------------------------------------------- Public Methods


    @Override
    public void start() {
        for (Entry<Adapter, String[]> entry : adapters.entrySet()) {
            final Adapter adapter = entry.getKey();
            adapter.start();
        }
        started = true;
    }

    /**
     * Map the {@link Request} to the proper {@link Adapter}
     * @param request The {@link Request}
     * @param response The {@link Response}
     */
    @Override
    public void service(Request request, Response response) throws Exception {
        // For backward compatibility.
        if (oldMappingAlgorithm) {
            int i = 0;
            int size = adapters.size();
            for (Entry<Adapter, String[]> entry : adapters.entrySet()) {
                entry.getKey().doService(request, response);
                if (response.getStatus() == 404 && i != size - 1) {
                    // Reset the
                    response.setStatus(HttpStatus.OK_200);
                } else {
                    return;
                }
                
                i++;
            }
        } else {
            //Request req = request.getRequest();
            MappingData mappingData;
            try {
                RequestURIRef uriRef = request.getRequest().getRequestURIRef();
                BufferChunk decodedURI = uriRef.getDecodedRequestURIBC();
                //MessageBytes decodedURI = req.decodedURI();
                //decodedURI.duplicate(req.requestURI());
                // TODO: cleanup notes (int version/string version)
                mappingData = (MappingData) request.getNote("MAPPING_DATA");
                if (mappingData == null) {
                    mappingData = new MappingData();
                    request.setNote("MAPPING_DATA", mappingData);
                } else {
                    mappingData.recycle();
                }

                if (mappingData == null) {
                    mappingData = (MappingData) request.getNote("MAPPING_DATA");
                }

                mapUriWithSemicolon(request.getRequest().serverName(),
                                    decodedURI,
                                    0,
                                    mappingData);


                Adapter adapter;
                if (mappingData.context != null && mappingData.context instanceof Adapter) {
                    if (mappingData.wrapper != null) {
                        adapter = (Adapter) mappingData.wrapper;
                    } else {
                        adapter = (Adapter) mappingData.context;
                    }
                    // We already decoded the URL.
                    adapter.setDecodeUrl(false);
                    adapter.doService(request, response);
                } else {
                    response.setStatus(HttpStatus.NOT_FOUND_404);
                    customizedErrorPage(gws, request, response);
                }
            } catch (Throwable t) {
                try {
                    response.setStatus(HttpStatus.NOT_FOUND_404);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Invalid URL: " + request.getRequestURI(), t);
                    }
                    customizedErrorPage(gws, request, response);
                } catch (Exception ex2) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Unable to error page", ex2);
                    }
                }
            }
        }
    }


    /**
     * Add a {@link Adapter} and its associated array of mapping. The mapping
     * data will be used to map incoming request to its associated {@link Adapter}.
     * @param adapter {@link Adapter} instance
     * @param mappings an array of mapping.
     */
    public void addGrizzlyAdapter(Adapter adapter, String[] mappings) {
        if (oldMappingAlgorithm) {
            throw new IllegalStateException("Cannot mix addGrizzlyAdapter(Adapter) "
                    + "and addGrizzlyAdapter(Adapter,String[]");
        }

        if (mappings.length == 0) {
            addGrizzlyAdapter(adapter, new String[]{""});
        } else {
            if (started) {
                adapter.start();
                if (adapter instanceof Monitorable) {
                    registerJmxForAdapter(adapter);
                }
            }
            adapters.put(adapter, mappings);
            for (String mapping : mappings) {
                String ctx = getContextPath(mapping);
                mapper.addContext(LOCAL_HOST, ctx, adapter,
                        new String[]{"index.html", "index.htm"}, null);
                mapper.addWrapper(LOCAL_HOST, ctx, mapping.substring(ctx.length()), adapter);
            }
        }

    }

    private void registerJmxForAdapter(final Adapter adapter) {
        final Monitorable monitorable = (Monitorable) adapter;
        final JmxObject jmx = monitorable.createManagementObject();
        monitors.putIfAbsent(adapter, jmx);
        gws.jmxManager.register(gws.managementObject, jmx, jmx.getJmxName());
    }

    private void deregisterJmxForAdapter(final Adapter adapter) {

        JmxObject jmx = monitors.get(adapter);
        if (jmx != null) {
            gws.jmxManager.unregister(jmx);
        }

    }

    private String getContextPath(String mapping) {
        String ctx;
        int slash = mapping.indexOf("/", 1);
        if (slash != -1) {
            ctx = mapping.substring(0, slash);
        } else {
            ctx = mapping;
        }

        if (ctx.startsWith("/*")) {
            ctx = "";
        }

        // Special case for the root context
        if (ctx.equals("/")) {
            ctx = "";
        }
        return ctx;
    }

    @Override
    public void destroy() {
        for (Entry<Adapter, String[]> adapter : adapters.entrySet()) {
            final Adapter a = adapter.getKey();
            a.destroy();
        }
        started = false;
    }

    /**
     * Remove a {@link Adapter}
     * @return <tt>true</tt> if removed
     */
    public boolean removeAdapter(Adapter adapter) {
        if (adapter == null) {
            throw new IllegalStateException();
        }
        String[] mappings = adapters.remove(adapter);
        if (mappings != null) {
            for (String mapping : mappings) {
                String ctx = getContextPath(mapping);
                mapper.removeContext(LOCAL_HOST, ctx);
            }
            deregisterJmxForAdapter(adapter);
            adapter.destroy();

        }

        return (mappings != null);
    }


    /**
     * Maps the decodedURI to the corresponding Adapter, considering that URI
     * may have a semicolon with extra data followed, which shouldn't be a part
     * of mapping process.
     *
     * @param serverName the server name as described by the Host header.
     * @param decodedURI decoded URI
     * @param semicolonPos semicolon position. Might be <tt>0</tt> if position
     *  wasn't resolved yet (so it will be resolved in the method),
     *  or <tt>-1</tt> if there is no semicolon in the URI.
     * @param mappingData {@link MappingData} based on the URI.
     *
     * @throws Exception if an error occurs mapping the request
     */
    private void mapUriWithSemicolon(final BufferChunk serverName,
                                     final BufferChunk decodedURI,
                                     int semicolonPos,
                                     final MappingData mappingData)
    throws Exception {

        final int oldEnd = decodedURI.getEnd();

        if (semicolonPos == 0) {
            semicolonPos = decodedURI.indexOf(';', 0);
        }

        if (semicolonPos == -1) {
            semicolonPos = oldEnd;
        }

        decodedURI.setEnd(semicolonPos);

        try {
            mapper.map(serverName, decodedURI, mappingData);
        } finally {
            decodedURI.setEnd(oldEnd);
        }
    }

}
