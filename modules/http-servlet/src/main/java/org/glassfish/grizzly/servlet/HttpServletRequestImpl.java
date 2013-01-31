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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletInputStream;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Session;
import org.glassfish.grizzly.http.server.util.Enumerator;
import org.glassfish.grizzly.localization.LogMessages;

/**
 * Facade class that wraps a {@link Request} request object.
 * All methods are delegated to the wrapped request.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @author Jean-Francois Arcand
 * @version $Revision: 1.7 $ $Date: 2007/08/01 19:04:28 $
 */
@SuppressWarnings("deprecation")
public class HttpServletRequestImpl implements HttpServletRequest, Holders.RequestHolder {
    private static final Logger logger = Grizzly.logger(HttpServletRequestImpl.class);
      
    private final ServletInputStreamImpl inputStream;
    private ServletReaderImpl reader;
    
    private HttpSessionImpl httpSession = null;

    private WebappContext contextImpl;
        
    private String contextPath = "";
    private String servletPath = "";

    private String pathInfo;
    
    /**
     * Using stream flag.
     */
    protected boolean usingInputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingReader = false;
    
    private static final ThreadCache.CachedTypeIndex<HttpServletRequestImpl> CACHE_IDX =
            ThreadCache.obtainIndex(HttpServletRequestImpl.class, 2);

    // ------------- Factory ----------------
    public static HttpServletRequestImpl create() {
        final HttpServletRequestImpl request =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (request != null) {
            return request;
        }

        return new HttpServletRequestImpl();
    }

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a wrapper for the specified request.
     *
     * @throws IOException if an input/output error occurs
     */
    protected HttpServletRequestImpl() {
        this.inputStream = new ServletInputStreamImpl();
    }

    public void initialize(Request request, WebappContext context) throws IOException {
        this.request = request;
        request.getInputBuffer().setAsyncEnabled(false); // switch Grizzly input to blocking mode by default
        inputStream.initialize(request);
        contextImpl = context;
    }

    // ----------------------------------------------- Class/Instance Variables

    /**
     * The wrapped request.
     */
    protected Request request = null;


