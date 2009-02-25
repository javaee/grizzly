/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 */

package com.sun.grizzly.http.servlet;

import com.sun.grizzly.util.Grizzly;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.Constants;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.IntrospectionUtils;
import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.Cookie;

import com.sun.grizzly.util.http.HttpRequestURIDecoder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

/**
 * Adapter class that can initiate a {@link javax.servlet.FilterChain} and execute its
 * {@link Filter} and its {@link Servlet}
 * 
 * Configuring a {@link GrizzlyWebServer} or {@link SelectorThread} to use this
 * {@link GrizzlyAdapter} implementation add the ability of servicing {@link Servlet}
 * as well as static resources. 
 * 
 * This class can be used to programatically configure a Servlet, Filters, listeners,
 * init parameters, context-param, etc. a application usually defined using the web.xml.
 * See {@link #addInitParameter(String, String)} {@link #addContextParameter(String, String)}
 * {@link #setProperty(String, Object)}, {@link #addServletListener(String)}, etc.
 * 
 * As an example:
 * 
 * <pre><code>
 *      GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
        try{
            ServletAdapter sa = new ServletAdapter();
            sa.setRootFolder("/Path/To/Exploded/War/File");
            sa.setServlet(new MyServlet());
   
            // Set the Servlet's Name
            // Any ServletConfig.getXXX method can be configured using this call.
            // The same apply for ServletContext.getXXX.
            sa.setProperty("display-name","myServlet");
            sa.addListener("foo.bar.myHttpSessionListener");
            sa.addContextParameter("databaseURI","jdbc://");
            sa.addInitParameter("password","hello"); 
            sa.setServletPath("/MyServletPath");
            sa.setContextPath("/myApp");
    
            ws.addGrizzlyAdapter(sa);

            ws.start();
        } catch (IOException ex){
            // Something when wrong.
        }
 * </code></pre>
 * 
 * @author Jeanfrancois Arcand
 */
public class ServletAdapter extends GrizzlyAdapter {
    public static final int REQUEST_RESPONSE_NOTES = 29;
    public static final int SERVLETCONFIG_NOTES = 30;   
    public static final String LOAD_ON_STARTUP="load-on-startup";
    protected volatile Servlet servletInstance = null;
    private FilterChainImpl filterChain = new FilterChainImpl();
    
    private transient ArrayList<String> listeners;
    
    private String servletPath = "";
    
    
    private String contextPath = "";
    
    
    private String fullUrlPath = "/";
    
    // Instanciate the Servlet when the start method is invoked.
    private boolean loadOnStartup = false;
    
    
    /**
     * The Servlet init/context parameters.
     */
    private HashMap<String,String> parameters;
    
    
    /**
     * Is the Servlet initialized.
     */
    private volatile boolean filterChainConfigured = false;
    
    
    private ReentrantLock filterChainReady = new ReentrantLock();
    
    
    /**
     * The {@link ServletContextImpl}
     */
    private final ServletContextImpl servletCtx;
    
    
    /**
     * The {@link ServletConfigImpl}
     */
    private ServletConfigImpl servletConfig;
    
    /**
     * Holder for our configured properties.
     */
    protected HashMap<String,Object> properties = new HashMap<String,Object>();
    
          
    public ServletAdapter() {
        this(".");
    }
    
    
    /**
     * Create a ServletAdapter which support the specific Servlet
     */
    public ServletAdapter(Servlet servlet){
        this(".");
        this.servletInstance = servlet;
    }

    /**
     * Create a new instance which will look for static pages located 
     * under <tt>publicDirectory</tt> folder.
     * @param publicDirectory the folder where the static resource are located.
     */    
    public ServletAdapter(String publicDirectory) {
        this(publicDirectory, new ServletContextImpl(), 
                new HashMap<String,String>(),new ArrayList<String>() );
    }
    
    
    protected ServletAdapter(String publicDirectory, ServletContextImpl servletCtx,
            HashMap<String,String> parameters, ArrayList<String> listeners){
        super(publicDirectory);
        this.servletCtx = servletCtx;
        servletConfig = new ServletConfigImpl(servletCtx); 
        this.parameters = parameters;
        this.listeners = listeners;
    }

    public ServletAdapter(Servlet servlet, ServletContextImpl servletContext) {
        super(".");
        servletInstance = servlet;
        servletCtx = servletContext;
    }

