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
package org.glassfish.grizzly.http.server;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.jmx.JmxEventListener;
import org.glassfish.grizzly.http.server.jmx.Monitorable;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.RequestURIRef;
import org.glassfish.grizzly.http.server.util.Mapper;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.http.server.Request.Note;
import org.glassfish.grizzly.http.util.CharChunk;

/**
 * The HttpServiceChain class allows the invocation of multiple {@link HttpRequestProcessor}s
 * every time a new HTTP request is ready to be handled. Requests are mapped
 * to their associated {@link HttpRequestProcessor} at runtime using the mapping
 * information configured when invoking the {@link HttpServiceChain#addService
 * (org.glassfish.grizzly.http.server.HttpService, java.lang.String[])}
 *
 *
 * Note: This class is <strong>NOT</strong> thread-safe, so make sure synchronization
 *  is performed when dynamically adding and removing {@link HttpRequestProcessor}
 *
 * @author Jeanfrancois Arcand
 */
public class HttpServiceChain extends HttpRequestProcessor implements JmxEventListener {

    private static final Logger LOGGER = Grizzly.logger(HttpServiceChain.class);
//    protected final static int MAPPING_DATA = 12;
//    protected final static int MAPPED_SERVICE = 13;
    private static final Note<MappingData> MAPPING_DATA_NOTE =
            Request.<MappingData>createNote("MAPPING_DATA");
    /**
     * The list of {@link HttpRequestProcessor} instance.
     */
    private ConcurrentHashMap<HttpRequestProcessor, String[]> services =
            new ConcurrentHashMap<HttpRequestProcessor, String[]>();
    private ConcurrentHashMap<HttpRequestProcessor, JmxObject> monitors =
            new ConcurrentHashMap<HttpRequestProcessor, JmxObject>();
    /**
     * Internal {@link Mapper} used to Map request to their associated {@link HttpRequestProcessor}
     */
    private Mapper mapper;
    /**
     * The default host.
     */
    private final static String LOCAL_HOST = "localhost";
    /**
     * Flag indicating this HttpService has been started.  Any subsequent
     * HttpService instances added to this chain after is has been started
     * will have their start() method invoked.
     */
    private boolean started;
    private final HttpServer httpServer;
    /**
     * Is the root context configured?
     */
    private boolean isRootConfigured = false;

    // ------------------------------------------------------------ Constructors
    public HttpServiceChain(final HttpServer httpServer) {
        this.httpServer = httpServer;
        mapper = new Mapper();
        mapper.setDefaultHostName(LOCAL_HOST);
        // We will decode it
        setDecodeUrl(false);
    }

    // ------------------------------------------- Methods from JmxEventListener
    @Override
    public void jmxEnabled() {
        for (Entry<HttpRequestProcessor, String[]> entry : services.entrySet()) {
            final HttpRequestProcessor httpService = entry.getKey();
            if (httpService instanceof Monitorable) {
                registerJmxForService(httpService);
            }
        }
    }

    @Override
    public void jmxDisabled() {
        for (Entry<HttpRequestProcessor, String[]> entry : services.entrySet()) {
            final HttpRequestProcessor httpService = entry.getKey();
            if (httpService instanceof Monitorable) {
                deregisterJmxForService(httpService);
            }
        }
    }

    // ---------------------------------------------------------- Public Methods
    @Override
    public void start() {
        for (Entry<HttpRequestProcessor, String[]> entry : services.entrySet()) {
            final HttpRequestProcessor httpService = entry.getKey();
            httpService.start();
        }
        started = true;
    }

