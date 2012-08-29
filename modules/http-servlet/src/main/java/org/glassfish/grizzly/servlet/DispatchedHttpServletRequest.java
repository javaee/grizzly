/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.glassfish.grizzly.http.server.util.Enumerator;
import org.glassfish.grizzly.http.server.util.Globals;
import org.glassfish.grizzly.http.server.util.ParameterMap;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Parameters;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Wrapper around a <code>javax.servlet.http.HttpServletRequest</code>
 * that transforms an application request object (which might be the original
 * one passed to a servlet.
 *
 * @author Bongjae Chang
 */
public class DispatchedHttpServletRequest extends HttpServletRequestWrapper {

    // ------------------------------------------------------- Static Variables

    /**
     * The set of attribute names that are special for request dispatchers
     */
    private static final HashSet<String> specials = new HashSet<String>(15);

    static {
        specials.add(RequestDispatcher.INCLUDE_REQUEST_URI);
        specials.add(RequestDispatcher.INCLUDE_CONTEXT_PATH);
        specials.add(RequestDispatcher.INCLUDE_SERVLET_PATH);
        specials.add(RequestDispatcher.INCLUDE_PATH_INFO);
        specials.add(RequestDispatcher.INCLUDE_QUERY_STRING);
        specials.add(RequestDispatcher.FORWARD_REQUEST_URI);
        specials.add(RequestDispatcher.FORWARD_CONTEXT_PATH);
        specials.add(RequestDispatcher.FORWARD_SERVLET_PATH);
        specials.add(RequestDispatcher.FORWARD_PATH_INFO);
        specials.add(RequestDispatcher.FORWARD_QUERY_STRING);
        specials.add(AsyncContext.ASYNC_REQUEST_URI);
        specials.add(AsyncContext.ASYNC_CONTEXT_PATH);
        specials.add(AsyncContext.ASYNC_SERVLET_PATH);
        specials.add(AsyncContext.ASYNC_PATH_INFO);
        specials.add(AsyncContext.ASYNC_QUERY_STRING);
    }


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new wrapped request around the specified servlet request.
     *
     * @param request the servlet request being wrapped
     * @param dispatcherType the dispatcher type
     */
    public DispatchedHttpServletRequest(HttpServletRequest request,
            WebappContext context,
            boolean crossContext,
            DispatcherType dispatcherType) {
        super(request);
        this.context = context;
        this.crossContext = crossContext;
        this.dispatcherType = dispatcherType;

        setRequest(request);
    }
    
    
    // ----------------------------------------------------- Instance Variables

    /**
     * The context for this request.
     */
    protected WebappContext context = null;

    /**
     * The context path for this request.
     */
    protected String contextPath = null;
    
    /**
     * If this request is cross context, since this changes session access
     * behavior.
     */
    protected boolean crossContext = false;

    /**
     * The dispatcher type.
     */
    protected DispatcherType dispatcherType;

    /**
     * Hash map used in the getParametersMap method.
     */
    private final ParameterMap parameterMap = new ParameterMap();
    private final Parameters mergedParameters = new Parameters();

    /**
     * Have the parameters for this request already been parsed?
     */
    private boolean parsedParams = false;
    
    /**
     * The path information for this request.
     */
    protected String pathInfo = null;

    /**
     * The query parameters for the current request.
     */
    private String queryParamString = null;

    /**
     * The query string for this request.
     */
    protected String queryString = null;

    /**
     * The current request dispatcher path.
     */
    protected Object requestDispatcherPath = null;

    /**
     * The request URI for this request.
     */
    protected String requestURI = null;

    /**
     * The servlet path for this request.
     */
    protected String servletPath = null;

    /**
     * Special attributes.
     */
    private HashMap<String, Object> specialAttributes = null;

    // ------------------------------------------------- ServletRequest Methods

    /**
     * Override the <code>getAttribute()</code> method of the wrapped request.
     *
     * @param name Name of the attribute to retrieve
     */
    @Override
    public Object getAttribute(String name) {

        if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            if ( requestDispatcherPath != null ){
                return requestDispatcherPath.toString();
            } else {
                return null;   
            }
        }

        if (!isSpecial(name)) {
            return getRequest().getAttribute(name);
        } else {
            Object value = null;
            if (specialAttributes != null) {
                value = specialAttributes.get(name);
            }
            if (value == null && name.startsWith("javax.servlet.forward")) {
                /*
                 * If it's a forward special attribute, and null, delegate
                 * to the wrapped request. This will allow access to the
                 * forward special attributes from a request that was first
                 * forwarded and then included, or forwarded multiple times
                 * in a row.
                 * Notice that forward special attributes are set only on
                 * the wrapper that was created for the initial forward
                 * (i.e., the top-most wrapper for a request that was
                 * forwarded multiple times in a row, and never included,
                 * will not contain any specialAttributes!).
                 * This is different from an include, where the special
                 * include attributes are set on every include wrapper.
                 */
                value = getRequest().getAttribute(name);
            }
            return value;
        }

    }


    /**
     * Override the <code>getAttributeNames()</code> method of the wrapped
     * request.
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return (new AttributeNamesEnumerator());
    }

    /**
     * Override the <code>removeAttribute()</code> method of the
     * wrapped request.
     *
     * @param name Name of the attribute to remove
     */
    @Override
    public void removeAttribute(String name) {

        if (isSpecial(name)) {
            if (specialAttributes != null) {
                specialAttributes.remove(name);
            }
        } else {
            getRequest().removeAttribute(name);
        }
    }


    /**
     * Override the <code>setAttribute()</code> method of the
     * wrapped request.
     *
     * @param name Name of the attribute to set
     * @param value Value of the attribute to set
     */
    @Override
    public void setAttribute(String name, Object value) {

        if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            requestDispatcherPath = value;
            return;
        }

        if (isSpecial(name)) {
            if (specialAttributes != null) {
                specialAttributes.put(name, value);
            }
        } else {
            getRequest().setAttribute(name, value);
        }
    }
    

    /**
     * Return a RequestDispatcher that wraps the resource at the specified
     * path, which may be interpreted as relative to the current request path.
     *
     * @param path Path of the resource to be wrapped
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {

        if (context == null)
            return (null);

        // If the path is already context-relative, just pass it through
        if (path == null)
            return (null);
        else if (path.startsWith("/"))
            return (context.getRequestDispatcher(path));

        // Convert a request-relative path to a context-relative one
        String servletPath = 
            (String) getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (servletPath == null)
            servletPath = getServletPath();

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

        return (context.getRequestDispatcher(relative));

    }
    
    
    @Override
    public DispatcherType getDispatcherType() {
        return dispatcherType;
    }


    // --------------------------------------------- HttpServletRequest Methods


    /**
     * Override the <code>getContextPath()</code> method of the wrapped
     * request.
     */
    @Override
    public String getContextPath() {

        return (this.contextPath);

    }
    
    
    /**
     * Override the <code>getParameter()</code> method of the wrapped request.
     *
     * @param name Name of the requested parameter
     */
    @Override
    public String getParameter(String name) {

        if (!parsedParams) {
            parseParameters();
        }
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(
                    new GetParameterPrivilegedAction(name));
        } else {
            return mergedParameters.getParameter(name);
        }
    }


    /**
     * Override the <code>getParameterMap()</code> method of the
     * wrapped request.
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        if (!parsedParams) {
            parseParameters();
        }
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged(
                    new GetParameterMapPrivilegedAction());
        } else {
            return getParameterMapInternal();
        }
    }


    /**
     * Override the <code>getParameterNames()</code> method of the
     * wrapped request.
     */
    @Override
    public Enumeration<String> getParameterNames() {
        if (!parsedParams) {
            parseParameters();
        }
        if (System.getSecurityManager() != null) {
            return new Enumerator<String>(AccessController.doPrivileged(
                    new GetParameterNamesPrivilegedAction()));
        } else {
            return new Enumerator<String>(mergedParameters.getParameterNames());
        }
    }


    /**
     * Override the <code>getParameterValues()</code> method of the
     * wrapped request.
     *
     * @param name Name of the requested parameter
     */
    @Override
    public String[] getParameterValues(String name) {
        if (!parsedParams) {
            parseParameters();
        }
        String[] ret;
        /*
         * Clone the returned array only if there is a security manager
         * in place, so that performance won't suffer in the nonsecure case
         */
        if (System.getSecurityManager() != null) {
            ret = AccessController.doPrivileged(
                    new GetParameterValuePrivilegedAction(name));
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = mergedParameters.getParameterValues(name);
        }
        return ret;
    }

    private ParameterMap getParameterMapInternal() {
        if (parameterMap.isLocked()) {
            return parameterMap;
        }
        for (final String name : mergedParameters.getParameterNames()) {
            final String[] values = mergedParameters.getParameterValues(name);
            parameterMap.put(name, values);
        }
        parameterMap.setLocked(true);
        return parameterMap;
    }

    /**
     * Parses the parameters of this request.
     * <p/>
     * If parameters are present in both the query string and the request
     * content, they are merged.
     */
    @SuppressWarnings( "unchecked")
    private void parseParameters() {
        if (parsedParams) {
            return;
        }
        final String enc = getCharacterEncoding();
        Charset charset;
        if (enc != null) {
            try {
                charset = Charsets.lookupCharset(enc);
            } catch (Exception e) {
                charset = Constants.DEFAULT_HTTP_CHARSET;
            }
        } else {
            charset = Constants.DEFAULT_HTTP_CHARSET;
        }
        mergedParameters.setEncoding(charset);
        mergedParameters.setQueryStringEncoding(charset);

        DataChunk queryDC = DataChunk.newInstance();
        queryDC.setString(queryParamString);
        mergedParameters.setQuery(queryDC);
        mergedParameters.handleQueryParameters();

        Map<String, String[]> paramMap = getRequest().getParameterMap();
        for (final Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            mergedParameters.addParameterValues(entry.getKey(), entry.getValue());
        }
        parsedParams = true;
    }

    /**
     * Override the <code>getPathInfo()</code> method of the wrapped request.
     */
    @Override
    public String getPathInfo() {

        return (this.pathInfo);

    }


    /**
     * Override the <code>getQueryString()</code> method of the wrapped
     * request.
     */
    @Override
    public String getQueryString() {

        return (this.queryString);

    }


    /**
     * Override the <code>getRequestURI()</code> method of the wrapped
     * request.
     */
    @Override
    public String getRequestURI() {

        return (this.requestURI);

    }


    /**
     * Override the <code>getRequestURL()</code> method of the wrapped
     * request.
     */
    @Override
    public StringBuffer getRequestURL() {

        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());

        return (url);
    }

    
    /**
     * Override the <code>getServletPath()</code> method of the wrapped
     * request.
     */
    @Override
    public String getServletPath() {

        return (this.servletPath);

    }
    

    // -------------------------------------------------------- Package Methods

    /**
     * Perform a shallow copy of the specified Map, and return the result.
     *
     * @param orig Origin Map to be copied
     */
    void copyMap(Map<String, String[]> orig, Map<String, String[]> dest) {

        if (orig == null)
            return;
        synchronized (orig) {
            for (Map.Entry<String, String[]> entry : orig.entrySet()) {
                dest.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Set the context path for this request.
     *
     * @param contextPath The new context path
     */
    void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Set the path information for this request.
     *
     * @param pathInfo The new path info
     */
    void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }

    /**
     * Set the query string for this request.
     *
     * @param queryString The new query string
     */
    void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    /**
     * Set the request that we are wrapping.
     *
     * @param request The new wrapped request
     */
    void setRequest(HttpServletRequest request) {

        super.setRequest(request);

        // Initialize the attributes for this request
        requestDispatcherPath = 
            request.getAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR);

        // Initialize the path elements for this request
        contextPath = request.getContextPath();
        pathInfo = request.getPathInfo();
        queryString = request.getQueryString();
        requestURI = request.getRequestURI();
        servletPath = request.getServletPath();

    }
    
    /**
     * Set the request URI for this request.
     *
     * @param requestURI The new request URI
     */
    void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    /**
     * Set the servlet path for this request.
     *
     * @param servletPath The new servlet path
     */
    void setServletPath(String servletPath) {
        this.servletPath = servletPath;
    }

    /**
     * Save query parameters for this request.
     *
     * @param queryString The query string containing parameters for this
     *                    request
         */
    void setQueryParams(String queryString) {
        this.queryParamString = queryString;
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Is this attribute name one of the special ones that is added only for
     * included servlets?
     *
     * @param name Attribute name to be tested
     */
    protected boolean isSpecial(String name) {
        return specials.contains(name);
        }

    /**
     * Initializes the special attributes of this request wrapper.
     *
     * @param requestUri The request URI
     * @param contextPath The context path
     * @param servletPath The servlet path
     * @param pathInfo The path info
     * @param queryString The query string
     */
    void initSpecialAttributes(String requestUri,
                                String contextPath,
                                String servletPath,
                                String pathInfo,
                               String queryString) {
        specialAttributes = new HashMap<String, Object>(5);

        switch (dispatcherType) {
            case INCLUDE:
            specialAttributes.put(RequestDispatcher.INCLUDE_REQUEST_URI,
                                  requestUri);
            specialAttributes.put(RequestDispatcher.INCLUDE_CONTEXT_PATH,
                                  contextPath);
            specialAttributes.put(RequestDispatcher.INCLUDE_SERVLET_PATH,
                                  servletPath);
            specialAttributes.put(RequestDispatcher.INCLUDE_PATH_INFO,
                                  pathInfo);
            specialAttributes.put(RequestDispatcher.INCLUDE_QUERY_STRING,
                                  queryString);
                break;
            case FORWARD:
            case ERROR:
            specialAttributes.put(RequestDispatcher.FORWARD_REQUEST_URI,
                                  requestUri);
            specialAttributes.put(RequestDispatcher.FORWARD_CONTEXT_PATH,
                                  contextPath);
            specialAttributes.put(RequestDispatcher.FORWARD_SERVLET_PATH,
                                  servletPath);
            specialAttributes.put(RequestDispatcher.FORWARD_PATH_INFO,
                                  pathInfo);
            specialAttributes.put(RequestDispatcher.FORWARD_QUERY_STRING,
                                  queryString);
                break;
            case ASYNC:
            specialAttributes.put(AsyncContext.ASYNC_REQUEST_URI,
                                  requestUri);
            specialAttributes.put(AsyncContext.ASYNC_CONTEXT_PATH,
                                  contextPath);
            specialAttributes.put(AsyncContext.ASYNC_SERVLET_PATH,
                                  servletPath);
            specialAttributes.put(AsyncContext.ASYNC_PATH_INFO,
                                  pathInfo);
            specialAttributes.put(AsyncContext.ASYNC_QUERY_STRING,
                                  queryString);
                break;
            default: // REQUEST
                break;
        }
    }

    /**
     * Merge the two sets of parameter values into a single String array.
     *
     * @param values1 First set of values
     * @param values2 Second set of values
     */
    protected String[] mergeValues(Object values1, Object values2) {

        ArrayList<String> results = new ArrayList<String>();

        if (values1 == null)
            ;
        else if (values1 instanceof String)
            results.add((String)values1);
        else if (values1 instanceof String[]) {
            String values[] = (String[]) values1;
            for (int i = 0; i < values.length; i++)
                results.add(values[i]);
        } else
            results.add(values1.toString());

        if (values2 == null)
            ;
        else if (values2 instanceof String)
            results.add((String)values2);
        else if (values2 instanceof String[]) {
            String values[] = (String[]) values2;
            for (int i = 0; i < values.length; i++)
                results.add(values[i]);
        } else
            results.add(values2.toString());

        String values[] = new String[results.size()];
        return results.toArray(values);

    }    

    public void recycle() {
//        parameterMap.setLocked( false );
//        parameterMap.clear();
    }


    // ----------------------------------- AttributeNamesEnumerator Inner Class

    /**
     * Utility class used to expose the special attributes as being available
     * as request attributes.
     */
    private final class AttributeNamesEnumerator implements Enumeration<String> {

        Enumeration<String> parentEnumeration = null;
        String next = null;
        private Iterator<String> specialNames = null;

        @SuppressWarnings( "unchecked" )
        public AttributeNamesEnumerator() {
            parentEnumeration = getRequest().getAttributeNames();
            if (specialAttributes != null) {
                specialNames = specialAttributes.keySet().iterator();
            }
        }

        @Override
        public boolean hasMoreElements() {
            return (specialNames != null && specialNames.hasNext())
                    || (next != null) 
                    || ((next = findNext()) != null);
        }

        @Override
        public String nextElement() {

            if (specialNames != null && specialNames.hasNext()) {
                return specialNames.next();
            }

            String result = next;
            if (next != null) {
                next = findNext();
            } else {
                throw new NoSuchElementException();
            }
            return result;
        }

        protected String findNext() {
            String result = null;
            while ((result == null) && (parentEnumeration.hasMoreElements())) {
                String current = parentEnumeration.nextElement();
                if (!isSpecial(current) ||
                        (!dispatcherType.equals(DispatcherType.FORWARD) &&
                        current.startsWith("javax.servlet.forward") &&
                        getAttribute(current) != null)) {
                    result = current;
                }
            }
            return result;
        }
    }

    
    public HttpServletRequestImpl getRequestFacade() {
        if (getRequest() instanceof HttpServletRequestImpl) {
            return (HttpServletRequestImpl) getRequest();
        } else {
            return ((DispatchedHttpServletRequest)getRequest()).getRequestFacade();
        }
    }
    
    private final class GetParameterPrivilegedAction implements PrivilegedAction<String> {
        public final String name;

        public GetParameterPrivilegedAction( String name ) {
            this.name = name;
        }

        @Override
        public String run() {
            return mergedParameters.getParameter( name );
        }
    }

    private final class GetParameterNamesPrivilegedAction implements PrivilegedAction<Set<String>> {
        @Override
        public Set<String> run() {
            return mergedParameters.getParameterNames();
        }
    }

    private final class GetParameterValuePrivilegedAction implements PrivilegedAction<String[]> {
        public final String name;

        public GetParameterValuePrivilegedAction( String name ) {
            this.name = name;
        }

        @Override
        public String[] run() {
            return mergedParameters.getParameterValues( name );
        }
    }

    private final class GetParameterMapPrivilegedAction implements PrivilegedAction<ParameterMap> {
        @Override
        public ParameterMap run() {
            return getParameterMapInternal();
        }
    }    
}
