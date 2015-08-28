/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.CompletionHandler;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.GracefulShutdownListener;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PortRange;
import org.glassfish.grizzly.ShutdownContext;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.CompressionConfig.CompressionMode;
import org.glassfish.grizzly.http.HttpCodecFilter;
import org.glassfish.grizzly.http.KeepAlive;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.monitoring.MonitoringUtils;
import org.glassfish.grizzly.nio.transport.TCPNIOServerConnection;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.strategies.SameThreadIOStrategy;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.ArraySet;
import org.glassfish.grizzly.utils.Futures;

public class NetworkListener {
    private static final Logger LOGGER = Grizzly.logger(NetworkListener.class);

    /**
     * The default network host to which the {@link HttpServer} will bind to in order to service <code>HTTP</code>
     * requests.
     */
    public static final String DEFAULT_NETWORK_HOST = "0.0.0.0";

    /**
     * The default network port to which the {@link HttpServer} will bind to in order to service <code>HTTP</code>
     * requests.
     */
    public static final int DEFAULT_NETWORK_PORT = 8080;

    /**
     * The network host to which the <code>HttpServer<code> will bind to in order to service <code>HTTP</code> requests.
     * If not explicitly set, the value of {@link #DEFAULT_NETWORK_HOST} will be used.
     */
    private String host = DEFAULT_NETWORK_HOST;

    /**
     * The network port to which the <code>HttpServer<code> will bind to in order to service <code>HTTP</code> requests.
     * If not explicitly set, the value of {@link #DEFAULT_NETWORK_PORT} will be used.
     */
    private int port = DEFAULT_NETWORK_PORT;
    /**
     * The flag indicates if the <code>HttpServer<code> will be bounnd to an inherited Channel.
     * If not explicitly set, the <code>HttpServer</code> will be bound to  {@link #DEFAULT_NETWORK_HOST}:{@link #DEFAULT_NETWORK_PORT}.
     */
    private final boolean isBindToInherited;
    
    /**
     * The time, in seconds, for which a request must complete processing.
     */
    private int transactionTimeout = -1;

    /**
     * The network port range to which the <code>HttpServer<code> will bind to
     * in order to service <code>HTTP</code> requests.
     * If not explicitly set, the value of {@link #port} will be used.
     */
    private PortRange portRange;

    /**
     * The logical <code>name</code> of this particular <code>NetworkListener</code> instance.
     */
    private final String name;
    /**
     * The configuration for HTTP keep-alive connections
     */
    private final KeepAlive keepAliveConfig = new KeepAlive();
    /**
     * The Grizzly {@link FilterChain} used to process incoming and outgoing network I/O.
     */
    private FilterChain filterChain;
    /**
     * The {@link TCPNIOTransport} used by this <code>NetworkListener</code>
     */
    private TCPNIOTransport transport;
    /**
     * TCP Server {@link Connection} responsible for accepting client connections
     */
    private TCPNIOServerConnection serverConnection;    
    
    /**
     * The default error page generator
     */
    private ErrorPageGenerator defaultErrorPageGenerator;
    
    /**
     * The HTTP server {@link SessionManager}.
     */
    private SessionManager sessionManager;
    
    {
        final TCPNIOTransportBuilder builder = TCPNIOTransportBuilder.newInstance();
        final int coresCount = Runtime.getRuntime().availableProcessors() * 2;
        
        transport = builder
                .setIOStrategy(SameThreadIOStrategy.getInstance())
                .setWorkerThreadPoolConfig(ThreadPoolConfig.defaultConfig()
                .setPoolName("Grizzly-worker")
                .setCorePoolSize(coresCount)
                .setMaxPoolSize(coresCount)
                .setMemoryManager(builder.getMemoryManager()))
                .build();
    }
    
    /**
     * Flag indicating whether or not this listener is secure.  Defaults to <code>false</code>
     */
    private boolean secure;

    /**
     * AddOns registered for the network listener
     */
    private final ArraySet<AddOn> addons = new ArraySet<AddOn>(AddOn.class);

