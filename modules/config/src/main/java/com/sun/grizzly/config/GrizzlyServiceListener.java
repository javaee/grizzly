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
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.config;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.grizzly.SocketBinder;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.config.dom.FileCache;
import com.sun.grizzly.config.dom.Http;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.ThreadPool;
import com.sun.grizzly.config.dom.Transport;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainEnabledTransport;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.WebFilter;
import com.sun.grizzly.http.WebFilterConfig;
import com.sun.grizzly.nio.NIOTransport;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.rcm.ResourceAllocationFilter;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.threadpool.DefaultThreadPool;

/**
 * <p>The GrizzlyServiceListener is responsible of mapping incoming requests to the proper Container or Grizzly
 * extensions. Registered Containers can be notified by Grizzly using three mode:</p>
 *
 * <ul>
 *      <li>At the transport level: Containers can be notified when TCP, TLS or UDP requests are mapped to them.</li>
 *      <li>At the protocol level: Containers can be notified when protocols (ex: SIP, HTTP) requests are mapped to them.</li>
 *      </li>At the requests level: Containers can be notified when specific patterns requests are mapped to them.</li>
 * <ul>
 *
 * @author Jeanfrancois Arcand
 * @author Justin Lee
 */
public class GrizzlyServiceListener {
    /**
     * The logger to use for logging messages.
     */
    protected static final Logger logger = Logger.getLogger(GrizzlyServiceListener.class.getName());
    private boolean isEmbeddedHttpSecured;
    private String name;
    private int port;
    private NIOTransport nioTransport;
    private WebFilterConfig webFilterConfig;
    private String defaultVirtualServer;

    /**
     * Configures the given grizzlyListener.
     *
     * @param networkListener The listener to configure
     */
    public GrizzlyServiceListener(NetworkListener networkListener) {
        final Protocol httpProtocol = networkListener.findHttpProtocol();
        if (httpProtocol != null) {
            isEmbeddedHttpSecured = Boolean.parseBoolean(
                httpProtocol.getSecurityEnabled());
        }
        configureListener(networkListener);
        setName(networkListener.getName());
        add(new WebFilter(getName(), webFilterConfig));
    }

    public WebFilterConfig getWebFilterConfig() {
        return webFilterConfig;
    }

    private void add(final Filter filter) {
        ((FilterChainEnabledTransport)nioTransport).getFilterChain().add(filter);
    }

    private void add(final Filter filter, final Class<? extends Filter> after) {
        final FilterChain chain = ((FilterChainEnabledTransport) nioTransport).getFilterChain();
        boolean added = false;
        for(int index = 0; index < chain.size() && !added; index++) {
            if(chain.get(index).getClass().isAssignableFrom(after)) {
                added = true;
                chain.add(index + 1, filter);
            }
        }
        if(!added) {
            chain.add(filter);
        }
    }

    public void start() throws IOException, InstantiationException {
        nioTransport.start();
    }

    public void stop() throws IOException {
        nioTransport.stop();
    }

    public String getDefaultVirtualServer() {
        return defaultVirtualServer;
    }

