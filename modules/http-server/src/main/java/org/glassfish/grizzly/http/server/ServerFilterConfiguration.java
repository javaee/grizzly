/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Grizzly;

/**
 * {@link HttpServerFilter} configuration.
 *
 * @author Alexey Stashok
 */
public class ServerFilterConfiguration {

    public static final int MAX_REQUEST_PARAMETERS = 10000;
    public static final String USE_SEND_FILE = "org.glassfish.grizzly.http.USE_SEND_FILE";

    private String httpServerName;
    private String httpServerVersion;
    private boolean sendFileEnabled;
    
    private boolean traceEnabled;
    private boolean passTraceRequest;
    private int maxRequestParameters = MAX_REQUEST_PARAMETERS;
    
    /**
     * The HTTP request scheme, which if non-null overrides default one picked
     * up by framework during runtime.
     */
    private String scheme;

    public ServerFilterConfiguration() {
        this("Grizzly", Grizzly.getDotedVersion());
    }

    public ServerFilterConfiguration(final String serverName, final String serverVersion) {
        this.httpServerName = serverName;
        this.httpServerVersion = serverVersion;
        configureSendFileSupport();
    }

    public ServerFilterConfiguration(ServerFilterConfiguration configuration) {
        this.httpServerName = configuration.httpServerName;
        this.httpServerVersion = configuration.httpServerVersion;
        this.sendFileEnabled = configuration.sendFileEnabled;
        this.scheme = configuration.scheme;
        this.traceEnabled = configuration.traceEnabled;
        this.passTraceRequest = configuration.passTraceRequest;
        this.maxRequestParameters = configuration.maxRequestParameters;
    }
    
    /**
     * @return the server name used for headers and default error pages.
     */
    public String getHttpServerName() {
        return httpServerName;

    }

    /**
     * Sets the server name used for HTTP response headers and default generated error pages.  If not value is
     * explicitly set, this value defaults to <code>Grizzly</code>.
     *
     * @param httpServerName server name
     */
    public void setHttpServerName(String httpServerName) {
        this.httpServerName = httpServerName;
    }

    /**
     * @return the version of this server used for headers and default error pages.
     */
    public String getHttpServerVersion() {
        return httpServerVersion;
    }

    /**
     * Sets the version of the server info sent in HTTP response headers and the default generated error pages.  If not
     * value is explicitly set, this value defaults to the current version of the Grizzly runtime.
     *
     * @param httpServerVersion server version
     */
    public void setHttpServerVersion(String httpServerVersion) {
        this.httpServerVersion = httpServerVersion;
    }

    /**
     * <p>
     * Returns <code>true</code> if File resources may be be sent using
     * {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     * </p>
     * <p/>
     * <p>
     * By default, this property will be true, except in the following cases:
     * </p>
     * <p/>
     * <ul>
     * <li>JVM OS is HP-UX</li>
     * <li>JVM OS is Linux, and the Oracle JVM in use is 1.6.0_17 or older</li>
     * </ul>
     * <p/>
     * <p>
     * This logic can be overridden by explicitly setting the property via
     * {@link #setSendFileEnabled(boolean)} or by specifying the system property
     * {@value #USE_SEND_FILE} with a value of <code>true</code>
     * </p>
     * <p/>
     * <p>
     * Finally, if the connection between endpoints is secure, send file functionality
     * will be disabled regardless of configuration.
     * </p>
     *
     * @return <code>true</code> if resources will be sent using
     *         {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     * @since 2.2
     */
    public boolean isSendFileEnabled() {
        return sendFileEnabled;
    }

    /**
     * Configure whether or sendfile support will enabled which allows sending
     * {@link java.io.File} resources via {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}.
     * If disabled, the more traditional byte[] copy will be used to send content.
     *
     * @param sendFileEnabled <code>true</code> to enable {@link java.nio.channels.FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
     *                        support.
     * @since 2.2
     */
    public void setSendFileEnabled(boolean sendFileEnabled) {
        this.sendFileEnabled = sendFileEnabled;
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
        return scheme;
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
        this.scheme = scheme;
    }

