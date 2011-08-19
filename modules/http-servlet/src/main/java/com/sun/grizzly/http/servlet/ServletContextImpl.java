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

import com.sun.grizzly.util.http.DispatcherHelper;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.Grizzly;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.Enumerator;
import com.sun.grizzly.util.http.MimeType;
import com.sun.grizzly.util.http.mapper.MappingData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Simple {@link ServletContext} implementation.
 * 
 * @author Jeanfrancois Arcand
 */
public class ServletContextImpl implements ServletContext {
    /**
     * Empty collection to serve as the basis for empty enumerations.
     * <strong>DO NOT ADD ANY ELEMENTS TO THIS COLLECTION!</strong>
     */
    private static final ArrayList empty = new ArrayList();


    /**
     * Thread local data used during request dispatch.
     */
    private ThreadLocal<DispatchData> dispatchData = new ThreadLocal<DispatchData>();

    private DispatcherHelper dispatcherHelper;
    

    private final transient ArrayList<EventListener> eventListeners = new ArrayList();
    
    
    /**
     * The merged context initialization parameters for this Context.
     */
    private final ConcurrentHashMap<String,String> parameters =
            new ConcurrentHashMap(16, 0.75f, 64);
    
    
    /**
     * The context attributes for this context.
     */
    private final ConcurrentHashMap attributes =
            new ConcurrentHashMap(16, 0.75f, 64);
        
    private String contextPath = "";
    
    
    private final Logger logger = Logger.getLogger("Grizzly");
    
    
    private String basePath = "";
    
    // display-name
    private String contextName = "";    
    
    
    // ----------------------------------------------------------------- //
    
