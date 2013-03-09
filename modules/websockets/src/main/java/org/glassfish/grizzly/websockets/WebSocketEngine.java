/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Connection.CloseListener;
import org.glassfish.grizzly.Connection.CloseType;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.util.Mapper;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.websockets.draft06.ClosingFrame;

/**
 * WebSockets engine implementation (singleton), which handles {@link WebSocketApplication}s registration, responsible
 * for client and server handshake validation.
 *
 * @author Alexey Stashok
 * @see WebSocket
 * @see WebSocketApplication
 */
public class WebSocketEngine {
    public static final String SEC_WS_ACCEPT = "Sec-WebSocket-Accept";
    public static final String SEC_WS_KEY_HEADER = "Sec-WebSocket-Key";
    public static final String SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String ORIGIN_HEADER = "Origin";
    public static final String SEC_WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    public static final String SEC_WS_EXTENSIONS_HEADER = "Sec-WebSocket-Extensions";
    public static final String SEC_WS_VERSION = "Sec-WebSocket-Version";
    public static final String WEBSOCKET = "websocket";
    public static final String RESPONSE_CODE_MESSAGE = "Switching Protocols";
    public static final String RESPONSE_CODE_HEADER = "Response Code";
    public static final int RESPONSE_CODE_VALUE = 101;
    public static final String UPGRADE = "upgrade";
    public static final String CONNECTION = "connection";
    public static final String CLIENT_WS_ORIGIN_HEADER = "Origin";
    public static final Version DEFAULT_VERSION = Version.DRAFT17;
    public static final int INITIAL_BUFFER_SIZE = 8192;
    public static final int DEFAULT_TIMEOUT = 30;
    private static final WebSocketEngine engine = new WebSocketEngine();
    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    public static final String SERVER_KEY_HASH = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final int MASK_SIZE = 4;
    private final List<WebSocketApplication> applications = new ArrayList<WebSocketApplication>();
    private final Attribute<WebSocketHolder> webSocketAttribute =
        Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("web-socket");

    // Association between WebSocketApplication and a value based on the
    // context path and url pattern.
    private final HashMap<WebSocketApplication, String> applicationMap =
            new HashMap<WebSocketApplication, String>();
    // Association between a particular context path and all applications with
    // sub-paths registered to that context path.
    private final HashMap<String, List<WebSocketApplication>> contextApplications =
            new HashMap<String, List<WebSocketApplication>>();

    private Mapper mapper = new Mapper();


    private WebSocketEngine() {
        mapper.setDefaultHostName("localhost");
    }

    public static WebSocketEngine getEngine() {
        return engine;
    }

    public static byte[] toArray(long length) {
        long value = length;
        byte[] b = new byte[8];
        for (int i = 7; i >= 0 && value > 0; i--) {
            b[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return b;
    }

    public static long toLong(byte[] bytes, int start, int end) {
        long value = 0;
        for (int i = start; i < end; i++) {
            value <<= 8;
            value ^= (long) bytes[i] & 0xFF;
        }
        return value;
    }

    public static List<String> toString(byte[] bytes) {
        return toString(bytes, 0, bytes.length);
    }

    public static List<String> toString(byte[] bytes, int start, int end) {
        List<String> list = new ArrayList<String>();
        for (int i = start; i < end; i++) {
            list.add(Integer.toHexString(bytes[i] & 0xFF).toUpperCase(Locale.US));
        }
        return list;
    }

    public WebSocketApplication getApplication(HttpRequestPacket request) {
        // try the Mapper first...
        MappingData data = new MappingData();
        try {
            mapper.mapUriWithSemicolon(request.serverName(),
                    request.getRequestURIRef().getDecodedRequestURIBC(),
                                data,
                                0);
            if (data.wrapper != null) {
                return (WebSocketApplication) data.wrapper;
            }
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, e.toString(), e);
            }
        }

        for (WebSocketApplication application : applications) {
            if (application.upgrade(request)) {
                return application;
            }
        }
        return null;
    }

