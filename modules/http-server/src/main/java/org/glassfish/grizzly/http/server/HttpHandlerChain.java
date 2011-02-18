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
import org.glassfish.grizzly.http.util.CharChunk;

/**
 * The HttpHandlerChain class allows the invocation of multiple {@link HttpHandler}s
 * every time a new HTTP request is ready to be handled. Requests are mapped
 * to their associated {@link HttpHandler} at runtime using the mapping
 * information configured when invoking the {@link HttpHandlerChain#addHandler
 * (org.glassfish.grizzly.http.server.HttpHandler, java.lang.String[])}
 *
 *
 * Note: This class is <strong>NOT</strong> thread-safe, so make sure synchronization
 *  is performed when dynamically adding and removing {@link HttpHandler}
 *
 * @author Jeanfrancois Arcand
 */
public class HttpHandlerChain extends HttpHandler implements JmxEventListener {

    private static final Logger LOGGER = Grizzly.logger(HttpHandlerChain.class);
//    protected final static int MAPPING_DATA = 12;
//    protected final static int MAPPED_SERVICE = 13;
//    private static final Note<MappingData> MAPPING_DATA_NOTE =
//            Request.<MappingData>createNote("MAPPING_DATA");
    /**
     * The list of {@link HttpHandler} instance.
     */
    private ConcurrentHashMap<HttpHandler, String[]> handlers =
            new ConcurrentHashMap<HttpHandler, String[]>();
    private ConcurrentHashMap<HttpHandler, JmxObject> monitors =
            new ConcurrentHashMap<HttpHandler, JmxObject>();
    /**
     * Internal {@link Mapper} used to Map request to their associated {@link HttpHandler}
     */
    private Mapper mapper;
    /**
     * The default host.
     */
    private final static String LOCAL_HOST = "localhost";
    /**
     * Flag indicating this HttpHandler has been started.  Any subsequent
     * HttpHandler instances added to this chain after is has been started
     * will have their start() method invoked.
     */
    private boolean started;
    private final HttpServer httpServer;
    /**
     * Is the root context configured?
     */
    private boolean isRootConfigured = false;

    // ------------------------------------------------------------ Constructors
    public HttpHandlerChain(final HttpServer httpServer) {
        this.httpServer = httpServer;
        mapper = new Mapper();
        mapper.setDefaultHostName(LOCAL_HOST);
        // We will decode it
        setDecodeUrl(false);
    }

    // ------------------------------------------- Methods from JmxEventListener
    @Override
    public void jmxEnabled() {
        for (Entry<HttpHandler, String[]> entry : handlers.entrySet()) {
            final HttpHandler httpHandler = entry.getKey();
            if (httpHandler instanceof Monitorable) {
                registerJmxForHandler(httpHandler);
            }
        }
    }

    @Override
    public void jmxDisabled() {
        for (Entry<HttpHandler, String[]> entry : handlers.entrySet()) {
            final HttpHandler httpHandler = entry.getKey();
            if (httpHandler instanceof Monitorable) {
                deregisterJmxForHandler(httpHandler);
            }
        }
    }

    // ---------------------------------------------------------- Public Methods
    @Override
    public void start() {
        for (Entry<HttpHandler, String[]> entry : handlers.entrySet()) {
            final HttpHandler httpHandler = entry.getKey();
            httpHandler.start();
        }
        started = true;
    }

