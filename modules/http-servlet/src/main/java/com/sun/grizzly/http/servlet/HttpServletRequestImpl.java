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

package com.sun.grizzly.http.servlet;

import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlySession;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.http.Cookie;
import com.sun.grizzly.util.res.StringManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletInputStream;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Facade class that wraps a {@link GrizzlyRequest} request object.  
 * All methods are delegated to the wrapped request.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @author Jean-Francois Arcand
 * @version $Revision: 1.7 $ $Date: 2007/08/01 19:04:28 $
 */
public class HttpServletRequestImpl implements HttpServletRequest {
      
    private ServletInputStreamImpl inputStream = null;
    
    private HttpSessionImpl httpSession = null;

    private ServletContextImpl contextImpl;        
        
    private String servletPath = "";
    
    private Logger logger = LoggerUtils.getLogger();

    
    // ----------------------------------------------------------- Constructors


    /**
     * Construct a wrapper for the specified request.
     *
     * @param request The request to be wrapped
     * @throws IOException if an input/output error occurs
     */
    public HttpServletRequestImpl(GrizzlyRequest request) throws IOException {
        this.request = request;
        this.inputStream = new ServletInputStreamImpl(request.createInputStream());
    }


    // ----------------------------------------------- Class/Instance Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package,
                                 Constants.class.getClassLoader());


    /**
     * The wrapped request.
     */
    protected GrizzlyRequest request = null;


    // --------------------------------------------------------- Public Methods
   
    
    /**
    * Prevent cloning the facade.
    */
    @Override
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
    
    
    /**
     * Clear facade.
     */
    public void clear() {
        request = null;
    }


    // ------------------------------------------------- ServletRequest Methods


    
    /**
     * {@inheritDoc}
     */
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
    public Enumeration getAttributeNames() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
                new GetAttributePrivilegedAction());        
        } else {
            return request.getAttributeNames();
        }
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
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
    public ServletInputStream getInputStream() throws IOException {

        if (inputStream == null) {
            inputStream = new ServletInputStreamImpl(request.createInputStream());
        }

        return inputStream;
    }

    /**
     * Underlying GrizzlyRequest could be recycled. For example InputStream is
     * not longer available. We need to update the ServletInputStream accordingly.
     */
    void update() throws IOException {
        if (inputStream != null) {
            inputStream.update(request.createInputStream());
        }
    }

    void recycle(){
        if(System.getSecurityManager() != null){
            inputStream = null;
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
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
    public Enumeration getParameterNames() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
                new GetParameterNamesPrivilegedAction());
        } else {
            return request.getParameterNames();
        }
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
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
    public String getProtocol() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getProtocol();
    }


    
    /**
     * {@inheritDoc}
     */
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
    public BufferedReader getReader() throws IOException {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getReader();
    }


    
    /**
     * {@inheritDoc}
     */
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
    public String getRemoteHost() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRemoteHost();
    }


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
    public Enumeration getLocales() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
                new GetLocalesPrivilegedAction());
        } else {
            return request.getLocales();
        }        
    }


    
    /**
     * {@inheritDoc}
     */
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
    public RequestDispatcher getRequestDispatcher(String path) {
        if (request == null) {
            throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null) {
            return (RequestDispatcher) AccessController.doPrivileged(
                    new GetRequestDispatcherPrivilegedAction(path));
        } else {
            return getRequestDispatcherInternal(path);
        }

    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    public String getRealPath(String path) {
        return contextImpl.getRealPath(path);
    }


    
    /**
     * {@inheritDoc}
     */
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
    public Enumeration getHeaders(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
                new GetHeadersPrivilegedAction(name));
        } else {
            return request.getHeaders(name);
        }         
    }


    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public Enumeration getHeaderNames() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (System.getSecurityManager() != null){
            return (Enumeration)AccessController.doPrivileged(
                new GetHeaderNamesPrivilegedAction());
        } else {
            return request.getHeaderNames();
        }             
    }


    
    /**
     * {@inheritDoc}
     */
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
    public String getMethod() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getMethod();
    }


    
    /**
     * {@inheritDoc}
     */    
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
    public String getContextPath() {
        return contextImpl.getContextPath();
    }

    
    /**
     * {@inheritDoc}
     */
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
    public boolean isUserInRole(String role) {
        throw new IllegalStateException("Not yet implemented");

    }


    
    /**
     * {@inheritDoc}
     */
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
    public StringBuffer getRequestURL() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRequestURL();
    }


    
    /**
     * {@inheritDoc}
     */
    public String getServletPath() {
        return servletPath;
    }

    
    /**
     * Initialize the session object if there is a valid session request
     */
    protected void initSession() {
        GrizzlySession session = request.getSession(false);
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
    public HttpSession getSession(boolean create) {
        if (httpSession == null && create){
            httpSession = new HttpSessionImpl(contextImpl);
        }
        
        if (httpSession != null){
            GrizzlySession session = request.getSession(create);
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
    public boolean isRequestedSessionIdValid() {
        return request.isRequestedSessionIdValid();
    }


    
    /**
     * {@inheritDoc}
     */
    public boolean isRequestedSessionIdFromCookie() {
        return request.isRequestedSessionIdFromCookie();
    }


    
    /**
     * {@inheritDoc}
     */
    public boolean isRequestedSessionIdFromURL() {
        return request.isRequestedSessionIdFromURL();
    }


    
    /**
     * {@inheritDoc}
     */    
    @SuppressWarnings({"deprecation", "deprecation"})
    public boolean isRequestedSessionIdFromUrl() {
        return isRequestedSessionIdFromURL();
    }

    
    /**
     * {@inheritDoc}
     */
    public javax.servlet.http.Cookie[] getCookies() {
        com.sun.grizzly.util.http.Cookie[] internalCookies = request.getCookies();
        if (internalCookies == null) {
            return null;
        }
        javax.servlet.http.Cookie[] cookies = new javax.servlet.http.Cookie[internalCookies.length];
        for (int i = 0; i < internalCookies.length; i++) {
            com.sun.grizzly.util.http.Cookie cook = internalCookies[i];
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
                cookies[i].setSecure(cook.getSecure());
                cookies[i].setVersion(cook.getVersion());
            }
        }
        return cookies;
    }

 
    
    /**
     * {@inheritDoc}
     */   
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

    private RequestDispatcher getRequestDispatcherInternal(String path) {
        if (contextImpl == null) {
            return null;
        }

        // If the path is already context-relative, just pass it through
        if (path == null) {
            return (null);
        } else if (path.startsWith("/")) {
            return (contextImpl.getRequestDispatcher(path));
        }

        // Convert a request-relative path to a context-relative one
        String servletPath = (String) getAttribute(DispatcherConstants.INCLUDE_SERVLET_PATH);
        if (servletPath == null) {
            servletPath = getServletPath();
        }

        // Add the path info, if there is any
        String pathInfo = getPathInfo();
        String requestPath = null;

        if (pathInfo == null) {
            requestPath = servletPath;
        } else {
            requestPath = servletPath + pathInfo;
        }

        int pos = requestPath.lastIndexOf('/');
        String relative = null;
        if (pos >= 0) {
            relative = requestPath.substring(0, pos + 1) + path;
        } else {
            relative = requestPath + path;
        }

        return contextImpl.getRequestDispatcher(relative);
    }


    // ----------------------------------------------------------- DoPrivileged
    
    private final class GetAttributePrivilegedAction
            implements PrivilegedAction {
        
        public Object run() {
            return request.getAttributeNames();
        }            
    }
     
    
    private final class GetParameterMapPrivilegedAction
            implements PrivilegedAction {
        
        public Object run() {
            return request.getParameterMap();
        }        
    }    
    
    
    private final class GetRequestDispatcherPrivilegedAction
            implements PrivilegedAction {

        private final String path;

        public GetRequestDispatcherPrivilegedAction(String path){
            this.path = path;
        }

        public Object run() {
            return getRequestDispatcherInternal(path);
        }           
    }    
    
    
    private final class GetParameterPrivilegedAction
            implements PrivilegedAction {

        public String name;

        public GetParameterPrivilegedAction(String name){
            this.name = name;
        }

        public Object run() {       
            return request.getParameter(name);
        }           
    }    
    
     
    private final class GetParameterNamesPrivilegedAction
            implements PrivilegedAction {
        
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

        public Object run() {       
            return request.getParameterValues(name);
        }           
    }    
  
    
    private final class GetCookiesPrivilegedAction
            implements PrivilegedAction {
        
        public Object run() {       
            return request.getCookies();
        }           
    }      
    
    
    private final class GetCharacterEncodingPrivilegedAction
            implements PrivilegedAction {
        
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
        
        public Object run() {       
            return request.getHeaders(name);
        }           
    }    
        
    
    private final class GetHeaderNamesPrivilegedAction
            implements PrivilegedAction {

        public Object run() {       
            return request.getHeaderNames();
        }           
    }  
            
    
    private final class GetLocalePrivilegedAction
            implements PrivilegedAction {

        public Object run() {       
            return request.getLocale();
        }           
    }    
            
    
    private final class GetLocalesPrivilegedAction
            implements PrivilegedAction {

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
                
        public Object run() {  
            throw new UnsupportedOperationException("Not supported yet.");
        }           
    }


}