    /**
     * Flag indicating whether or not the chunked transfer encoding is enabled.  Defaults to <code>true</code>.
     */
    private boolean chunkingEnabled = true;
    /**
     * Configuration for the {@link SSLEngine} that will be used for secure listeners.
     */
    private SSLEngineConfigurator sslEngineConfig;
    /**
     * The maximum size of an incoming <code>HTTP</code> message.
     */
    private int maxHttpHeaderSize = -1;
    /**
     * {@link FileCache} to be used by this <code>NetworkListener</code>.
     */
    private final FileCache fileCache = new FileCache();
    /**
     * Maximum size, in bytes, of all data waiting to be written.
     */
    private volatile int maxPendingBytes = -1;
    /**
     * Flag indicating the state of this listener.
     */
    private State state = State.STOPPED;
    /**
     * Future to control graceful shutdown status
     */
    private FutureImpl<NetworkListener> shutdownFuture;
    /**
     * CompletionHandler for filter shutdown notification.
     */
    private CompletionHandler<HttpServerFilter> shutdownCompletionHandler;
    /**
     * {@link HttpServerFilter} associated with this listener.
     */
    private HttpServerFilter httpServerFilter;
    /**
     * {@link HttpCodecFilter} associated with this listener.
     */
    private HttpCodecFilter httpCodecFilter;
    /**
     * {@link CompressionConfig}
     */
    private final CompressionConfig compressionConfig = new CompressionConfig();
    
    private boolean authPassThroughEnabled;
    private int maxFormPostSize = 2 * 1024 * 1024;
    private int maxBufferedPostSize = 2 * 1024 * 1024;
    private String restrictedUserAgents;
    private int uploadTimeout;
    private boolean disableUploadTimeout;
    private boolean traceEnabled;
    private String uriEncoding;
    private Boolean sendFileEnabled;
    
    /**
     * The auxiliary configuration, which might be used, when Grizzly HttpServer
     * is running behind some HTTP gateway like reverse proxy or load balancer.
     */
    private BackendConfiguration backendConfiguration;

    private int maxRequestHeaders = MimeHeaders.MAX_NUM_HEADERS_DEFAULT;
    private int maxResponseHeaders = MimeHeaders.MAX_NUM_HEADERS_DEFAULT;

    // ------------------------------------------------------------ Constructors

    /**
     * <p> Constructs a new <code>NetworkListener</code> using the specified <code>name</code>.  The listener's host and
     * port will default to {@link #DEFAULT_NETWORK_HOST} and {@link #DEFAULT_NETWORK_PORT}. </p>
     *
     * @param name the logical name of the listener.
     */
    public NetworkListener(final String name) {
        this(name, false);
    }

    /**
     * <p> Constructs a new <code>NetworkListener</code> using the specified <code>name</code>, which,
     * depending on <code>isBindToInherited</code> will or will not be bound to an inherited Channel.</p>
     *
     * @param name the logical name of the listener.
     * @param isBindToInherited if <tt>true</tt> the <code>NetworkListener</code> will be
     * bound to an inherited Channel, otherwise default {@link #DEFAULT_NETWORK_HOST} and {@link #DEFAULT_NETWORK_PORT}
     * will be used.
     * 
     * @see System#inheritedChannel()
     */
    public NetworkListener(final String name, final boolean isBindToInherited) {
        validateArg("name", name);
        this.name = name;
        this.isBindToInherited = isBindToInherited;
    }
    
    /**
     * <p> Constructs a new <code>NetworkListener</code> using the specified <code>name</code> and <code>host</code>.
     * The listener's port will default to {@link #DEFAULT_NETWORK_PORT}. </p>
     *
     * @param name the logical name of the listener.
     * @param host the network host to which this listener will bind.
     */
    public NetworkListener(final String name, final String host) {
        this(name, host, DEFAULT_NETWORK_PORT);
    }

