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
 * [mapping of copyright owner]"
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
package com.sun.grizzly.http.server;

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.http.server.jmx.JmxEventListener;
import com.sun.grizzly.http.server.jmx.Monitorable;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.RequestURIRef;
import com.sun.grizzly.http.util.UDecoder;
import com.sun.grizzly.http.util.MessageBytes;
import com.sun.grizzly.http.server.util.Mapper;
import com.sun.grizzly.http.server.util.MappingData;
import com.sun.grizzly.monitoring.jmx.JmxObject;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The GrizzlyAdapterChain class allows the invocation of multiple {@link GrizzlyAdapter}s
 * every time a new HTTP request is ready to be handled. Requests are mapped
 * to their associated {@link GrizzlyAdapter} at runtime using the mapping
 * information configured when invoking the {@link com.sun.grizzly.http.server.GrizzlyAdapterChain#addGrizzlyAdapter
 * (com.sun.grizzly.http.server.GrizzlyAdapter, java.lang.String[])}
 *
 *
 * Note: This class is <strong>NOT</strong> thread-safe, so make sure synchronization
 *  is performed when dynamically adding and removing {@link GrizzlyAdapter}
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzlyAdapterChain extends GrizzlyAdapter implements JmxEventListener {
    private static final Logger logger = Grizzly.logger(GrizzlyAdapterChain.class);

    private UDecoder urlDecoder = new UDecoder();
    protected final static int MAPPING_DATA = 12;
    protected final static int MAPPED_ADAPTER = 13;
    /**
     * The list of {@link GrizzlyAdapter} instance.
     */
    private ConcurrentHashMap<GrizzlyAdapter, String[]> adapters =
            new ConcurrentHashMap<GrizzlyAdapter, String[]>();
    private ConcurrentHashMap<GrizzlyAdapter, JmxObject> monitors =
            new ConcurrentHashMap<GrizzlyAdapter, JmxObject>();
    /**
     * Internal {@link Mapper} used to Map request to their associated {@link GrizzlyAdapter}
     */
    private Mapper mapper = new Mapper();
    /**
     * The default host.
     */
    private final static String LOCAL_HOST = "localhost";
    /**
     * Use the deprecated mechanism.
     */
    private boolean oldMappingAlgorithm = false;

    /**
     * Flag indicating this GrizzlyAdapter has been started.  Any subsequent
     * GrizzlyAdapter instances added to this chain after is has been started
     * will have their start() method invoked.
     */
    private boolean started;

    private final GrizzlyWebServer gws;


    // ------------------------------------------------------------ Constructors


    public GrizzlyAdapterChain(final GrizzlyWebServer gws) {
        this.gws = gws;
        mapper.setDefaultHostName(LOCAL_HOST);
        // We will decode it
        setDecodeUrl(false);
    }


    // ------------------------------------------- Methods from JmxEventListener

    @Override
    public void jmxEnabled() {
        for (Entry<GrizzlyAdapter,String[]> entry : adapters.entrySet()) {
            final GrizzlyAdapter adapter = entry.getKey();
            if (adapter instanceof Monitorable) {
                registerJmxForAdapter(adapter);
            }
        }
    }

    @Override
    public void jmxDisabled() {
        for (Entry<GrizzlyAdapter,String[]> entry : adapters.entrySet()) {
            final GrizzlyAdapter adapter = entry.getKey();
            if (adapter instanceof Monitorable) {
                deregisterJmxForAdapter(adapter);
            }
        }
    }


    // ---------------------------------------------------------- Public Methods


    @Override
    public void start() {
        for (Entry<GrizzlyAdapter, String[]> entry : adapters.entrySet()) {
            final GrizzlyAdapter adapter = entry.getKey();
            adapter.start();
        }
        started = true;
    }

    /**
     * Map the {@link GrizzlyRequest} to the proper {@link GrizzlyAdapter}
     * @param request The {@link GrizzlyRequest}
     * @param response The {@link GrizzlyResponse}
     */
    @Override
    public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
        // For backward compatibility.
        if (oldMappingAlgorithm) {
            int i = 0;
            int size = adapters.size();
            for (Entry<GrizzlyAdapter, String[]> entry : adapters.entrySet()) {
                entry.getKey().doService(request, response);
                if (response.getStatus() == 404 && i != size - 1) {
                    // Reset the
                    response.setStatus(200, "OK");
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

                // Map the request without any trailing.
                //ByteChunk uriBB = decodedURI.getByteChunk();
                int semicolon = decodedURI.indexOf(';', 0);
                if (semicolon > 0) {
                    decodedURI.setBuffer(decodedURI.getBuffer(), decodedURI.getStart(), semicolon);
                }

                //HttpRequestURIDecoder.decode(decodedURI,urlDecoder,null,null);
                if (mappingData == null) {
                    mappingData = (MappingData) request.getNote("MAPPING_DATA");
                }

                // TODO Mapper needs to be changes to not rely on MessageBytes/
                //   CharChunk/ByteChunk, etc.
                // Map the request to its Adapter
                MessageBytes serverName = MessageBytes.newInstance();
                serverName.setString(request.getServerName());
                MessageBytes decURI = MessageBytes.newInstance();
                decURI.setString(decodedURI.toString());
                mapper.map(serverName, decURI, mappingData);

                GrizzlyAdapter adapter;
                if (mappingData.context != null && mappingData.context instanceof GrizzlyAdapter) {
                    if (mappingData.wrapper != null) {
                        adapter = (GrizzlyAdapter) mappingData.wrapper;
                    } else {
                        adapter = (GrizzlyAdapter) mappingData.context;
                    }
                    // We already decoded the URL.
                    adapter.setDecodeUrl(false);
                    adapter.doService(request, response);
                } else {
                    response.getResponse().setStatus(404);
                    customizedErrorPage(request, response);
                }
            } catch (Throwable t) {
                try {
                    response.setStatus(404);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Invalid URL: " + request.getRequestURI(), t);
                    }
                    customizedErrorPage(request, response);
                } catch (Exception ex2) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Unable to error page", ex2);
                    }
                }
            }
        }
    }


    /**
     * Add a {@link GrizzlyAdapter} and its associated array of mapping. The mapping
     * data will be used to map incoming request to its associated {@link GrizzlyAdapter}.
     * @param adapter {@link GrizzlyAdapter} instance
     * @param mappings an array of mapping.
     */
    public void addGrizzlyAdapter(GrizzlyAdapter adapter, String[] mappings) {
        if (oldMappingAlgorithm) {
            throw new IllegalStateException("Cannot mix addGrizzlyAdapter(GrizzlyAdapter) "
                    + "and addGrizzlyAdapter(GrizzlyAdapter,String[]");
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

    private void registerJmxForAdapter(final GrizzlyAdapter adapter) {
        final Monitorable monitorable = (Monitorable) adapter;
        final JmxObject jmx = monitorable.createManagementObject();
        monitors.putIfAbsent(adapter, jmx);
        gws.jmxManager.register(gws.managementObject, jmx, jmx.getJmxName());
    }

    private void deregisterJmxForAdapter(final GrizzlyAdapter adapter) {

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
        for (Entry<GrizzlyAdapter, String[]> adapter : adapters.entrySet()) {
            final GrizzlyAdapter a = adapter.getKey();
            a.destroy();
        }
        started = false;
    }

    /**
     * Remove a {@link GrizzlyAdapter}
     * @return <tt>true</tt> if removed
     */
    public boolean removeAdapter(GrizzlyAdapter adapter) {
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
}
