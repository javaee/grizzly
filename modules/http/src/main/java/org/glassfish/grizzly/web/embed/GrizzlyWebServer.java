/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */

package org.glassfish.grizzly.web.embed;

import org.glassfish.grizzly.web.arp.DefaultAsyncHandler;
import org.glassfish.grizzly.web.arp.AsyncFilter;
import org.glassfish.grizzly.web.arp.AsyncHandler;
import org.glassfish.grizzly.web.Management;
import org.glassfish.grizzly.web.container.Adapter;
import org.glassfish.grizzly.web.container.http11.GrizzlyAdapter;
import org.glassfish.grizzly.web.container.http11.GrizzlyAdapterChain;
import org.glassfish.grizzly.web.container.http11.GrizzlyRequest;
import org.glassfish.grizzly.web.container.http11.GrizzlyResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.util.IdleTimeoutFilter;
import org.glassfish.grizzly.web.WebFilter;
import org.glassfish.grizzly.web.arp.AsyncExecutor;
import org.glassfish.grizzly.web.arp.AsyncWebFilter;
import org.glassfish.grizzly.web.arp.AsyncWebFilterConfig;
import org.glassfish.grizzly.web.container.StaticResourcesAdapter;
import org.glassfish.grizzly.web.container.util.http.mapper.Mapper;