    /**
     * <p> Constructs a new <code>NetworkListener</code> using the specified <code>name</code>, <code>host</code>, and
     * <code>port</code>. </p>
     *
     * @param name the logical name of the listener.
     * @param host the network host to which this listener will bind.
     * @param port the network port to which this listener will bind..
     */
    public NetworkListener(final String name, final String host, final int port) {
        validateArg("name", name);
        validateArg("host", host);
        if (port < 0) {
            throw new IllegalArgumentException("Invalid port");
        }
        this.name = name;
        this.host = host;
        this.port = port;
        isBindToInherited = false;
    }

    /**
     * <p> Constructs a new <code>NetworkListener</code> using the specified <code>name</code>, <code>host</code>, and
     * <code>port</code>. </p>
     *
     * @param name the logical name of the listener.
     * @param host the network host to which this listener will bind.
     * @param portRange the network port range to which this listener will bind..
     */
    public NetworkListener(final String name,
        final String host,
        final PortRange portRange) {
        validateArg("name", name);
        validateArg("host", host);

        this.name = name;
        this.host = host;
        this.port = -1;
        this.portRange = portRange;
        isBindToInherited = false;
    }

    // ----------------------------------------------------------- Configuration

    /**
     * @return the logical name of this listener.
     */
    public String getName() {
        return name;

    }

    /**
     * @return the network host to which this listener is configured to bind to.
     */
    public String getHost() {
        return host;

    }

    /**
     * @return the network port to which this listener is configured to bind to.
     * If the {@link HttpServer} has not been started yet - the returned value
     * may be:
     * <tt>-1</tt>, if {@link PortRange} will be used to bind the listener;
     * <tt>0</tt>, if the port will be assigned by OS;
     * <tt>0 < N < 65536</tt>, the port this listener will be bound to.
     * If {@link HttpServer} has been started - the value returned is the port the
     * this listener is bound to.
     */
    public int getPort() {
        return port;

    }

    /**
     * @return the network port range to which this listener is configured to bind to.
     */
    public PortRange getPortRange() {
        return portRange;

    }

    /**
     * @return the configuration for the keep-alive HTTP connections.
     */
    public KeepAlive getKeepAlive() {
        return keepAliveConfig;

    }

    /**
     * @return the {@link TCPNIOTransport} used by this listener.
     */
    public TCPNIOTransport getTransport() {
        return transport;

    }

    /**
     * <p> This allows the developer to specify a custom {@link TCPNIOTransport} implementation to be used by this
     * listener. </p>
     * <p/>
     * <p> Attempts to change the transport implementation while the listener is running will be ignored. </p>
     *
     * @param transport a custom {@link TCPNIOTransport} implementation.
     */
    public void setTransport(final TCPNIOTransport transport) {
        if (transport == null) {
            return;
        }
        if (!transport.isStopped()) {
            return;
        }
        this.transport = transport;

    }

    /**
     * Return the array of the registered {@link AddOn}s.
     * Please note, possible array modifications wont affect the
     * {@link NetworkListener}'s addons list.
     *
     * @return the array of the registered {@link AddOn}s.
     */
    public AddOn[] getAddOns() {
        return addons.obtainArrayCopy();
    }

    /**
     * Returns the direct addons collection, registered on the NetworkListener.
     * @return the direct addons collection, registered on the NetworkListener.
     */
    protected ArraySet<AddOn> getAddOnSet() {
        return addons;
    }

    /**
     * Registers {@link AddOn} on this NetworkListener.
     * @param addon the {@link AddOn} to be registered.
     *
     * @return <tt>true</tt>, if the {@link AddOn} wasn't registered before,
     *  otherwise the existing {@link AddOn} will be replaced and this method
     *  returns <tt>false</tt>.
     */
    public boolean registerAddOn(final AddOn addon) {
        return addons.add(addon);
    }

