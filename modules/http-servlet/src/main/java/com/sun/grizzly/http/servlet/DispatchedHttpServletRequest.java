/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.util.Charsets;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.Enumerator;
import com.sun.grizzly.util.http.Globals;
import com.sun.grizzly.util.http.ParameterMap;
import com.sun.grizzly.util.http.Parameters;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Wrapper around a <code>javax.servlet.http.HttpServletRequest</code>
 * that transforms an application request object (which might be the original
 * one passed to a servlet.
 *
 * @author Bongjae Chang
 */
public class DispatchedHttpServletRequest extends HttpServletRequestWrapper {

    private String contextPath;
    private String requestURI;
    private String servletPath;
    private String pathInfo;
    private String queryString;
    private HashMap specialAttributes;
    private DispatcherConstants.DispatcherType dispatcherType;
    private Object requestDispatcherPath;

    /**
     * The set of attribute names that are special for request dispatchers
     */
    private static final HashSet<String> specials = new HashSet<String>( 15 );

    static {
        specials.add( DispatcherConstants.INCLUDE_REQUEST_URI );
        specials.add( DispatcherConstants.INCLUDE_CONTEXT_PATH );
        specials.add( DispatcherConstants.INCLUDE_SERVLET_PATH );
        specials.add( DispatcherConstants.INCLUDE_PATH_INFO );
        specials.add( DispatcherConstants.INCLUDE_QUERY_STRING );
        specials.add( DispatcherConstants.FORWARD_REQUEST_URI );
        specials.add( DispatcherConstants.FORWARD_CONTEXT_PATH );
        specials.add( DispatcherConstants.FORWARD_SERVLET_PATH );
        specials.add( DispatcherConstants.FORWARD_PATH_INFO );
        specials.add( DispatcherConstants.FORWARD_QUERY_STRING );
        specials.add( DispatcherConstants.ASYNC_REQUEST_URI );
        specials.add( DispatcherConstants.ASYNC_CONTEXT_PATH );
        specials.add( DispatcherConstants.ASYNC_SERVLET_PATH );
        specials.add( DispatcherConstants.ASYNC_PATH_INFO );
        specials.add( DispatcherConstants.ASYNC_QUERY_STRING );
    }

    /**
     * Hash map used in the getParametersMap method.
     */
    private final ParameterMap parameterMap = new ParameterMap();
    private final Parameters mergedParameters = new Parameters();

    /**
     * Have the parameters for this request already been parsed?
     */
    private boolean parsedParams = false;

    public DispatchedHttpServletRequest( HttpServletRequest request, DispatcherConstants.DispatcherType dispatcherType ) {
        super( request );
        this.dispatcherType = dispatcherType;
        setRequest( request );
    }

    /**
     * Set the request that we are wrapping.
     *
     * @param request The new wrapped request
     */
    private void setRequest( HttpServletRequest request ) {
        super.setRequest( request );
        // Initialize the attributes for this request
        requestDispatcherPath = request.getAttribute( Globals.DISPATCHER_REQUEST_PATH_ATTR );
        // Initialize the path elements for this request
        contextPath = request.getContextPath();
        requestURI = request.getRequestURI();
        servletPath = request.getServletPath();
        pathInfo = request.getPathInfo();
        queryString = request.getQueryString();
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath( String contextPath ) {
        this.contextPath = contextPath;
    }

    @Override
    public String getRequestURI() {
        return requestURI;
    }

    public void setRequestURI( String requestURI ) {
        this.requestURI = requestURI;
    }

    @Override
    public String getServletPath() {
        return servletPath;
    }

    public void setServletPath( String servletPath ) {
        this.servletPath = servletPath;
    }

    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    public void setPathInfo( String pathInfo ) {
        this.pathInfo = pathInfo;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    public void setQueryString( String queryString ) {
        this.queryString = queryString;
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public String getParameter( String name ) {
        if( !parsedParams )
            parseParameters();
        if( System.getSecurityManager() != null ) {
            return (String)AccessController.doPrivileged(
                    new GetParameterPrivilegedAction( name ) );
        } else {
            return mergedParameters.getParameter( name );
        }
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public Enumeration getParameterNames() {
        if( !parsedParams )
            parseParameters();
        if( System.getSecurityManager() != null ) {
            return new Enumerator( (Set)AccessController.doPrivileged(
                    new GetParameterNamesPrivilegedAction() ) );
        } else {
            return mergedParameters.getParameterNames();
        }
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public String[] getParameterValues( String name ) {
        if( !parsedParams )
            parseParameters();
        String[] ret;
        /*
         * Clone the returned array only if there is a security manager
         * in place, so that performance won't suffer in the nonsecure case
         */
        if( System.getSecurityManager() != null ) {
            ret = (String[])AccessController.doPrivileged(
                    new GetParameterValuePrivilegedAction( name ) );
            if( ret != null ) {
                ret = ret.clone();
            }
        } else {
            ret = mergedParameters.getParameterValues( name );
        }
        return ret;
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public Map getParameterMap() {
        if( !parsedParams )
            parseParameters();
        if( System.getSecurityManager() != null ) {
            return (Map)AccessController.doPrivileged(
                    new GetParameterMapPrivilegedAction() );
        } else {
            return getParameterMapInternal();
        }
    }

    private ParameterMap getParameterMapInternal() {
        if( parameterMap.isLocked() )
            return parameterMap;
        for (Enumeration<String> e = mergedParameters.getParameterNames(); e.hasMoreElements();) {
            final String name = e.nextElement();
            final String[] values = mergedParameters.getParameterValues( name );
            parameterMap.put( name, values );
        }
        parameterMap.setLocked( true );
        return parameterMap;
    }

    /**
     * Parses the parameters of this request.
     * <p/>
     * If parameters are present in both the query string and the request
     * content, they are merged.
     */
    @SuppressWarnings( "unchecked" )
    private void parseParameters() {
        if( parsedParams ) {
            return;
        }
        final String enc = getCharacterEncoding();
        Charset charset;
        if( enc != null ) {
            try {
                charset = Charsets.lookupCharset(enc);
            } catch( Exception e ) {
                charset = Charsets.DEFAULT_CHARSET;
            }
        } else {
            charset = Charsets.DEFAULT_CHARSET;
        }
        mergedParameters.setEncoding( charset.toString() );
        mergedParameters.setQueryStringEncoding( charset.toString() );

        MessageBytes queryDC = MessageBytes.newInstance();
        queryDC.setString( queryString );
        mergedParameters.setQuery( queryDC );
        mergedParameters.handleQueryParameters();

        Map<String, String[]> paramMap = getRequest().getParameterMap();
        for( final Map.Entry<String, String[]> entry : paramMap.entrySet() ) {
            mergedParameters.addParameterValues( entry.getKey(), entry.getValue() );
        }
        parsedParams = true;
    }

    @Override
    public Object getAttribute( String name ) {
        if( name.equals( Globals.DISPATCHER_REQUEST_PATH_ATTR ) ) {
            return requestDispatcherPath != null ? requestDispatcherPath.toString() : null;
        }

        if( !isSpecial( name ) ) {
            return getRequest().getAttribute( name );
        } else {
            Object value = null;
            if( specialAttributes != null ) {
                value = specialAttributes.get( name );
            }
            if( value == null && name.startsWith( "javax.servlet.forward" ) ) {
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
                value = getRequest().getAttribute( name );
            }
            return value;
        }
    }

    @Override
    public Enumeration getAttributeNames() {
        return new AttributeNamesEnumerator();
    }

    @SuppressWarnings( "unchecked" )
    @Override
    public void setAttribute( String name, Object value ) {
        if( name.equals( Globals.DISPATCHER_REQUEST_PATH_ATTR ) ) {
            requestDispatcherPath = value;
            return;
        }

        if( isSpecial( name ) ) {
            if( specialAttributes != null ) {
                specialAttributes.put( name, value );
            }
        } else {
            getRequest().setAttribute( name, value );
        }
    }

    @Override
    public void removeAttribute( String name ) {
        if( isSpecial( name ) ) {
            if( specialAttributes != null ) {
                specialAttributes.remove( name );
            }
        } else {
            getRequest().removeAttribute( name );
        }
    }

    private boolean isSpecial( String name ) {
        return specials.contains( name );
    }

    /**
     * Initializes the special attributes of this request wrapper.
     *
     * @param requestUri  The request URI
     * @param contextPath The context path
     * @param servletPath The servlet path
     * @param pathInfo    The path info
     * @param queryString The query string
     */
    @SuppressWarnings( "unchecked" )
    void initSpecialAttributes( String requestUri,
                                String contextPath,
                                String servletPath,
                                String pathInfo,
                                String queryString ) {
        specialAttributes = new HashMap( 5 );

        switch( dispatcherType ) {
            case INCLUDE:
                specialAttributes.put( DispatcherConstants.INCLUDE_REQUEST_URI, requestUri );
                specialAttributes.put( DispatcherConstants.INCLUDE_CONTEXT_PATH, contextPath );
                specialAttributes.put( DispatcherConstants.INCLUDE_SERVLET_PATH, servletPath );
                specialAttributes.put( DispatcherConstants.INCLUDE_PATH_INFO, pathInfo );
                specialAttributes.put( DispatcherConstants.INCLUDE_QUERY_STRING, queryString );
                break;
            case FORWARD:
            case ERROR:
                specialAttributes.put( DispatcherConstants.FORWARD_REQUEST_URI, requestUri );
                specialAttributes.put( DispatcherConstants.FORWARD_CONTEXT_PATH, contextPath );
                specialAttributes.put( DispatcherConstants.FORWARD_SERVLET_PATH, servletPath );
                specialAttributes.put( DispatcherConstants.FORWARD_PATH_INFO, pathInfo );
                specialAttributes.put( DispatcherConstants.FORWARD_QUERY_STRING, queryString );
                break;
            case ASYNC:
                specialAttributes.put( DispatcherConstants.ASYNC_REQUEST_URI, requestUri );
                specialAttributes.put( DispatcherConstants.ASYNC_CONTEXT_PATH, contextPath );
                specialAttributes.put( DispatcherConstants.ASYNC_SERVLET_PATH, servletPath );
                specialAttributes.put( DispatcherConstants.ASYNC_PATH_INFO, pathInfo );
                specialAttributes.put( DispatcherConstants.ASYNC_QUERY_STRING, queryString );
                break;
        }
    }

    public HttpServletRequestImpl getRequestFacade() {
        if( getRequest() instanceof HttpServletRequestImpl ) {
            return (HttpServletRequestImpl)getRequest();
        } else {
            return ( (DispatchedHttpServletRequest)getRequest() ).getRequestFacade();
        }
    }

    public void recycle() {
        parameterMap.setLocked( false );
        parameterMap.clear();
    }

    /**
     * Utility class used to expose the special attributes as being available
     * as request attributes.
     */
    private final class AttributeNamesEnumerator implements Enumeration {

        protected Enumeration<String> parentEnumeration = null;
        protected String next = null;
        private Iterator<String> specialNames = null;

        @SuppressWarnings( "unchecked" )
        public AttributeNamesEnumerator() {
            parentEnumeration = getRequest().getAttributeNames();
            if( specialAttributes != null ) {
                specialNames = specialAttributes.keySet().iterator();
            }
        }

        public boolean hasMoreElements() {
            return ( specialNames != null && specialNames.hasNext() )
                   || ( next != null )
                   || ( ( next = findNext() ) != null );
        }

        public Object nextElement() {

            if( specialNames != null && specialNames.hasNext() ) {
                return specialNames.next();
            }

            String result = next;
            if( next != null ) {
                next = findNext();
            } else {
                throw new NoSuchElementException();
            }
            return result;
        }

        protected String findNext() {
            String result = null;
            while( ( result == null ) && ( parentEnumeration.hasMoreElements() ) ) {
                String current = parentEnumeration.nextElement();
                if( !isSpecial( current ) ||
                    ( !dispatcherType.equals( DispatcherConstants.DispatcherType.FORWARD ) &&
                      current.startsWith( "javax.servlet.forward" ) &&
                      getAttribute( current ) != null ) ) {
                    result = current;
                }
            }
            return result;
        }
    }

    private final class GetParameterPrivilegedAction implements PrivilegedAction {
        public final String name;

        public GetParameterPrivilegedAction( String name ) {
            this.name = name;
        }

        public Object run() {
            return mergedParameters.getParameter( name );
        }
    }

    private final class GetParameterNamesPrivilegedAction implements PrivilegedAction {
        public Object run() {
            return mergedParameters.getParameterNames();
        }
    }

    private final class GetParameterValuePrivilegedAction implements PrivilegedAction {
        public final String name;

        public GetParameterValuePrivilegedAction( String name ) {
            this.name = name;
        }

        public Object run() {
            return mergedParameters.getParameterValues( name );
        }
    }

    private final class GetParameterMapPrivilegedAction implements PrivilegedAction {
        public Object run() {
            return getParameterMapInternal();
        }
    }
}