    /**
     * Map the {@link Request} to the proper {@link HttpHandler}
     * @param request The {@link Request}
     * @param response The {@link Response}
     */
    @Override
    public void service(final Request request, final Response response) throws Exception {
        // For backward compatibility.
        //Request req = request.getRequest();
//        MappingData mappingData = null;
        try {
            final RequestURIRef uriRef = request.getRequest().getRequestURIRef();
            final DataChunk decodedURI = uriRef.getDecodedRequestURIBC();
            //MessageBytes decodedURI = req.decodedURI();
            //decodedURI.duplicate(req.requestURI());
            // TODO: cleanup notes (int version/string version)
//            mappingData = request.getNote(MAPPING_DATA_NOTE);
//            if (mappingData == null) {
              final MappingData mappingData = request.obtainMappingData();
//            } else {
//                mappingData.recycle();
//            }

            mapUriWithSemicolon(request.getRequest().serverName(),
                    decodedURI,
                    0,
                    mappingData);


            HttpHandler httpHandler;
            if (mappingData.context != null && mappingData.context instanceof HttpHandler) {
                if (mappingData.wrapper != null) {
                    httpHandler = (HttpHandler) mappingData.wrapper;
                } else {
                    httpHandler = (HttpHandler) mappingData.context;
                }

                updateContextPath(request, mappingData.contextPath.toString());
                
                // We already decoded the URL.
                httpHandler.setDecodeUrl(false);
                httpHandler.doHandle(request, response);
            } else {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                customizedErrorPage(request, response);
            }
        } catch (Throwable t) {
            try {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Invalid URL: " + request.getRequestURI(), t);
                }
                customizedErrorPage(request, response);
            } catch (Exception ex2) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, "Unable to error page", ex2);
                }
            }
        }
    }

    /**
     * Add a {@link HttpHandler} and its associated array of mapping. The mapping
     * data will be used to map incoming request to its associated {@link HttpHandler}.
     * @param httpHandler {@link HttpHandler} instance
     * @param mappings an array of mapping.
     */
    public void addHandler(HttpHandler httpHandler, String[] mappings) {
        if (mappings.length == 0) {
            addHandler(httpHandler, new String[]{""});
        } else {
            if (started) {
                httpHandler.start();
                if (httpHandler instanceof Monitorable) {
                    registerJmxForHandler(httpHandler);
                }
            }
            handlers.put(httpHandler, mappings);
            for (String mapping : mappings) {

                final String ctx = getContextPath(mapping);
                final String wrapper = getWrapperPath(ctx, mapping);
                if (ctx.length() != 0) {
                    mapper.addContext(LOCAL_HOST, ctx, httpHandler,
                            new String[]{"index.html", "index.htm"}, null);
                } else {
                    if (!isRootConfigured && wrapper.startsWith("*.")) {
                        isRootConfigured = true;
                        final HttpHandler a = new HttpHandler() {

                            @Override
                            public void service(Request request, Response response) {
                                try {
                                    customizedErrorPage(request, response);
                                } catch (Exception ignored) {
                                }
                            }
                        };
                        mapper.addContext(LOCAL_HOST, ctx, a,
                                new String[]{"index.html", "index.htm"}, null);
                    } else {
                        mapper.addContext(LOCAL_HOST, ctx, httpHandler,
                                new String[]{"index.html", "index.htm"}, null);
                    }
                }
                mapper.addWrapper(LOCAL_HOST, ctx, wrapper, httpHandler);


//                String ctx = getContextPath(mapping);
//                mapper.addContext(LOCAL_HOST, ctx, httpHandler,
//                        new String[]{"index.html", "index.htm"}, null);
//                mapper.addWrapper(LOCAL_HOST, ctx, mapping.substring(ctx.length()), httpHandler);
            }
        }

    }

    private void registerJmxForHandler(final HttpHandler httpHandler) {
        final Monitorable monitorable = (Monitorable) httpHandler;
        final JmxObject jmx = monitorable.createManagementObject();
        monitors.putIfAbsent(httpHandler, jmx);
        httpServer.jmxManager.register(httpServer.managementObject, jmx, jmx.getJmxName());
    }

    private void deregisterJmxForHandler(final HttpHandler httpHandler) {

        JmxObject jmx = monitors.get(httpHandler);
        if (jmx != null) {
            httpServer.jmxManager.deregister(jmx);
        }

    }

    private String getWrapperPath(String ctx, String mapping) {

        if (mapping.indexOf("*.") > 0) {
            return mapping.substring(mapping.lastIndexOf("/") + 1);
        } else if (ctx.length() != 0) {
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
        for (Entry<HttpHandler, String[]> handler : handlers.entrySet()) {
            final HttpHandler a = handler.getKey();
            a.destroy();
        }
        started = false;
    }

    /**
     * Remove a {@link HttpHandler}
     * @return <tt>true</tt> if removed
     */
    public boolean removeHttpHandler(final HttpHandler httpHandler) {
        if (httpHandler == null) {
            throw new IllegalStateException();
        }
        String[] mappings = handlers.remove(httpHandler);
        if (mappings != null) {
            for (String mapping : mappings) {
                String ctx = getContextPath(mapping);
                mapper.removeContext(LOCAL_HOST, ctx);
            }
            deregisterJmxForHandler(httpHandler);
            httpHandler.destroy();

        }

        return (mappings != null);
    }

    /**
     * Maps the decodedURI to the corresponding HttpHandler, considering that URI
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

    public void removeAllHttpHandlers() {
        for (final HttpHandler handler : handlers.keySet()) {
            removeHttpHandler(handler);
        }
    }
}
