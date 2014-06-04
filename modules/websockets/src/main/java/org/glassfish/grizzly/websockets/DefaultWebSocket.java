/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.Constants;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.servlet.HttpServletRequestImpl;
import org.glassfish.grizzly.servlet.HttpServletResponseImpl;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.websockets.draft06.ClosingFrame;
import org.glassfish.grizzly.websockets.frametypes.PingFrameType;
import org.glassfish.grizzly.websockets.frametypes.PongFrameType;
import org.glassfish.grizzly.websockets.glassfish.GlassfishSupport;

@SuppressWarnings({"StringContatenationInLoop"})
public class DefaultWebSocket implements WebSocket {
    private static final Logger LOGGER = Grizzly.logger(DefaultWebSocket.class);
    
    private final Queue<WebSocketListener> listeners =
            new ConcurrentLinkedQueue<WebSocketListener>();
    
    protected final ProtocolHandler protocolHandler;
    protected final HttpServletRequest servletRequest;

    protected Broadcaster broadcaster = new DummyBroadcaster();
    
    enum State {
        NEW, CONNECTED, CLOSING, CLOSED
    }

    EnumSet<State> connected = EnumSet.<State>range(State.CONNECTED, State.CLOSING);
    private final AtomicReference<State> state = new AtomicReference<State>(State.NEW);
    
    public DefaultWebSocket(final ProtocolHandler protocolHandler,
                            final WebSocketListener... listeners) {
        this(protocolHandler, null, listeners);
    }

    public DefaultWebSocket(final ProtocolHandler protocolHandler,
                            final HttpRequestPacket request,
                            final WebSocketListener... listeners) {
        
        this.protocolHandler = protocolHandler;
        final FilterChainContext ctx = protocolHandler.getFilterChainContext();
        
        if (ctx != null) { // ctx != null means server side.
            final WSRequestImpl grizzlyRequest = new WSRequestImpl();
            final Response grizzlyResponse = grizzlyRequest.getResponse();

            grizzlyRequest.initialize(request, ctx, null);
            grizzlyResponse.initialize(grizzlyRequest, request.getResponse(),
                    ctx, null, null);

            try {
                // Has to be called before servlet request/response wrappers initialization
                grizzlyRequest.parseSessionId();
                
                final WSServletRequestImpl grizzlyServletRequest =
                        new WSServletRequestImpl();
                final WSServletResponseImpl grizzlyServletResponse =
                        new WSServletResponseImpl();
                
                final WebSocketMappingData mappingData =
                        protocolHandler.getMappingData();
                
                grizzlyServletRequest.initialize(grizzlyRequest,
                        grizzlyServletResponse, mappingData);
                grizzlyServletResponse.initialize(grizzlyResponse, grizzlyServletRequest);
                
                servletRequest = grizzlyServletRequest;
            } catch (IOException e) {
                throw new IllegalStateException("Unexpected exception", e);
            }
        } else {
            servletRequest = null;
        }
        
        for (WebSocketListener listener : listeners) {
            add(listener);
        }
        
        protocolHandler.setWebSocket(this);
    }

    /**
     * Returns the upgrade request for this WebSocket.  
     * 
     * @return the upgrade request for this {@link WebSocket}.  This method
     *  may return <code>null</code> depending on the context under which this
     *  {@link WebSocket} was created.
     */
    public HttpServletRequest getUpgradeRequest() {
        return servletRequest;
    }
    
    public Collection<WebSocketListener> getListeners() {
        return listeners;
    }

    @Override
    public final boolean add(WebSocketListener listener) {
        return listeners.add(listener);
    }

    @Override
    public final boolean remove(WebSocketListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public boolean isConnected() {
        return connected.contains(state.get());
    }

    public void setClosed() {
        state.set(State.CLOSED);
    }

    @Override
    public void onClose(final DataFrame frame) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            final ClosingFrame closing = (ClosingFrame) frame;
            protocolHandler.close(closing.getCode(), closing.getTextPayload());
        } else {
            state.set(State.CLOSED);
            protocolHandler.doClose();
        }
        