    public boolean upgrade(FilterChainContext ctx, HttpContent requestContent) throws IOException {
        final HttpRequestPacket request = (HttpRequestPacket) requestContent.getHttpHeader();
        final WebSocketApplication app = WebSocketEngine.getEngine().getApplication(request);
        WebSocket socket = null;
        try {
            if (app != null) {
                final ProtocolHandler protocolHandler = loadHandler(request.getHeaders());
                if (protocolHandler == null) {
                    handleUnsupportedVersion(ctx, request);
                    return false;
                }
                final Connection connection = ctx.getConnection();
                protocolHandler.setFilterChainContext(ctx);
                protocolHandler.setConnection(connection);
                socket = app.createSocket(protocolHandler, request, app);
                WebSocketHolder holder =
                        WebSocketEngine.getEngine().setWebSocketHolder(connection, protocolHandler, socket);
                holder.application = app;
                protocolHandler.handshake(ctx, app, requestContent);
                request.getConnection().addCloseListener(new CloseListener() {
                    @Override
                    public void onClosed(final Connection connection,
                            final CloseType type) throws IOException {
                        
                        final WebSocket webSocket = getWebSocket(connection);
                        webSocket.close();
                        webSocket.onClose(new ClosingFrame(WebSocket.END_POINT_GOING_DOWN,
                            "Close detected on connection"));
                    }
                });
                socket.onConnect();
                return true;
            }
        } catch (HandshakeException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            if (socket != null) {
                socket.close();
            }
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
    public synchronized void register(String contextPath, String urlPattern, WebSocketApplication app) {
        String contextPathLocal = getContextPath(contextPath);
        mapper.addContext("localhost", contextPathLocal, app, null, null);
        mapper.addWrapper("localhost", contextPathLocal, urlPattern, app);
        applicationMap.put(app, contextPathLocal + '|' + urlPattern);
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
        String pattern = applicationMap.remove(app);
        if (pattern != null) {
            String[] parts = pattern.split("\\|");
            mapper.removeWrapper("localhost", parts[0], parts[1]);
            List<WebSocketApplication> apps = contextApplications.get(parts[0]);
            apps.remove(app);
            if (apps.isEmpty()) {
                mapper.removeContext("localhost", parts[0]);
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
        contextApplications.clear();
        applications.clear();
        mapper = new Mapper();
        mapper.setDefaultHostName("localhost");
    }

    /**
     * Returns <tt>true</tt> if passed Grizzly {@link Connection} is associated with a {@link WebSocket}, or
     * <tt>false</tt> otherwise.
     *
     * @param connection Grizzly {@link Connection}.
     *
     * @return <tt>true</tt> if passed Grizzly {@link Connection} is associated with a {@link WebSocket}, or
     *         <tt>false</tt> otherwise.
     */
    boolean webSocketInProgress(Connection connection) {
        return webSocketAttribute.get(connection) != null;
    }

    /**
     * Get the {@link WebSocket} associated with the Grizzly {@link Connection}, or <tt>null</tt>, if there none is
     * associated.
     *
     * @param connection Grizzly {@link Connection}.
     *
     * @return the {@link WebSocket} associated with the Grizzly {@link Connection}, or <tt>null</tt>, if there none is
     *         associated.
     */
    public WebSocket getWebSocket(Connection connection) {
        final WebSocketHolder holder = getWebSocketHolder(connection);
        return holder == null ? null : holder.webSocket;
    }

    public WebSocketHolder getWebSocketHolder(final Connection connection) {
        return webSocketAttribute.get(connection);
    }

    public WebSocketHolder setWebSocketHolder(final Connection connection, ProtocolHandler handler, WebSocket socket) {
        final WebSocketHolder holder = new WebSocketHolder(handler, socket);
        webSocketAttribute.set(connection, holder);
        return holder;
    }

    private static void handleUnsupportedVersion(final FilterChainContext ctx,
                                                 final HttpRequestPacket request)
    throws IOException {
        HttpResponsePacket response = HttpResponsePacket.builder(request).build();
        response.setStatus(HttpStatus.BAD_REQUEST_400);
        response.addHeader(WebSocketEngine.SEC_WS_VERSION, Version.getSupportedWireProtocolVersions());
        ctx.write(response);
    }


    private static String getContextPath(String mapping) {
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

    /**
     * WebSocketHolder object, which gets associated with the Grizzly {@link Connection}.
     */
    public final static class WebSocketHolder {
        public volatile WebSocket webSocket;
        public volatile HandShake handshake;
        public volatile WebSocketApplication application;
        public volatile Buffer buffer;
        public volatile ProtocolHandler handler;

        WebSocketHolder(final ProtocolHandler handler, final WebSocket socket) {
            this.handler = handler;
            webSocket = socket;
        }
    }
}
