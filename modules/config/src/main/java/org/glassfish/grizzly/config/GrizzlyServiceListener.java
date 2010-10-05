/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2006-2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.config;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.config.dom.FileCache;
import org.glassfish.grizzly.config.dom.Http;
import org.glassfish.grizzly.config.dom.NetworkListener;
import org.glassfish.grizzly.config.dom.Protocol;
import org.glassfish.grizzly.config.dom.ThreadPool;
import org.glassfish.grizzly.config.dom.Transport;
import org.glassfish.grizzly.config.dom.Ssl;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainEnabledTransport;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterAdapter;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.WebFilter;
import org.glassfish.grizzly.http.WebFilterConfig;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.rcm.ResourceAllocationFilter;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.tcp.Adapter;
import org.glassfish.grizzly.threadpool.DefaultThreadPool;

/**
 * <p>The GrizzlyServiceListener is responsible of mapping incoming requests to the proper Container or Grizzly
 * extensions. Registered Containers can be notified by Grizzly using three mode:</p>
 *
 * <ul> <li>At the transport level: Containers can be notified when TCP, TLS or UDP requests are mapped to them.</li>
 * <li>At the protocol level: Containers can be notified when protocols (ex: SIP, HTTP) requests are mapped to
 * them.</li> </li>At the requests level: Containers can be notified when specific patterns requests are mapped to
 * them.</li> <ul>
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
    private HttpServerFilter webFilterConfig;
    private String defaultVirtualServer;

    /**
     * Configures the given grizzlyListener.
     *
     * @param networkListener The listener to configure
     */
    public GrizzlyServiceListener(NetworkListener networkListener) throws IOException {
        final Protocol httpProtocol = networkListener.findHttpProtocol();
        if (httpProtocol != null) {
            isEmbeddedHttpSecured = Boolean.parseBoolean(
                httpProtocol.getSecurityEnabled());
        }
        configureListener(networkListener);
        setName(networkListener.getName());
        final WebFilter filter = new WebFilter(getName(), webFilterConfig);
        filter.initialize();
        add(filter);
        System.out.println(Arrays.toString(((FilterChainEnabledTransport) nioTransport).getFilterChain().toArray()));
    }

    public WebFilterConfig getWebFilterConfig() {
        return webFilterConfig;
    }

    private void add(final Filter filter) {
        ((FilterChainEnabledTransport) nioTransport).getFilterChain().add(filter);
    }

    private void add(final Filter filter, final Class<? extends Filter> after) {
        final FilterChain chain = ((FilterChainEnabledTransport) nioTransport).getFilterChain();
        boolean added = false;
        for (int index = 0; index < chain.size() && !added; index++) {
            if (chain.get(index).getClass().isAssignableFrom(after)) {
                added = true;
                chain.add(index + 1, filter);
            }
        }
        if (!added) {
            chain.add(filter);
        }
    }

    public void start() throws IOException {
        System.out.println("Starting listener " + getName());
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
        configureThreadPool(networkListener);
    }

    protected void configureTransport(NetworkListener listener) {
        final Transport transport = listener.findTransport();
        try {
            if ("tcp".equalsIgnoreCase(transport.getName())) {
            if(listener.findHttpProtocol() != null) {
                createHttpServer(listener);
            } else {
                throw new GrizzlyConfigException("Unsupported listener configuration on " + listener.getName());
            }
        } else {
            throw new GrizzlyConfigException("Unsupported transport type " + transport.getName());
        }
        } catch (IOException e) {
            throw new GrizzlyConfigException(e.getMessage(), e);
        }
    }

    private void createHttpServer(final NetworkListener listener) throws IOException {
        final HttpServer server = new HttpServer();
        final org.glassfish.grizzly.http.server.NetworkListener http
            = new org.glassfish.grizzly.http.server.NetworkListener(listener.getName(), listener.getAddress(),
            Integer.parseInt(listener.getPort()));
        final Transport transport = listener.findTransport();
        final int backLog = Integer.parseInt(transport.getMaxConnectionsCount());

        http.
//        nioTransport = TransportFactory.getInstance().createTCPTransport();
//        final TCPNIOTransport tcp = (TCPNIOTransport) nioTransport;
//        tcp.setTcpNoDelay(GrizzlyConfig.toBoolean(transport.getTcpNoDelay()));
//
//        nioTransport.setSelectorRunnersCount(Integer.parseInt(transport.getAcceptorThreads()));
//        transport settings
//        webFilterConfig = new WebFilterConfig();
//        webFilterConfig.setClassLoader(getClass().getClassLoader());
//        webFilterConfig.setRequestBufferSize(Integer.parseInt(transport.getBufferSizeBytes()));
//        webFilterConfig.setDisplayConfiguration(GrizzlyConfig.toBoolean(transport.getDisplayConfiguration()));

        server.addListener(http);
        server.start();
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
                configureSsl(protocol);
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
           for (com.glassfish.grizzly.config.dom.ProtocolFinder finderConfig : findersConfig) {
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
           com.glassfish.grizzly.config.dom.ProtocolChainInstanceHandler pcihConfig = protocol
               .getProtocolChainInstanceHandler();
           if (pcihConfig == null) {
               logger.log(Level.WARNING, "Empty protocol declaration");
               return null;
           }
           ProtocolChain protocolChain = null;
           com.glassfish.grizzly.config.dom.ProtocolChain protocolChainConfig = pcihConfig.getProtocolChain();
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
           for (com.glassfish.grizzly.config.dom.ProtocolFilter protocolFilterConfig : protocolChainConfig
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

    private void configureSsl(final Protocol protocol) {
        final Ssl ssl = protocol.getSsl();
        if (Boolean.valueOf(ssl.getAllowLazyInit())) {
            System.out.println("Lazy init'ing ssl");
            add(new LazyInitSslFilter(ssl));
        } else {
            System.out.println("adding ssl filter");
            add(new SSLFilter(SSLConfigHolder.configureSSL(ssl)), TransportFilter.class);
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
            org.glassfish.grizzly.http.FileCache fileCache = new org.glassfish.grizzly.http.FileCache(webFilterConfig);
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

    private class LazyInitSslFilter extends FilterAdapter {
        private Ssl ssl;
        private boolean initialized = false;

        public LazyInitSslFilter(final Ssl ssl) {
            this.ssl = ssl;
        }

        private void init() {
            if (!initialized) {
                add(new SSLFilter(SSLConfigHolder.configureSSL(ssl)), getClass());
                final FilterChain chain = ((FilterChainEnabledTransport) nioTransport).getFilterChain();
                chain.remove(this);
                initialized = true;
            }
        }

        @Override
        public NextAction handleAccept(final FilterChainContext ctx, final NextAction nextAction) throws IOException {
            init();
            return nextAction;
        }
    }
}