        WebSocketListener listener;
        while ((listener = listeners.poll()) != null) {
            listener.onClose(this, frame);
        }
    }

    @Override
    public void onConnect() {
        state.set(State.CONNECTED);
        
        for (WebSocketListener listener : listeners) {
            listener.onConnect(this);
        }
    }

    @Override
    public void onFragment(boolean last, byte[] fragment) {
        for (WebSocketListener listener : listeners) {
            listener.onFragment(this, fragment, last);
        }
    }

    @Override
    public void onFragment(boolean last, String fragment) {
        for (WebSocketListener listener : listeners) {
            listener.onFragment(this, fragment, last);
        }
    }

    @Override
    public void onMessage(byte[] data) {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, data);
        }
    }

    @Override
    public void onMessage(String text) {
        for (WebSocketListener listener : listeners) {
            listener.onMessage(this, text);
        }
    }

    @Override
    public void onPing(DataFrame frame) {
        send(new DataFrame(new PongFrameType(), frame.getBytes()));
        for (WebSocketListener listener : listeners) {
            listener.onPing(this, frame.getBytes());
        }
    }

    @Override
    public void onPong(DataFrame frame) {
        for (WebSocketListener listener : listeners) {
            listener.onPong(this, frame.getBytes());
        }
    }

    @Override
    public void close() {
        close(NORMAL_CLOSURE, null);
    }

    @Override
    public void close(int code) {
        close(code, null);
    }

    @Override
    public void close(int code, String reason) {
        if (state.compareAndSet(State.CONNECTED, State.CLOSING)) {
            protocolHandler.close(code, reason);
        }
    }

    @Override
    public GrizzlyFuture<DataFrame> send(byte[] data) {
        if (isConnected()) {
            return protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public GrizzlyFuture<DataFrame> send(String data) {
        if (isConnected()) {
            return protocolHandler.send(data);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public void broadcast(Iterable<? extends WebSocket> recipients,
            String data) {
        if (state.get() == State.CONNECTED) {
            broadcaster.broadcast(recipients, data);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    @Override
    public void broadcast(Iterable<? extends WebSocket> recipients,
            byte[] data) {
        if (state.get() == State.CONNECTED) {
            broadcaster.broadcast(recipients, data);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    @Override
    public void broadcastFragment(Iterable<? extends WebSocket> recipients,
            String data, boolean last) {
        if (state.get() == State.CONNECTED) {
            broadcaster.broadcastFragment(recipients, data, last);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    @Override
    public void broadcastFragment(Iterable<? extends WebSocket> recipients,
            byte[] data, boolean last) {
        if (state.get() == State.CONNECTED) {
            broadcaster.broadcastFragment(recipients, data, last);
        } else {
            throw new RuntimeException("Socket is already closed.");
        }
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<DataFrame> sendPing(byte[] data) {
        return send(new DataFrame(new PingFrameType(), data));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<DataFrame> sendPong(byte[] data) {
        return send(new DataFrame(new PongFrameType(), data));
    }

    private GrizzlyFuture<DataFrame> send(DataFrame frame) {
        if (isConnected()) {
            return protocolHandler.send(frame);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public GrizzlyFuture<DataFrame> stream(boolean last, String fragment) {
        if (isConnected()) {
            return protocolHandler.stream(last, fragment);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    @Override
    public GrizzlyFuture<DataFrame> stream(boolean last, byte[] bytes, int off, int len) {
        if (isConnected()) {
            return protocolHandler.stream(last, bytes, off, len);
        } else {
            throw new RuntimeException("Socket is not connected.");
        }
    }

    protected byte[] toRawData(String text) {
        return toRawData(text, true);
    }

    protected byte[] toRawData(byte[] binary) {
        return toRawData(binary, true);
    }

    protected byte[] toRawData(String fragment, boolean last) {
        final DataFrame dataFrame = protocolHandler.toDataFrame(fragment, last);
        return protocolHandler.frame(dataFrame);
    }

    protected byte[] toRawData(byte[] binary, boolean last) {
        final DataFrame dataFrame = protocolHandler.toDataFrame(binary, last);
        return protocolHandler.frame(dataFrame);
    }

    @SuppressWarnings("unchecked")
    protected void sendRaw(byte[] rawData) {
        final Connection connection = protocolHandler.getConnection();
        final MemoryManager mm = connection.getTransport().getMemoryManager();
        final Buffer buffer = Buffers.wrap(mm, rawData);
        buffer.allowBufferDispose(false);
        
        connection.write(buffer);
    }

    protected Broadcaster getBroadcaster() {
        return broadcaster;
    }

    protected void setBroadcaster(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }
    
    private class WSRequestImpl extends Request {

        public WSRequestImpl() {
            super(new WSResponseImpl());
        }

        /**
         * Make method visible for websockets
         */
        @Override
        protected void parseSessionId() {
            // Try to get session id from request-uri
            super.parseSessionId();

            // Try to get session id from cookie
            Cookie[] parsedCookies = getCookies();
            if (parsedCookies != null) {
                for (Cookie c : parsedCookies) {
                    if (Constants.SESSION_COOKIE_NAME.equals(c.getName())) {
                        setRequestedSessionId(c.getValue());
                        setRequestedSessionCookie(true);
                        break;
                    }
                }
            }
        }
    } // END WSRequestImpl    
    
    private class WSResponseImpl extends Response {

        public WSResponseImpl() {
        }
    } // END WSResponseImpl 
    
    private class WSServletRequestImpl extends HttpServletRequestImpl {

        private GlassfishSupport glassfishSupport;
        private String pathInfo;
        private String servletPath;
        private String contextPath;
        private boolean isUserPrincipalUpdated;
        
        private BufferedReader reader;
        
        public void initialize(final Request request,
                final HttpServletResponseImpl servletResponse,
                final WebSocketMappingData mappingData) throws IOException {
            
            if (mappingData != null) {
                updatePaths(mappingData);
            } else {
                contextPath = request.getContextPath();
            }
            
            if (mappingData != null && mappingData.isGlassfish) {
                glassfishSupport = new GlassfishSupport(mappingData.context,
                        mappingData.wrapper, this);
            } else {
                glassfishSupport = new GlassfishSupport();
            }
            
            super.initialize(request, servletResponse,
                    new WebappContext("web-socket-ctx", contextPath));
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (usingReader)
                throw new IllegalStateException("Illegal attempt to call getInputStream() after getReader() has already been called.");

            usingInputStream = true;
            return Utils.NULL_SERVLET_INPUT_STREAM;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (usingInputStream)
                throw new IllegalStateException("Illegal attempt to call getReader() after getInputStream() has already been called.");

            usingReader = true;
            //inputBuffer.checkConverter();
            if (reader == null) {
                reader = new BufferedReader(Utils.NULL_READER);
            }

            return reader;
        }

        @Override
        protected void initSession() {
            if (!glassfishSupport.isValid()) {
                super.initSession();
            }
        }

        @Override
        public HttpSession getSession(boolean create) {
            if (glassfishSupport.isValid()) {
                return glassfishSupport.getSession(create);
            }

            return super.getSession(create);
        }

        @Override
        public boolean isUserInRole(String role) {
            if (glassfishSupport.isValid()) {
                return glassfishSupport.isUserInRole(role);
            }

            return super.isUserInRole(role);
        }

        @Override
        public Principal getUserPrincipal() {
            checkGlassfishAuth();

            return super.getUserPrincipal();
        }

        @Override
        public String getRemoteUser() {
            checkGlassfishAuth();

            return super.getRemoteUser();
        }

        @Override
        public String getAuthType() {
            checkGlassfishAuth();

            return super.getAuthType();
        }

        @Override
        public String getContextPath() {
            return contextPath;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }
        
        private void updatePaths(final WebSocketMappingData mappingData) {
            
            pathInfo = mappingData.pathInfo.toString();
            servletPath = mappingData.wrapperPath.toString();
            contextPath = mappingData.contextPath.toString();
        }

        private void checkGlassfishAuth() {
            if (glassfishSupport.isValid() && !isUserPrincipalUpdated) {
                isUserPrincipalUpdated = true;
                glassfishSupport.updateUserPrincipal(WSServletRequestImpl.this.request);
            }
        }
    } // END WSServletRequestImpl
    
    private static class WSServletResponseImpl extends HttpServletResponseImpl {
        private PrintWriter writer;
        
        @Override
        public PrintWriter getWriter() throws IOException {
            if (usingOutputStream)
                throw new IllegalStateException("Illegal attempt to call getWriter() after getOutputStream has already been called.");

            usingWriter = true;
            if (writer == null) {
                writer = new PrintWriter(Utils.NULL_WRITER);
            }

            return writer;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (usingWriter)
                throw new IllegalStateException("Illegal attempt to call getOutputStream() after getWriter() has already been called.");

            usingOutputStream = true;
            return Utils.NULL_SERVLET_OUTPUT_STREAM;
        }
        
    } // END WSServletResponseImpl
}