    /**
     * Map the {@link Request} to the proper {@link HttpRequestProcessor}
     * @param request The {@link Request}
     * @param response The {@link Response}
     */
    @Override
    public void service(final Request request, final Response response) throws Exception {
        // For backward compatibility.
        //Request req = request.getRequest();
        MappingData mappingData;
        try {
            final RequestURIRef uriRef = request.getRequest().getRequestURIRef();
            final DataChunk decodedURI = uriRef.getDecodedRequestURIBC();
            //MessageBytes decodedURI = req.decodedURI();
            //decodedURI.duplicate(req.requestURI());
            // TODO: cleanup notes (int version/string version)
            mappingData = request.getNote(MAPPING_DATA_NOTE);
            if (mappingData == null) {
                mappingData = new MappingData();
                request.setNote(MAPPING_DATA_NOTE, mappingData);
            } else {
                mappingData.recycle();
            }

            mapUriWithSemicolon(request.getRequest().serverName(),
                    decodedURI,
                    0,
                    mappingData);


            HttpRequestProcessor httpService;
            if (mappingData.context != null && mappingData.context instanceof HttpRequestProcessor) {
                if (mappingData.wrapper != null) {
                    httpService = (HttpRequestProcessor) mappingData.wrapper;
                } else {
                    httpService = (HttpRequestProcessor) mappingData.context;
                }
                // We already decoded the URL.
                httpService.setDecodeUrl(false);
                httpService.doService(request, response);
            } else {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                customizedErrorPage(httpServer, request, response);
            }
        } catch (Throwable t) {
            try {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Invalid URL: " + request.getRequestURI(), t);
                }
                customizedErrorPage(httpServer, request, response);
            } catch (Exception ex2) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, "Unable to error page", ex2);
                }
            }
        }
    }

    /**
     * Add a {@link HttpRequestProcessor} and its associated array of mapping. The mapping
     * data will be used to map incoming request to its associated {@link HttpRequestProcessor}.
     * @param httpService {@link HttpRequestProcessor} instance
     * @param mappings an array of mapping.
     */
    public void addService(HttpRequestProcessor httpService, String[] mappings) {
        if (mappings.length == 0) {
            addService(httpService, new String[]{""});
        } else {
            if (started) {
                httpService.start();
                if (httpService instanceof Monitorable) {
                    registerJmxForService(httpService);
                }
            }
            services.put(httpService, mappings);
            for (String mapping : mappings) {

                final String ctx = getContextPath(mapping);
                final String wrapper = getWrapperPath(ctx, mapping);
                if (!ctx.equals("")) {
                    mapper.addContext(LOCAL_HOST, ctx, httpService,
                            new String[]{"index.html", "index.htm"}, null);
                } else {
                    if (!isRootConfigured && wrapper.startsWith("*.")) {
                        isRootConfigured = true;
                        final HttpRequestProcessor a = new HttpRequestProcessor() {

                            @Override
                            public void service(Request request, Response response) {
                                try {
                                    customizedErrorPage(httpServer, request, response);
                                } catch (Exception ex) {
                                }
                            }
                        };
                        mapper.addContext(LOCAL_HOST, ctx, a,
                                new String[]{"index.html", "index.htm"}, null);
                    } else {
                        mapper.addContext(LOCAL_HOST, ctx, httpService,
                                new String[]{"index.html", "index.htm"}, null);
                    }
                }
                mapper.addWrapper(LOCAL_HOST, ctx, wrapper, httpService);


//                String ctx = getContextPath(mapping);
//                mapper.addContext(LOCAL_HOST, ctx, httpService,
//                        new String[]{"index.html", "index.htm"}, null);
//                mapper.addWrapper(LOCAL_HOST, ctx, mapping.substring(ctx.length()), httpService);
            }
        }

    }

    private void registerJmxForService(final HttpRequestProcessor httpService) {
        final Monitorable monitorable = (Monitorable) httpService;
        final JmxObject jmx = monitorable.createManagementObject();
        monitors.putIfAbsent(httpService, jmx);
        httpServer.jmxManager.register(httpServer.managementObject, jmx, jmx.getJmxName());
    }

    private void deregisterJmxForService(final HttpRequestProcessor httpService) {

        JmxObject jmx = monitors.get(httpService);
        if (jmx != null) {
            httpServer.jmxManager.unregister(jmx);
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

        if (ctx.startsWith("/*.") || ctx.startsWith("*.")) {
            if (ctx.indexOf("/") == ctx.lastIndexOf("/")) {
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
        for (Entry<HttpRequestProcessor, String[]> service : services.entrySet()) {
            final HttpRequestProcessor a = service.getKey();
            a.destroy();
        }
        started = false;
    }

    /**
     * Remove a {@link HttpRequestProcessor}
     * @return <tt>true</tt> if removed
     */
    public boolean removeHttpService(HttpRequestProcessor httpService) {
        if (httpService == null) {
            throw new IllegalStateException();
        }
        String[] mappings = services.remove(httpService);
        if (mappings != null) {
            for (String mapping : mappings) {
                String ctx = getContextPath(mapping);
                mapper.removeContext(LOCAL_HOST, ctx);
            }
            deregisterJmxForService(httpService);
            httpService.destroy();

        }

        return (mappings != null);
    }

    /**
     * Maps the decodedURI to the corresponding HttpService, considering that URI
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
    private void mapUriWithSemicolon(final DataChunk serverName,
            final DataChunk decodedURI,
            int semicolonPos,
            final MappingData mappingData)
            throws Exception {

        final CharChunk charChunk = decodedURI.getCharChunk();
        final int oldEnd = charChunk.getEnd();

        if (semicolonPos == 0) {
            semicolonPos = decodedURI.indexOf(';', 0);
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

    public void removeAllHttpServices() {
        for (final HttpRequestProcessor service : services.keySet()) {
            removeHttpService(service);
        }
    }
}
