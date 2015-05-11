/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.websockets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.GenericCloseListener;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.util.Mapper;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;

/**
 * WebSockets engine implementation (singleton), which handles {@link WebSocketApplication}s registration, responsible
 * for client and server handshake validation.
 *
 * @author Alexey Stashok
 * @see WebSocket
 * @see WebSocketApplication
 */
public class WebSocketEngine {

    public static final Version DEFAULT_VERSION = Version.RFC6455;
    public static final int DEFAULT_TIMEOUT = 30;
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final WebSocketEngine engine = new WebSocketEngine();
    static final Logger logger = Logger.getLogger(Constants.WEBSOCKET);

    private final List<WebSocketApplication> applications = new ArrayList<WebSocketApplication>();


    // Association between WebSocketApplication and a value based on the
    // context path and url pattern.
    private final HashMap<WebSocketApplication, String> applicationMap =
            new HashMap<WebSocketApplication, String>(4);
    // Association between full path and registered application.
    private final HashMap<String, WebSocketApplication> fullPathToApplication =
            new HashMap<String, WebSocketApplication>(4);
    // Association between a particular context path and all applications with
    // sub-paths registered to that context path.
    private final HashMap<String, List<WebSocketApplication>> contextApplications =
            new HashMap<String, List<WebSocketApplication>>(2);

    private final HttpResponsePacket.Builder unsupportedVersionsResponseBuilder;

    private Mapper mapper = new Mapper();


    private WebSocketEngine() {
        mapper.setDefaultHostName("localhost");
        unsupportedVersionsResponseBuilder = new HttpResponsePacket.Builder();
        unsupportedVersionsResponseBuilder.status(HttpStatus.BAD_REQUEST_400.getStatusCode());
        unsupportedVersionsResponseBuilder.header(Constants.SEC_WS_VERSION,
                               Version.getSupportedWireProtocolVersions());
    }

    public static WebSocketEngine getEngine() {
        return engine;
    }



    public WebSocketApplication getApplication(HttpRequestPacket request) {
        final WebSocketApplicationReg appReg = getApplication(request, mapper);
        return appReg != null ? appReg.app : null;
    }

    private WebSocketApplicationReg getApplication(
            final HttpRequestPacket request,
            final Mapper glassfishMapper) {
        
        final boolean isGlassfish = glassfishMapper != null;

        WebSocketApplication foundWebSocketApp = null;
        final WebSocketMappingData data = new WebSocketMappingData(isGlassfish);
        
        try {
            mapper.mapUriWithSemicolon(request,
                    request.getRequestURIRef().getDecodedRequestURIBC(),
                                data,
                                0);
            if (data.wrapper != null) {
                foundWebSocketApp = (WebSocketApplication) data.wrapper;
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, e.toString(), e);
            }
        }

        if (foundWebSocketApp == null) {
            for (WebSocketApplication application : applications) {
                if (application.upgrade(request)) {
                    foundWebSocketApp = application;
                    break;
                }
            }
        }
        
        if (foundWebSocketApp == null) {
            return null;
        }
        
