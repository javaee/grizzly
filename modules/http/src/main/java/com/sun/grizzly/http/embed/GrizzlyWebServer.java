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
 */

package com.sun.grizzly.http.embed;

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.Management;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.deployer.DeployException;
import com.sun.grizzly.http.deployer.Deployable;
import com.sun.grizzly.http.deployer.Deployer;
import com.sun.grizzly.http.deployer.DeploymentConfiguration;
import com.sun.grizzly.http.deployer.DeploymentID;
import com.sun.grizzly.http.deployer.FromURIDeployer;
import com.sun.grizzly.ssl.SSLSelectorThread;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapterChain;
import com.sun.grizzly.tcp.http11.GrizzlyListener;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.ClassLoaderUtil;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.http.mapper.Mapper;
import com.sun.grizzly.util.net.jsse.JSSEImplementation;
import java.net.UnknownHostException;

import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * <p>
 * This class creates a WebServer that listen for http request. By default, 
 * creating an instance of this class can be used as it is to synchronously serve resources located 
 * under {@link #webResourcesPath}. By default, the {@link StaticResourcesAdapter} is
 * used when there is no {@link GrizzlyAdapter} specified.  The {@link StaticResourcesAdapter}
 * allow servicing of static resources like html files, images files, etc. 
 * 
 * The {@link GrizzlyAdapter} provides developers with a simple and consistent mechanism for extending 
 * the functionality of the Grizzly WebServer and for bridging existing 
 * http based technology like JRuby-on-Rail, Servlet, Bayeux Protocol or any
 * http based protocol. 
 * </p><p>You can extend the GrizzlyWebServer by adding one or serveral
 * {@link GrizzlyAdapter}. If more that one are used, the {@link GrizzlyAdapterChain}
 * will be used to map the request to its associated {@link GrizzlyAdapter}s, using a {@link Mapper}.
 * A {@link GrizzlyAdapter} gets invoked
 * as soon as the http request has been parsed and 
 * decoded. The  {@link GrizzlyAdapter#service(GrizzlyRequest, GrizzlyResponse)}
 * method is invoked with a
 * {@link GrizzlyRequest} and {@link GrizzlyResponse} that can be used to extend the
 * functionality of the Web Server. By default, all http requests are synchronously
 * executed. 
 * </p><p>Asynchronous request processing is automatically enabled
 * as soon as one or several {@link AsyncFilter} are added, using the 
 * {@link #addAsyncFilter} method. {@link AsyncFilter} can be used when
 * asynchronous operation are required, like suspending the current http request,
 * delaying the invocation of {@link GrizzlyAdapter} etc. The state of the
 * request processing can be managed using {@link AsyncExecutor}, like resuming
 * the process so {@link GrizzlyAdapter}s can be invoked.
 * </p><p>
 * The following picture describes how {@link AsyncFilter} and {@link GrizzlyAdapter}
 * are invoked.
 * </p><p><pre><code>
 * ----------------------------------------------------------------------------
 * - AsyncFilter.doFilter() ---> AsyncExecutor.execute()    -------|          -
 * -                                                               |          -
 * -                                                               |          -
 * -                                                               |          -
 * - AsyncFilter.doFilter() <-- GrizzlyAdapter2.service()   <------|          -
 * ----------------------------------------------------------------------------
 * </code></pre></p><p>
 * Here is some examples:</p><p><pre><code>
 * <strong>Synchronous Web Server servicing static resources</strong>
        GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
        try{
            ws.start();
        } catch (IOException ex){
            // Something when wrong.
        } 
 
 * <strong>Synchronous Web Server servicing customized resources</strong>
        GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
        try{
            ws.addGrizzlyAdapter(new GrizzlyAdapter(){  
                
                public void service(GrizzlyRequest request, GrizzlyResponse response){
                    try {
                        response.getWriter().println("Grizzly is soon cool");
                    } catch (IOException ex) {                        
                    }
                }
            });
            ws.start();
        } catch (IOException ex){
            // Something when wrong.
        } 
 * <strong>Synchronous Web Server servicing a Servlet</strong>
   
        GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
        try{
            ServletAdapter sa = new ServletAdapter();
            sa.setRootFolder("/Path/To/Exploded/War/File");
            sa.setServlet(new MyServlet());
            ws.addGrizzlyAdapter(sa);
            ws.start();
        } catch (IOException ex){
            // Something when wrong.
        } 
 * <strong>Synchronous Web Server servicing two Servlet</strong>
   
        GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
        try{
            ServletAdapter sa = new ServletAdapter();
            sa.setRootFolder("/Path/To/Exploded/War/File");
            sa.setServlet(new MyServlet());
            ws.addGrizzlyAdapter(sa);
  
            ServletAdapter sa = new ServletAdapter();
            sa.setRootFolder("/Path/To/Exploded/War2/File");
            sa.setServlet(new MySecondServlet());
            ws.addGrizzlyAdapter(sa);
  
            ws.start();
        } catch (IOException ex){
            // Something when wrong.
        } 
 * 
 * <strong>Asynchronous Web Server servicing customized resources</strong>
 * The example below delay the request processing for 10 seconds, without holding 
 * a thread.
 * 
        GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
        try{
            ws.addAsyncFilter(new AsyncFilter() {
                private final ScheduledThreadPoolExecutor scheduler = 
                        new ScheduledThreadPoolExecutor(1);
                public boolean doFilter(final AsyncExecutor asyncExecutor) {
                    //Throttle the request
                    scheduler.schedule(new Callable() {
                        public Object call() throws Exception {
                            asyncExecutor.execute();
                            asyncExecutor.postExecute();
                            return null;
                        }
                    }, 10, TimeUnit.SECONDS);
                    
                    // Call the next AsyncFilter
                    return true;
                }
            });
                                        
            ws.addGrizzlyAdapter(new GrizzlyAdapter(){                  
                public void service(GrizzlyRequest request, GrizzlyResponse response){
                    try {
                        response.getWriter().println("Grizzly is soon cool");
                    } catch (IOException ex) {                        
                    }
                }
            });
            ws.start();
        } catch (IOException ex){
            // Something when wrong.
        } 
 * <strong>Asynchronous Web Server servicing Servlet</strong>
 * The example below delay the request processing for 10 seconds, without holding 
 * a thread.
 * 
        GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
        try{
            ws.addAsyncFilter(new AsyncFilter() {
                private final ScheduledThreadPoolExecutor scheduler = 
                        new ScheduledThreadPoolExecutor(1);
                public boolean doFilter(final AsyncExecutor asyncExecutor) {
                    //Throttle the request
                    scheduler.schedule(new Callable() {
                        public Object call() throws Exception {
                            asyncExecutor.execute();
                            asyncExecutor.postExecute();
                            return null;
                        }
                    }, 10, TimeUnit.SECONDS);
                    
                    // Call the next AsyncFilter
                    return true;
                }
            });
  
            ServletAdapter sa = new ServletAdapter();
            sa.setRootFolder("/Path/To/Exploded/War/File");
            sa.setServlet(new MyServlet());
            ws.addGrizzlyAdapter(sa);
 
            ws.start();
        } catch (IOException ex){
            // Something when wrong.
        }  
 * <strong>Asynchronous Web Server servicing Servlet and supporting the Bayeux Protocol</strong>

        GrizzlyWebServer ws = new GrizzlyWebServer("/var/www");
        try{
            // Add Comet Support
            ws.addAsyncFilter(new CometAsyncFilter());
 
            //Add Bayeux support
            CometdAdapter cometdAdapter = new CometdAdapter();
            ws.addGrizzlyAdapter(cometdAdapter);
  
            ServletAdapter sa = new ServletAdapter();
            sa.setRootFolder("/Path/To/Exploded/War/File");
            sa.setServlet(new MyServlet());
            ws.addGrizzlyAdapter(sa);
 
            ws.start();
        } catch (IOException ex){
            // Something when wrong.
        } 
 * <strong>Synchronous Web Server servicing static resources eand exposed using JMX Mbeans</strong>

        GrizzlyWebServer ws = new GrizzlyWebServer(path);    
        ws.enableJMX(new Management() {

            public void registerComponent(Object bean, ObjectName oname, String type) 
                    throws Exception{
                Registry.getRegistry().registerComponent(bean,oname,type);
            }

            public void unregisterComponent(ObjectName oname) throws Exception{
                Registry.getRegistry().
                        unregisterComponent(oname);
            }  
        });
        ws.start();
    }
}
 * <strong>Synchronous Web Server servicing two Servlet</strong>
 * 
 *      GrizzlyWebServer ws = new GrizzlyWebServer(path);
        ServletAdapter sa = new ServletAdapter();
        sa.setRootFolder(".");
        sa.setServletInstance(new ServletTest("Adapter-1"));
        ws.addGrizzlyAdapter(sa, new String[]{"/Adapter-1"});

        ServletAdapter sa2 = new ServletAdapter();
        sa2.setRootFolder("/tmp");
        sa2.setServletInstance(new ServletTest("Adapter-2"));
        ws.addGrizzlyAdapter(sa2, new String[]{"/Adapter-2"});

        System.out.println("Grizzly WebServer listening on port 8080");
        ws.start();
 *
 *  * <strong>Synchronous Web Server servicing two Servlet using SSL</strong>
 *
 *      GrizzlyWebServer ws = new GrizzlyWebServer(443,path,5,true);
 *      SSlConfig sslConfig = new SSLConfig();
 *      sslConfig.setTrustStoreFile("Path-to-trustore");
 *      sslConfig.setKeyStoreFile("Path-to-Trustore");
 *      ws.setSSLConfig(sslConfig);
 *
        ServletAdapter sa = new ServletAdapter();
        sa.setRootFolder(".");
        sa.setServletInstance(new ServletTest("Adapter-1"));
        ws.addGrizzlyAdapter(sa, new String[]{"/Adapter-1"});

        ServletAdapter sa2 = new ServletAdapter();
        sa2.setRootFolder("/tmp");
        sa2.setServletInstance(new ServletTest("Adapter-2"));
        ws.addGrizzlyAdapter(sa2, new String[]{"/Adapter-2"});

        System.out.println("Grizzly WebServer listening on port 8080");
        ws.start();
 * </code></pre></p>
 * @author Jeanfrancois Arcand
 */
public class GrizzlyWebServer {
    // The default host
    private static final String DEFAULT_HOST = "0.0.0.0";
    
    // The default port
    private static final int DEFAULT_PORT = 8080;

    // The port
    private static final String DEFAULT_WEB_RESOURCES_PATH = ".";

    // The underlying {@link GrizzlyListener}
    private GrizzlyListener grizzlyListener;
    
    // SelectorThread mBean
    private ObjectInstance stMBean;

    
    // The underlying {@link GrizzlyAdapterChain}
    private GrizzlyAdapterChain adapterChains = new GrizzlyAdapterChain();
    
    
    // List of {@link GrizzlyAdapter} and its associated mapping
    private HashMap<GrizzlyAdapter,String[]> adapters
            = new HashMap<GrizzlyAdapter,String[]>();

    
    // Are we started?
    private boolean isStarted = false;

    
    // List of {@link AsyncFilter}
    private ArrayList<AsyncFilter> asyncFilters = new ArrayList<AsyncFilter>();
    
    
    // The path to static resource.
    private String webResourcesPath = DEFAULT_WEB_RESOURCES_PATH;
    
    
    // The mBean default object name.
    private String mBeanName = "com.sun.grizzly:type=GrizzlyWebServer,name=GrizzlyHttpEngine-" 
            + DEFAULT_PORT;
       
    
    // The {@link Statistis} instance associated with this instance.
    private Statistics statistics;

    // Host 
    private final String host;
    
    // Listening port
    private final int port;
    
    public enum PROTOCOL { HTTP, AJP }

  /**
     * Create a default GrizzlyWebServer
     */
    public GrizzlyWebServer() {
        this(DEFAULT_PORT);
    }
    
    
    /**
     * Create a WebServer that listen on port
     * @param port The port opened
     */
    public GrizzlyWebServer(int port) {
        this(port, DEFAULT_WEB_RESOURCES_PATH);
    }
    
    
    /**
     * Create a WebServer which server files from {@link #webResourcesPath}
     * @param webResourcesPath the path to the web resource (ex: /var/www)
     */
    public GrizzlyWebServer(String webResourcesPath) {
        this(DEFAULT_PORT, webResourcesPath);
    }   
    
    
    /**
     * Create a WebServer that listen on port
     * @param port The port opened
     *
     * @deprecated use {@link #setMaxThreads(int)} to set maximum number of
     *             threads in a thread pool
     */
    public GrizzlyWebServer(int port, int maxThreads) {
        this(port, maxThreads, DEFAULT_WEB_RESOURCES_PATH);
    }


    /**
     * Create a WebServer that listen on port
     * @param port The port opened
     * @param webResourcesPath the path to the web resource (ex: /var/www)
     */
    public GrizzlyWebServer(int port, String webResourcesPath) {
        this(port, webResourcesPath, false);
    }


    /**
     * Create a WebServer that listen on port
     * @param port The port opened
     * @param maxThreads The maximum number of Thread created
     * @param webResourcesPath the path to the web resource (ex: /var/www)

     * @deprecated use {@link #setMaxThreads(int)} to set maximum number of
     *             threads in a thread pool
     */
    public GrizzlyWebServer(int port, int maxThreads, String webResourcesPath) {
        this(port, maxThreads, webResourcesPath, false);
    }


    /**
     * Create a WebServer that listen for secure tls/https requests
     * @param port The port opened
     * @param webResourcesPath the path to the web resource (ex: /var/www)
     * @param secure <tt>true</tt> if https needs to be used.
     */
    public GrizzlyWebServer(int port, String webResourcesPath, boolean secure) {
        this(port, 5, webResourcesPath, secure);
    }
    
    
    /**
     * Create a WebServer that listen for secure tls/https requests
     * @param host The host to listen on.
     * @param port The port opened
     * @param webResourcesPath the path to the web resource (ex: /var/www)
     * @param secure <tt>true</tt> if https needs to be used.
     */
    public GrizzlyWebServer(String host, int port, String webResourcesPath,
            boolean secure) throws UnknownHostException {
        this(host, port, 5, webResourcesPath, secure);
    }

    
    /**
     * Create a WebServer that listen for secure tls/https requests
     * @param port The port to listen on
     * @param maxThreads The maximum number of Thread created
     * @param webResourcesPath the path to the web resource (ex: /var/www)
     * @param secure <tt>true</tt> if https needs to be used.
     * 
     * @deprecated use {@link #setMaxThreads(int)} to set maximum number of
     *             threads in a thread pool
     */
    public GrizzlyWebServer(int port, int maxThreads,
            String webResourcesPath, boolean secure) {
        this.host = DEFAULT_HOST;
        this.port = port;
        
        createSelectorThread(port, secure);
        setMaxThreads(maxThreads);
        this.webResourcesPath = webResourcesPath;
    }

    /**
     * Create a WebServer that listen for secure tls/https requests
     * @param host The host to listen on.
     * @param port The port to listen on.
     * @param maxThreads The maximum number of Thread created
     * @param webResourcesPath the path to the web resource (ex: /var/www)
     * @param secure <tt>true</tt> if https needs to be used.
     */
    protected GrizzlyWebServer(String host, int port, int maxThreads,
            String webResourcesPath, boolean secure) throws UnknownHostException {
        
        this.host = host;
        this.port = port;
        
        createSelectorThread(host, port, secure);
        setMaxThreads(maxThreads);
        this.webResourcesPath = webResourcesPath;
    }
    
    /**
     * Create an underlying {@link SelectorThread}
     * @param port The port to listen on.
     * @param secure <tt>true</tt> if https needs to be used.
     */
    private void createSelectorThread(int port, boolean secure) {
        try {
            createSelectorThread(DEFAULT_HOST, port, secure);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Create an underlying {@link SelectorThread}
     * @param host The host to listen on.
     * @param port The port to listen on.
     * @param secure <tt>true</tt> if https needs to be used.
     */
    private void createSelectorThread(String host, int port, boolean secure)
            throws UnknownHostException {
        
        if (secure) {
            SSLSelectorThread sslSelectorThread = new SSLSelectorThread();
            try {
                sslSelectorThread.setSSLImplementation(new JSSEImplementation());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            grizzlyListener = sslSelectorThread;
        } else {
            grizzlyListener = new SelectorThread();
        }
        
        ((SelectorThread) grizzlyListener).setAddress(InetAddress.getByName(host));
        ((SelectorThread) grizzlyListener).setPort(port);
    }

    
    /**
     * Return the underlying {@link SelectorThread}. Only advanced users
     * should manipulate that class.
     * @return {@link SelectorThread}
     */
    public SelectorThread getSelectorThread(){
        supportHttp();
        return (SelectorThread)grizzlyListener;
    }
    
    
    /**
     * Add an {@link AsyncFilter}. Adding {@link AsyncFilter} automatically
     * enable Grizzly Asynchronous Request Processing mode. {@link AsyncFilter}s
     * are always invoked before {@link GrizzlyAdapter}
     *  
     * @param asyncFilter An {@link AsyncFilter}
     */
    public void addAsyncFilter(AsyncFilter asyncFilter){
        asyncFilters.add(asyncFilter);
    }
    
    
    /**
     * Add a {@link GrizzlyAdapter}. {@link GrizzlyAdapter} will be invoked by
     * Grizzly in the order they are added, e.g the first added is always the 
     * first invoked. {@link GrizzlyAdapter}s
     * are always invoked after {@link AsyncFilter}
     * @param grizzlyAdapter a {@link GrizzlyAdapter}
     * @deprecated - Use {@link #addGrizzlyAdapter(GrizzlyAdapter, String[])}
     */
    public void addGrizzlyAdapter(GrizzlyAdapter grizzlyAdapter){
        adapters.put(grizzlyAdapter,new String[0]);
    }

    
    /**
     * Add a {@link GrizzlyAdapter} with its associated mapping. A request will
     * be dispatched to a {@link GrizzlyAdapter} based on its mapping value.
     * @param grizzlyAdapter a {@link GrizzlyAdapter}
     * @param mapping An array contains the context path mapping information.
     */
    public void addGrizzlyAdapter(GrizzlyAdapter grizzlyAdapter, String[] mapping){
        adapters.put(grizzlyAdapter,mapping);
        adapterChains.setHandleStaticResources(false);
        if (isStarted) {
            grizzlyAdapter.start();
            Adapter ga = grizzlyListener.getAdapter();
            if (ga instanceof GrizzlyAdapterChain){
                ((GrizzlyAdapterChain)ga).addGrizzlyAdapter(grizzlyAdapter, mapping);
            } else {
                updateGrizzlyAdapters();
            }  
        }
        
    }

    /**
     * Remove a {@link GrizzlyAdapter}
     * return true of the operation was successful.
     */
    public boolean removeGrizzlyAdapter(GrizzlyAdapter grizzlyAdapter){
        if (adapters.size() > 1){
            adapterChains.removeAdapter(grizzlyAdapter);
        }
        boolean removed = adapters.remove(grizzlyAdapter) != null;
        if (isStarted) {
            Adapter ga = grizzlyListener.getAdapter();
            if (ga instanceof GrizzlyAdapterChain){
                ((GrizzlyAdapterChain)ga).removeAdapter(grizzlyAdapter);
            } else {
                updateGrizzlyAdapters();
            }    
        }
        return removed;
    }
    
    
    /**
     * Set the {@link SSLConfig} instance used when https is required
     */
    public void setSSLConfig(SSLConfig sslConfig){
        if (!(grizzlyListener instanceof SSLSelectorThread)){
            throw new IllegalStateException("This instance isn't supporting SSL/HTTPS");
        }
        ((SSLSelectorThread)grizzlyListener).setSSLConfig(sslConfig);
    }
    
    /**
     * Set to <tt>true</tt> if you want to use asynchronous write operations. Asynchronous
     * write operations may significantly improve performance under high load, but
     * may also consume more memory. Default is set to false.
     * @param asyncWrite true to enabled asynchronous write I/O operations.
     */
    public void useAsynchronousWrite(boolean asyncWrite){
        if (!(grizzlyListener instanceof SelectorThread)){
            ((SelectorThread)grizzlyListener).setAsyncHttpWriteEnabled(asyncWrite);
        }
    }

    /**
     * Start the GrizzlyWebServer and start listening for http requests. Calling 
     * this method several time has no effect once GrizzlyWebServer has been started.
     * @throws IOException If can't startup.
     */
    public void start() throws IOException{
        if (isStarted) return;
        if (grizzlyListener instanceof SSLSelectorThread) {
            // if needed create default SSLContext
            SSLSelectorThread sslST = (SSLSelectorThread) grizzlyListener;
            if (sslST.getSSLContext() == null) {
                SSLConfig sslConfig = new SSLConfig(true);
                if (!sslConfig.validateConfiguration(true)) {
                    // failed to create default
                    throw new RuntimeException("Failed to create default SSLConfig.");
                }
                sslST.setSSLContext(sslConfig.createSSLContext());
            }
        }
        isStarted = true;

        // Use the default
        updateGrizzlyAdapters();
        
        if (asyncFilters.size() > 0){
            ((SelectorThread)grizzlyListener).setEnableAsyncExecution(true);
            AsyncHandler asyncHandler = new DefaultAsyncHandler();
            for (AsyncFilter asyncFilter: asyncFilters){
                asyncHandler.addAsyncFilter(asyncFilter); 
            }
            ((SelectorThread)grizzlyListener).setAsyncHandler(asyncHandler);
        }

        try {
            grizzlyListener.listen();
        } catch (InstantiationException ex) {
            throw new IOException(ex.getMessage());
        }
        
    }

    private void updateGrizzlyAdapters() {
        adapterChains = new GrizzlyAdapterChain();
        if (adapters.isEmpty()){
            adapterChains.setRootFolder(webResourcesPath);
            adapterChains.setHandleStaticResources(true);
            grizzlyListener.setAdapter(adapterChains);
        } else {
            for (Entry<GrizzlyAdapter,String[]> entry: adapters.entrySet()){
                // For backward compatibility
                if (entry.getValue() == null || entry.getValue().length == 0){
                    adapterChains.addGrizzlyAdapter(entry.getKey(),new String[]{""});
                } else {
                    adapterChains.addGrizzlyAdapter(entry.getKey(),entry.getValue());
                }
            }
            grizzlyListener.setAdapter(adapterChains);
            adapterChains.setHandleStaticResources(false);
            adapterChains.setRootFolder(webResourcesPath);
        }
    }


    /**
     * Enable JMX Management by configuring the {@link Management}
     * @param jmxManagement An instance of the {@link Management} interface
     */
    public void enableJMX(Management jmxManagement) {
        supportHttp();
        if (jmxManagement == null) {
            return;
        }

        ((SelectorThread) grizzlyListener).setManagement(jmxManagement);
        try {
            ObjectName sname = new ObjectName(mBeanName);
            jmxManagement.registerComponent(grizzlyListener, sname, null);
        } catch (Exception ex) {
            SelectorThread.logger().log(Level.SEVERE, "Enabling JMX failed", ex);
        }
    }
    
    /**
     * Are we using a {@link SelectorThread}
     * @return true
     */
    private void supportHttp() {
        if (!(grizzlyListener instanceof SelectorThread)) {
            throw new IllegalStateException("Not supported with GrizzlyListener: "
                    + grizzlyListener);
        }
    }


    /**
     * Return a {@link Statistics} instance that can be used to gather
     * statistics. By default, the {@link Statistics} object <strong>is not</strong>
     * gathering statistics. Invoking {@link Statistics#startGatheringStatistics}
     * will do it.
     */
    public Statistics getStatistics() {
        supportHttp();
        if (statistics == null) {
            statistics = new Statistics(((SelectorThread) grizzlyListener));
        }
        return statistics;
    }
 
    /**
     * Set the initial number of  threads in a thread pool.
     * @param coreThreads the initial number of threads in a thread pool.
     */
    public void setCoreThreads(int coreThreads) {
        supportHttp();
        ((SelectorThread)grizzlyListener).setCoreThreads(coreThreads);
    }

    /**
     * Set the maximum number of  threads in a thread pool.
     * @param maxThreads the maximum number of threads in a thread pool.
     */
    public void setMaxThreads(int maxThreads) {
        supportHttp();
        ((SelectorThread)grizzlyListener).setMaxThreads(maxThreads);
    }
    
    /**
     * Stop the GrizzlyWebServer.
     */ 
    public void stop(){
        if (!isStarted) return;
        isStarted = false;
        if (grizzlyListener instanceof SelectorThread){
            ((SelectorThread)grizzlyListener).stopEndpoint();
        }
        // need to stop gathering (there are threads running for stats collection)
        if (statistics != null) {
            statistics.stopGatheringStatistics();
        }
    }
    
    
    /**
     * Return an already configured {@link GrizzlyWebServer} that can serves
     * static resources.
     * @param path the directory to serve static resource from.
     * @return a ready to use {@link GrizzlyWebServer} that listen on port 8080
     */
    public static GrizzlyWebServer newConfiguredInstance(String path){
        GrizzlyWebServer ws = new GrizzlyWebServer(8080);
        ws.addGrizzlyAdapter(new GrizzlyAdapter(path){           
            {
                setHandleStaticResources(true);
            }
            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) {
                try {
                    response.setStatus(404);
                    response.flushBuffer();
                } catch (IOException ex) {
                    final Logger localLogger = LoggerUtils.getLogger();
                    if (localLogger.isLoggable(Level.SEVERE)) {
                        localLogger.log(Level.SEVERE,
                                   LogMessages.SEVERE_GRIZZLY_HTTP_GWS_IO_ERROR(),
                                   ex);
                    }
                }
            }
        });
        return ws;
    }

    /**
     * Enable support for protocol like HTTP or AJP (Apache Java Protocol like mod_jk)
     * @param p the {@link PROTOCOL}
     */
    public void enableProtocol(PROTOCOL p){
        isStarted = false;
        if (p == PROTOCOL.AJP){
            grizzlyListener = (GrizzlyListener)
                    ClassLoaderUtil.load("com.sun.grizzly.http.jk.server.JkCoyoteHandler");
            grizzlyListener.setAdapter(adapter());
        } else {
            createSelectorThread(port, isStarted);
        }
    }

    /**
     * Return the current internal Adapter used.
     * @return the current {@link Adapter}
     */
    private Adapter adapter(){
         if (adapters.size() == 1){
            adapters.keySet().iterator().next().setRootFolder(webResourcesPath);
            return adapters.keySet().iterator().next();
        } else {
            return adapterChains;
        }
    }

    /**
     * Deploy given deployable using provided deployer.
     *
     * @param toDeploy      Deployable to deploy.
     * @param deployer      to be used for deployment.
     * @param configuration Deployment configuration.
     * @return Deployment identification.
     *
     * @throws DeployException Deployment failed.
     */
    public <T extends Deployable, V extends DeploymentConfiguration>
    DeploymentID deploy(T toDeploy, Deployer<T, V> deployer, V configuration)
        throws DeployException {
        return deployer.deploy(this, toDeploy, configuration);
    }

    /**
     * Deploy from given uri using provided deployer.
     *
     * @param fromURI       uri to deploy from.
     * @param deployer      to be used for deployment.
     * @param configuration Deployment configuration.
     * @return Deployment identification.
     *
     * @throws DeployException Deployment failed.
     */
    public <T extends Deployable, V extends DeploymentConfiguration>
    DeploymentID deploy(URI fromURI, FromURIDeployer<T, V> deployer, V configuration)
        throws DeployException {
        return deployer.deploy(this, fromURI, configuration);
    }

    /**
     * Undeploy deploymentId using provided deployer.
     *
     * @param deploymentId Deployment identification to be undeployed.
     * @param deployer     to be used for undeployng.
     * @param <T>          Type of object to be deployed.
     * @param <V>          Deployment configuration type.
     */
    public <T extends Deployable, V extends DeploymentConfiguration>
    void undeploy(DeploymentID deploymentId, Deployer<T, V> deployer) {
        deployer.undeploy(this, deploymentId);
    }
}
