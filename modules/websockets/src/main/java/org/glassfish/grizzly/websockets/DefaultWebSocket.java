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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.logging.Logger;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.servlet.HttpServletRequestImpl;
import org.glassfish.grizzly.servlet.HttpServletResponseImpl;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.websockets.glassfish.GlassfishSupport;

@SuppressWarnings({"StringContatenationInLoop"})
public class DefaultWebSocket extends SimpleWebSocket {
    private static final Logger LOGGER = Grizzly.logger(DefaultWebSocket.class);

    protected final HttpServletRequest servletRequest;

    public DefaultWebSocket(final ProtocolHandler protocolHandler,
                            final HttpRequestPacket request,
                            final WebSocketListener... listeners) {

        super(protocolHandler, listeners);
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


    // ----------------------------------------------------------- Inner Classes

    
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
                final String sessionCookieNameLocal = obtainSessionCookieName();
                for (Cookie c : parsedCookies) {
                    if (sessionCookieNameLocal.equals(c.getName())) {
                        setRequestedSessionId(c.getValue());
                        setRequestedSessionCookie(true);
                        break;
                    }
                }
            }
        }
    } // END WSRequestImpl    
    
    private static class WSResponseImpl extends Response {

        public WSResponseImpl() {
        }
    } // END WSResponseImpl 
    
    private static class WSServletRequestImpl extends HttpServletRequestImpl {

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
