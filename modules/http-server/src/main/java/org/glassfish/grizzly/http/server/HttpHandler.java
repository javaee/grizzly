/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.util.DispatcherHelper;
import org.glassfish.grizzly.http.server.util.HtmlHelper;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.RequestURIRef;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Base class to use when Request/Response/InputStream/OutputStream
 * are needed to implement a customized HTTP container/extension to the
 * HTTP module.
 *
 * The {@link HttpHandler} provides developers
 * with a simple and consistent mechanism for extending the functionality of the
 * HTTP WebServer and for bridging existing HTTP based technology like
 * JRuby-on-Rail, Servlet, Bayeux Protocol or any HTTP based protocol.
 *
 * @author Jeanfrancois Arcand
 */
public abstract class HttpHandler {
    
    private final static Logger LOGGER = Grizzly.logger(HttpHandler.class);
    
    private final static RequestExecutorProvider DEFAULT_REQUEST_EXECUTOR_PROVIDER =
            new RequestExecutorProvider.WorkerThreadProvider();
    
    /**
     * Allow request that uses encoded slash.
     */
    private boolean allowEncodedSlash = false;

    /**
     * Is the URL decoded
     */
    private boolean decodeURL = false;

    /**
     * Request URI encoding
     */
    private Charset requestURIEncoding;

    /**
     * Are custom status messages (reason phrases) allowed?
     */
    private boolean allowCustomStatusMessage = true;

    /**
     * HttpHandler name
     */
    private final String name;
    
    /**
     * Create <tt>HttpHandler</tt>.
     */
    public HttpHandler() {
        this(null);
    }

    /**
     * Create <tt>HttpHandler</tt> with the specific name.
     * @param name
     */
    public HttpHandler(String name) {
        this.name = name;
    }

    /**
     * Get the <tt>HttpHandler</tt> name.
     * @return the <tt>HttpHandler</tt> name.
     */
    public String getName() {
        return name;
    }

    /**
     * Pre-processes <tt>HttpHandler</tt> {@link Request} and {@link Response},
     * checks the HTTP acknowledgment and decodes URL if required. Then passes
     * control to {@link #service(Request, Response)}.
     *
     * @param request the {@link Request}
     * @param response the {@link Response}
     *
     * @return <tt>true</tt> if the {@link Request} has already been processed in
     *         the current {@link Thread}, or <tt>false</tt> otherwise
     * @throws Exception if an error occurs serving a static resource or
     *  from the invocation of {@link #service(Request, Response)}
     */
    boolean doHandle(final Request request, final Response response) throws Exception {
        request.setRequestExecutorProvider(getRequestExecutorProvider());
        request.setSessionCookieName(getSessionCookieName());
        request.setSessionManager(getSessionManager(request));
        response.setErrorPageGenerator(getErrorPageGenerator(request));

        if (request.requiresAcknowledgement()) {
            if (!sendAcknowledgment(request, response)) {
                return true;
            }
        }

        try {
            final HttpRequestPacket httpRequestPacket = request.getRequest();
            final RequestURIRef requestURIRef = httpRequestPacket.getRequestURIRef();
            requestURIRef.setDefaultURIEncoding(requestURIEncoding);

            if (decodeURL) {
                // URI decoding
                try {
                    requestURIRef.getDecodedRequestURIBC(allowEncodedSlash);
                } catch (CharConversionException e) {
                    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                    response.setDetailMessage("Invalid URI: " + e.getMessage());
                    return true;
                }
            }

            response.getResponse().setAllowCustomReasonPhrase(
                    allowCustomStatusMessage);
            
            // Parse request URL and if there is an HTTP session parameter -
            // extract it from the request URL and store for future use
            request.parseSessionId();
            
            return runService(request, response);
        } catch (Exception t) {
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_HTTP_SERVER_HTTPHANDLER_SERVICE_ERROR(), t);
            HtmlHelper.setErrorAndSendErrorPage(request, response,
                    response.getErrorPageGenerator(),
                    500, HttpStatus.INTERNAL_SERVER_ERROR_500.getReasonPhrase(),
                    HttpStatus.INTERNAL_SERVER_ERROR_500.getReasonPhrase(), t);
        }
        
