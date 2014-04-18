/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.io.CharConversionException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.jmxbase.JmxEventListener;
import org.glassfish.grizzly.http.server.jmxbase.Monitorable;
import org.glassfish.grizzly.http.server.util.DispatcherHelper;
import org.glassfish.grizzly.http.server.util.Mapper;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.RequestURIRef;
import org.glassfish.grizzly.localization.LogMessages;
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

    private static final Map<HttpHandlerRegistration, PathUpdater> ROOT_URLS;
    
    static {
        ROOT_URLS = new HashMap<HttpHandlerRegistration, PathUpdater>(3);
        ROOT_URLS.put(HttpHandlerRegistration.fromString(""), new EmptyPathUpdater());
        ROOT_URLS.put(HttpHandlerRegistration.fromString("/"), new SlashPathUpdater());
        ROOT_URLS.put(HttpHandlerRegistration.fromString("/*"), new SlashStarPathUpdater());
    }
    
    private final FullUrlPathResolver fullUrlPathResolver =
            new FullUrlPathResolver(this);
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
    private final ConcurrentMap<HttpHandler, HttpHandlerRegistration[]> handlers =
            DataStructures.<HttpHandler, HttpHandlerRegistration[]>getConcurrentMap();
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
    private volatile RootHttpHandler rootHttpHandler;
    
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
            for (HttpHandler httpHandler : handlers.keySet()) {
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
            for (HttpHandler httpHandler : handlers.keySet()) {
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
        response.setErrorPageGenerator(getErrorPageGenerator(request));
        
        try {
            final RootHttpHandler rootHttpHandlerLocal = rootHttpHandler;
            
            if (rootHttpHandlerLocal != null) {
                final HttpHandler rh = rootHttpHandlerLocal.httpHandler;
                rootHttpHandlerLocal.pathUpdater.update(this, rh, request);
                return rh.doHandle(request, response);
            }
            
            final RequestURIRef uriRef = request.getRequest().getRequestURIRef();
            uriRef.setDefaultURIEncoding(getRequestURIEncoding());
            final DataChunk decodedURI = uriRef.getDecodedRequestURIBC(
                    isAllowEncodedSlash());
            
            final MappingData mappingData = request.obtainMappingData();

            mapper.mapUriWithSemicolon(request.getRequest(),
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
                response.sendError(404);
            }
        } catch (Exception t) {
            try {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Internal server error", t);
                }
            } catch (Exception ex2) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_HTTP_SERVER_HTTPHANDLERCHAIN_ERRORPAGE(), ex2);
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
     * @throws java.lang.Exception
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
    public void addHandler(final HttpHandler httpHandler,
            final String[] mappings) {
        
    }
    
    /**
     * Add a {@link HttpHandler} and its associated array of mapping. The mapping
     * data will be used to map incoming request to its associated {@link HttpHandler}.
     * @param httpHandler {@link HttpHandler} instance
     * @param mappings an array of mapping.
     */
    public void addHandler(final HttpHandler httpHandler,
            final HttpHandlerRegistration[] mappings) {
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

                for (HttpHandlerRegistration reg : mappings) {
                    
                    final String ctx = reg.getContextPath();
                    final String wrapper = reg.getUrlPattern();
                    if (ctx.length() != 0) {
                        mapper.addContext(LOCAL_HOST, ctx, httpHandler,
                                new String[]{"index.html", "index.htm"}, null);
                    } else {
                        if (!isRootConfigured && wrapper.startsWith("*.")) {
                            isRootConfigured = true;
                            final HttpHandler a = new HttpHandler() {

                                @Override
                                public void service(Request request,
                                        Response response) throws IOException {
                                    response.sendError(404);
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
                }
                
                // Check if the only one HttpHandler is registered
                // and if it's a root HttpHandler - apply optimization
                if (handlersCount == 1 && mappings.length == 1 &&
                        ROOT_URLS.containsKey(mappings[0])) {
                    rootHttpHandler = new RootHttpHandler(httpHandler,
                            ROOT_URLS.get(mappings[0]));
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
     * @param httpHandler {@link HttpHandler} to remove
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

            final HttpHandlerRegistration[] mappings = handlers.remove(httpHandler);
            if (mappings != null) {
                for (HttpHandlerRegistration mapping : mappings) {
                    final String contextPath = mapping.getContextPath();
                    
                    mapper.removeWrapper(LOCAL_HOST, contextPath,
                            mapping.getUrlPattern());
                    
                    if (mapper.getWrapperNames(LOCAL_HOST, name).length == 0) {
                        mapper.removeContext(LOCAL_HOST, contextPath);
                    }
                }
                
                deregisterJmxForHandler(httpHandler);
                httpHandler.destroy();

                // Check if the only one HttpHandler left
                // and if it's a root HttpHandler - apply optimization
                handlersCount--;
                if (handlersCount == 1) {
                    final Map.Entry<HttpHandler, HttpHandlerRegistration[]> entry =
                            handlers.entrySet().iterator().next();
                    final HttpHandlerRegistration[] lastHttpHandlerMappings = entry.getValue();
                    if (lastHttpHandlerMappings.length == 1
                            && ROOT_URLS.containsKey(lastHttpHandlerMappings[0])) {
                        rootHttpHandler = new RootHttpHandler(httpHandler,
                                ROOT_URLS.get(lastHttpHandlerMappings[0]));
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
            for (HttpHandler httpHandler : handlers.keySet()) {
                httpHandler.start();
            }
        } finally {
            mapperUpdateLock.readLock().unlock();
        }
        
        started = true;
    }

    @Override
    public synchronized void destroy() {
        mapperUpdateLock.writeLock().lock();
        
        try {
            for (HttpHandler httpHandler : handlers.keySet()) {
                httpHandler.destroy();
            }
        } finally {
            mapperUpdateLock.writeLock().unlock();
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

    private final class DispatchHelperImpl implements DispatcherHelper {

        @Override
        public void mapPath(final HttpRequestPacket requestPacket, final DataChunk path,
                final MappingData mappingData) throws Exception {
            
            mapper.map(requestPacket, path, mappingData);
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
    
    private static final class RootHttpHandler {
        private final HttpHandler httpHandler;
        private final PathUpdater pathUpdater;

        public RootHttpHandler(final HttpHandler httpHandler,
                final PathUpdater pathUpdater) {
            this.httpHandler = httpHandler;
            this.pathUpdater = pathUpdater;
        }
    }
    
    private static interface PathUpdater {

        public void update(HttpHandlerChain handlerChain,
                HttpHandler httpHandler, Request request);
    }
    
    private static class SlashPathUpdater implements PathUpdater {

        @Override
        public void update(final HttpHandlerChain handlerChain,
                final HttpHandler httpHandler, final Request request) {

            request.setContextPath("");
            request.setPathInfo((String) null);
            request.setHttpHandlerPath(handlerChain.fullUrlPathResolver);
        }
        
    }
    
    private static class SlashStarPathUpdater implements PathUpdater {

        @Override
        public void update(final HttpHandlerChain handlerChain,
                final HttpHandler httpHandler, final Request request) {
            request.setContextPath("");
            request.setPathInfo(handlerChain.fullUrlPathResolver);
            request.setHttpHandlerPath("");
        }
    }
    
    private static class EmptyPathUpdater implements PathUpdater {

        @Override
        public void update(final HttpHandlerChain handlerChain,
                final HttpHandler httpHandler, final Request request) {
            request.setContextPath("");
            request.setPathInfo((String) null);
            request.setHttpHandlerPath((String) null);
        }
    }    
    
    private static class FullUrlPathResolver implements Request.PathResolver {
        private final HttpHandler httpHandler;

        public FullUrlPathResolver(HttpHandler httpHandler) {
            this.httpHandler = httpHandler;
        }
        
        @Override
        public String resolve(final Request request) {
            try {
                final RequestURIRef uriRef = request.getRequest().getRequestURIRef();
                uriRef.setDefaultURIEncoding(httpHandler.getRequestURIEncoding());
                final DataChunk decodedURI = uriRef.getDecodedRequestURIBC(
                        httpHandler.isAllowEncodedSlash());
                
                final int pos = decodedURI.indexOf(';', 0);
                return pos < 0 ? decodedURI.toString() : decodedURI.toString(0, pos);
            } catch (CharConversionException e) {
                throw new IllegalStateException(e);
            }
        }
        
    }
}
