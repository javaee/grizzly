/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.tcp.http11;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.http.DispatcherHelper;
import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.buf.UDecoder;
import com.sun.grizzly.util.http.HttpRequestURIDecoder;
import com.sun.grizzly.util.http.mapper.Mapper;
import com.sun.grizzly.util.http.mapper.MappingData;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The GrizzlyAdapterChain class allow the invokation of multiple {@link GrizzlyAdapter}
 * every time a new HTTP requests is ready to be handled. Requests are mapped
 * to their associated {@link GrizzlyAdapter} at runtime using the mapping
 * information configured when invoking the {@link GrizzlyAdapterChain#addGrizzlyAdapter
 * (com.sun.grizzly.tcp.http11.GrizzlyAdapter, java.lang.String[])
 *
 * Below is a simple example using two {@link Servlet}
 * <pre><code>
 *
GrizzlyWebServer ws = new GrizzlyWebServer(path);
ServletAdapter sa = new ServletAdapter();
sa.setRootFolder(".");
sa.setServletInstance(new ServletTest("Adapter-1"));
ws.addGrizzlyAdapter(sa, new String[]{"/Adapter-1"});
ServletAdapter sa2 = new ServletAdapter();
sa2.setRootFolder("/tmp");
sa2.setServletInstance(new ServletTest("Adapter-2"));
ws.addGrizzlyAdapter(sa2, new String[]{"/Adapter-2"});
System.out.println("Grizzly WebServer listening on port 8080");
ws.start();
 * 
 * </code></pre>
 * 
 * Note: This class is <strong>NOT</strong> thread-safe, so make sure you synchronize
 *       when dynamically adding and removing {@link GrizzlyAdapter}
 *  
 * @author Jeanfrancois Arcand
 */
public class GrizzlyAdapterChain extends GrizzlyAdapter {

    private UDecoder urlDecoder = new UDecoder();
    protected final static int MAPPING_DATA = 12;
    protected final static int MAPPED_ADAPTER = 13;
    protected final static int INVOKED_ADAPTER = 15;

    /**
     * The name -> {@link GrizzlyAdapter} map.
     */
    private final ConcurrentHashMap<String, GrizzlyAdapter> adaptersByName =
            new ConcurrentHashMap<String, GrizzlyAdapter>();
    
    /**
     * The list of {@link GrizzlyAdapter} instance.
     */
    private ConcurrentHashMap<GrizzlyAdapter, String[]> adapters =
            new ConcurrentHashMap<GrizzlyAdapter, String[]>();
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
     * Is the root context configured?
     */
    private boolean isRootConfigured = false;

    /**
     * DispatcherHelper, which maps path or name to the Mapper entry.
     */
    private final DispatcherHelper dispatcherHelper;

    public GrizzlyAdapterChain() {
        mapper.setDefaultHostName(LOCAL_HOST);
        setHandleStaticResources(false);
        dispatcherHelper = new DispatcherHelperImpl();
        // We will decode it
        setDecodeUrl(false);
    }

    @Override
    public void start() {
        for (Entry<GrizzlyAdapter, String[]> entry : adapters.entrySet()) {
            entry.getKey().start();
        }
    }

    /**
     * Map the {@link GrizzlyRequest} to the proper {@link GrizzlyAdapter}
     * @param request The {@link GrizzlyRequest}
     * @param response The {@link GrizzlyResponse}
     */
    @Override
    public void service(GrizzlyRequest request, GrizzlyResponse response)
            throws Exception {
        // For backward compatibility.
        final Request req = request.getRequest();
        req.setNote(INVOKED_ADAPTER, null);
        
        if (oldMappingAlgorithm) {
            int i = 0;
            int size = adapters.size();
            for (Entry<GrizzlyAdapter, String[]> entry : adapters.entrySet()) {
                final GrizzlyAdapter adapter = entry.getKey();
                adapter.service(request, response);
                if (response.getStatus() == 404 && i != size - 1) {
                    // Reset the
                    response.setStatus(200, "OK");
                } else {
                    req.setNote(INVOKED_ADAPTER, adapter);
                    return;
                }
            }
        } else {
            MappingData mappingData = null;
            try {
                MessageBytes decodedURI = req.decodedURI();
                decodedURI.duplicate(req.requestURI());
                mappingData = (MappingData) req.getNote(MAPPING_DATA);
                if (mappingData == null) {
                    mappingData = new MappingData();
                    req.setNote(MAPPING_DATA, mappingData);
                } else {
                    mappingData.recycle();
                }

                HttpRequestURIDecoder.decode(decodedURI, urlDecoder, null, null);
                if (mappingData == null) {
                    mappingData = (MappingData) req.getNote(MAPPING_DATA);
                }

                // Map the request without any trailling.
                // Map the request to its Adapter
                mapUriWithSemicolon(req.serverName(), decodedURI, 0, mappingData);

                GrizzlyAdapter adapter = null;
                if (mappingData.context != null && mappingData.context instanceof GrizzlyAdapter) {
                    if (mappingData.wrapper != null) {
                        adapter = (GrizzlyAdapter) mappingData.wrapper;
                    } else {
                        adapter = (GrizzlyAdapter) mappingData.context;
                    }
                    // We already decoded the URL.
                    adapter.setDecodeUrl(false);
                    adapter.service(request.getRequest(), response.getResponse());
                    req.setNote(INVOKED_ADAPTER, adapter);
                } else {
                    response.getResponse().setStatus(404);
                    customizedErrorPage(req, response.getResponse());
                }
            } catch (Throwable t) {
                try {
                    response.setStatus(404);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Invalid URL: " + req.decodedURI(), t);
                    }
                    customizedErrorPage(req, response.getResponse());
                } catch (Exception ex2) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.log(Level.WARNING, "Unable to error page", ex2);
                    }
                }
            }
        }
    }

    @Override
    public void afterService(GrizzlyRequest request,
            GrizzlyResponse response) throws Exception {
        final Request req = request.getRequest();
        final GrizzlyAdapter adapter = (GrizzlyAdapter) req.getNote(INVOKED_ADAPTER);
        if (adapter != null) {
            adapter.afterService(request, response);
        }
    }


    /**
     * Add a {@link GrizzlyAdapter} to the chain.
     * @param {@link GrizzlyAdapter} to the chain.
     * @deprecated - uses {@link GrizzlyAdapterChain#addGrizzlyAdapter(com.sun.grizzly.tcp.http11.GrizzlyAdapter, java.lang.String[])}
     */
    public void addGrizzlyAdapter(GrizzlyAdapter adapter) {
        oldMappingAlgorithm = true;
        adapters.put(adapter, new String[]{""});
    }

    /**
     * Add a {@link GrizzlyAdapter} and its assciated array of mapping. The mapping
     * data will be used to map incoming request to its associated {@link GrizzlyAdapter}.
     * @param adapter {@link GrizzlyAdapter} instance
     * @param mappings an array of mapping.
     */
    public void addGrizzlyAdapter(GrizzlyAdapter adapter, String[] mappings) {
        if (oldMappingAlgorithm) {
            throw new IllegalStateException("Cannot mix addGrizzlyAdapter(GrizzlyAdapter) " +
                    "and addGrizzlyAdapter(GrizzlyAdapter,String[]");
        }

        if (mappings.length == 0) {
            addGrizzlyAdapter(adapter);
        } else {
            adapters.put(adapter, mappings);
            final String name = adapter.getName();
            if (name != null) {
                adaptersByName.put(adapter.getName(), adapter);
            }
            for (String mapping : mappings) {
                String ctx = getContextPath(mapping);
                String wrapper = getWrapperPath(ctx, mapping);
                if (!ctx.equals("")){
                    mapper.addContext(LOCAL_HOST, ctx, adapter,
                            new String[]{"index.html", "index.htm"}, null);
                } else {
                    if (!isRootConfigured && wrapper.startsWith("*.")){
                        isRootConfigured = true;
                        GrizzlyAdapter a = new GrizzlyAdapter(getRootFolder()){
                            {
                                setHandleStaticResources(false);
                            }

                            @Override
                            public void service(GrizzlyRequest request, GrizzlyResponse response) {
                                try {
                                    customizedErrorPage(request.getRequest(), response.getResponse());
                                } catch (Exception ex) {
                                }
                            }
                        };
                        mapper.addContext(LOCAL_HOST, ctx, a,
                                new String[]{"index.html", "index.htm"}, null);            
                    } else {              
                        mapper.addContext(LOCAL_HOST, ctx, adapter,
                            new String[]{"index.html", "index.htm"}, null);
                    }      
                }
                mapper.addWrapper(LOCAL_HOST,ctx, wrapper , adapter);
                adapter.setDispatcherHelper(dispatcherHelper);
            }
        }
    }

    private String getWrapperPath(String ctx, String mapping) {

        if (mapping.indexOf("*.") > 0) {
            return mapping.substring(mapping.lastIndexOf("/") + 1);
        } else if (!ctx.equals("")) {
            return mapping.substring(ctx.length());
        } else {
            return mapping;
        }
    }

    private String getContextPath(String mapping) {
        String ctx = "";
        int slash = mapping.indexOf("/", 1);
        if (slash != -1) {
            ctx = mapping.substring(0, slash);
        } else {
            ctx = mapping;
        }

        if (ctx.startsWith("/*.") ||ctx.startsWith("*.") ) {
            if (ctx.indexOf("/") == ctx.lastIndexOf("/")){
                ctx = "";
            } else {
                ctx = ctx.substring(1);
            }
        }
        
        
        if (ctx.startsWith("/*") || ctx.startsWith("*")) {
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
            adapter.getKey().destroy();
        }
    }

    /**
     * Remove a {@link GrizzlyAdapter}
     * @return <tt>true</tt> if removed
     */
    public boolean removeAdapter(GrizzlyAdapter adapter) {
        if (adapter == null) {
            throw new IllegalStateException();
        }

        final String name = adapter.getName();
        if (name != null) {
            adaptersByName.remove(name);
        }
        String[] mappings = adapters.remove(adapter);
        if (mappings != null) {
            for (String mapping : mappings) {
                String ctx = getContextPath(mapping);
                mapper.removeContext(LOCAL_HOST, ctx);
            }
            adapter.destroy();
        }

        return (mappings != null);
    }

    /**
     * Maps the decodedURI to the corresponding Adapter, considering that URI
     * may have a semicolon with extra data followed, which shouldn't be a part
     * of mapping process.
     *
     * @param serverName server name per the Host header.
     * @param decodedURI URI
     * @param semicolonPos semicolon position. Might be <tt>0</tt> if position
     *  wasn't resolved yet (so it will be resolved in the method), or
     *  <tt>-1</tt> if there is no semicolon in the URI.
     * @param mappingData mapping data for this request
     *
     * @throws Exception if an error occurs mapping the request
     */
    private void mapUriWithSemicolon(final MessageBytes serverName,
            final MessageBytes decodedURI, int semicolonPos,
            final MappingData mappingData) throws Exception {

        final CharChunk charChunk = decodedURI.getCharChunk();
        final int oldEnd = charChunk.getEnd();

        if (semicolonPos == 0) {
            semicolonPos = decodedURI.indexOf(';');
        }

        if (semicolonPos == -1) {
            semicolonPos = oldEnd;
        }

        charChunk.setEnd(semicolonPos);

        try {
            mapper.map(serverName, decodedURI, mappingData);
        } finally {
            charChunk.setEnd(oldEnd);
        }
    }

    private final class DispatcherHelperImpl implements DispatcherHelper {

        public void mapPath(final MessageBytes host, final MessageBytes path,
                final MappingData mappingData) throws Exception {

            mapper.map(host, path, mappingData);
        }

        public void mapName(final MessageBytes name, final MappingData mappingData) {
            final String nameStr = name.toString();

            final GrizzlyAdapter handler = adaptersByName.get(nameStr);
            if (handler != null) {
                mappingData.wrapper = handler;
                mappingData.servletName = nameStr;
            }
        }
    }
}