    /**
     * Deregisters {@link AddOn} from this NetworkListener.
     * @param addon the {@link AddOn} to deregister.
     *
     * @return <tt>true</tt>, if the {@link AddOn} was successfully removed, or
     *          <tt>false</tt> the the {@link AddOn} wasn't registered on the
     *          NetworkListener.
     */
    public boolean deregisterAddOn(final AddOn addon) {
        return addons.remove(addon);
    }

    /**
     * @return <code>true</code> if the HTTP response bodies should be chunked if not content length has been explicitly
     *         specified.
     */
    public boolean isChunkingEnabled() {
        return chunkingEnabled;

    }

    /**
     * Enable/disable chunking of an HTTP response body if no content length has been explictly specified.  Chunking is
     * enabled by default.
     *
     * @param chunkingEnabled <code>true</code> to enable chunking; <code>false</code> to disable.
     */
    public void setChunkingEnabled(boolean chunkingEnabled) {
        this.chunkingEnabled = chunkingEnabled;
    }

    /**
     * @return <code>true</code> if this is a secure listener, otherwise <code>false</code>.  Listeners are not secure
     *         by default.
     */
    public boolean isSecure() {
        return secure;

    }

    /**
     * <p> Enable or disable security for this listener. </p>
     * <p/>
     * <p> Attempts to change this value while the listener is running will be ignored. </p>
     *
     * @param secure if <code>true</code> this listener will be secure.
     */
    public void setSecure(final boolean secure) {
        if (!isStopped()) {
            return;
        }
        this.secure = secure;

    }

    /**
     * Get the HTTP request scheme, which if non-null overrides default one
     * picked up by framework during runtime.
     *
     * @return the HTTP request scheme
     * 
     * @since 2.2.4
     */
    public String getScheme() {
        final BackendConfiguration config = backendConfiguration;
        return config != null ? config.getScheme() : null;
    }

    /**
     * Set the HTTP request scheme, which if non-null overrides default one
     * picked up by framework during runtime.
     *
     * @param scheme the HTTP request scheme
     * 
     * @since 2.2.4
     */
    public void setScheme(String scheme) {
        BackendConfiguration config = backendConfiguration;
        if (config == null) {
            config = new BackendConfiguration();
        }
        
        config.setScheme(scheme);
        this.backendConfiguration = config;
    }
    
    /**
     * @return the auxiliary configuration, which might be used, when Grizzly
     * HttpServer is running behind HTTP gateway like reverse proxy or load balancer.
     *
     * @since 2.3.18
     */    
    public BackendConfiguration getBackendConfiguration() {
        return backendConfiguration;
    }

    /**
     * Sets the auxiliary configuration, which might be used, when Grizzly HttpServer
     * is running behind HTTP gateway like reverse proxy or load balancer.
     *
     * @param backendConfiguration {@link BackendConfiguration}
     * @since 2.3.18
     */
    public void setBackendConfiguration(BackendConfiguration backendConfiguration) {
        this.backendConfiguration = backendConfiguration;
    }

    /**
     * Returns the maximum number of headers allowed for a request.
     *
     * @since 2.2.11
     */
    public int getMaxRequestHeaders() {
        return maxRequestHeaders;
    }

    /**
     * Sets the maximum number of headers allowed for a request.
     *
     * If the specified value is less than zero, then there may be an
     * unlimited number of headers (memory permitting).
     *
     * @since 2.2.11
     */
    public void setMaxRequestHeaders(int maxRequestHeaders) {
        this.maxRequestHeaders = maxRequestHeaders;
    }

    /**
     * Returns the maximum number of headers allowed for a response.
     *
     * @since 2.2.11
     */
    public int getMaxResponseHeaders() {
        return maxResponseHeaders;
    }

    /**
     * Sets the maximum number of headers allowed for a response.
     *
     * If the specified value is less than zero, then there may be an
     * unlimited number of headers (memory permitting).
     *
     * @since 2.2.11
     */
    public void setMaxResponseHeaders(int maxResponseHeaders) {
        this.maxResponseHeaders = maxResponseHeaders;
    }