    /**
     * {@inheritDoc}
     */ 
    @Override
    public void start(){
        try{
            initWebDir();
            configureClassLoader(webDir.getCanonicalPath());
            configureServletEnv();
            fullUrlPath = contextPath + servletPath;
            setResourcesContextPath(fullUrlPath);
            if (loadOnStartup){
                loadServlet();
            }
        } catch (Throwable t){
            logger.log(Level.SEVERE,"start",t);
        }
    }
   
    
    /**
     * Create a {@link URLClassLoader} which has the capability of loading classes
     * jar under an exploded war application.
     */
    protected void configureClassLoader(String appliPath) throws IOException{        
        Thread.currentThread().setContextClassLoader(
                ClassLoaderUtil.createURLClassLoader(appliPath));
    }
    
    
    /**
     * {@inheritDoc}
     */ 
    @Override
    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        try {
            Request req = request.getRequest();
            Response res = response.getResponse();            
            String uri = request.getRequestURI();

            // The request is not for us.
            if (!uri.startsWith(fullUrlPath)){
                customizeErrorPage(response,"Resource Not Found", 404);
                return;
            }
            
            HttpServletRequestImpl httpRequest = (HttpServletRequestImpl) 
                    req.getNote(REQUEST_RESPONSE_NOTES);
            HttpServletResponseImpl httpResponse = (HttpServletResponseImpl) 
                    res.getNote(REQUEST_RESPONSE_NOTES);
 
            if (httpRequest == null) {
                httpRequest = new HttpServletRequestImpl(request);
                httpResponse = new HttpServletResponseImpl(response);

                req.setNote(REQUEST_RESPONSE_NOTES, httpRequest);
                res.setNote(REQUEST_RESPONSE_NOTES, httpResponse);
            }

            Cookie[] cookies = request.getCookies();
            if (cookies != null) for (Cookie c : cookies) {
                if (Constants.SESSION_COOKIE_NAME.equals(c.getName())) {
                    request.setRequestedSessionId(c.getValue());
                    request.setRequestedSessionCookie(true);
                    break;
                }
            }

            loadServlet();
            
            httpRequest.setContextImpl(servletCtx);     
            httpRequest.setServletPath(servletPath);
 
            httpRequest.initSession();
            
            //TODO: Make this configurable.
            httpResponse.addHeader("server", "grizzly/" + Grizzly.getDotedVersion());
 
            filterChain.invokeFilterChain(httpRequest, httpResponse);
        } catch (Throwable ex) {
            logger.log(Level.SEVERE, "service exception:", ex);
            customizeErrorPage(response,"Internal Error", 500);
        }
    }

    
    /**
     * Customize the error page returned to the client.
     * @param response the {@link GrizzlyResponse}
     * @param message  the Http error message
     * @param erroCode the error code.
     */
    public void customizeErrorPage(GrizzlyResponse response,String message, int errorCode){                    
        response.setStatus(errorCode, message);
        response.setContentType("text/html");
        try{
            response.getWriter().write("<html><body><h1>" + message
                    + "</h1></body></html>");
            response.getWriter().flush();
        } catch (IOException ex){
            // We are in a very bad shape. Ignore.
        }
    }
    
    /**
     * Load a {@link Servlet} instance.
     */
    protected void loadServlet() throws ServletException{

        try{
            filterChainReady.lock();       
            if (filterChainConfigured){
                return;
            }
        
            if (servletInstance == null){
                String servletClassName = System.getProperty("com.sun.grizzly.servletClass");
                if (servletClassName != null) {
                    servletInstance = (Servlet)ClassLoaderUtil.load(servletClassName);
                }

                if (servletInstance != null) {                                       
                    logger.info("Loading Servlet: " + servletInstance.getClass().getName());  
                }
            }

            if (servletInstance != null){
                servletInstance.init(servletConfig);
            }
            filterChain.setServlet(servletConfig, servletInstance);           
            filterChain.init();
 
            filterChainConfigured = true;
        } finally {
            filterChainReady.unlock();
        }
    }
    
    
    /**
     * Configure the {@link ServletContext} and {@link ServletConfig}
     * 
     * @throws javax.servlet.ServletException
     */
    protected void configureServletEnv() throws ServletException{
        MessageBytes c = MessageBytes.newInstance();
        fullUrlPath = contextPath + servletPath;
        if (!fullUrlPath.equals("")){
            char[] ch = fullUrlPath.toCharArray();
            c.setChars(ch, 0, ch.length);
            HttpRequestURIDecoder.normalize(c);
            fullUrlPath = c.getCharChunk().toString();
        }
        
        if (fullUrlPath.equals("/")){
            contextPath = "";
        }

        servletCtx.setInitParameter(parameters);
        servletCtx.setContextPath(contextPath);  
        servletCtx.setBasePath(getRootFolder());               
        configureProperties(servletCtx);
        servletCtx.initListeners(listeners);
        configureProperties(servletConfig);
    }
   
    
    /**
     * {@inheritDoc}
     */     
    @Override
    public void afterService(GrizzlyRequest request, GrizzlyResponse response) 
            throws Exception {
        filterChain.recycle();
    }
    
    
    /**
     * Add a new servlet initialization parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addInitParameter(String name, String value){
        parameters.put(name, value);
    }
    
    
    /**
     * Add a new servlet context parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addContextParameter(String name, String value){
        parameters.put(name, value);
    }    
    
    
    /**
     * Add a {@link Filter} to the {@link FilterChain}
     * @param filter an instance of Filter
     * @param filterName the Filter's name
     * @param initParameters the Filter init parameters.
     */
    public void addFilter(Filter filter, String filterName, Map initParameters){
        FilterConfigImpl filterConfig = new FilterConfigImpl(servletCtx);
        filterConfig.setFilter(filter);
        filterConfig.setFilterName(filterName);
        filterConfig.setInitParameters(initParameters);
        filterChain.addFilter(filterConfig);
    }
    
    
    /**
     * Return the {@link Servlet} instance used by this {@link Adapter}
     * @return
     */
    public Servlet getServletInstance() {
        return servletInstance;
    }

    
    /**
     * Set the {@link Servlet} instance used by this {@link Adapter}
     * @param servletInstance an instance of Servlet.
     */ 
    public void setServletInstance(Servlet servletInstance) {
        this.servletInstance = servletInstance;
    }


    /**
     *
     * Returns the part of this request's URL that calls
     * the servlet. This path starts with a "/" character
     * and includes either the servlet name or a path to
     * the servlet, but does not include any extra path
     * information or a query string. Same as the value of
     * the CGI variable SCRIPT_NAME.
     *
     * <p>This method will return an empty string ("") if the
     * servlet used to process this request was matched using
     * the "/*" pattern.
     *
     * @return		a <code>String</code> containing
     *			the name or path of the servlet being
     *			called, as specified in the request URL,
     *			decoded, or an empty string if the servlet
     *			used to process the request is matched
     *			using the "/*" pattern.
     *
     */  
    public String getServletPath() {
        return servletPath;
    }

    
    /**
     * Programmatically set the servlet path of the Servlet.
     * @param servletPath
     */
    public void setServletPath(String servletPath) {
        this.servletPath = servletPath;
    }
   
    
    /**
     *
     * Returns the portion of the request URI that indicates the context
     * of the request. The context path always comes first in a request
     * URI. The path starts with a "/" character but does not end with a "/"
     * character. For servlets in the default (root) context, this method
     * returns "". The container does not decode this string.
     *
     * <p>It is possible that a servlet container may match a context by
     * more than one context path. In such cases this method will return the
     * actual context path used by the request and it may differ from the
     * path returned by the
     * {@link javax.servlet.ServletContext#getContextPath()} method.
     * The context path returned by
     * {@link javax.servlet.ServletContext#getContextPath()}
     * should be considered as the prime or preferred context path of the
     * application.
     *
     * @return		a <code>String</code> specifying the
     *			portion of the request URI that indicates the context
     *			of the request
     *
     * @see javax.servlet.ServletContext#getContextPath()
     */  
    public String getContextPath() {
        return contextPath;
    }

    
    /**
     * Programmatically set the context path of the Servlet.
     * @param contextPath
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }
    
    /**
     * Add Servlet listeners like {@link ServletContextAttributeListener},
     * {@link ServletContextListener}.
     */
    public void addServletListener(String listenerName){
        listeners.add(listenerName);
    }
    
         
    /**
     * Use reflection to configure Object setter.
     */
    private void configureProperties(Object object){
        Iterator keys = properties.keySet().iterator();
        while( keys.hasNext() ) {
            String name = (String)keys.next();
            String value = properties.get(name).toString();
            IntrospectionUtils.setProperty(object, name, value);
        }       
    }
    
    
    /**
     * Return a configured property. Property apply to {@link ServletContext}
     * and {@link ServletConfig}
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    
    /**
     * Set a configured property. Property apply to {@link ServletContext}
     * and {@link ServletConfig}. Use this method to map what's you usually
     * have in a web.xml like display-name, context-param, etc.
     * @param name Name of the property to set
     * @param value of the property.
     */
    public void setProperty(String name, Object value) {                 
        
        if (name.equalsIgnoreCase(LOAD_ON_STARTUP) && value != null
                && ((new Integer(value.toString())) <= 1)){
            loadOnStartup = true;
        }
        
        // Get rid of "-";
        int pos = name.indexOf("-");
        if (pos > 0){
            String pre = name.substring(0,pos);
            String post = name.substring(pos+1);
            name = pre + post;
        }
        properties.put(name, value);

    }

    
    /** 
     * Remove a configured property. Property apply to {@link ServletContext}
     * and {@link ServletConfig}
     */
    public void removeProperty(String name) {
        properties.remove(name);
    } 
    
    
    /**
     * Destroy this Servlet and its associated {@link ServletContextListener}
     */
    @Override
    public void destroy(){
        super.destroy();
        servletCtx.destroyListeners();
    }
     
    
    /**
     * Create a new {@link ServletAdapter} instance that will share the same 
     * init-parameters, {@link ServletContext} and Servlet's listener.
     * @param servlet - The Servlet associated with the {@link ServletAdapter}
     * @return a new {@link ServletAdapter}
     */
    public ServletAdapter newServletAdapter(Servlet servlet){
        ServletAdapter sa = new ServletAdapter(".",servletCtx,parameters, listeners);
        sa.setServletInstance(servlet);
        sa.setServletPath(servletPath);
        return sa;
    }

    protected ServletContextImpl getServletCtx() {
        return servletCtx;
    }

    protected ArrayList<String> getListeners() {
        return listeners;
    }

    protected HashMap<String, String> getParameters() {
        return parameters;
    }
}