        if (isGlassfish) {
            assert glassfishMapper != null;
            
            // do one more mapping, this time using GF Mapper to retrieve
            // correspondent web application
            
            try {
                data.recycle();
                glassfishMapper.mapUriWithSemicolon(request,
                        request.getRequestURIRef().getDecodedRequestURIBC(),
                                    data,
                                    0);
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, e.toString(), e);
                }
            }
        }
        
        return new WebSocketApplicationReg(foundWebSocketApp,
                // if contextPath == null - don't return any mapping info
                data.contextPath.isNull() ? null : data);
    }
    
    public boolean upgrade(FilterChainContext ctx, HttpContent requestContent)
            throws IOException {
        return upgrade(ctx, requestContent, null);
    }
    
    // Mapper will be non-null when integrated in GF.
    public boolean upgrade(FilterChainContext ctx, HttpContent requestContent,
            Mapper mapper) throws IOException {
        final HttpRequestPacket request =
                (HttpRequestPacket) requestContent.getHttpHeader();
        final WebSocketApplicationReg reg =
                WebSocketEngine.getEngine().getApplication(request, mapper);
        
        WebSocket socket = null;
        try {
            if (reg != null) {
                final ProtocolHandler protocolHandler = loadHandler(request.getHeaders());
                if (protocolHandler == null) {
                    handleUnsupportedVersion(ctx, request);
                    return false;
                }
                final Connection connection = ctx.getConnection();
                protocolHandler.setFilterChainContext(ctx);
                protocolHandler.setConnection(connection);
                protocolHandler.setMappingData(reg.mappingData);
                
                ctx.setMessage(null); // remove the message from the context, so underlying layers will not try to update it.
                
                final WebSocketApplication app = reg.app;
                socket = app.createSocket(protocolHandler, request, app);
                WebSocketHolder holder =
                        WebSocketHolder.set(connection, protocolHandler, socket);
                holder.application = app;
                protocolHandler.handshake(ctx, app, requestContent);
                request.getConnection().addCloseListener(new GenericCloseListener() {
                    @Override
                    public void onClosed(final Closeable closeable,
                            final CloseType type) throws IOException {
                        
                        final WebSocket webSocket = WebSocketHolder.getWebSocket(connection);
                        webSocket.close();
                        webSocket.onClose(new ClosingFrame(WebSocket.END_POINT_GOING_DOWN,
                            "Close detected on connection"));
                    }
                });
                socket.onConnect();
                return true;
            }
        } catch (HandshakeException e) {
            logger.log(Level.FINE, e.getMessage(), e);
            if (socket != null) {
                socket.close();
            }
            
            throw e;
        }
        
        return false;
    }

    public static ProtocolHandler loadHandler(MimeHeaders headers) {
        for (Version version : Version.values()) {
            if (version.validate(headers)) {
                return version.createHandler(false);
            }
        }
        return null;
    }

    /**
     * Register a WebSocketApplication to a specific context path and url pattern.
     * If you wish to associate this application with the root context, use an
     * empty string for the contextPath argument.
     *
     * <pre>
     * Examples:
     *   // WS application will be invoked:
     *   //    ws://localhost:8080/echo
     *   // WS application will not be invoked:
     *   //    ws://localhost:8080/foo/echo
     *   //    ws://localhost:8080/echo/some/path
     *   register("", "/echo", webSocketApplication);
     *
     *   // WS application will be invoked:
     *   //    ws://localhost:8080/echo
     *   //    ws://localhost:8080/echo/some/path
     *   // WS application will not be invoked:
     *   //    ws://localhost:8080/foo/echo
     *   register("", "/echo/*", webSocketApplication);
     *
     *   // WS application will be invoked:
     *   //    ws://localhost:8080/context/echo
     *
     *   // WS application will not be invoked:
     *   //    ws://localhost:8080/echo
     *   //    ws://localhost:8080/context/some/path
     *   register("/context", "/echo", webSocketApplication);
     * </pre>
     *
     * @param contextPath the context path (per servlet rules)
     * @param urlPattern url pattern (per servlet rules)
     * @param app the WebSocket application.
     */
    public synchronized void register(final String contextPath,
            final String urlPattern, final WebSocketApplication app) {
        if (contextPath == null || urlPattern == null) {
            throw new IllegalArgumentException("contextPath and urlPattern must not be null");
        }
        
        if (!urlPattern.startsWith("/")) {
            throw new IllegalArgumentException("The urlPattern must start with '/'");
        }
        
        String contextPathLocal = getContextPath(contextPath);
        final String fullPath = contextPathLocal + '|' + urlPattern;
        
        final WebSocketApplication oldApp = fullPathToApplication.get(fullPath);
        if (oldApp != null) {
            unregister(oldApp);
        }
        
        mapper.addContext("localhost", contextPathLocal,
                "[Context '" + contextPath + "']", EMPTY_STRING_ARRAY, null);
        mapper.addWrapper("localhost", contextPathLocal, urlPattern, app);
        applicationMap.put(app, fullPath);
        fullPathToApplication.put(fullPath, app);
        if (contextApplications.containsKey(contextPathLocal)) {
            contextApplications.get(contextPathLocal).add(app);
        } else {
            List<WebSocketApplication> apps = new ArrayList<WebSocketApplication>(4);
            apps.add(app);
            contextApplications.put(contextPathLocal, apps);
        }
    }

    /**
     *
     * @deprecated Use {@link #register(String, String, WebSocketApplication)}
     */
    @Deprecated
    public synchronized void register(WebSocketApplication app) {
        applications.add(app);
    }

    public synchronized void unregister(WebSocketApplication app) {
        String fullPath = applicationMap.remove(app);
        if (fullPath != null) {
            fullPathToApplication.remove(fullPath);
            String[] parts = fullPath.split("\\|");
            mapper.removeWrapper("localhost", parts[0], parts[1]);
            List<WebSocketApplication> apps = contextApplications.get(parts[0]);
            apps.remove(app);
            if (apps.isEmpty()) {
                mapper.removeContext("localhost", parts[0]);
                contextApplications.remove(parts[0]);
            }
            return;
        }
        applications.remove(app);
    }

    /**
     * Un-registers all {@link WebSocketApplication} instances with the 
     * {@link WebSocketEngine}.
     */
    public synchronized void unregisterAll() {
        applicationMap.clear();
        fullPathToApplication.clear();
        contextApplications.clear();
        applications.clear();
        mapper = new Mapper();
        mapper.setDefaultHostName("localhost");
    }

    private void handleUnsupportedVersion(final FilterChainContext ctx,
                                                 final HttpRequestPacket request)
    throws IOException {
        unsupportedVersionsResponseBuilder.requestPacket(request);
        ctx.write(unsupportedVersionsResponseBuilder.build());
    }


    private static String getContextPath(String mapping) {
        String ctx;
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

    private static class WebSocketApplicationReg {
        private final WebSocketApplication app;
        private final WebSocketMappingData mappingData;

        public WebSocketApplicationReg(final WebSocketApplication app,
                final WebSocketMappingData mappingData) {
            this.app = app;
            this.mappingData = mappingData;
        }
    }
}
