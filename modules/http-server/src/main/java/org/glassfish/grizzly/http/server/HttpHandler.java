/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.io.OutputBuffer;
import org.glassfish.grizzly.http.server.util.HtmlHelper;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.http.server.util.DispatcherHelper;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.http.util.RequestURIRef;
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
    
    /**
     * Allow request that uses encoded slash.
     */
    private boolean allowEncodedSlash = false;

    /**
     * Allow request that uses encoded backslash.
     */
    private boolean allowEncodedBackSlash = false;

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
     * @throws Exception if an error occurs serving a static resource or
     *  from the invocation of {@link #service(Request, Response)}
     */
    public final void doHandle(Request request, Response response) throws Exception {

        if (request.requiresAcknowledgement()) {
            if (!sendAcknowledgment(request, response)) {
                return;
            }
        }

        try {
            final HttpRequestPacket httpRequestPacket = request.getRequest();
            final RequestURIRef requestURIRef = httpRequestPacket.getRequestURIRef();
            requestURIRef.setDefaultURIEncoding(requestURIEncoding);

            if (decodeURL) {
                // URI decoding
                try {
                    requestURIRef.getDecodedRequestURIBC(allowEncodedSlash,
                            allowEncodedBackSlash);
                } catch (CharConversionException e) {
                    response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                    response.setDetailMessage("Invalid URI: " + e.getMessage());
                    return;
                }
            }

            response.getResponse().setAllowCustomReasonPhrase(
                    allowCustomStatusMessage);
            
            request.parseSessionId();
            service(request, response);
        } catch (Exception t) {
            LOGGER.log(Level.FINE, "service exception", t);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setDetailMessage("Internal Error");
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
     * @param allowEncodedSlash
     */
    public void setAllowEncodedSlash(boolean allowEncodedSlash) {
        this.allowEncodedSlash = allowEncodedSlash;
    }

    /**
     * Is HTTP URL request allowed to contains encoded backslash.
     * @return Is HTTP URL request allowed to contains encoded backslash.
     */
    public boolean isAllowEncodedBackSlash() {
        return allowEncodedSlash;
    }

    /**
     * When true, URL that contains encoded backslash will be allowed. When false,
     * the URL will be rejected and considered as an invalid one.
     * @param allowEncodedBackSlash
     */
    public void setAllowEncodedBackSlash(boolean allowEncodedBackSlash) {
        this.allowEncodedBackSlash = allowEncodedBackSlash;
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
     * Customize the error page.
     * @param req The {@link Request} object
     * @param res The {@link Response} object
     * @throws Exception
     */
    protected void customizedErrorPage(final Request req,
                                       final Response res)
            throws Exception {

        /**
         * With Grizzly, we just return a 404 with a simple error message.
         */
        res.setStatus(HttpStatus.NOT_FOUND_404);
        // TODO re-implement
        final ServerFilterConfiguration config = req.getServerFilter().getConfiguration();
        
        final String serverName = config.getHttpServerName() + '/' +
                config.getHttpServerVersion();
        
        final ByteBuffer bb = HtmlHelper.getErrorPage("Not Found",
                                                "Resource identified by path '" + req.getRequestURI() + "', does not exist.",
                                                serverName);
        res.setContentLength(bb.limit());
        res.setContentType("text/html");
        OutputBuffer out = res.getOutputBuffer();
        out.prepareCharacterEncoder();
        out.write(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
        out.close();
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