/**
 * <p>
 * This class creates a WebServer that listen for http request. By default, 
 * creating an instance of this class can be used as it is to synchronously serve resources located 
 * under {@link #webResourcesPath}. By default, the {@link StaticResourcesAdapter} is
 * used when there is no {@link GrizzlyAdapter} specified.  The {@link StaticResourcesAdapter}
 * allow servicing of static resources like html files, images files, etc. 
 * 
 * The {@link GrizzlyAdapter} provides developpers with a simple and consistent mechanism for extending 
 * the functionality of the Grizzly WebServer and for bridging existing 
 * http based technology like JRuby-on-Rail, Servlet, Bayeux Protocol or any
 * http based protocol. 
 * </p><p>You can extend the GrizzlyWebServer by adding one or serveral
 * {@link GrizzlyAdapter}. If more that one are used, the {@link GrizzlyAdapterChain}
 * will be used to map the request to its associated {@link GrizzlyAdapter}s, using a {@link Mapper}.
 * A {@link GrizzlyAdapter} gets invoked
 * as soon as the http request has been parsed and 
 * decoded. The  {@link GrizzlyAdapter#service(org.glassfish.grizzly.web.container.http11.GrizzlyRequest, corg.glassfish.grizzly.web.container.http11.GrizzlyResponse)}
 * method is invoked with a
 * {@link GrizzlyRequest} and {@link GrizzlyResponse} that can be used to extend the
 * functionality of the Web Server. By default, all http requests are synchronously
 * executed. 
 * </p><p>Asynchronous request processing is automatically enabled
 * as soon as one or several {@link AsyncFilter} are added, using the 
 * {@link #addAsyncHandler} method. {@link AsyncFilter} can be used when 
 * asynchronous operation are required, like suspending the current http request,
 * delaying the invokation of {@link GrizzlyAdapter} etc. The state of the 
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
    // The host
    private static final String DEFAULT_HOST = "0.0.0.0";

    // The port
    private static final int DEFAULT_PORT = 8080;

    // The web resources path
    private static final String DEFAULT_WEB_RESOURCES_PATH = ".";

    // Host, on which WebServer will listen
    private String host;

    // Port, on which WebServer will listen
    private int port = DEFAULT_PORT;

    // The underlying {@link Transport}
    private TCPNIOTransport transport;

    // The underlying {@link WebFilter}
    private AsyncWebFilter webFilter;
    
    // Flag, which indicates if WebServer uses secured connections
    private boolean isSecure;

    // SSL configuration
    private SSLEngineConfigurator sslConfiguration;

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
    private String mBeanName = "org.glassfish.grizzly.web:type=GrizzlyWebServer,name=GrizzlyHttpEngine-"
            + DEFAULT_PORT;
       
    
    // The {@link Statistis} instance associated with this instance.
    private Statistics statistics;


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
     * @param webResourcesPath the path to the web resource (ex: /var/www)
     */
    public GrizzlyWebServer(int port, String webResourcesPath) {
        this(port, webResourcesPath, false);
    }
    

    /**
     * Create a WebServer that listen for secure tls/https requests
     * @param port The port opened
     * @param webResourcesPath the path to the web resource (ex: /var/www)
     * @param secure <tt>true</tt> if https needs to be used.
     */
    public GrizzlyWebServer(int port, String webResourcesPath, boolean isSecure) {
        this.port = port;
        this.host = DEFAULT_HOST;
        this.webResourcesPath = webResourcesPath;
        this.isSecure = isSecure;
        this.webFilter = new AsyncWebFilter(Integer.toString(port));
        webFilter.getConfig().setAsyncEnabled(false);
        this.transport = TransportFactory.getInstance().createTCPTransport();
        
        sslConfiguration = new SSLEngineConfigurator(
                SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(), false,
                false, false);
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
     * Add a {@link GrizzlyAdapter} with its associated mapping. A request will
     * be dispatched to a {@link GrizzlyAdapter} based on its mapping value.
     * @param grizzlyAdapter a {@link GrizzlyAdapter}
     * @param mapping An array contains the context path mapping information.
     */
    public void addGrizzlyAdapter(GrizzlyAdapter grizzlyAdapter, String[] mapping){
        adapters.put(grizzlyAdapter, mapping);
        adapterChains.setHandleStaticResources(false);
        if (isStarted) {
            grizzlyAdapter.start();
            Adapter ga = webFilter.getConfig().getAdapter();
            if (ga instanceof GrizzlyAdapterChain){
                ((GrizzlyAdapterChain) ga).addGrizzlyAdapter(grizzlyAdapter,
                        mapping);
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
            Adapter ga = webFilter.getConfig().getAdapter();
            if (ga instanceof GrizzlyAdapterChain){
                ((GrizzlyAdapterChain)ga).removeAdapter(grizzlyAdapter);
            } else {
                updateGrizzlyAdapters();
            }    
        }
        return removed;
    }

    /**
     * Returns <tt>true</tt>, if secured connections is enabled,
     *         or <tt>false</tt> otherwise
     * @return <tt>true</tt>, if secured connections is enabled,
     *         or <tt>false</tt> otherwise
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Adjust the secured connection mode.
     * @param isSecure <tt>true</tt>, if secured connections should be enabled,
     *         or <tt>false</tt> otherwise
     */
    public void setSecure(boolean isSecure) {
        this.isSecure = isSecure;
    }

    /**
     * Get the secured connection configuration.
     * @return the secured connection configuration.
     */
    public SSLEngineConfigurator getSSLConfiguration() {
        return sslConfiguration;
    }

    /**
     * Set the secured connection configuration.
     * @param sslConfiguration the secured connection configuration.
     */
    public void setSSLConfiguration(SSLEngineConfigurator sslConfiguration) {
        this.sslConfiguration = sslConfiguration;
    }

    /**
     * Get {@link GrizzlyWebServer} underlying {@link Transport}.
     * @return {@link GrizzlyWebServer} underlying {@link Transport}.
     */
    public Transport getTransport() {
        return transport;
    }

    /**
     * Set {@link GrizzlyWebServer} underlying {@link Transport}.
     * @param transport {@link GrizzlyWebServer} underlying {@link Transport}.
     */
    public void setTransport(Transport transport) {
        this.transport = (TCPNIOTransport) transport;
    }

    /**
     * Get {@link GrizzlyWebServer} underlying {@link AsyncWebFilterConfig}.
     * @return {@link GrizzlyWebServer} underlying {@link AsyncWebFilterConfig}.
     */
    public AsyncWebFilterConfig getWebConfig() {
        return webFilter.getConfig();
    }

    /**
     * Set {@link GrizzlyWebServer} underlying {@link AsyncWebFilterConfig}.
     * @param webFilter {@link GrizzlyWebServer}
     *                  underlying {@link AsyncWebFilterConfig}.
     */
    public void setWebFilter(AsyncWebFilterConfig webConfig) {
        webFilter.setConfig(webConfig);
    }

    /**
     * Get host, on which {@link GrizzlyWebServer} listens.
     * Default value is 0.0.0.0
     *
     * @return host, on which {@link GrizzlyWebServer} listens.
     */
    public String getHost() {
        return host;
    }

    /**
     * Set host, on which {@link GrizzlyWebServer} listens.
     * Default value is 0.0.0.0
     *
     * @param host The host, on which {@link GrizzlyWebServer} listens.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Get port, on which {@link GrizzlyWebServer} listens.
     * Default value is 8080.
     *
     * @return port, on which {@link GrizzlyWebServer} listens.
     */
    public int getPort() {
        return port;
    }

    /**
     * Set port, on which {@link GrizzlyWebServer} listens.
     * Default value is 8080.
     *
     * @param port The port, on which {@link GrizzlyWebServer} listens.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Start the GrizzlyWebServer and start listening for http requests. Calling 
     * this method several time has no effect once GrizzlyWebServer has been started.
     * @throws java.io.IOException
     */
    public void start() throws IOException{
        if (isStarted) return;
        isStarted = true;

        if (asyncFilters.size() > 0){
            webFilter.getConfig().setAsyncEnabled(true);
            AsyncHandler asyncHandler = new DefaultAsyncHandler();
            for (AsyncFilter asyncFilter: asyncFilters){
                asyncHandler.addAsyncFilter(asyncFilter); 
            }
            webFilter.getConfig().setAsyncHandler(asyncHandler);
        }
        
        // Use the default
        updateGrizzlyAdapters();

        transport.getFilterChain().add(new TransportFilter());

        transport.getFilterChain().add(new IdleTimeoutFilter(
                webFilter.getConfig().getKeepAliveTimeoutInSeconds(),
                TimeUnit.SECONDS));
        
        if (isSecure) {
            transport.getFilterChain().add(new SSLFilter(sslConfiguration));
        }

        transport.getFilterChain().add(webFilter);

        webFilter.initialize();
        transport.bind(host, port);
        transport.start();
    }

    private void updateGrizzlyAdapters() {
        adapterChains = new GrizzlyAdapterChain();
        if (adapters.size() == 0) {
            adapterChains.setRootFolder(webResourcesPath);
            adapterChains.setHandleStaticResources(true);
            webFilter.getConfig().setAdapter(adapterChains);
        } else if (adapters.size() == 1) {
            webFilter.getConfig().setAdapter(adapters.keySet().iterator().next());
            adapters.keySet().iterator().next().setRootFolder(webResourcesPath);
        } else {          
            for (Entry<GrizzlyAdapter,String[]> entry : adapters.entrySet()) {
                // For backward compatibility
                if (entry.getValue().length == 0) {
                    adapterChains.addGrizzlyAdapter(entry.getKey());
                } else {
                    adapterChains.addGrizzlyAdapter(entry.getKey(), entry.getValue());
                }
            }
            webFilter.getConfig().setAdapter(adapterChains);
            adapterChains.setHandleStaticResources(true);
            adapterChains.setRootFolder(webResourcesPath);
        }
    }

    /**
     * Enable JMX Management by configuring the {@link Management}
     * @param jmxManagement An instance of the {@link Management} interface
     */
    public void enableJMX(Management jmxManagement){
        if (jmxManagement == null) return;
        
        webFilter.getJmxManager().setManagement(jmxManagement);
        try {
            ObjectName sname = new ObjectName(mBeanName);                   
            webFilter.getJmxManager().registerComponent(webFilter, sname, null);
        } catch (Exception ex) {
            WebFilter.logger().log(Level.SEVERE, "Enabling JMX failed", ex);
        }      
    }
    
    
    /**
     * Return a {@link Statistics} instance that can be used to gather
     * statistics. By default, the {@link Statistics} object <strong>is not</strong>
     * gathering statistics. Invoking {@link Statistics#startGatheringStatistics}
     * will do it.
     */
    public Statistics getStatistics() {
        if (statistics == null) {
            statistics = new Statistics(webFilter);
        }
        
        return statistics;
    } 
    
    /**
     * Stop the GrizzlyWebServer.
     */ 
    public void stop() {
        if (!isStarted) return;
        isStarted = false;
        try {
            webFilter.release();
            transport.stop();
            TransportFactory.getInstance().close();
        } catch (Exception e) {
            Logger.getLogger(GrizzlyWebServer.class.getName()).log(
                    Level.WARNING, null, e);
        } finally {
            transport.getFilterChain().clear();
        }
    }
    
    
    /**
     * Return an already configured {@link GrizzlyWebServer} that can serves
     * static resources.
     * @param path the directory to serve static resource from.
     * @return a ready to use {@link GrizzlyWebServer} that listen on port 8080
     */
    public final static GrizzlyWebServer newConfiguredInstance(String path){
        GrizzlyWebServer ws = new GrizzlyWebServer(8080);
        ws.addGrizzlyAdapter(new GrizzlyAdapter(path) {
            {
                setHandleStaticResources(true);
            }
            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) {
                try {
                    response.setStatus(404);
                    response.flushBuffer();
                } catch (IOException e) {
                    Logger.getLogger(GrizzlyWebServer.class.getName()).log(
                            Level.SEVERE, null, e);
                }
            }
        }, new String[0]);
        return ws;
    }
}