    /**
     * @return the {@link SSLEngine} configuration for this listener.
     */
    public SSLEngineConfigurator getSslEngineConfig() {
        return sslEngineConfig;
    }

    /**
     * <p> Provides customization of the {@link SSLEngine} used by this listener. </p>
     * <p/>
     * <p> Attempts to change this value while the listener is running will be ignored. </p>
     *
     * @param sslEngineConfig custom SSL configuration.
     */
    public void setSSLEngineConfig(final SSLEngineConfigurator sslEngineConfig) {
        if (!isStopped()) {
            return;
        }
        this.sslEngineConfig = sslEngineConfig;

    }

    /**
     * @return the maximum header size for an HTTP request.
     */
    public int getMaxHttpHeaderSize() {
        return maxHttpHeaderSize;

    }

    /**
     * <p> Configures the maximum header size for an HTTP request. </p>
     * <p/>
     * <p> Attempts to change this value while the listener is running will be ignored. </p>
     *
     * @param maxHttpHeaderSize the maximum header size for an HTTP request.
     */
    public void setMaxHttpHeaderSize(final int maxHttpHeaderSize) {
        if (!isStopped()) {
            return;
        }
        this.maxHttpHeaderSize = maxHttpHeaderSize;

    }

    /**
     * @return the {@link FilterChain} used to by the {@link TCPNIOTransport} associated with this listener.
     */
    public FilterChain getFilterChain() {
        return filterChain;

    }

    /**
     * <p> Specifies the {@link FilterChain} to be used by the {@link TCPNIOTransport} associated with this listener.
     * </p>
     * <p/>
     * <p> Attempts to change this value while the listener is running will be ignored. </p>
     *
     * @param filterChain the {@link FilterChain}.
     */
    void setFilterChain(final FilterChain filterChain) {
        if (!isStopped()) {
            return;
        }
        if (filterChain != null) {
            this.filterChain = filterChain;
        }

    }

    /**
     * @return the {@link FileCache} associated with this listener.
     */
    public FileCache getFileCache() {
        return fileCache;
    }

    /**
     * @return the maximum size, in bytes, of all data waiting to be written to the associated {@link Connection}.
     *  If not explicitly set, the value will be -1 which effectively disables
     *  resource enforcement.
     */
    public int getMaxPendingBytes() {
        return maxPendingBytes;

    }

    /**
     * The maximum size, in bytes, of all data waiting to be written to the associated {@link Connection}.
     * If the value is zero or less, then no resource enforcement will take place.
     *
     * @param maxPendingBytes the maximum size, in bytes, of all data waiting to be written to the associated {@link
     * Connection}.
     */
    public void setMaxPendingBytes(int maxPendingBytes) {
        this.maxPendingBytes = maxPendingBytes;
        transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(maxPendingBytes);
        }

    // ---------------------------------------------------------- Public Methods

    /**
     * @return <code>true</code> if this listener has been paused, otherwise <code>false</code>
     */
    public boolean isPaused() {
        return state == State.PAUSED;
    }

    /**
     * @return <code>true</code> if the listener has been started, otherwise <code>false</code>.
     */
    public boolean isStarted() {
        return state != State.STOPPED;

    }

