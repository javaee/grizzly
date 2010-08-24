/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.server;

import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.http.HttpCodecFilter;
import com.sun.grizzly.http.server.filecache.FileCache;
import com.sun.grizzly.monitoring.jmx.JmxObject;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.ssl.SSLEngineConfigurator;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GrizzlyListener {

    private static final Logger LOGGER = Grizzly.logger(GrizzlyListener.class);

    /**
     * The default network host to which the {@link GrizzlyWebServer} will
     * bind to in order to service <code>HTTP</code> requests.  
     */
    public static final String DEFAULT_NETWORK_HOST = "0.0.0.0";


    /**
     * The default network port to which the {@link GrizzlyWebServer} will
     * bind to in order to service <code>HTTP</code> requests.
     */
    public static final int DEFAULT_NETWORK_PORT = 8080;


    /**
     * The network host to which the <code>GrizzlyWebServer<code> will
     * bind to in order to service <code>HTTP</code> requests.   If not
     * explicitly set, the value of {@link #DEFAULT_NETWORK_HOST} will be used.
     */
    private String host = DEFAULT_NETWORK_HOST;


    /**
     * The network port to which the <code>GrizzlyWebServer<code> will
     * bind to in order to service <code>HTTP</code> requests.  If not
     * explicitly set, the value of {@link #DEFAULT_NETWORK_PORT} will be used.
     */
    private int port = DEFAULT_NETWORK_PORT;


    /**
     * The logical <code>name</code> of this particular
     * <code>GrizzlyListener</code> instance.
     */
    private final String name;

    /**
     * The number int seconds a connection may be idle before being timed out.
     */
    private int keepAliveTimeoutInSeconds = Constants.KEEP_ALIVE_TIMEOUT_IN_SECONDS;


    /**
     * The Grizzly {@link FilterChain} used to process incoming and outgoing
     * network I/O.
     */
    private FilterChain filterChain;


    /**
     * The {@link TCPNIOTransport} used by this <code>GrizzlyListener</code>
     */
    private TCPNIOTransport transport =
            TransportFactory.getInstance().createTCPTransport();


    /**
     * Flag indicating whether or not this listener is secure.  Defaults to
     * <code>false</code>
     */
    private boolean secure;


    /**
     * Flag indicated whether or not Web Sockets is enabled.  Defaults to
     * <code>false</code>
     */
    private boolean webSocketsEnabled;


    /**
     * Configuration for the {@link javax.net.ssl.SSLEngine} that will be used
     * for secure listeners.
     */
    private SSLEngineConfigurator sslEngineConfig;


    /**
     * The maximum size of an incoming <code>HTTP</code> message.
     * TODO: this doesn't feel like the right location
     */
    private int maxHttpHeaderSize = -1;


    /**
     * {@link FileCache} to be used by this <code>GrizzlyListener</code>.
     */
    private FileCache fileCache = new FileCache();


    /**
     * Maximum size, in bytes, of all data within all pending writes.
     */
    private volatile int maxPendingBytes;


    /**
     * Flag indicating the paused state of this listener.
     */
    private boolean paused;


    /**
     * {@link WebServerFilter} associated with this listener.
     */
    private WebServerFilter webServerFilter;


    /**
     * {@link HttpCodecFilter} associated with this listener.
     */
    private HttpCodecFilter httpCodecFilter;


    // ------------------------------------------------------------ Constructors


    /**
     * <p>
     * Constructs a new <code>GrizzlyListener</code> using the specified
     * <code>name</code>.  The listener's host and port will default to
     * {@link #DEFAULT_NETWORK_HOST} and {@link #DEFAULT_NETWORK_PORT}.
     * </p>
     *
     * @param name the logical name of the listener.
     */
    public GrizzlyListener(final String name) {

        validateArg("name", name);
        this.name = name;

    }


    /**
     * <p>
     * Constructs a new <code>GrizzlyListener</code> using the specified
     * <code>name</code> and <code>host</code>.  The listener's port will
     * default to {@link #DEFAULT_NETWORK_PORT}.
     * </p>
     *
     * @param name the logical name of the listener.
     * @param host the network host to which this listener will bind.
     */
    public GrizzlyListener(final String name, final String host) {

        validateArg("name", name);
        validateArg("host", host);
        this.name = name;
        this.host = host;

    }


    /**
     * <p>
     * Constructs a new <code>GrizzlyListener</code> using the specified
     * <code>name</code>, <code>host</code>, and <code>port</code>.
     * </p>
     *
     * @param name the logical name of the listener.
     * @param host the network host to which this listener will bind.
     * @param port the network port to which this listener will bind..
     */
    public GrizzlyListener(final String name,
                           final String host,
                           final int port) {

        validateArg("name", name);
        validateArg("host", name);
        if (port <= 0) {
            throw new IllegalArgumentException("Invalid port");
        }
        this.name = name;
        this.host = host;
        this.port = port;
        
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
     */
    public int getPort() {

        return port;

    }


    /**
     * @return the number in seconds a connection may be idle before being
     *  timed out.
     */
    public int getKeepAliveTimeoutInSeconds() {

        return keepAliveTimeoutInSeconds;

    }


    /**
     * <p>
     * Configures idle connection timeout behavior.
     * </p>
     *
     * @param keepAliveTimeoutInSeconds the number in seconds a connection may
     *  be idle before being timed out.  Values less than zero are ignored.
     */
    public void setKeepAliveTimeoutInSeconds(final int keepAliveTimeoutInSeconds) {

        if (!transport.isStopped()) {
            return;
        }

        if (keepAliveTimeoutInSeconds < 0) {
            return;
        }
        this.keepAliveTimeoutInSeconds = keepAliveTimeoutInSeconds;

    }


    /**
     * @return the {@link TCPNIOTransport} used by this listener.
     */
    public TCPNIOTransport getTransport() {

        return transport;

    }


    /**
     * <p>
     * This allows the developer to specify a custom {@link TCPNIOTransport}
     * implementation to be used by this listener.
     * </p>
     *
     * <p>
     * Attempts to change the transport implementation while the listener
     * is running will be ignored.
     * </p>
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
     * @return <code>true</code> if Web Sockets is enabled, otherwise,
     *  returns <code>false</code>.
     */
    public boolean isWebSocketsEnabled() {

        return webSocketsEnabled;

    }


    /**
     * Enables/disables Web Sockets support for this listener.
     *
     * @param webSocketsEnabled <code>true</code> if Web Sockets support
     *  should be enabled.
     */
    public void setWebSocketsEnabled(boolean webSocketsEnabled) {

        this.webSocketsEnabled = webSocketsEnabled;

    }


    /**
     * @return <code>true</code> if this is a secure listener, otherwise
     *  <code>false</code>.  Listeners are not secure by default.
     */
    public boolean isSecure() {

        return secure;

    }


    /**
     * <p>
     * Enable or disable security for this listener.
     * </p>
     *
     * <p>
     * Attempts to change this value while the listener is running will
     * be ignored.
     * </p>
     *
     * @param secure if <code>true</code> this listener will be secure.
     */
    public void setSecure(final boolean secure) {

        if (!transport.isStopped()) {
            return;
        }
        this.secure = secure;

    }


    /**
     * @return the {@link javax.net.ssl.SSLEngine} configuration for this
     *  listener.
     */
    public SSLEngineConfigurator getSslEngineConfig() {

        return sslEngineConfig;

    }


    /**
     * <p>
     * Provides customization of the {@link javax.net.ssl.SSLEngine}
     * used by this listener.
     * </p>
     *
     * <p>
     * Attempts to change this value while the listener is running will
     * be ignored.
     * </p>
     *
     * @param sslEngineConfig custom SSL configuration.
     */
    public void setSSLEngineConfig(final SSLEngineConfigurator sslEngineConfig) {

        if (!transport.isStopped()) {
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
     * <p>
     * Configures the maximum header size for an HTTP request.
     * </p>
     *
     * <p>
     * Attempts to change this value while the listener is running will
     * be ignored.
     * </p>
     *
     * @param maxHttpHeaderSize the maximum header size for an HTTP request.
     */
    public void setMaxHttpHeaderSize(final int maxHttpHeaderSize) {

        if (!transport.isStopped()) {
            return;
        }
        this.maxHttpHeaderSize = maxHttpHeaderSize;

    }


    /**
     * @return the {@link FilterChain} used to by the {@link TCPNIOTransport}
     *  associated with this listener.
     */
    public FilterChain getFilterChain() {

        return filterChain;

    }


    /**
     * <p>
     * Specifies the {@link FilterChain} to be used by the {@link TCPNIOTransport}
     * associated with this listener.
     * </p>
     *
     * <p>
     * Attempts to change this value while the listener is running will
     * be ignored.
     * </p>
     *
     * @param filterChain the {@link FilterChain}.
     */
    public void setFilterChain(final FilterChain filterChain) {

        if (!transport.isStopped()) {
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
     * @return the maximum size, in bytes, of all writes pending to be written
     *  to their associated {@link com.sun.grizzly.Connection}.
     */
    public int getMaxPendingBytes() {

        return maxPendingBytes;

    }


    /**
     * The maximum size, in bytes, of all writes pending to be written
     * to their associated {@link com.sun.grizzly.Connection}.
     *
     * @param maxPendingBytes the maximum size, in bytes, of all writes pending
     *  to be written to their associated {@link com.sun.grizzly.Connection}.
     */
    public void setMaxPendingBytes(int maxPendingBytes) {

        this.maxPendingBytes = maxPendingBytes;
        transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(maxPendingBytes);

    }


    // ---------------------------------------------------------- Public Methods


    /**
     * @return <code>true</code> if this listener has been paused, otherwise
     *  <code>false</code>
     */
    public boolean isPaused() {
        return paused;
    }


    /**
     * @return <code>true</code> if the listener has been started, otherwise
     *  <code>false</code>.
     */
    public boolean isStarted() {

        return (!transport.isStopped());
        
    }


    /**
     * <p>
     * Starts the listener.
     * </p>
     *
     * @throws IOException if an error occurs when attempting to start the
     *  listener.
     */
    public synchronized void start() throws IOException {

        if (!transport.isStopped()) {
            return;
        }


        if (filterChain == null) {
            throw new IllegalStateException("No FilterChain available."); // i18n
        }

        transport.setProcessor(filterChain);
        transport.bind(host, port);
        transport.start();

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                       "Started listener bound to [{0}]",
                       (host + ':' + port));
        }


    }


    /**
     * <p>
     * Stops the listener.
     * </p>
     *
     * @throws IOException if an error occurs when attempting to stop
     *  the listener.
     */
    public synchronized void stop() throws IOException {

        if (transport.isStopped()) {
            return;
        }

        transport.stop();

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                       "Stopped listener bound to [{0}]",
                       (host + ':' + port));
        }

    }


    /**
     * <p>
     * Pauses the listener.
     * </p>
     *
     * @throws IOException if an error occurs when attempting to pause the
     *  listener.
     */
    public synchronized void pause() throws IOException {

        if (transport.isStopped() || transport.isPaused()) {
            return;
        }

        transport.pause();
        paused = true;
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                       "Paused listener bound to [{0}]",
                       (host + ':' + port));
        }

    }


    /**
     * <p>
     * Resumes a paused listener.
     * </p>
     *
     * @throws IOException if an error occurs when attempting to resume the
     *  listener.
     */
    public synchronized void resume() throws IOException {

        if (transport.isStopped() || !transport.isPaused()) {
            return;
        }
        transport.resume();
        paused = false;
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                       "Resumed listener bound to [{0}]",
                       (host + ':' + port));
        }

    }

    /**
     * @return a value containing the name, host, port, and secure status of
     *  this listener.
     */
    @Override
    public String toString() {
        return "GrizzlyListener{" +
                "name='" + name + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", secure=" + secure +
                '}';
    }



    public JmxObject createManagementObject() {
        return new com.sun.grizzly.http.server.jmx.GrizzlyListener(this);
    }


    public WebServerFilter getWebServerFilter() {

        if (webServerFilter == null) {
            for (Filter f : filterChain) {
                if (f instanceof WebServerFilter) {
                    webServerFilter = (WebServerFilter) f;
                    break;
                }
            }
        }
        return webServerFilter;

    }


    public HttpCodecFilter getHttpCodecFilter() {

        if (httpCodecFilter == null) {
            for (Filter f : filterChain) {
                if (f instanceof HttpCodecFilter) {
                    httpCodecFilter = (HttpCodecFilter) f;
                    break;
                }
            }
        }
        return httpCodecFilter;
        
    }


    // --------------------------------------------------------- Private Methods


    private static void validateArg(final String name, final String value) {

        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException("Argument "
                                                  + name
                                                  + " cannot be "
                                                  + ((value == null)
                                                          ? "null"
                                                          : "have a zero length")); // I18n
        }

    }    
}
