/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.jmxbase.JmxEventListener;
import org.glassfish.grizzly.http.server.jmxbase.Monitorable;
import org.glassfish.grizzly.http.server.util.DispatcherHelper;
import org.glassfish.grizzly.http.server.util.Mapper;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.RequestURIRef;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * The HttpHandlerChain class allows the invocation of multiple {@link HttpHandler}s
 * every time a new HTTP request is ready to be handled. Requests are mapped
 * to their associated {@link HttpHandler} at runtime using the mapping
 * information configured when invoking the {@link HttpHandlerChain#addHandler
 * (org.glassfish.grizzly.http.server.HttpHandler, java.lang.String[])}
 *
 * @author Jeanfrancois Arcand
 */
public class HttpHandlerChain extends HttpHandler implements JmxEventListener {

    private static final Logger LOGGER = Grizzly.logger(HttpHandlerChain.class);

    /**
     * The name -> {@link HttpHandler} map.
     */
    private final ConcurrentMap<String, HttpHandler> handlersByName =
            DataStructures.<String, HttpHandler>getConcurrentMap();

    private final ReentrantReadWriteLock mapperUpdateLock =
            new ReentrantReadWriteLock();
    
    /**
     * The list of {@link HttpHandler} instance.
     */
    private final ConcurrentMap<HttpHandler, String[]> handlers =
            DataStructures.<HttpHandler, String[]>getConcurrentMap();
    private final ConcurrentMap<HttpHandler, Object> monitors =
            DataStructures.<HttpHandler, Object>getConcurrentMap();
    
    /**
     * Number of registered HttpHandlers
     */
    private int handlersCount;
    
    /**
     * The root {@link HttpHandler}, used in cases when HttpHandlerChain has
     * only one {@link HttpHandler} registered and this {@link HttpHandler} is
     * registered as root resource.
     */
    private volatile HttpHandler rootHttpHandler;
    
    /**
     * Internal {@link Mapper} used to Map request to their associated {@link HttpHandler}
     */
    private final Mapper mapper;
    