    /**
     * <p> Starts the listener. </p>
     *
     * @throws IOException if an error occurs when attempting to start the listener.
     */
    public synchronized void start() throws IOException {
        if (isStarted()) {
            return;
        }
        
        shutdownFuture = null;
        if (filterChain == null) {
            throw new IllegalStateException("No FilterChain available."); // i18n
        }
        transport.setProcessor(filterChain);

        if (isBindToInherited) {
            serverConnection = transport.bindToInherited();
        } else {
            serverConnection = (port != -1) ?
                transport.bind(host, port) :
                transport.bind(host, portRange, transport.getServerConnectionBackLog());
        }
        
        port = ((InetSocketAddress) serverConnection.getLocalAddress()).getPort();

        transport.addShutdownListener(new GracefulShutdownListener() {
            @Override
            public void shutdownRequested(final ShutdownContext shutdownContext) {
                final FutureImpl<NetworkListener> shutdownFutureLocal = shutdownFuture;
                shutdownCompletionHandler =
                        new EmptyCompletionHandler<HttpServerFilter>() {
                            @Override
                            public void completed(final HttpServerFilter filter) {
                                try {
                                    shutdownContext.ready();
                                    shutdownFutureLocal.result(NetworkListener.this);
                                } catch (Throwable e) {
                                    shutdownFutureLocal.failure(e);
                                }
                            }
                        };

                getHttpServerFilter().prepareForShutdown(shutdownCompletionHandler);
            }

            @Override
            public void shutdownForced() {
                serverConnection = null;
                if (shutdownFuture != null) {
                    shutdownFuture.result(NetworkListener.this);
                }
            }
        });

        transport.start();

        state = State.RUNNING;

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                "Started listener bound to [{0}]",
                host + ':' + port);
        }

    }

    public synchronized GrizzlyFuture<NetworkListener> shutdown(final long gracePeriod,
                                                                final TimeUnit timeUnit) {
        if (state == State.STOPPING
                || state == State.STOPPED) {
            return shutdownFuture != null ? shutdownFuture :
                    Futures.createReadyFuture(this);
        } else if (state == State.PAUSED) {
            resume();
        }

        state = State.STOPPING;

        shutdownFuture = Futures.createSafeFuture();

        getHttpServerFilter().prepareForShutdown(shutdownCompletionHandler);

        transport.shutdown(gracePeriod, timeUnit);

        return shutdownFuture;
    }
    
    /**
     * <p> Gracefully shuts down the listener. </p>   Any exceptions
     * thrown during the shutdown process will be propagated to the returned
     * {@link GrizzlyFuture}.
     * @return {@link GrizzlyFuture}
     */
    public synchronized GrizzlyFuture<NetworkListener> shutdown() {
        return shutdown(-1, TimeUnit.MILLISECONDS);
    }

    /**
     * <p> Immediately shuts down the listener. </p>
     *
     * @throws IOException if an error occurs when attempting to shut down the listener
     */
    public synchronized void shutdownNow() throws IOException {
        if (state == State.STOPPED) {
            return;
        }
        
        try {
            serverConnection = null;
            transport.shutdownNow();
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,
                    "Stopped listener bound to [{0}]",
                    host + ':' + port);
            }
        } finally {
            state = State.STOPPED;
            if (shutdownFuture != null) {
                shutdownFuture.result(this);
            }
        }
    }

    /**
     * <p> Immediately shuts down the listener. </p>
     *
     * @throws IOException if an error occurs when attempting to shut down the listener
     * @deprecated use {@link #shutdownNow()}
     */
    public void stop() throws IOException {
        shutdownNow();
    }

    /**
     * <p> Pauses the listener. </p>
     */
    public synchronized void pause() {
        if (state != State.RUNNING) {
            return;
        }
        transport.pause();
        state = State.PAUSED;
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                "Paused listener bound to [{0}]",
                host + ':' + port);
        }

    }

    /**
     * <p> Resumes a paused listener. </p>
     */
    public synchronized void resume() {
        if (state != State.PAUSED) {
            return;
        }
        transport.resume();
        state = State.RUNNING;
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                "Resumed listener bound to [{0}]",
                host + ':' + port);
        }

    }

    /**
     * @return a value containing the name, host, port, and secure status of this listener.
     */
    @Override
    public String toString() {
        return "NetworkListener{" +
            "name='" + name + '\'' +
            ", host='" + host + '\'' +
            ", port=" + port +
            ", secure=" + secure +
            ", state=" + state +
            '}';
    }

    public Object createManagementObject() {
        return MonitoringUtils.loadJmxObject(
                "org.glassfish.grizzly.http.server.jmx.NetworkListener",
                this, NetworkListener.class);
    }

    public HttpServerFilter getHttpServerFilter() {
        if (httpServerFilter == null) {
            final int idx = filterChain.indexOfType(HttpServerFilter.class);
            if (idx == -1) {
                return null;
            }
            
            httpServerFilter = (HttpServerFilter) filterChain.get(idx);
        }
        return httpServerFilter;

    }

    public HttpCodecFilter getHttpCodecFilter() {
        if (httpCodecFilter == null) {
            final int idx = filterChain.indexOfType(HttpCodecFilter.class);
            if (idx == -1) {
                return null;
            }
            
            httpCodecFilter = (HttpCodecFilter) filterChain.get(idx);
        }
        return httpCodecFilter;

    }
    // --------------------------------------------------------- Private Methods

    private static void validateArg(final String name, final String value) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException("Argument "
                + name
                + " cannot be "
                + (value == null
                ? "null"
                : "have a zero length")); // I18n
        }

    }

    public boolean isAuthPassThroughEnabled() {
        return authPassThroughEnabled;
    }

    public void setAuthPassThroughEnabled(final boolean authPassthroughEnabled) {
        this.authPassThroughEnabled = authPassthroughEnabled;
    }

    /**
     * Returns {@link CompressionConfig} configuration.
     * 
     * @since 2.3.5
     */
    public CompressionConfig getCompressionConfig() {
        return compressionConfig;
    }

    /**
     * @deprecated use <tt>getCompressionConfig().getCompressionMode().name()</tt>
     */
    public String getCompression() {
        return compressionConfig.getCompressionMode().name();
    }

    /**
     * @deprecated use <tt>getCompressionConfig().setCompressionMode(mode)</tt>
     */
    public void setCompression(final String compression) {
        compressionConfig.setCompressionMode(CompressionMode.fromString(compression));
    }
    
    /**
     * @deprecated use <tt>getCompressionConfig().getCompressionMinSize()</tt>
     */
    public int getCompressionMinSize() {
        return compressionConfig.getCompressionMinSize();
    }

    /**
     * @deprecated use <tt>getCompressionConfig().setCompressionMinSize(int)</tt>
     */
    public void setCompressionMinSize(final int compressionMinSize) {
        compressionConfig.setCompressionMinSize(compressionMinSize);
    }

    /**
     * @deprecated use <tt>getCompressionConfig().getCompressableMimeTypes()</tt>
     */
    public String getCompressibleMimeTypes() {
        return setToString(compressionConfig.getCompressableMimeTypes());
    }

    /**
     * @deprecated use <tt>getCompressionConfig().setCompressableMimeTypes(Set&lt;String&gt;)</tt>
     */
    public void setCompressibleMimeTypes(final String compressibleMimeTypes) {
        compressionConfig.setCompressableMimeTypes(stringToSet(compressibleMimeTypes));
    }
    
    /**
     * @deprecated use <tt>getCompressionConfig().getNoCompressionUserAgents()</tt>
     */
    public String getNoCompressionUserAgents() {
        return setToString(compressionConfig.getNoCompressionUserAgents());
    }

    /**
     * @deprecated use <tt>getCompressionConfig().setNoCompressionUserAgents(Set&lt;String&gt;)</tt>
     */
    public void setNoCompressionUserAgents(final String noCompressionUserAgents) {
        compressionConfig.setNoCompressionUserAgents(stringToSet(noCompressionUserAgents));
    }
    
    public boolean isDisableUploadTimeout() {
        return disableUploadTimeout;
    }

    public void setDisableUploadTimeout(final boolean disableUploadTimeout) {
        this.disableUploadTimeout = disableUploadTimeout;
    }

    /**
     * Gets the maximum size of the POST body generated by an HTML form.
     * <code>-1</code> value means no size limits applied.
     * 
     * @since 2.3
     */
    public int getMaxFormPostSize() {
        return maxFormPostSize;
    }

    /**
     * Sets the maximum size of the POST body generated by an HTML form.
     * <code>-1</code> value means no size limits applied.
     * 
     * @since 2.3
     */
    public void setMaxFormPostSize(final int maxFormPostSize) {
        this.maxFormPostSize = maxFormPostSize < 0 ? -1 : maxFormPostSize;
    }

    /**
     * Gets the maximum POST body size, which can buffered in memory.
     * <code>-1</code> value means no size limits applied.
     *
     * @since 2.3
     */
    public int getMaxBufferedPostSize() {
        return maxBufferedPostSize;
    }

    /**
     * Sets the maximum POST body size, which can buffered in memory.
     * <code>-1</code> value means no size limits applied.
     *
     * @since 2.3
     */
    public void setMaxBufferedPostSize(final int maxBufferedPostSize) {
        this.maxBufferedPostSize = maxBufferedPostSize < 0 ? -1 : maxBufferedPostSize;
    }

    public String getRestrictedUserAgents() {
        return restrictedUserAgents;
    }

    public void setRestrictedUserAgents(final String restrictedUserAgents) {
        this.restrictedUserAgents = restrictedUserAgents;
    }

    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public void setTraceEnabled(final boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public int getUploadTimeout() {
        return uploadTimeout;
    }

    public void setUploadTimeout(final int uploadTimeout) {
        this.uploadTimeout = uploadTimeout;
    }

    public String getUriEncoding() {
        return uriEncoding;
    }

    public void setUriEncoding(final String uriEncoding) {
        this.uriEncoding = uriEncoding;
    }

    /**
     * @return The timeout, in seconds, within which a request must complete
     *  its processing. If not explicitly set, no transaction timeout will
     *  be enforced.
     */
    public int getTransactionTimeout() {
        return transactionTimeout;
    }

    /**
     * Sets the time, in seconds, within which a request must complete its
     * processing.  A value less than or equal to zero will disable this
     * timeout. Note that this configuration option is only considered when the
     * transport's {@link org.glassfish.grizzly.Transport#getWorkerThreadPool()}
     * thread pool is used to run a {@link HttpHandler}.
     *
     * @param transactionTimeout timeout in seconds
     */
    public void setTransactionTimeout(final int transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

    /**
     * @see org.glassfish.grizzly.http.server.ServerFilterConfiguration#isSendFileEnabled()
     *
     * @since 2.2
     */
    public boolean isSendFileEnabled() {
        return sendFileEnabled;
    }

    /**
     * @see ServerFilterConfiguration#setSendFileEnabled(boolean)
     *
     * @since 2.2
     */
    public void setSendFileEnabled(boolean sendFileEnabled) {
        this.sendFileEnabled = sendFileEnabled;
    }

    /**
     * @return the <tt>NetworkListener</tt> default {@link ErrorPageGenerator}.
     */
    public ErrorPageGenerator getDefaultErrorPageGenerator() {
        return defaultErrorPageGenerator;
    }

    /**
     * Sets the <tt>NetworkListener</tt> default {@link ErrorPageGenerator}.
     * 
     * @param defaultErrorPageGenerator 
     */
    public void setDefaultErrorPageGenerator(
            final ErrorPageGenerator defaultErrorPageGenerator) {
        this.defaultErrorPageGenerator = defaultErrorPageGenerator;
    }

    /**
     * @return the HTTP server {@link SessionManager}
     *
     * @see		#setSessionManager
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Sets the HTTP server {@link SessionManager}.
     *
     * @param sessionManager	{@link SessionManager}
     */    
    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    boolean isSendFileExplicitlyConfigured() {
        return (sendFileEnabled != null);
    }

    private boolean isStopped() {
        return state == State.STOPPED || state == State.STOPPING;
    }

    private static String setToString(final Set<String> set) {
        final StringBuilder sb = new StringBuilder(set.size() * 10);
        for (String elem : set) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            
            sb.append(elem);
        }
        
        return sb.toString();
    }
    
    private static Set<String> stringToSet(final String s) {
        if (s == null) {
            return null;
        }
        
        return new HashSet<String>(Arrays.asList(s.split(",")));
    }    
    
}