    // --------------------------------------------------------- Public Methods
   
    
    /**
    * Prevent cloning the facade.
    */
    @Override
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
    
    
    // ------------------------------------------------- ServletRequest Methods


    
    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String name) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getAttribute(name);
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Enumeration getAttributeNames() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null){
            return AccessController.doPrivileged(
                new GetAttributePrivilegedAction());        
        } else {
            return new Enumerator(request.getAttributeNames());
        }
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public String getCharacterEncoding() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null){
            return AccessController.doPrivileged(
                new GetCharacterEncodingPrivilegedAction());
        } else {
            return request.getCharacterEncoding();
        }         
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setCharacterEncoding(String env)
            throws java.io.UnsupportedEncodingException {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        request.setCharacterEncoding(env);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getContentLength() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getContentLength();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getContentType();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (usingReader)
            throw new IllegalStateException("Illegal attempt to call getInputStream() after getReader() has already been called.");

        usingInputStream = true;

        return inputStream;
    }

    void recycle(){
        request = null;
        reader = null;
        
        inputStream.recycle();

        usingInputStream = false;
        usingReader = false;
    }
    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public String getParameter(String name) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null){
            return AccessController.doPrivileged(
                new GetParameterPrivilegedAction(name));
        } else {
            return request.getParameter(name);
        }
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Enumeration getParameterNames() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null){
            return new Enumerator(AccessController.doPrivileged(
                new GetParameterNamesPrivilegedAction()));
        } else {
            return new Enumerator(request.getParameterNames());
        }
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public String[] getParameterValues(String name) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        String[] ret;

        /*
         * Clone the returned array only if there is a security manager
         * in place, so that performance won't suffer in the nonsecure case
         */
        if (System.getSecurityManager() != null){
            ret = AccessController.doPrivileged(
                new GetParameterValuePrivilegedAction(name));
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = request.getParameterValues(name);
        }

        return ret;
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Map getParameterMap() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null){
            return AccessController.doPrivileged(
                new GetParameterMapPrivilegedAction());        
        } else {
            return request.getParameterMap();
        }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String getProtocol() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getProtocol().getProtocolString();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getScheme() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getScheme();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getServerName() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getServerName();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getServerPort() {

        if (request == null) {
           throw new IllegalStateException("Null request object");
        }

        return request.getServerPort();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public BufferedReader getReader() throws IOException {
        if (usingInputStream)
            throw new IllegalStateException("Illegal attempt to call getReader() after getInputStream() has already been called.");

        usingReader = true;
        //inputBuffer.checkConverter();
        if (reader == null) {
            reader = new ServletReaderImpl(request.getReader());
        }
        
        return reader;

    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteAddr() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getRemoteAddr();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteHost() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getRemoteHost();
    }


    @Override
    public void setAttribute(String name, Object value) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        Object oldValue = request.getAttribute(name);
        request.setAttribute(name, value);
        
        EventListener[] listeners = contextImpl.getEventListeners();
        if (listeners.length == 0)
            return;
        ServletRequestAttributeEvent event = null;

        for (int i = 0, len = listeners.length; i < len; i++) {
            if (!(listeners[i] instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners[i];
            try {
                if (event == null) {
                    if (oldValue != null)
                        event =
                            new ServletRequestAttributeEvent(contextImpl,
                                                             this, name, oldValue);
                    else
                        event =
                            new ServletRequestAttributeEvent(contextImpl,
                                                             this, name, value);
                }
                if (oldValue != null) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_ATTRIBUTE_LISTENER_ADD_ERROR("ServletRequestAttributeListener", listener.getClass().getName()),
                               t);
                }
            }
        }
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(String name) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }
        Object value = request.getAttribute(name);
        request.removeAttribute(name);

        // Notify interested application event listeners
        EventListener[] listeners = contextImpl.getEventListeners();
        if (listeners.length == 0)
            return;
        ServletRequestAttributeEvent event = null;
        for (int i = 0, len = listeners.length; i < len; i++) {
            if (!(listeners[i] instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners[i];
            try {
                if (event == null) {
                    event = new ServletRequestAttributeEvent(contextImpl,
                            this, name, value);
                }
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_ATTRIBUTE_LISTENER_REMOVE_ERROR("ServletRequestAttributeListener", listener.getClass().getName()),
                           t);
            }
        }
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Locale getLocale() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null){
            return AccessController.doPrivileged(
                new GetLocalePrivilegedAction());
        } else {
            return request.getLocale();
        }        
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Enumeration getLocales() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(
                new GetLocalesPrivilegedAction());
        } else {
            return new Enumerator(request.getLocales());
        }        
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSecure() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.isSecure();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings( "unchecked" )
    public RequestDispatcher getRequestDispatcher( String path ) {
        if( request == null ) {
            throw new IllegalStateException("Null request object");
        }

        if( System.getSecurityManager() != null ) {
            return AccessController.doPrivileged(
                    new GetRequestDispatcherPrivilegedAction( path ) );
        } else {
            return getRequestDispatcherInternal( path );
        }
    }


    private RequestDispatcher getRequestDispatcherInternal( String path ) {
        if( contextImpl == null ) {
            return null;
        }

        // If the path is already context-relative, just pass it through
        if( path == null ) {
            return ( null );
        } else if( path.startsWith( "/" ) ) {
            return ( contextImpl.getRequestDispatcher( path ) );
        }

        // Convert a request-relative path to a context-relative one
        String servletPath = (String)getAttribute( DispatcherConstants.INCLUDE_SERVLET_PATH );
        if( servletPath == null ) {
            servletPath = getServletPath();
        }

        // Add the path info, if there is any
        String pathInfo = getPathInfo();
        String requestPath = null;

        if( pathInfo == null ) {
            requestPath = servletPath;
        } else {
            requestPath = servletPath + pathInfo;
        }

        int pos = requestPath.lastIndexOf( '/' );
        String relative = null;
        if( pos >= 0 ) {
            relative = requestPath.substring( 0, pos + 1 ) + path;
        } else {
            relative = requestPath + path;
        }

        return contextImpl.getRequestDispatcher( relative );
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("deprecation")
    public String getRealPath(String path) {
        return contextImpl.getRealPath(path);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getAuthType() {
        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getAuthType();
    }


    @SuppressWarnings("unchecked")
    public Cookie[] getGrizzlyCookies() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        Cookie[] ret;

        /*
         * Clone the returned array only if there is a security manager
         * in place, so that performance won't suffer in the nonsecure case
         */
        if (System.getSecurityManager() != null){
            ret = AccessController.doPrivileged(
                new GetCookiesPrivilegedAction());
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = request.getCookies();
        }

        return ret;
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public long getDateHeader(String name) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getDateHeader(name);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(String name) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getHeader(name);
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Enumeration getHeaders(String name) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null){
            return AccessController.doPrivileged(
                new GetHeadersPrivilegedAction(name));
        } else {
            return new Enumerator(request.getHeaders(name).iterator());
        }         
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Enumeration getHeaderNames() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        if (System.getSecurityManager() != null){
            return AccessController.doPrivileged(
                new GetHeaderNamesPrivilegedAction());
        } else {
            return new Enumerator(request.getHeaderNames().iterator());
        }             
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getIntHeader(String name) {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getIntHeader(name);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getMethod() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getMethod().getMethodString();
    }


    
    /**
     * {@inheritDoc}
     */    
    @Override
    public String getPathInfo(){
        if (request == null){
            throw new IllegalStateException("Null request object");
        }

        return pathInfo;
    }


    
    /**
     * {@inheritDoc}
     */    
    @Override
    public String getPathTranslated() {
        if (getPathInfo() == null) {
            return (null);
        } else {
            return (contextImpl.getRealPath(getPathInfo()));
        }

    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getContextPath() {
        return contextPath;
    }

    
    protected void setContextPath(String contextPath) {
        if (contextPath == null) {
            this.contextPath = "";
        }
        this.contextPath = contextPath;
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getQueryString() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getQueryString();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteUser() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getRemoteUser();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isUserInRole(String role) {
        throw new IllegalStateException("Not yet implemented");

    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public java.security.Principal getUserPrincipal() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getUserPrincipal();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestedSessionId() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getRequestedSessionId();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestURI() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getRequestURI();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuffer getRequestURL() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return Request.appendRequestURL(request, new StringBuffer());
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getServletPath() {
        return servletPath;
    }

    
    /**
     * Initialize the session object if there is a valid session request
     */
    protected void initSession() {
        Session session = request.getSession(false);
        if (session != null) {
            httpSession = new HttpSessionImpl(contextImpl);
            httpSession.notifyNew();
            httpSession.setSession(session);
            httpSession.access();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpSession getSession(boolean create) {
        if (httpSession == null && create){
            httpSession = new HttpSessionImpl(contextImpl);
        }
        
        if (httpSession != null){
            Session session = request.getSession(create);
            if(session != null) {
                httpSession.setSession(session);
                httpSession.access();
            } else {
                return null;
            }
        }
        return httpSession;
    }


    
    /**
     * {@inheritDoc}
     */    
    @Override
    public HttpSession getSession() {

        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return getSession(true);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdValid() {
        return request.isRequestedSessionIdValid();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return request.isRequestedSessionIdFromCookie();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedSessionIdFromURL() {
        return request.isRequestedSessionIdFromURL();
    }


    
    /**
     * {@inheritDoc}
     */    
    @Override
    @SuppressWarnings({"deprecation"})
    public boolean isRequestedSessionIdFromUrl() {
        return isRequestedSessionIdFromURL();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public javax.servlet.http.Cookie[] getCookies() {
        final Cookie[] internalCookies = request.getCookies();
        if (internalCookies == null) {
            return null;
        }
        javax.servlet.http.Cookie[] cookies = new javax.servlet.http.Cookie[internalCookies.length];
        for (int i = 0; i < internalCookies.length; i++) {
            final Cookie cook = internalCookies[i];
            if (cook instanceof CookieWrapper) {
                cookies[i] = ((CookieWrapper) internalCookies[i]).getWrappedCookie();
            } else {
                cookies[i] = new javax.servlet.http.Cookie(cook.getName(), cook.getValue());
                cookies[i].setComment(cook.getComment());
                if (cook.getDomain() != null) {
                    cookies[i].setDomain(cook.getDomain());
                }
                cookies[i].setMaxAge(cook.getMaxAge());
                cookies[i].setPath(cook.getPath());
                cookies[i].setSecure(cook.isSecure());
                cookies[i].setVersion(cook.getVersion());
            }
        }
        return cookies;
    }

 
    
    /**
     * {@inheritDoc}
     */   
    @Override
    public int getRemotePort() {
        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getRemotePort();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalName() {
        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getLocalName();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalAddr() {
        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getLocalAddr();    
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getLocalPort() {
        if (request == null) {
            throw new IllegalStateException("Null request object");
        }

        return request.getLocalPort();  
    }


    /**
     * Return the underlying {@link WebappContext}
     * @return  Return the underlying {@link WebappContext}
     */
    protected WebappContext getContextImpl() {
        return contextImpl;
    }

    
     /**
     * Set the underlying {@link WebappContext}
     * @param contextImpl the underlying {@link WebappContext}
     */
    protected void setContextImpl(WebappContext contextImpl) {
        this.contextImpl = contextImpl;
    }
    
  
    /**
     * Programmatically set the servlet path value. Default is an empty String.
     * @param servletPath Servlet path to set.
     */
    public void setServletPath(final String servletPath){
        if (servletPath != null) {
            if (servletPath.length() == 0) {
                this.servletPath = "";
            }  else {
                this.servletPath = servletPath;
            }
        }
    }

    protected void setPathInfo(final String pathInfo) {
        this.pathInfo = pathInfo;
    }

    public Request getRequest() {
        return request;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Request getInternalRequest() {
        return request;
    }
    
    // ----------------------------------------------------------- DoPrivileged

    
    private final class GetAttributePrivilegedAction
            implements PrivilegedAction<Enumeration<String>> {
        
        @Override
        public Enumeration<String> run() {
            return new Enumerator<String>(request.getAttributeNames());
        }            
    }
     
    
    private final class GetParameterMapPrivilegedAction
            implements PrivilegedAction<Map<String, String[]>> {
        
        @Override
        public Map<String, String[]> run() {
            return request.getParameterMap();
        }        
    }    
    
    
    private final class GetRequestDispatcherPrivilegedAction
            implements PrivilegedAction<RequestDispatcher> {

        private final String path;

        public GetRequestDispatcherPrivilegedAction(String path){
            this.path = path;
        }
        
        @Override
        public RequestDispatcher run() {   
            return getRequestDispatcherInternal(path);
        }           
    }    
    
    
    private final class GetParameterPrivilegedAction
            implements PrivilegedAction<String> {

        public final String name;

        public GetParameterPrivilegedAction(String name){
            this.name = name;
        }

        @Override
        public String run() {       
            return request.getParameter(name);
        }           
    }    
    
     
    private final class GetParameterNamesPrivilegedAction
            implements PrivilegedAction<Set<String>> {
        
        @Override
        public Set<String> run() {          
            return request.getParameterNames();
        }           
    } 
    
    
    private final class GetParameterValuePrivilegedAction
            implements PrivilegedAction<String[]> {

        public final String name;

        public GetParameterValuePrivilegedAction(String name){
            this.name = name;
        }

        @Override
        public String[] run() {       
            return request.getParameterValues(name);
        }           
    }    
  
    
    private final class GetCookiesPrivilegedAction
            implements PrivilegedAction<Cookie[]> {
        
        @Override
        public Cookie[] run() {       
            return request.getCookies();
        }           
    }      
    
    
    private final class GetCharacterEncodingPrivilegedAction
            implements PrivilegedAction<String> {
        
        @Override
        public String run() {       
            return request.getCharacterEncoding();
        }           
    }   
        
    
    private final class GetHeadersPrivilegedAction
            implements PrivilegedAction<Enumeration<String>> {

        private final String name;

        public GetHeadersPrivilegedAction(String name){
            this.name = name;
        }
        
        @Override
        public Enumeration<String> run() {       
            return new Enumerator<String>(request.getHeaders(name));
        }           
    }    
        
    
    private final class GetHeaderNamesPrivilegedAction
            implements PrivilegedAction<Enumeration<String>> {

        @Override
        public Enumeration<String> run() {
            return new Enumerator<String>(request.getHeaderNames());
        }           
    }  
            
    
    private final class GetLocalePrivilegedAction
            implements PrivilegedAction<Locale> {

        @Override
        public Locale run() {       
            return request.getLocale();
        }           
    }    
            
    
    private final class GetLocalesPrivilegedAction
            implements PrivilegedAction<Enumeration<Locale>> {

        @Override
        public Enumeration<Locale> run() {       
            return new Enumerator<Locale>(request.getLocales());
        }           
    }    
    
//    private static final class GetSessionPrivilegedAction
//            implements PrivilegedAction {
//
//        private final boolean create;
//        
//        public GetSessionPrivilegedAction(boolean create){
//            this.create = create;
//        }
//                
//        @Override
//        public Object run() {  
//            throw new UnsupportedOperationException("Not supported yet.");
//        }           
//    }


}
