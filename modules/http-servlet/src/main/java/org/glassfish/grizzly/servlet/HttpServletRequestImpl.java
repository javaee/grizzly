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
import java.util.List;
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
import org.glassfish.grizzly.http.server.Constants;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Session;
import org.glassfish.grizzly.http.server.util.Enumerator;
import org.glassfish.grizzly.http.util.StringManager;
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
public class HttpServletRequestImpl implements HttpServletRequest {
    private static final Logger logger = Grizzly.logger(HttpServletRequestImpl.class);
      
    private final ServletInputStreamImpl inputStream;
    private ServletReaderImpl reader;
    
    private HttpSessionImpl httpSession = null;

    private ServletContextImpl contextImpl;        
        
    private String servletPath = "";
    
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

    public void initialize(Request request) throws IOException {
        this.request = request;
        inputStream.initialize(request);
    }

    // ----------------------------------------------- Class/Instance Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package, Request.class.getClassLoader());


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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (String)AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        request.setCharacterEncoding(env);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getContentLength() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getContentLength();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getContentType();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (usingReader)
            throw new IllegalStateException
                (sm.getString("request.getInputStream.ise"));

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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (String)AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return new Enumerator((Set) AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        String[] ret = null;

        /*
         * Clone the returned array only if there is a security manager
         * in place, so that performance won't suffer in the nonsecure case
         */
        if (System.getSecurityManager() != null){
            ret = (String[]) AccessController.doPrivileged(
                new GetParameterValuePrivilegedAction(name));
            if (ret != null) {
                ret = (String[]) ret.clone();
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Map)AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getProtocol().getProtocolString();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getScheme() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getScheme();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getServerName() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getServerName();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getServerPort() {

        if (request == null) {
           throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getServerPort();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public BufferedReader getReader() throws IOException {
        if (usingInputStream)
            throw new IllegalStateException
                (sm.getString("request.getReader.ise"));

        usingReader = true;
        //inputBuffer.checkConverter();
        if (reader == null) {
            reader = new ServletReaderImpl(request.getReader(false));
        }
        
        return reader;

    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteAddr() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRemoteAddr();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteHost() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRemoteHost();
    }


    @Override
    public void setAttribute(String name, Object value) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        Object oldValue = request.getAttribute(name);
        request.setAttribute(name, value);
        
        List listeners = contextImpl.getListeners();
        if (listeners.isEmpty())
            return;
        ServletRequestAttributeEvent event = null;

        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners.get(i);
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }
        Object value = request.getAttribute(name);
        request.removeAttribute(name);

        // Notify interested application event listeners
        List listeners = contextImpl.getListeners();
        if (listeners.isEmpty())
            return;
        ServletRequestAttributeEvent event = null;
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners.get(i);
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Locale)AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.isSecure();
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public String getRealPath(String path) {
        return contextImpl.getRealPath(path);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getAuthType() {
        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getAuthType();
    }


    @SuppressWarnings("unchecked")
    public Cookie[] getGrizzlyCookies() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        Cookie[] ret = null;

        /*
         * Clone the returned array only if there is a security manager
         * in place, so that performance won't suffer in the nonsecure case
         */
        if (System.getSecurityManager() != null){
            ret = (Cookie[])AccessController.doPrivileged(
                new GetCookiesPrivilegedAction());
            if (ret != null) {
                ret = (Cookie[]) ret.clone();
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getDateHeader(name);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getIntHeader(name);
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getMethod() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getMethod().getMethodString();
    }


    
    /**
     * {@inheritDoc}
     */    
    @Override
    public String getPathInfo(){
        if (request == null){
            throw new IllegalStateException(
                    sm.getString("requestFacade.nullRequest"));
        }

        String path = request.getRequestURI();
        StringBuilder pathToRemove = new StringBuilder();
        pathToRemove.append(contextImpl.getContextPath()); 
        pathToRemove.append(getServletPath());
        String s = pathToRemove.toString();
        if (path.startsWith(s)){
            String pathInfo = path.substring(s.length());
            return "".equals(pathInfo) ? null : pathInfo;
        } else {
            throw new IllegalStateException("Request path not in servlet context.");
        }
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
        return contextImpl.getContextPath();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getQueryString() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getQueryString();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRemoteUser() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getUserPrincipal();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestedSessionId() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRequestedSessionId();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getRequestURI() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRequestURI();
    }


    
    /**
     * {@inheritDoc}
     */
    @Override
    public StringBuffer getRequestURL() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
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
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRemotePort();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalName() {
        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getLocalName();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalAddr() {
        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getLocalAddr();    
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getLocalPort() {
        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getLocalPort();  
    }


    /**
     * Return the underlying {@link ServletContextImpl}
     * @return  Return the underlying {@link ServletContextImpl}
     */
    protected ServletContextImpl getContextImpl() {
        return contextImpl;
    }

    
     /**
     * Set the underlying {@link ServletContextImpl}
     * @param contextImpl the underlying {@link ServletContextImpl}
     */
    protected void setContextImpl(ServletContextImpl contextImpl) {
        this.contextImpl = contextImpl;
    }
    
  
    /**
     * Programmatically set the servlet path value. Default is an empty String.
     * @param servletPath Servlet path to set.
     */
    protected void setServletPath(String servletPath){
        this.servletPath = servletPath;
    }    
    // ----------------------------------------------------------- DoPrivileged
    
    private final class GetAttributePrivilegedAction
            implements PrivilegedAction {
        
        @Override
        public Object run() {
            return request.getAttributeNames();
        }            
    }
     
    
    private final class GetParameterMapPrivilegedAction
            implements PrivilegedAction {
        
        @Override
        public Object run() {
            return request.getParameterMap();
        }        
    }    
    
    
    private final class GetRequestDispatcherPrivilegedAction
            implements PrivilegedAction {

        private String path;

        public GetRequestDispatcherPrivilegedAction(String path){
            this.path = path;
        }
        
        @Override
        public Object run() {   
            throw new UnsupportedOperationException("Not supported yet.");
        }           
    }    
    
    
    private final class GetParameterPrivilegedAction
            implements PrivilegedAction {

        public String name;

        public GetParameterPrivilegedAction(String name){
            this.name = name;
        }

        @Override
        public Object run() {       
            return request.getParameter(name);
        }           
    }    
    
     
    private final class GetParameterNamesPrivilegedAction
            implements PrivilegedAction {
        
        @Override
        public Object run() {          
            return request.getParameterNames();
        }           
    } 
    
    
    private final class GetParameterValuePrivilegedAction
            implements PrivilegedAction {

        public String name;

        public GetParameterValuePrivilegedAction(String name){
            this.name = name;
        }

        @Override
        public Object run() {       
            return request.getParameterValues(name);
        }           
    }    
  
    
    private final class GetCookiesPrivilegedAction
            implements PrivilegedAction {
        
        @Override
        public Object run() {       
            return request.getCookies();
        }           
    }      
    
    
    private final class GetCharacterEncodingPrivilegedAction
            implements PrivilegedAction {
        
        @Override
        public Object run() {       
            return request.getCharacterEncoding();
        }           
    }   
        
    
    private final class GetHeadersPrivilegedAction
            implements PrivilegedAction {

        private String name;

        public GetHeadersPrivilegedAction(String name){
            this.name = name;
        }
        
        @Override
        public Object run() {       
            return request.getHeaders(name);
        }           
    }    
        
    
    private final class GetHeaderNamesPrivilegedAction
            implements PrivilegedAction {

        @Override
        public Object run() {       
            return request.getHeaderNames();
        }           
    }  
            
    
    private final class GetLocalePrivilegedAction
            implements PrivilegedAction {

        @Override
        public Object run() {       
            return request.getLocale();
        }           
    }    
            
    
    private final class GetLocalesPrivilegedAction
            implements PrivilegedAction {

        @Override
        public Object run() {       
            return request.getLocales();
        }           
    }    
    
    private final class GetSessionPrivilegedAction
            implements PrivilegedAction {

        private boolean create;
        
        public GetSessionPrivilegedAction(boolean create){
            this.create = create;
        }
                
        @Override
        public Object run() {  
            throw new UnsupportedOperationException("Not supported yet.");
        }           
    }


}