        return true;
    }

    private boolean runService(final Request request, final Response response)
            throws Exception {
        
        final Executor threadPool = getRequestExecutorProvider().getExecutor(request);
        final HttpServerFilter httpServerFilter = request.getServerFilter();
        final Connection connection = request.getContext().getConnection();
        
        if (threadPool == null) {
            final SuspendStatus suspendStatus = response.initSuspendStatus();
            
            HttpServerProbeNotifier.notifyBeforeService(
                    httpServerFilter, connection, request, HttpHandler.this);
            
            service(request, response);
            return !suspendStatus.getAndInvalidate();
        } else {
            final FilterChainContext ctx = request.getContext();
            ctx.suspend();
            
            threadPool.execute(new Runnable() {

                @Override
                public void run() {
                    final SuspendStatus suspendStatus = response.initSuspendStatus();

                    boolean wasSuspended;
                    try {
                        HttpServerProbeNotifier.notifyBeforeService(
                                httpServerFilter, connection, request,
                                HttpHandler.this);
                        
                        service(request, response);
                        wasSuspended = suspendStatus.getAndInvalidate();
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, "service exception", e);
                        if (!response.isCommitted()) {
                            response.reset();
                            try {
                                HtmlHelper.setErrorAndSendErrorPage(
                                        request, response,
                                        response.getErrorPageGenerator(),
                                        500, HttpStatus.INTERNAL_SERVER_ERROR_500.getReasonPhrase(),
                                        HttpStatus.INTERNAL_SERVER_ERROR_500.getReasonPhrase(),
                                        e);
                            } catch (IOException ignored) {
                            }
                        }
                        wasSuspended = false;
                    }
                    
                    if (!wasSuspended) {
                        ctx.resume();
                    }
                }
            });
            
            return false;
        }
    }
    
    
    /**
     * This method should contain the logic for any HTTP extension to the
     * Grizzly HTTP web server.
     * @param request The {@link Request}
     * @param response The {@link Response}
     */
    public abstract void service(Request request, Response response) throws Exception;


    /**
     * Called when the {@link HttpHandler}'s
     * container is started by invoking {@link HttpServer#start}.
     *
     * By default, it does nothing.
     */
    public void start() {
    }


    /**
     * Invoked when the {@link HttpServer} and may be overridden by custom
     * implementations to perform implementation specific resource reclaimation
     * tasks.
     *
     * By default, this method does nothing.
     */
    public void destroy() {
    }

    /**
     * Returns <code>true</code> if custom status messages (reason phrases)
     * are allowed for this response, or <code>false</tt> otherwise.
     *
     * @return <code>true</code> if custom status messages (reason phrases)
     * are allowed for this response, or <code>false</tt> otherwise.
     */
    public boolean isAllowCustomStatusMessage() {
        return allowCustomStatusMessage;
    }

    /**
     * Sets if the custom status messages (reason phrases) are allowed for
     * this response.
     *
     * @param allowCustomStatusMessage <code>true</code> if custom status
     * messages (reason phrases) are allowed for this response,
     * or <code>false</tt> otherwise.
     */
    public void setAllowCustomStatusMessage(boolean allowCustomStatusMessage) {
        this.allowCustomStatusMessage = allowCustomStatusMessage;
    }

    /**
     * Is HTTP URL request allowed to contains encoded slash.
     * @return Is HTTP URL request allowed to contains encoded slash.
     */
    public boolean isAllowEncodedSlash() {
        return allowEncodedSlash;
    }

    /**
     * When true, URL that contains encoded slash will be allowed. When false,
     * the URL will be rejected and considered as an invalid one.
     * @param allowEncodedSlash true
     */
    public void setAllowEncodedSlash(boolean allowEncodedSlash) {
        this.allowEncodedSlash = allowEncodedSlash;
    }

    /**
     * Get the request URI encoding used by this <tt>HttpHandler</tt>.
     * @return the request URI encoding used by this <tt>HttpHandler</tt>.
     */
    public Charset getRequestURIEncoding() {
        return requestURIEncoding;
    }

    /**
     * Set the request URI encoding used by this <tt>HttpHandler</tt>.
     * @param requestURIEncoding the request URI encoding used by this <tt>HttpHandler</tt>.
     */
    public void setRequestURIEncoding(final Charset requestURIEncoding) {
        this.requestURIEncoding = requestURIEncoding;
    }

    /**
     * Set the request URI encoding used by this <tt>HttpHandler</tt>.
     * @param requestURIEncoding the request URI encoding used by this <tt>HttpHandler</tt>.
     */
    public void setRequestURIEncoding(final String requestURIEncoding) {
        this.requestURIEncoding = Charsets.lookupCharset(requestURIEncoding);
    }

    /**
     * @return the {@link RequestExecutorProvider} responsible for executing
     * user's code in {@link HttpHandler#service(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response)}
     * and notifying {@link ReadHandler}, {@link WriteHandler} registered by the user.
     */
    public RequestExecutorProvider getRequestExecutorProvider() {
        return DEFAULT_REQUEST_EXECUTOR_PROVIDER;
    }
    
    /**
     * Returns the {@link ErrorPageGenerator}, that might be used
     * (if an error occurs) during {@link Request} processing.
     * 
     * @param request {@link Request}
     * 
     * @return the {@link ErrorPageGenerator}, that might be used
     * (if an error occurs) during {@link Request} processing
     */
    protected ErrorPageGenerator getErrorPageGenerator(final Request request) {
        return request.getHttpFilter().getConfiguration().getDefaultErrorPageGenerator();
    }
    
    /**
     * @return session cookie name, if not set default JSESSIONID name will be used
     */
    protected String getSessionCookieName() {
        return null;
    }

    /**
     * @param request {@link Request}
     * 
     * @return the {@link SessionManager} to be used. <tt>null</tt> value implies {@link DefaultSessionManager}
     */
    protected SessionManager getSessionManager(final Request request) {
        return request.getHttpFilter().getConfiguration().getSessionManager();
    }
    
    /**
     * The default implementation will acknowledge an <code>Expect: 100-Continue</code>
     * with a response line with the status 100 followed by the final response
     * to this request.
     *
     * @param request the {@link Request}.
     * @param response the {@link Response}.
     *
     * @return <code>true</code> if request processing should continue after
     *  acknowledgment of the expectation, otherwise return <code>false</code>.
     *
     * @throws IOException if an error occurs sending the acknowledgment.
     */
    protected boolean sendAcknowledgment(final Request request,
            final Response response)
            throws IOException {

        if ("100-continue".equalsIgnoreCase(request.getHeader(Header.Expect))) {
            response.setStatus(HttpStatus.CONINTUE_100);
            response.sendAcknowledgement();
            return true;
        } else {
            response.setStatus(HttpStatus.EXPECTATION_FAILED_417);
            return false;
        }
    }

    /**
     * Should this class decode the URL
     */
    protected void setDecodeUrl(boolean decodeURL){
        this.decodeURL = decodeURL;
    }

    /**
     * Utility method to update {@link Request} path values.
     * 
     * @param request
     * @param mappingData
     */
    protected static void updatePaths(final Request request,
            final MappingData mappingData) {
        request.setContextPath(mappingData.contextPath.toString());
        request.setPathInfo(mappingData.pathInfo.toString());
        request.setHttpHandlerPath(mappingData.wrapperPath.toString());
    }

    protected void setDispatcherHelper(final DispatcherHelper dispatcherHelper) {
    }
}
