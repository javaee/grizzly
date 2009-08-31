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
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.Cookie;

import com.sun.grizzly.util.http.HttpRequestURIDecoder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;

/**
 * Adapter class that can initiate a {@link javax.servlet.FilterChain} and execute its
 * {@link Filter} and its {@link Servlet}
 * 
 * Configuring a {@link com.sun.grizzly.http.embed.GrizzlyWebServer} or
 * {@link com.sun.grizzly.http.SelectorThread} to use this
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
            sa.addListener(MyOtherHttpSessionListener.class);
            sa.addServletContextListener(new FooServletContextListener());
            sa.addServletContextAttributeListener(new BarServletCtxAttListener());
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
     * The context parameters.
     */
    private HashMap<String,String> contextParameters;

    /**
     * The servlet initialization parameters
     */
    private HashMap<String,String> servletInitParameters;
    
    
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
    
    /**
     * Initialize the {@link ServletContext}
     */
    protected boolean initialize = true;

    protected ClassLoader classLoader;
    

    public ServletAdapter() {
        this(".");
    }
    
    
    /**
     * Create a ServletAdapter which support the specific Servlet
     *
     * @param servlet Instance to be used by this adapter.
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
                new HashMap<String,String>(), new HashMap<String,String>(),
                new ArrayList<String>() );
    }

    /**
     * Convenience constructor.
     *
     * @param publicDirectory The folder where the static resource are located.
     * @param servletCtx {@link ServletContextImpl} to be used by new instance.
     * @param contextParameters Context parameters.
     * @param servletInitParameters servlet initialization parameres.
     * @param listeners Listeners.
     */
    protected ServletAdapter(String publicDirectory, ServletContextImpl servletCtx,
            HashMap<String,String> contextParameters, HashMap<String,String> servletInitParameters,
            ArrayList<String> listeners){
       this(publicDirectory, servletCtx, contextParameters, servletInitParameters, listeners, true);
    }

    /**
     * Convenience constructor.
     *
     * @param publicDirectory The folder where the static resource are located.
     * @param servletCtx {@link ServletContextImpl} to be used by new instance.
     * @param contextParameters Context parameters.
     * @param servletInitParameters servlet initialization parameres.
     * @param listeners Listeners.
     * @param initialze false only when the {@link #newServletAdapter()} is invoked.
     */
    protected ServletAdapter(String publicDirectory, ServletContextImpl servletCtx,
            HashMap<String,String> contextParameters, HashMap<String,String> servletInitParameters,
            ArrayList<String> listeners, boolean initialize){
        super(publicDirectory);
        this.servletCtx = servletCtx;
        servletConfig = new ServletConfigImpl(servletCtx, servletInitParameters); 
        this.contextParameters = contextParameters;
        this.servletInitParameters = servletInitParameters;
        this.listeners = listeners;
        this.initialize = initialize;
    }

    /**
     * Convenience constructor.
     *
     * @param publicDirectory The folder where the static resource are located.
     * @param servletCtx {@link ServletContextImpl} to be used by new instance.
     * @param contextParameters Context parameters.
     * @param servletInitParameters servlet initialization parameres.
     * @param listeners Listeners.
     * @param initialze false only when the {@link #newServletAdapter()} is invoked.
     */
    protected ServletAdapter(String publicDirectory, ServletContextImpl servletCtx,
            HashMap<String,String> contextParameters, HashMap<String,String> servletInitParameters,
            boolean initialize){
        super(publicDirectory);
        this.servletCtx = servletCtx;
        servletConfig = new ServletConfigImpl(servletCtx, servletInitParameters);
        this.contextParameters = contextParameters;
        this.servletInitParameters = servletInitParameters;
        this.initialize = initialize;
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
            if (initialize) {
                initWebDir();
                configureClassLoader(webDir.getCanonicalPath());
            }
            if( classLoader != null ) {
                ClassLoader prevClassLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader( classLoader );
                try {
                    configureServletEnv();
                    fullUrlPath = contextPath + servletPath;
                    setResourcesContextPath( fullUrlPath );
                    if( loadOnStartup ) {
                        loadServlet();
                    }
                } finally {
                    Thread.currentThread().setContextClassLoader( prevClassLoader );
                }
            } else {
                configureServletEnv();
                fullUrlPath = contextPath + servletPath;
                setResourcesContextPath( fullUrlPath );
                if( loadOnStartup ) {
                    loadServlet();
                }
            }
        } catch (Throwable t){
            logger.log(Level.SEVERE,"start",t);
        }
    }
   
    
    /**
     * Create a {@link java.net.URLClassLoader} which has the capability of
     * loading classes jar under an exploded war application.
     *
     * @param applicationPath Application class path.
     * @throws java.io.IOException I/O error.
     */
    protected void configureClassLoader(String applicationPath) throws IOException{
        if( classLoader == null )
            classLoader = ClassLoaderUtil.createURLClassLoader(applicationPath);
    }
    
    
    /**
     * {@inheritDoc}
     */ 
    @Override
    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        if( classLoader != null ) {
            ClassLoader prevClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader( classLoader );
            try {
                doService( request, response );
            } finally {
                Thread.currentThread().setContextClassLoader( prevClassLoader );
            }
        } else {
            doService( request, response );
        }
    }

    public void doService(GrizzlyRequest request, GrizzlyResponse response) {
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
     * @param response  the {@link GrizzlyResponse}
     * @param message   the Http error message
     * @param errorCode the error code.
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
     *
     * @throws javax.servlet.ServletException If failed to
     * {@link Servlet#init(javax.servlet.ServletConfig)}.
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
     * Configure the {@link com.sun.grizzly.http.servlet.ServletContextImpl}
     * and {@link com.sun.grizzly.http.servlet.ServletConfigImpl}
     * 
     * @throws javax.servlet.ServletException Error while configuring
     * {@link Servlet}.
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

        if (initialize) {
            servletCtx.setInitParameter(contextParameters);
            servletCtx.setContextPath(contextPath);  
            servletCtx.setBasePath(getRootFolder() + File.separatorChar  + contextPath);
            configureProperties(servletCtx);
            servletCtx.initListeners(listeners);
        }
        servletConfig.setInitParameters(servletInitParameters);
        configureProperties(servletConfig);
    }
   
    
    /**
     * {@inheritDoc}
     */     
    @Override
    public void afterService(GrizzlyRequest request, GrizzlyResponse response) 
            throws Exception {
        try{
            HttpServletRequestImpl httpRequest = (HttpServletRequestImpl)
                request.getRequest().getNote(REQUEST_RESPONSE_NOTES);
            HttpServletResponseImpl httpResponse = (HttpServletResponseImpl)
                response.getResponse().getNote(REQUEST_RESPONSE_NOTES);

            httpRequest.recycle();
            httpResponse.recycle();
        } finally {
            filterChain.recycle();
        }
    }
    
    
    /**
     * Add a new servlet initialization parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addInitParameter(String name, String value){
        servletInitParameters.put(name, value);
    }
    
    
    /**
     * Add a new servlet context parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addContextParameter(String name, String value){
        contextParameters.put(name, value);
    }    
    
    
    /**
     * Add a {@link Filter} to the
     * {@link com.sun.grizzly.http.servlet.FilterChainImpl}
     *
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
     * Return the {@link Servlet} instance used by this {@link ServletAdapter}
     * @return {@link Servlet} isntance.
     */
    public Servlet getServletInstance() {
        return servletInstance;
    }

    
    /**
     * Set the {@link Servlet} instance used by this {@link ServletAdapter}
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
     *
     * @param servletPath Path of {@link Servlet}.
     */
    public void setServletPath(String servletPath) {
        this.servletPath = servletPath;
        if (!servletPath.equals("") && !servletPath.startsWith("/")){
            servletPath = "/" + servletPath;
        }
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
     *
     * @param contextPath Context path.
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }
    
    /**
     * Add Servlet listeners that implement {@link EventListener}
     *
     * @param listenerName name of a Servlet listener
     */
    public void addServletListener(String listenerName){
        if (listenerName == null) return;
        listeners.add(listenerName);
    }

    /**
     * Use reflection to configure Object setter.
     *
     * @param object Populate this object with available properties.
     */
    private void configureProperties(Object object){
        for (String s : properties.keySet()) {
            String value = properties.get(s).toString();
            IntrospectionUtils.setProperty(object, s, value);
        }       
    }
    
    
    /**
     * Return a configured property. Property apply to
     * {@link com.sun.grizzly.http.servlet.ServletContextImpl}
     * and {@link com.sun.grizzly.http.servlet.ServletConfigImpl}
     *
     * @param name Name of property to get.
     * @return Value of property.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    
    /**
     * Set a configured property. Property apply to
     * {@link com.sun.grizzly.http.servlet.ServletContextImpl}
     * and {@link com.sun.grizzly.http.servlet.ServletConfigImpl}.
     * Use this method to map what's you usually
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
            String post = name.substring(pos+1, pos+2).toUpperCase() + name.substring(pos+2);
            name = pre + post;
        }
        properties.put(name, value);

    }

    
    /** 
     * Remove a configured property. Property apply to
     * {@link com.sun.grizzly.http.servlet.ServletContextImpl}
     * and {@link com.sun.grizzly.http.servlet.ServletConfigImpl}
     *
     * @param name Property name to remove.
     */
    public void removeProperty(String name) {
        properties.remove(name);
    } 
    
    
    /**
     * Destroy this Servlet and its associated
     * {@link javax.servlet.ServletContextListener}
     */
    @Override
    public void destroy(){
        if( classLoader != null ) {
            ClassLoader prevClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader( classLoader );
            try {
                super.destroy();
                servletCtx.destroyListeners();
                filterChain.destroy();
            } finally {
                Thread.currentThread().setContextClassLoader( prevClassLoader );
            }
        } else {
            super.destroy();
            servletCtx.destroyListeners();
            filterChain.destroy();
        }
    }
     
    
    /**
     * Create a new {@link ServletAdapter} instance that will share the same 
     * {@link com.sun.grizzly.http.servlet.ServletContextImpl} and Servlet's
     * listener but with an empty map of init-parameters.
     *
     * @param servlet - The Servlet associated with the {@link ServletAdapter}
     * @return a new {@link ServletAdapter}
     */
    public ServletAdapter newServletAdapter(Servlet servlet){
        ServletAdapter sa = new ServletAdapter(".",servletCtx, contextParameters,
                new HashMap<String,String>(), listeners,
                false);
        if( classLoader != null )
            sa.setClassLoader( classLoader );
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

    protected HashMap<String, String> getContextParameters() {
        return contextParameters;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader( ClassLoader classLoader ) {
        this.classLoader = classLoader;
    }
}