    /**
     * @return <tt>true</tt> if the <tt>TRACE</tt> request will be passed
     *  to the registered {@link HttpHandler}s, otherwise <tt>false</tt> if the
     *  <tt>TRACE</tt> request will be handled by Grizzly.
     *
     * @since 2.2.7
     */
    public boolean isPassTraceRequest() {
        return passTraceRequest;
    }

    /**
     * If <tt>passTraceRequest</tt> is <tt>true</tt>, the <tt>TRACE</tt> request
     * will be passed to the registered {@link HttpHandler}s. Otherwise,
     * <tt>TRACE</tt> will be handled by Grizzly.
     *
     * By default, <tt>TRACE</tt> requests will be handled by Grizzly.
     *
     * @param passTraceRequest boolean to configure if trace requests will
     *                         be handled by Grizzly or by a configured
     *                         {@link HttpHandler}.
     *
     * @since 2.2.7
     */
    public void setPassTraceRequest(boolean passTraceRequest) {
        this.passTraceRequest = passTraceRequest;
    }

    /**
     *
     * @return <tt>true</tt> if a proper response to HTTP <tt>TRACE</tt> is to
     *  be generated, or <tt>false</tt> if a 405 is to be returned instead.
     *
     * @since 2.2.7
     */
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    /**
     * If <tt>enabled</tt> is <tt>true</tt> the <tt>TRACE</tt> method will be
     * respected and a proper response will be generated.  Otherwise, the
     * method will be considered as not allowed and an HTTP 405 will be returned.
     *
     * This method only comes into effect when <code>setPassTraceRequest(false)</code>
     * has been called.
     *
     * @param enabled boolean to configure how grizzly handles TRACE requests
     *
     * @since 2.2.7
     */
    public void setTraceEnabled(final boolean enabled) {
        traceEnabled = enabled;
    }

    /**
     * Returns the maximum number of parameters allowed per request.  If the
     * value is less than zero, then there will be no limit on parameters.  By
     * default, the limit imposed is {@value #MAX_REQUEST_PARAMETERS}.
     *
     * @return the maximum number of parameters, or <tt>-1</tt> if there is no
     *  imposed limit.
     *
     * @since 2.2.8
     */
    public int getMaxRequestParameters() {
        return maxRequestParameters;
    }

    /**
     * Sets the maximum number of parameters allowed for a request.
     *
     * @param maxRequestParameters the maximum number of parameters.
     *
     * @since 2.2.8
     */
    public void setMaxRequestParameters(int maxRequestParameters) {
        if (maxRequestParameters < 0) {
            this.maxRequestParameters = -1;
        } else {
            this.maxRequestParameters = maxRequestParameters;
        }
    }


    // --------------------------------------------------------- Private Methods


    private void configureSendFileSupport() {

        if ((System.getProperty("os.name").equalsIgnoreCase("linux")
                && !linuxSendFileSupported())
                || System.getProperty("os.name").equalsIgnoreCase("HP-UX")) {
            sendFileEnabled = false;
        }

        // overrides the config from the previous block
        if (System.getProperty(USE_SEND_FILE) != null) {
            sendFileEnabled = Boolean.valueOf(System.getProperty(USE_SEND_FILE));
        }

    }


    private static boolean linuxSendFileSupported(final String jdkVersion) {
        if (jdkVersion.startsWith("1.6")) {
            int idx = jdkVersion.indexOf('_');
            if (idx == -1) {
                return false;
            }
            StringBuilder sb = new StringBuilder(3);
            final String substr = jdkVersion.substring(idx + 1);
            int len = Math.min(substr.length(), 3);
            for (int i = 0; i < len; i++) {
                final char c = substr.charAt(i);
                if (Character.isDigit(c)) {
                    sb.append(c);
                    continue;
                }
                break;
            }
            if (sb.length() == 0) {
                return false;
            }
            final int patchRev = Integer.parseInt(sb.toString());
            return (patchRev >= 18);
        } else {
            return jdkVersion.startsWith("1.7") || jdkVersion.startsWith("1.8");
        }
    }

    private static boolean linuxSendFileSupported() {
        return linuxSendFileSupported(System.getProperty("java.version"));
    }

}