    /**
     * DispatchHelper, which maps path or name to the Mapper entry
     */
    private final DispatcherHelper dispatchHelper;

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
        dispatchHelper = new DispatchHelperImpl();
        // We will decode it
        setDecodeUrl(false);
    }

    // ------------------------------------------- Methods from JmxEventListener
    @Override
    public void jmxEnabled() {
        mapperUpdateLock.readLock().lock();
        
        try {
            for (Entry<HttpHandler, String[]> entry : handlers.entrySet()) {
                final HttpHandler httpHandler = entry.getKey();
                if (httpHandler instanceof Monitorable) {
                    registerJmxForHandler(httpHandler);
                }
            }
        } finally {
            mapperUpdateLock.readLock().unlock();
        }
    }

    @Override
    public void jmxDisabled() {
        mapperUpdateLock.readLock().lock();
        
        try {
            for (Entry<HttpHandler, String[]> entry : handlers.entrySet()) {
                final HttpHandler httpHandler = entry.getKey();
                if (httpHandler instanceof Monitorable) {
                    deregisterJmxForHandler(httpHandler);
                }
            }
        } finally {
            mapperUpdateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    boolean doHandle(final Request request, final Response response)
            throws Exception {
        try {
            final HttpHandler rootHttpHandlerLocal = rootHttpHandler;
            
            if (rootHttpHandlerLocal != null) {
                // use default path values (don't call updatePaths)
                return rootHttpHandlerLocal.doHandle(request, response);
            }
            
            final RequestURIRef uriRef = request.getRequest().getRequestURIRef();
            uriRef.setDefaultURIEncoding(getRequestURIEncoding());
            final DataChunk decodedURI = uriRef.getDecodedRequestURIBC(
                    isAllowEncodedSlash(), isAllowEncodedBackSlash());
            
            final MappingData mappingData = request.obtainMappingData();

            mapper.mapUriWithSemicolon(request.getRequest().serverName(),
                    decodedURI,
                    mappingData,
                    0);


            HttpHandler httpHandler;
            if (mappingData.context != null && mappingData.context instanceof HttpHandler) {
                if (mappingData.wrapper != null) {
                    httpHandler = (HttpHandler) mappingData.wrapper;
                } else {
                    httpHandler = (HttpHandler) mappingData.context;
                }

                updatePaths(request, mappingData);

                return httpHandler.doHandle(request, response);
            } else {
                response.setStatus(HttpStatus.NOT_FOUND_404);
                customizedErrorPage(request, response);
            }
        } catch (Exception t) {
            try {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Internal server error", t);
                }
            } catch (Exception ex2) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, "Unable to error page", ex2);
                }
            }
        }
        
        return true;
    }
    
    // ---------------------------------------------------------- Public Methods
    
    /**
     * Map the {@link Request} to the proper {@link HttpHandler}
     * @param request The {@link Request}
     * @param response The {@link Response}
     */
    @Override
    public void service(final Request request, final Response response) throws Exception {
        throw new IllegalStateException("Method doesn't have to be called");
    }

    /**
     * Add a {@link HttpHandler} and its associated array of mapping. The mapping
     * data will be used to map incoming request to its associated {@link HttpHandler}.
     * @param httpHandler {@link HttpHandler} instance
     * @param mappings an array of mapping.
     */
    public void addHandler(HttpHandler httpHandler, String[] mappings) {
        mapperUpdateLock.writeLock().lock();
        
        try {
            if (mappings.length == 0) {
                addHandler(httpHandler, new String[]{""});
            } else {
                if (started) {
                    httpHandler.start();
                    if (httpHandler instanceof Monitorable) {
                        registerJmxForHandler(httpHandler);
                    }
                }

                if (handlers.put(httpHandler, mappings) == null) {
                    handlersCount++;
                }

                final String name = httpHandler.getName();
                if (name != null) {
                    handlersByName.put(name, httpHandler);
                }

                httpHandler.setDispatcherHelper(dispatchHelper);

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
                
                // Check if the only one HttpHandler is registered
                // and if it's a root HttpHandler - apply optimization
                if (handlersCount == 1 && mappings.length == 1 &&
                        ("".equals(mappings[0]) ||  "/".equals(mappings[0]))) {
                    rootHttpHandler = httpHandler;
                } else {
                    rootHttpHandler = null;
                }
            }
        } finally {
            mapperUpdateLock.writeLock().unlock();
        }

    }

    /**
     * Remove a {@link HttpHandler}
     * @return <tt>true</tt> if removed
     */
    public boolean removeHttpHandler(final HttpHandler httpHandler) {
        if (httpHandler == null) {
            throw new IllegalStateException();
        }
        
        mapperUpdateLock.writeLock().lock();
        
        try {
            final String name = httpHandler.getName();
            if (name != null) {
                handlersByName.remove(name);
            }

            String[] mappings = handlers.remove(httpHandler);
            if (mappings != null) {
                for (String mapping : mappings) {
                    String ctx = getContextPath(mapping);
                    mapper.removeContext(LOCAL_HOST, ctx);
                }
                deregisterJmxForHandler(httpHandler);
                httpHandler.destroy();
                
                // Check if the only one HttpHandler left
                // and if it's a root HttpHandler - apply optimization
                handlersCount--;
                if (handlersCount == 1) {
                    final Map.Entry<HttpHandler, String[]> entry =
                            handlers.entrySet().iterator().next();
                    final HttpHandler lastHttpHandler = entry.getKey();
                    final String[] lastHttpHandlerMappings = entry.getValue();
                    if (lastHttpHandlerMappings.length == 1
                            && ("".equals(lastHttpHandlerMappings[0]) ||
                            "/".equals(lastHttpHandlerMappings[0]))) {
                        rootHttpHandler = httpHandler;
                    } else {
                        rootHttpHandler = null;
                    }
                } else {
                    rootHttpHandler = null;
                }
            }
            
            return (mappings != null);
        } finally {
            mapperUpdateLock.writeLock().unlock();
        }
    }

    public void removeAllHttpHandlers() {
        mapperUpdateLock.writeLock().lock();
        
        try {
            for (final HttpHandler handler : handlers.keySet()) {
                removeHttpHandler(handler);
            }
        } finally {
            mapperUpdateLock.writeLock().unlock();
        }
    }
    
    @Override
    public synchronized void start() {
        mapperUpdateLock.readLock().lock();
        
        try {
            for (Entry<HttpHandler, String[]> entry : handlers.entrySet()) {
                final HttpHandler httpHandler = entry.getKey();
                httpHandler.start();
            }
        } finally {
            mapperUpdateLock.readLock().unlock();
        }
        
        started = true;
    }

    @Override
    public synchronized void destroy() {
        mapperUpdateLock.readLock().lock();
        
        try {
            for (Entry<HttpHandler, String[]> handler : handlers.entrySet()) {
                final HttpHandler a = handler.getKey();
                a.destroy();
            }
        } finally {
            mapperUpdateLock.readLock().unlock();
        }
        started = false;
    }

    private void registerJmxForHandler(final HttpHandler httpHandler) {
        final Monitorable monitorable = (Monitorable) httpHandler;
        final Object jmx = monitorable.createManagementObject();
        if (monitors.putIfAbsent(httpHandler, jmx) == null) {
            httpServer.jmxManager.register(httpServer.managementObject, jmx);
        }
    }

    private void deregisterJmxForHandler(final HttpHandler httpHandler) {
        final Object jmx = monitors.remove(httpHandler);
        if (jmx != null) {
            httpServer.jmxManager.deregister(jmx);
        }
    }

    private String getWrapperPath(String ctx, String mapping) {

        if (mapping.indexOf("*.") > 0) {
            return mapping.substring(mapping.lastIndexOf("/") + 1);
        } else if (ctx.length() != 0) {
            return mapping.substring(ctx.length());
        } else if (mapping.startsWith("//")) {
            return mapping.substring(1);
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

    private final class DispatchHelperImpl implements DispatcherHelper {

        @Override
        public void mapPath(final DataChunk host, final DataChunk path,
                final MappingData mappingData) throws Exception {
            
            mapper.map(host, path, mappingData);
        }

        @Override
        public void mapName(final DataChunk name, final MappingData mappingData) {
            final String nameStr = name.toString();
            
            final HttpHandler handler = handlersByName.get(nameStr);
            if (handler != null) {
                mappingData.wrapper = handler;
                mappingData.servletName = nameStr;
            }
        }
    }
}