    public boolean isEmbeddedHttpSecured() {
        return isEmbeddedHttpSecured;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPort() {
        return port;
    }

    public void configureListener(NetworkListener networkListener) {
        configureTransport(networkListener);
        final boolean mayEnableComet = !"admin-listener".equalsIgnoreCase(networkListener.getName());
        configureProtocol(networkListener, mayEnableComet);
        configureThreadPool(networkListener/*, getThreadPoolTimeoutSeconds()*/);
    }

    protected void configureTransport(NetworkListener listener) {
        final Transport transport = listener.findTransport();
        try {
            final String address = listener.getAddress();
            port = Integer.parseInt(listener.getPort());
            final int backLog = Integer.parseInt(transport.getMaxConnectionsCount());
            if ("tcp".equalsIgnoreCase(transport.getName())) {
                nioTransport = TransportFactory.getInstance().createTCPTransport();
                final TCPNIOTransport tcp = (TCPNIOTransport) nioTransport;
                tcp.setTcpNoDelay(GrizzlyConfig.toBoolean(transport.getTcpNoDelay()));
            } else if ("udp".equalsIgnoreCase(transport.getName())) {
                nioTransport = TransportFactory.getInstance().createUDPTransport();
            } else {
                throw new RuntimeException("Unknown transport type " + transport.getName());
            }
            add(new TransportFilter());
            ((SocketBinder) nioTransport).bind(address, port, backLog);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new GrizzlyConfigException(e.getMessage(), e);
        }
        nioTransport.setSelectorRunnersCount(Integer.parseInt(transport.getAcceptorThreads()));
        // transport settings
        webFilterConfig = new WebFilterConfig();
        webFilterConfig.setClassLoader(getClass().getClassLoader());
        webFilterConfig.setRequestBufferSize(Integer.parseInt(transport.getBufferSizeBytes()));
        webFilterConfig.setDisplayConfiguration(GrizzlyConfig.toBoolean(transport.getDisplayConfiguration()));
    }

    private void configureProtocol(NetworkListener networkListener, boolean mayEnableComet) {
        final Protocol protocol = networkListener.findProtocol();
        if (protocol.getHttp() != null) {
            // Only HTTP protocol defined
            final Http http = protocol.getHttp();
            configureHttpListenerProperty(http);
            configureKeepAlive(http);
            configureHttpProtocol(http);
            configureFileCache(http.getFileCache());
            defaultVirtualServer = http.getDefaultVirtualServer();
            // acceptor-threads
            if (mayEnableComet && GrizzlyConfig.toBoolean(http.getCometSupportEnabled())) {
//                configureComet(habitat);
            }
            if (Boolean.valueOf(protocol.getSecurityEnabled())) {
                add(new SSLFilter(SSLConfigHolder.configureSSL(protocol.getSsl())), TransportFilter.class);
            }
/*
       } else if (protocol.getPortUnification() != null) {
           // Port unification
           PortUnification pu = protocol.getPortUnification();
           final String puFilterClassname = pu.getClassname();
           if (puFilterClassname != null) {
               try {
                   puFilter = (PUReadFilter) newInstance(puFilterClassname);
                   configureElement(puFilter, pu);
               } catch (Exception e) {
                   logger.log(Level.WARNING,
                       "Can not initialize port unification filter: " +
                           puFilterClassname + " default filter will be used instead", e);
               }
           }
           List<ProtocolFinder> findersConfig = pu.getProtocolFinder();
           for (com.sun.grizzly.config.dom.ProtocolFinder finderConfig : findersConfig) {
               String finderClassname = finderConfig.getClassname();
               try {
                   ProtocolFinder protocolFinder = (ProtocolFinder) newInstance(finderClassname);
                   configureElement(protocolFinder, finderConfig);
                   Protocol subProtocol = finderConfig.findProtocol();
                   ProtocolChainInstanceHandler protocolChain =
                       configureProtocol(networkListener, subProtocol,
                           habitat, mayEnableComet);
                   final String protocolName = subProtocol.getName();
                   finders.add(new ConfigProtocolFinderWrapper(protocolName, protocolFinder));
                   final String[] protocols = new String[]{protocolName};
                   if (protocolChain != null) {
                       handlers.add(new CustomFilterChainProtocolHandler(protocolChain) {
                           public String[] getProtocols() {
                               return protocols;
                           }

                           public ByteBuffer getByteBuffer() {
                               final WorkerThread workerThread = (WorkerThread) Thread.currentThread();
                               if (workerThread.getSSLEngine() != null) {
                                   return workerThread.getInputBB();
                               }
                               return null;
                           }
                       });
                   } else {
                       handlers.add(new WebProtocolHandler(protocolName));
                   }
               } catch (Exception e) {
                   logger.log(Level.WARNING, "Can not initialize sub protocol. Finder: " +
                       finderClassname, e);
               }
           }
           configurePortUnification();
       } else {
           com.sun.grizzly.config.dom.ProtocolChainInstanceHandler pcihConfig = protocol
               .getProtocolChainInstanceHandler();
           if (pcihConfig == null) {
               logger.log(Level.WARNING, "Empty protocol declaration");
               return null;
           }
           ProtocolChain protocolChain = null;
           com.sun.grizzly.config.dom.ProtocolChain protocolChainConfig = pcihConfig.getProtocolChain();
           final String protocolChainClassname = protocolChainConfig.getClassname();
           if (protocolChainClassname != null) {
               try {
                   protocolChain = (ProtocolChain) newInstance(protocolChainClassname);
                   configureElement(protocolChain, protocolChainConfig);
               } catch (Exception e) {
                   logger.log(Level.WARNING, "Can not initialize protocol chain: " +
                       protocolChainClassname + ". Default one will be used", e);
               }
           }
           if (protocolChain == null) {
               protocolChain = new DefaultProtocolChain();
           }
           for (com.sun.grizzly.config.dom.ProtocolFilter protocolFilterConfig : protocolChainConfig
               .getProtocolFilter()) {
               String filterClassname = protocolFilterConfig.getClassname();
               try {
                   ProtocolFilter filter = (ProtocolFilter) newInstance(filterClassname);
                   configureElement(filter, protocolFilterConfig);
                   protocolChain.addFilter(filter);
               } catch (Exception e) {
                   logger.log(Level.WARNING, "Can not initialize protocol filter: " +
                       filterClassname, e);
                   throw new IllegalStateException("Can not initialize protocol filter: " +
                       filterClassname);
               }
           }
           // Ignore ProtocolChainInstanceHandler class name configuration
           final ProtocolChain finalProtocolChain = protocolChain;
           ProtocolChainInstanceHandler pcih = new DefaultProtocolChainInstanceHandler() {
               @Override
               public boolean offer(ProtocolChain protocolChain) {
                   return true;
               }

               @Override
               public ProtocolChain poll() {
                   return finalProtocolChain;
               }

           };
           return pcih;
       }
       return null;
*/
        }
    }

/*
        protected void configurePortUnification() {
            configurePortUnification(finders, handlers, preprocessors);
        }

        @Override
        public void configurePortUnification(List<ProtocolFinder> protocolFinders,
            List<ProtocolHandler> protocolHandlers,
            List<PUPreProcessor> preProcessors) {
            if (puFilter != null) {
                puFilter.configure(protocolFinders, protocolHandlers, preProcessors);
            } else {
                super.configurePortUnification(protocolFinders, protocolHandlers,
                    preProcessors);
            }
        }

        private final void configureComet(Habitat habitat) {
            final AsyncFilter cometFilter = habitat.getComponent(AsyncFilter.class, "comet");
            if (cometFilter != null) {
                setEnableAsyncExecution(true);
                asyncHandler = new DefaultAsyncHandler();
                asyncHandler.addAsyncFilter(cometFilter);
                setAsyncHandler(asyncHandler);
            }
        }
    */

    /**
     * Configure the Grizzly FileCache mechanism
     */
    private void configureFileCache(FileCache cache) {
        if (cache != null && GrizzlyConfig.toBoolean(cache.getEnabled())) {
            com.sun.grizzly.http.FileCache fileCache = new com.sun.grizzly.http.FileCache(webFilterConfig);
            webFilterConfig.setFileCache(fileCache);
            fileCache.setSecondsMaxAge(Integer.parseInt(cache.getMaxAgeSeconds()));
            fileCache.setMaxCacheEntries(Integer.parseInt(cache.getMaxFilesCount()));
            fileCache.setMaxLargeCacheSize(Integer.parseInt(cache.getMaxCacheSizeBytes()));
        }
    }

    private void configureHttpListenerProperty(Http http) throws NumberFormatException {
        // http settings
        try {
            webFilterConfig.setAdapter((Adapter) Class.forName(http.getAdapter()).newInstance());
        } catch (Exception e) {
            throw new GrizzlyConfigException(e.getMessage(), e);
        }
        webFilterConfig.setMaxKeepAliveRequests(Integer.parseInt(http.getMaxConnections()));
        webFilterConfig
            .getProperties().put("authPassthroughEnabled", GrizzlyConfig.toBoolean(http.getAuthPassThroughEnabled()));
        webFilterConfig.setMaxPostSize(Integer.parseInt(http.getMaxPostSizeBytes()));
        webFilterConfig.setCompression(http.getCompression());
        webFilterConfig.setCompressableMimeTypes(http.getCompressableMimeType());
        nioTransport.setWriteBufferSize(Integer.parseInt(http.getSendBufferSizeBytes()));
        webFilterConfig.setNoCompressionUserAgents(http.getNoCompressionUserAgents());
        webFilterConfig.setCompressionMinSize(Integer.parseInt(http.getCompressionMinSizeBytes()));
        webFilterConfig.setRestrictedUserAgents(http.getRestrictedUserAgents());
        if (GrizzlyConfig.toBoolean(http.getRcmSupportEnabled())) {
            ((TCPNIOTransport) nioTransport).getFilterChain().add(new ResourceAllocationFilter());
        }
        webFilterConfig.setUploadTimeout(Integer.parseInt(http.getConnectionUploadTimeoutMillis()));
        webFilterConfig.setDisableUploadTimeout(GrizzlyConfig.toBoolean(http.getUploadTimeoutEnabled()));
        webFilterConfig.setUseChunking(GrizzlyConfig.toBoolean(http.getChunkingEnabled()));
        webFilterConfig.getProperties().put("uriEncoding", http.getUriEncoding());
        webFilterConfig.getProperties().put("traceEnabled", GrizzlyConfig.toBoolean(http.getTraceEnabled()));
    }

    private void configureKeepAlive(Http http) {
        if (http != null) {
            webFilterConfig.setKeepAliveTimeoutInSeconds(Integer.parseInt(http.getTimeoutSeconds()));
            webFilterConfig.setMaxKeepAliveRequests(Integer.parseInt(http.getMaxConnections()));
        }
    }

    private void configureHttpProtocol(Http http) {
        if (http != null) {
            webFilterConfig.setMaxHttpHeaderSize(Integer.parseInt(http.getHeaderBufferLengthBytes()));
        }
    }

    private void configureThreadPool(NetworkListener networkListener) {
        final ThreadPool threadPool = networkListener.findThreadPool();
        if (null != threadPool) {
            try {
                Http http = networkListener.findHttpProtocol().getHttp();
                int keepAlive = http == null ? 0 : Integer.parseInt(http.getTimeoutSeconds());
                final int maxQueueSize = threadPool.getMaxQueueSize() != null ? Integer.MAX_VALUE
                    : Integer.parseInt(threadPool.getMaxQueueSize());
                final int minThreads = Integer.parseInt(threadPool.getMinThreadPoolSize());
                final int maxThreads = Integer.parseInt(threadPool.getMaxThreadPoolSize());
                final int timeout = Integer.parseInt(threadPool.getIdleThreadTimeoutSeconds());
                webFilterConfig.setWorkerThreadPool(newThreadPool(minThreads, maxThreads, maxQueueSize,
                    keepAlive < 0 ? Long.MAX_VALUE : keepAlive * 1000, TimeUnit.MILLISECONDS));
//                webFilterConfig.setCoreThreads(minThreads);
//                webFilterConfig.setMaxThreads(maxThreads);
                List<String> l = ManagementFactory.getRuntimeMXBean().getInputArguments();
                boolean debugMode = false;
                for (String s : l) {
                    if (s.trim().startsWith("-Xrunjdwp:")) {
                        debugMode = true;
                        break;
                    }
                }
                if (!debugMode && timeout > 0) {
                    // Idle Threads cannot be alive more than 15 minutes by default
                    webFilterConfig.setTransactionTimeout(timeout * 1000);
                } else {
                    // Disable the mechanism
                    webFilterConfig.setTransactionTimeout(-1);
                }
            } catch (NumberFormatException ex) {
                logger.log(Level.WARNING, " Invalid thread-pool attribute", ex);
            }
        }
    }

    protected DefaultThreadPool newThreadPool(int minThreads, int maxThreads,
        int maxQueueSize, long timeout, TimeUnit timeunit) {
        return new DefaultThreadPool(minThreads, maxThreads, maxQueueSize, timeout, timeunit);
    }

    public NIOTransport getTransport() {
        return nioTransport;
    }
}
