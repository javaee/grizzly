/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 */

package com.sun.grizzly.http.server.embed;

import com.sun.grizzly.Processor;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.http.server.Constants;
import com.sun.grizzly.http.server.WebServerFilter;
import com.sun.grizzly.http.server.adapter.GrizzlyAdapter;
import com.sun.grizzly.http.server.adapter.GrizzlyAdapterChain;
import com.sun.grizzly.ssl.SSLContextConfigurator;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.ssl.SSLEngineConfigurator;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.utils.ChunkingFilter;
import com.sun.grizzly.utils.IdleTimeoutFilter;
import com.sun.grizzly.util.http.mapper.Mapper;


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
 * decoded. The  {@link GrizzlyAdapter#service(com.sun.grizzly.tcp.http11.GrizzlyRequest, ccom.sun.grizzly.tcp.http11.GrizzlyResponse)}
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
        GrizzlyWebServer ws = new GrizzlyWebServer(443,path,5,true);
        SSLContextConfigurator sslConfig = new SSLContextConfigurator();
        sslConfig.setTrustStoreFile("Path-to-trustore");
        sslConfig.setKeyStoreFile("Path-to-Trustore");
        ws.setSSLConfiguration(new SSLEngineConfigurator(
                sslConfig.createSSLContext(), false, false, false);
 
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
 * @author Alexey Stashok
 */
public class GrizzlyWebServer {


    /**
     * The TCPNIOTransport implementation used by this server instance.
     */
    private TCPNIOTransport transport;


    /**
     * Configuration details for this server instance.
     */
    private final ServerConfiguration configuration = new ServerConfiguration();


    /**
     * Configuration details for the transport used by the server.
     */
    private final ListenerConfiguration listenerConfig = new ListenerConfiguration();


    /**
     * Flag indicating whether or not this server instance has been started.
     */
    private boolean started;


    // ---------------------------------------------------------- Public Methods


    /**
     * @return {@link GrizzlyWebServer} underlying {@link TCPNIOTransport}
     */
    public TCPNIOTransport getTransport() {
        return transport;
    }

    /**
     * @param transport {@link GrizzlyWebServer} underlying {@link TCPNIOTransport}
     */
    public void setTransport(TCPNIOTransport transport) {
        this.transport = transport;
    }


    /**
     * @return the {@link ServerConfiguration} used to configure this
     *  {@link GrizzlyWebServer} instance
     */
    public ServerConfiguration getServerConfiguration() {
        return configuration;
    }


    public ListenerConfiguration getListenerConfiguration() {
        return listenerConfig;
    }



    /**
     * Start the GrizzlyWebServer and start listening for http requests. Calling 
     * this method several time has no effect once GrizzlyWebServer has been started.
     *
     * @throws java.io.IOException
     */
    public void start() throws IOException{

        if (started) {
            return;
        }
        started = true;

        TCPNIOTransport trans = getTransport();
        ServerConfiguration serverConfig = getServerConfiguration();
        // TODO: Should we assume a full configured transport if explicitly set?
        if (trans == null) {
            ListenerConfiguration listenerConf = getListenerConfiguration();
            trans = buildListener(listenerConf, serverConfig);
            setTransport(trans);
            trans.bind(listenerConf.getHost(), listenerConf.getPort());
        }
        trans.start();
        
        GrizzlyAdapter adapter = serverConfig.getAdapter();
        if (adapter != null) {
            adapter.start();
        }

    }



    /**
     * Enable JMX Management by configuring the {@link Management}
     * @param jmxManagement An instance of the {@link Management} interface
     */
//    public void enableJMX(Management jmxManagement){
//        if (jmxManagement == null) return;
//
//        webFilter.getJmxManager().setManagement(jmxManagement);
//        try {
//            ObjectName sname = new ObjectName(mBeanName);
//            webFilter.getJmxManager().registerComponent(webFilter, sname, null);
//        } catch (Exception ex) {
//            WebFilter.logger().log(Level.SEVERE, "Enabling JMX failed", ex);
//        }
//    }
    
    
    /**
     * Return a {@link Statistics} instance that can be used to gather
     * statistics. By default, the {@link Statistics} object <strong>is not</strong>
     * gathering statistics. Invoking {@link Statistics#startGatheringStatistics}
     * will do it.
     */
//    public Statistics getStatistics() {
//        if (statistics == null) {
//            statistics = new Statistics(webFilter);
//        }
//
//        return statistics;
//    }
    
    /**
     * Stop the GrizzlyWebServer.
     */ 
    public void stop() {

        if (!started) {
            return;
        }
        started = false;
        try {
            GrizzlyAdapter adapter = getServerConfiguration().getAdapter();
            if (adapter != null) {
                adapter.destroy();
            }
            getTransport().stop();
            TransportFactory.getInstance().close();
        } catch (Exception e) {
            Logger.getLogger(GrizzlyWebServer.class.getName()).log(
                    Level.WARNING, null, e);
        } finally {
            Processor p = getTransport().getProcessor();
            if (p instanceof FilterChain) {
                ((FilterChain) p).clear();
            }
        }

    }
    
    
    /**
     * Return an already configured {@link GrizzlyWebServer} that can serves
     * static resources.
     * @param path the directory to serve static resource from.
     * @return a ready to use {@link GrizzlyWebServer} that listen on port 8080
     */
//    public final static GrizzlyWebServer newConfiguredInstance(String path){
//        GrizzlyWebServer ws = new GrizzlyWebServer(8080);
//        ws.addGrizzlyAdapter(new GrizzlyAdapter(path) {
//            {
//                setHandleStaticResources(true);
//            }
//            @Override
//            public void service(GrizzlyRequest request, GrizzlyResponse response) {
//                try {
//                    response.setStatus(404);
//                    response.flushBuffer();
//                } catch (IOException e) {
//                    Logger.getLogger(GrizzlyWebServer.class.getName()).log(
//                            Level.SEVERE, null, e);
//                }
//            }
//        }, new String[0]);
//        return ws;
//    }


    // --------------------------------------------------------- Private Methods


    private TCPNIOTransport buildListener(ListenerConfiguration config,
                                          ServerConfiguration serverConfig) {

        TCPNIOTransport trans = TransportFactory.getInstance().createTCPTransport();
        FilterChain chain = config.getFilterChain();
        if (chain == null) {
            FilterChainBuilder builder = FilterChainBuilder.stateless();
            builder.add(new TransportFilter());
            builder.add(new ChunkingFilter(1024));
            builder.add(new IdleTimeoutFilter(config.getKeepAliveTimeoutInSeconds(),
                                             TimeUnit.SECONDS));
            if (config.isSecure()) {
                SSLEngineConfigurator sslConfig = config.getSslEngineConfig();
                if (sslConfig == null) {
                    sslConfig =
                          new SSLEngineConfigurator(
                                SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(),
                                false,
                                false,
                                false);
                    config.setSSLEngineConfig(sslConfig);
                }
                builder.add(new SSLFilter(sslConfig, null));
            }
            int maxHeaderSize = ((config.getMaxHttpHeaderSize() == -1)
                                 ? HttpServerFilter.DEFAULT_MAX_HTTP_PACKET_HEADER_SIZE
                                 : config.getMaxHttpHeaderSize());
            builder.add(new HttpServerFilter(maxHeaderSize));
            builder.add(new WebServerFilter(serverConfig));
            chain = builder.build();
            config.setFilterChain(chain);
            trans.setProcessor(chain);
        }

        return trans;
        
    }


    // ---------------------------------------------------------- Nested Classes


    /**
     * TODO Documentation
     */
    public static class ListenerConfiguration {

        /**
         * <p>
         * The network port to which the <code>GrizzlyWebServer<code> will
         * bind to in order to service <code>HTTP</code> requests.  The network port
         * number is {@value #DEFAULT_NETWORK_PORT}.
         * </p>
         */
        public static final int DEFAULT_NETWORK_PORT = 8080;


        /**
         * <p>
         * The network host to which the <code>GrizzlyWebServer<code> will
         * bind to in order to service <code>HTTP</code> requests.  The host
         * name is {@value #DEFAULT_NETWORK_HOST}
         */
        public static final String DEFAULT_NETWORK_HOST = "0.0.0.0";


        /*
         * The network port to which this server instance will bind to.
         */
        private int port = DEFAULT_NETWORK_PORT;


        /*
         * Flag indicating requests to this server instance should be secured via
         * SSL.
         */
        private boolean secure;


        /*
         * The configuration for this server instance's SSL support.
         */
        private SSLEngineConfigurator sslEngineConfig;


        /*
         * The network interface address/host name to bind to.
         */
        private String host = DEFAULT_NETWORK_HOST;


        /*
         * Number of seconds before idle keep-alive connections expire
         */
        private int keepAliveTimeoutInSeconds = Constants.KEEP_ALIVE_TIMEOUT_IN_SECONDS;


        /*
         * FilterChain to be used by this listener.
         */
        private FilterChain filterChain;


        /*
         * The maximum size, in bytes, of an HTTP message.
         */
        private int maxHttpHeaderSize = -1;


        // -------------------------------------------------------- Constructors


        private ListenerConfiguration() { }


        // ------------------------------------------------------ Public Methods


        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public FilterChain getFilterChain() {
            return filterChain;
        }

        public void setFilterChain(FilterChain filterChain) {
            this.filterChain = filterChain;
        }

        public SSLEngineConfigurator getSslEngineConfig() {
            return sslEngineConfig;
        }

        public void setSSLEngineConfig(SSLEngineConfigurator sslEngineConfig) {
            this.sslEngineConfig = sslEngineConfig;
        }

        public int getKeepAliveTimeoutInSeconds() {
            return keepAliveTimeoutInSeconds;
        }

        public void setKeepAliveTimeoutInSeconds(int keepAliveTimeoutInSeconds) {
            this.keepAliveTimeoutInSeconds = keepAliveTimeoutInSeconds;
        }

        public int getMaxHttpHeaderSize() {
            return maxHttpHeaderSize;
        }

        public void setMaxHttpHeaderSize(int maxHttpHeaderSize) {
            this.maxHttpHeaderSize = maxHttpHeaderSize;
        }

    } // END ListenerConfig


    /**
     * TODO: Documentation
     */
    public static class ServerConfiguration {

        // Configuration Properties

        /**
         * <p>
         * The directory from which the <code>GrizzlyWebServer</code> will service
         * static resources from.  If this value is used, the <code>GrizzlyWebServer</code>
         * will serve resources from the directory in which the <code>GrizzlyWebServer</code>
         * was launched.
         * </p>
         */
        public static final String DEFAULT_WEB_RESOURCES_PATH = ".";


        /*
         * The directory from which static resources will be served from.
         */
        private String webResourcesPath = DEFAULT_WEB_RESOURCES_PATH;


        /*
         * Static file cache.
         */
        //private FileCache fileCache;


        // Non-exposed

        private Map<GrizzlyAdapter, String[]> adapters = new LinkedHashMap<GrizzlyAdapter, String[]>();
        private GrizzlyAdapterChain adapterChain;


        // ------------------------------------------------------------ Constructors


        private ServerConfiguration() { }


        // ---------------------------------------------------------- Public Methods


        public String getWebResourcesPath() {
            return webResourcesPath;
        }

        public void setWebResourcesPath(String webResourcesPath) {
            this.webResourcesPath = webResourcesPath;
        }

//        public FileCache getFileCache() {
//            return fileCache;
//        }
//
//        public void setFileCache(FileCache fileCache) {
//            this.fileCache = fileCache;
//        }




        /**
         * TODO: Update docs
         * Add a {@link GrizzlyAdapter} with its associated mapping. A request will
         * be dispatched to a {@link GrizzlyAdapter} based on its mapping value.
         *
         * @param grizzlyAdapter a {@link GrizzlyAdapter}
         * @param mapping        An array contains the context path mapping information.
         */
        public void addGrizzlyAdapter(GrizzlyAdapter grizzlyAdapter,
                                      String[] mapping) {

            adapters.put(grizzlyAdapter, mapping);
            if (adapters.size() > 1) {
                adapterChain = new GrizzlyAdapterChain();
                adapterChain.setHandleStaticResources(false);
                for (Map.Entry<GrizzlyAdapter,String[]> e : adapters.entrySet()) {
                    adapterChain.addGrizzlyAdapter(e.getKey(), e.getValue());
                }
                adapters.put(grizzlyAdapter, mapping);
            }

        }

        /**
         * TODO: Update docs
         * Remove a {@link GrizzlyAdapter}
         * return <tt>true</tt>, if the operation was successful.
         */
        public boolean removeGrizzlyAdapter(GrizzlyAdapter grizzlyAdapter) {

            if (adapters.size() > 1) {
                adapterChain.removeAdapter(grizzlyAdapter);
            }
            return (adapters.remove(grizzlyAdapter) != null);

        }


        /**
         * TODO Docs
         * @return
         */
        public GrizzlyAdapter getAdapter() {

            if (adapters.size() == 1) {
                GrizzlyAdapter adapter = adapters.keySet().iterator().next();
                adapter.setRootFolder(webResourcesPath);
                return adapter;
            }
            if (adapterChain == null) {
                adapterChain = new GrizzlyAdapterChain();
                adapterChain.setHandleStaticResources(true);
                adapterChain.setRootFolder(webResourcesPath);
            }
            return adapterChain;

        }

    } // END ServerConfiguration


    // -------------------------------------------------------------------- MAIN

    /**
     * TODO - REMOVE later
     * @param args
     */
    public static void main(String[] args) {

        GrizzlyWebServer server = new GrizzlyWebServer();
        server.getServerConfiguration().setWebResourcesPath("/tmp");
        try {
            server.start();
            System.out.println("Press any key to stop the server...");
            System.in.read();
        } catch (IOException ioe) {
            System.err.println(ioe.toString());
            ioe.printStackTrace();
        } finally {
            server.stop();
        }

    }
}