    /**
     * Notify the {@link ServletContextListener} that we are starting.
     */
    protected void initListeners(List<String> listeners){
        
        for(String listenerClass: listeners){
        EventListener el = null;
            try {
                el = (EventListener)Thread.currentThread().getContextClassLoader()
                        .loadClass(listenerClass).newInstance();
                eventListeners.add(el);
            } catch (Throwable e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTEXT_LISTENER_LOAD_ERROR(listenerClass),
                               e);
                }
            }
        }
        
        ServletContextEvent event = null;
        for (int i = 0; i < eventListeners.size(); i++) {
            if (!(eventListeners.get(i) instanceof ServletContextListener))
                continue;
            ServletContextListener listener =
                (ServletContextListener) eventListeners.get(i);
            if (event == null) {
                event = new ServletContextEvent(this);
            }
            try {
                listener.contextInitialized(event);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_INITIALIZED_ERROR("contextInitialized", "ServletContextListener", listener.getClass().getName()),
                               t);
                }
            }
        }
    }
    
    
    /**
     * Stop {@link ServletContextListener}
     */
    protected void destroyListeners(){
        ServletContextEvent event = null;
        for (int i = 0; i < eventListeners.size(); i++) {
            if (!(eventListeners.get(i) instanceof ServletContextListener))
                continue;
            ServletContextListener listener =
                (ServletContextListener) eventListeners.get(i);
            if (event == null) {
                event = new ServletContextEvent(this);
            }
            try {
                listener.contextDestroyed(event);
            } catch (Throwable t) {
                 if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_DESTROYED_ERROR("contextDestroyed", "ServletContextListener", listener.getClass().getName()),
                               t);
                }
            }
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    public String getContextPath() {
        return contextPath;
    }
    
    
    /**
     * Programmatically set the context path of the Servlet.
     * @param contextPath
     */
    protected void setContextPath(String contextPath){
        this.contextPath = contextPath;
    }

    
    /**
     * Return a <code>ServletContext</code> object that corresponds to a
     * specified URI on the server.  This method allows servlets to gain
     * access to the context for various parts of the server, and as needed
     * obtain <code>RequestDispatcher</code> objects or resources from the
     * context.  The given path must be absolute (beginning with a "/"),
     * and is interpreted based on our virtual host's document root.
     *
     * @param uri Absolute URI of a resource on the server
     */
    public ServletContext getContext(String uri) {
       // Validate the format of the specified argument
       if( uri == null || !uri.startsWith( "/" ) ) {
           return null;
       }
       if( dispatcherHelper == null ) {
           return null;
       }

       // Use the thread local URI and mapping data
       DispatchData dd = dispatchData.get();
       if( dd == null ) {
           dd = new DispatchData();
           dispatchData.set( dd );
       } else {
           dd.recycle();
       }
       MessageBytes uriDC = dd.uriMB;
       // Retrieve the thread local mapping data
       MappingData mappingData = dd.mappingData;

       try {
           uriDC.setString( uri );
           dispatcherHelper.mapPath(dd.hostMB, uriDC, mappingData );
           if( mappingData.context == null ) {
               return null;
           }
       } catch( Exception e ) {
           // Should never happen
           if( logger.isLoggable( Level.WARNING ) ) {
               logger.log( Level.WARNING, "Error during mapping", e );
           }
           return null;
       }

       if( !( mappingData.context instanceof ServletAdapter ) ) {
           return null;
       }
       ServletAdapter context = (ServletAdapter)mappingData.context;
       return context.getServletCtx();
    }

    
    /**
     * Return the major version of the Java Servlet API that we implement.
     */
    public int getMajorVersion() {
        return 2;
    }


    /**
     * Return the minor version of the Java Servlet API that we implement.
     */
    public int getMinorVersion() {
        return 5;
    }

    
    /**
     * Return the MIME type of the specified file, or <code>null</code> if
     * the MIME type cannot be determined.
     *
     * @param file Filename for which to identify a MIME type
     */
    public String getMimeType(String file) {

        if (file == null)
            return (null);
        int period = file.lastIndexOf(".");
        if (period < 0)
            return (null);
        String extension = file.substring(period + 1);
        if (extension.length() < 1)
            return (null);
        return MimeType.get(extension);
    }


    /**
     * Return a Set containing the resource paths of resources member of the
     * specified collection. Each path will be a String starting with
     * a "/" character. The returned set is immutable.
     *
     * @param path Collection path
     */
    public Set getResourcePaths(String path) {

        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(path);
        }

        path = normalize(path);
        if (path == null)
            return (null);
        
        File[] files =  new File(basePath, path).listFiles();
        Set<String> list = new HashSet<String>();
        if (files != null){
            for (File f : files){
                try {
                    String canonicalPath = f.getCanonicalPath();
                    
                    // add a trailing "/" if a folder
                    if(f.isDirectory()){
                    	canonicalPath = canonicalPath + "/";
                    }
                    
                    canonicalPath = canonicalPath.substring(
                            canonicalPath.indexOf(basePath) + basePath.length());
                    list.add(canonicalPath.replace("\\", "/"));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return list;
    }

    
    /**
     * Return the URL to the resource that is mapped to a specified path.
     * The path must begin with a "/" and is interpreted as relative to the
     * current context root.
     *
     * @param path The path to the desired resource
     *
     * @exception MalformedURLException if the path is not given
     *  in the correct form
     */
    public URL getResource(String path) throws MalformedURLException {

        if (path == null || !path.startsWith("/")) {
            throw new MalformedURLException(path);
        }
        
        path = normalize(path);
        if (path == null)
            return (null);
        
        // Help the UrlClassLoader, which is not able to load resources
        // that contains '//'
        if (path.length() > 1){
            path = path.substring(1);
        }
        
        URL url = Thread.currentThread().getContextClassLoader()
                    .getResource(path); 
        return url;
    }

    
    /**
     * Return the requested resource as an {@link InputStream}.  The
     * path must be specified according to the rules described under
     * <code>getResource</code>.  If no such resource can be identified,
     * return <code>null</code>.
     *
     * @param path The path to the desired resource.
     */
    public InputStream getResourceAsStream(String path) {

        path = normalize(path);
        if (path == null)
            return (null);
  
        // Help the UrlClassLoader, which is not able to load resources
        // that contains '//'
        if (path.length() > 1){
            path = path.substring(1);
        }
        
        return Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(path);
    }

     
    /**
     * {@inheritDoc}
     */   
    public RequestDispatcher getRequestDispatcher(String path) {
                // Validate the path argument
        if( path == null ) {
            return null;
        }
        if( dispatcherHelper == null ) {
            return null;
        }
        if( !path.startsWith( "/" ) && path.length() != 0 ) {
            throw new IllegalArgumentException( "Path " + path + " does not start with ''/'' and is not empty" );
        }

        // Get query string
        String queryString = null;
        int pos = path.indexOf( '?' );
        if( pos >= 0 ) {
            queryString = path.substring( pos + 1 );
            path = path.substring( 0, pos );
        }
        path = normalize( path );
        if( path == null )
            return null;
        pos = path.length();

        // Use the thread local URI and mapping data
        DispatchData dd = dispatchData.get();
        if( dd == null ) {
            dd = new DispatchData();
            dispatchData.set( dd );
        } else {
            dd.recycle();
        }
        MessageBytes uriDC = dd.uriMB;
        // Retrieve the thread local mapping data
        MappingData mappingData = dd.mappingData;

        try {
            uriDC.setString( contextPath + path );
            dispatcherHelper.mapPath(dd.hostMB, uriDC, mappingData );
            if( mappingData.wrapper == null ) {
                return null;
            }
        } catch( Exception e ) {
            // Should never happen
            if( logger.isLoggable( Level.WARNING ) ) {
                logger.log( Level.WARNING, "Error during mapping", e );
            }
            return null;
        }

        if( !( mappingData.wrapper instanceof ServletAdapter ) ) {
            return null;
        }
        ServletAdapter wrapper = (ServletAdapter)mappingData.wrapper;
        String wrapperPath = mappingData.wrapperPath.toString();
        String pathInfo = mappingData.pathInfo.toString();
        // Construct a RequestDispatcher to process this request
        return new RequestDispatcherImpl( wrapper, uriDC.toString(), wrapperPath, pathInfo, queryString, null );
    }

   
    /**
     * {@inheritDoc}
     */     
    public RequestDispatcher getNamedDispatcher(String name) {
                // Validate the name argument
        if( name == null )
            return null;
        if( dispatcherHelper == null ) {
            return null;
        }

        // Use the thread local URI and mapping data
        DispatchData dd = dispatchData.get();
        if( dd == null ) {
            dd = new DispatchData();
            dispatchData.set( dd );
        } else {
            dd.recycle();
        }
//        DataChunk uriDC = dd.uriDC;
        MessageBytes servletNameDC = dd.servletNameMB;
        // Retrieve the thread local mapping data
        MappingData mappingData = dd.mappingData;
        // Map the name
//        uriDC.setString( contextPath );
        servletNameDC.setString( name );

        try {
            dispatcherHelper.mapName( servletNameDC, mappingData );
            if( !( mappingData.wrapper instanceof ServletAdapter ) ) {
                return null;
            }
        } catch( Exception e ) {
            // Should never happen
            if( logger.isLoggable( Level.WARNING ) ) {
                logger.log( Level.WARNING, "Error during mapping", e );
            }
            return null;
        }

        ServletAdapter wrapper = (ServletAdapter) mappingData.wrapper;
        // Construct a RequestDispatcher to process this request
        return new RequestDispatcherImpl( wrapper, null, null, null, null, name );
    }

    
   /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Servlet getServlet(String name) {
        return (null);
    }

    
    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServlets() {
        return (new Enumerator(empty));
    }

    
    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServletNames() {
        return (new Enumerator(empty));
    }

     
    /**
     * {@inheritDoc}
     */   
    public void log(String string) {
        logger.log(Level.INFO,string);
    }

   
    /**
     * {@inheritDoc}
     */     
    @SuppressWarnings("deprecation")
    public void log(Exception e, String msg) {
        logger.log(Level.INFO,msg,e);
    }

    
    /**
     * {@inheritDoc}
     */    
    public void log(String msg, Throwable t) {
        logger.log(Level.INFO,msg,t);
    }

    
    /**
     * Return the real path for a given virtual path, if possible; otherwise
     * return <code>null</code>.
     *
     * @param path The path to the desired resource
     */
    public String getRealPath(String path) {

        if (path == null) {
            return null;
        }

        return new File(basePath,path).getAbsolutePath();
    }

    
    public String getServerInfo() {
        return Grizzly.getServerInfo();
    }

    
   /**
     * Return the value of the specified initialization parameter, or
     * <code>null</code> if this parameter does not exist.
     *
     * @param name Name of the initialization parameter to retrieve
     */
    public String getInitParameter(final String name) {        
        return parameters.get(name);
    }

    
    /**
     * Return the names of the context's initialization parameters, or an
     * empty enumeration if the context has no initialization parameters.
     */
    public Enumeration getInitParameterNames() {
        return (new Enumerator(parameters.keySet()));
    }
    
    
    protected void setInitParameter(Map<String,String> parameters){
        this.parameters.clear();
        this.parameters.putAll(parameters);
    }

    
    /**
     * Return the value of the specified context attribute, if any;
     * otherwise return <code>null</code>.
     *
     * @param name Name of the context attribute to return
     */
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    
    /**
     * Return an enumeration of the names of the context attributes
     * associated with this context.
     */
    public Enumeration getAttributeNames() {
        return new Enumerator(attributes.keySet());
    }

    
    /**
     * Bind the specified value with the specified context attribute name,
     * replacing any existing value for that name.
     *
     * @param name Attribute name to be bound
     * @param value New attribute value to be bound
     */
    @SuppressWarnings("unchecked")
    public void setAttribute(String name, Object value) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                ("Cannot be null");

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }
        
        Object oldValue = attributes.put(name, value) ;
        
        ServletContextAttributeEvent event = null;
        for (int i = 0; i < eventListeners.size(); i++) {
            if (!(eventListeners.get(i) instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) eventListeners.get(i);
            try {
                if (event == null) {
                    if (oldValue!=null)
                        event =
                            new ServletContextAttributeEvent(this,
                                                             name, oldValue);
                    else
                        event =
                            new ServletContextAttributeEvent(this,
                                                             name, value);
                    
                }
                if (oldValue!=null){
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_ATTRIBUTE_LISTENER_ADD_ERROR("ServletContextAttributeListener", listener.getClass().getName()),
                               t);
                }
            }
        }
    }


    /**
     * Remove the context attribute with the specified name, if any.
     *
     * @param name Name of the context attribute to be removed
     */
    public void removeAttribute(String name) {
        Object value = attributes.remove(name);
        if (value == null)
            return;
        
        ServletContextAttributeEvent event = null;
        for (int i = 0; i < eventListeners.size(); i++) {
            if (!(eventListeners.get(i) instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) eventListeners.get(i);
            try {
                if (event == null) {
                    event = new ServletContextAttributeEvent(this,name, value);

                }
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_ATTRIBUTE_LISTENER_REMOVE_ERROR("ServletContextAttributeListener", listener.getClass().getName()),
                               t);
                }
            }
        }
    }

    
    /**
     * {@inheritDoc}
     */
    public String getServletContextName() {
        return contextName;
    }
    
    
    /**
     * Set the Servlet context name (display-name)
     * @param contextName
     */
    public void setDisplayName(String contextName){
        this.contextName = contextName;
    }

    
    /**
     * Return a context-relative path, beginning with a "/", that represents
     * the canonical version of the specified path after ".." and "." elements
     * are resolved out.  If the specified path attempts to go outside the
     * boundaries of the current context (i.e. too many ".." path elements
     * are present), return <code>null</code> instead.
     *
     * @param path Path to be normalized
     */
    protected String normalize(String path) {

        if (path == null) {
            return null;
        }

        String normalized = path;

        // Normalize the slashes and add leading slash if necessary
        if (normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0)
                break;
            if (index == 0)
                return (null);  // Trying to go outside our context
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) +
                normalized.substring(index + 3);
        }

        // Return the normalized path that we have completed
        return (normalized);

    }   
    
    /**
     * Return the current based path.
     * @return basePath
     */
    protected String getBasePath() {
        return basePath;
    }

    
    /**
     * Set the basePath used by the {@link #getRealPath}.
     * @param basePath
     */
    protected void setBasePath(String basePath) {
        this.basePath = basePath;
    }

     protected void setDispatcherHelper( DispatcherHelper dispatcherHelper ) {
        this.dispatcherHelper = dispatcherHelper;
    }

    /**
     * Internal class used as thread-local storage when doing path
     * mapping during dispatch.
     */
    private static final class DispatchData {
        public final MessageBytes hostMB;
        public final MessageBytes uriMB;
        public final MessageBytes servletNameMB;
        public final MappingData mappingData;

        public DispatchData() {
            hostMB = MessageBytes.newInstance();
            uriMB = MessageBytes.newInstance();
            servletNameMB = MessageBytes.newInstance();
            mappingData = new MappingData();
        }

        public void recycle() {
            hostMB.recycle();
            uriMB.recycle();
            servletNameMB.recycle();
            mappingData.recycle();
        }
    }
    
    // ----------------------------------- Listener implementation ----------//

    
    /**
     * Return the Servlet Listener.
     * @return return the Servlet Listener.
     */
    protected List<EventListener> getListeners(){
        return eventListeners;
    }
}
