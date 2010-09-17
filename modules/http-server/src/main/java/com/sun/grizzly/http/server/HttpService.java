/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.server.io.OutputBuffer;
import com.sun.grizzly.http.server.util.HtmlHelper;
import com.sun.grizzly.http.util.HttpStatus;

import java.io.CharConversionException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class to use when Request/Response/InputStream/OutputStream
 * are needed to implement a customized HTTP container/extension to the
 * HTTP module.
 *
 * The {@link HttpService} provides developers
 * with a simple and consistent mechanism for extending the functionality of the
 * HTTP WebServer and for bridging existing http based technology like
 * JRuby-on-Rail, Servlet, Bayeux Protocol or any HTTP based protocol.
 *
 * @author Jeanfrancois Arcand
 */
public abstract class HttpService {
    
    private final static Logger logger = Grizzly.logger(HttpService.class);
    
    protected final StaticResourcesHandler staticResourcesHandler =
            new StaticResourcesHandler();


    /**
     * Allow request that uses encoded slash.
     */
    private boolean allowEncodedSlash = false;


    /**
     * Is the URL decoded
     */
    private boolean decodeURL = true;
    
    /**
     * Create <tt>HttpService</tt>, which, by default, won't handle requests
     * to the static resources.
     */
    public HttpService() {
        this(null);
    }


    /**
     * Create a new instance which will look for static pages located
     * under the <tt>docRoot</tt>. If the <tt>docRoot</tt> is <tt>null</tt> -
     * static pages won't be served by this <tt>HttpService</tt>
     * 
     * @param docRoot the folder where the static resource are located.
     * If the <tt>docRoot</tt> is <tt>null</tt> - static pages won't be served
     * by this <tt>HttpService</tt>
     */
    public HttpService(String docRoot) {
        staticResourcesHandler.setDocRoot(docRoot);
    }

    /**
     * Handles static resources if this adapter is configured to do so, otherwise
     * invokes {@link #service(Request, Response)}.
     *
     * @param request the {@link Request}
     * @param response the {@link Response}
     *
     * @throws Exception if an error occurs serving a static resource or
     *  from the invocation of {@link #service(Request, Response)}
     */
    public final void doService(Request request, Response response) throws Exception {

        if (request.requiresAcknowledgement()) {
            if (!sendAcknowledgment(request, response)) {
                return;
            }
        }
        
        if (staticResourcesHandler.getDocRoot() != null &&
                staticResourcesHandler.handle(request, response)) {
            return;
        }

        try {
            if (decodeURL){
                // URI decoding
                final HttpRequestPacket httpRequestPacket = request.getRequest();
                try {
                    httpRequestPacket.getRequestURIRef().getDecodedRequestURIBC(allowEncodedSlash);
                } catch (CharConversionException e) {
                    response.setStatus(HttpStatus.NOT_FOUND_404);
                    response.setDetailMessage("Invalid URI: " + e.getMessage());
                    return;
                }
            }
            request.parseSessionId();
            service(request, response);
        } catch (Exception t) {
            logger.log(Level.SEVERE,"service exception", t);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setDetailMessage("Internal Error");
            throw t;
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
     * Called when the {@link HttpService}'s
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
     * Get {@link StaticResourcesHandler}, which handles requests to a static resources.
     * @return {@link StaticResourcesHandler}, which handles requests to a static resources.
     */
    public StaticResourcesHandler getStaticResourcesHandler() {
        return staticResourcesHandler;
    }

    /**
     * Is http url request allowed to contains encoded slash.
     * @return Is http url request allowed to contains encoded slash.
     */
    public boolean isAllowEncodedSlash() {
        return allowEncodedSlash;
    }

    /**
     * When true, url that contains encoded slash will be allowed. When false,
     * the url will be rejected and considered ans an invalid one.
     * @param allowEncodedSlash true
     */
    public void setAllowEncodedSlash(boolean allowEncodedSlash) {
        this.allowEncodedSlash = allowEncodedSlash;
    }

    /**
     * Return the directory from where files will be serviced, or <tt>null</tt>,
     * if static resources won't be served by this <tt>HttpService</tt>.
     * 
     * @return the directory from where file will be serviced, or <tt>null</tt>,
     * if static resources won't be served by this <tt>HttpService</tt>.
     */
    public File getDocRoot() {
        return staticResourcesHandler.getDocRoot();
    }

    /**
     * Set the directory from where files will be serviced, if passed value is
     * <tt>null</tt> - static resources won't be served by this <tt>HttpService</tt>.
     *
     * @param docRoot the directory from where files will be serviced, if passed value is
     * <tt>null</tt> - static resources won't be served by this <tt>HttpService</tt>.
     */
    public void setDocRoot(String docRoot) {
        staticResourcesHandler.setDocRoot(docRoot);
    }

    /**
     * Set the directory from where files will be serviced, if passed value is
     * <tt>null</tt> - static resources won't be served by this <tt>HttpService</tt>.
     *
     * @param docRoot the directory from where files will be serviced, if passed value is
     * <tt>null</tt> - static resources won't be served by this <tt>HttpService</tt>.
     */
    public void setDocRoot(File docRoot) {
        staticResourcesHandler.setDocRoot(docRoot);
    }

    /**
     * Customize the error page.
     * @param server the {@link HttpServer} associated with this adapter.
     * @param req The {@link Request} object
     * @param res The {@link Response} object
     * @throws Exception
     */
    protected void customizedErrorPage(HttpServer server,
                                       Request req,
                                       Response res)
            throws Exception {

        /**
         * With Grizzly, we just return a 404 with a simple error message.
         */
        res.setStatus(HttpStatus.NOT_FOUND_404);
        // TODO re-implement
        final ServerConfiguration c = server.getServerConfiguration();
        final String serverName = c.getHttpServerName() + '/' + c.getHttpServerVersion();
        ByteBuffer bb = HtmlHelper.getErrorPage("Not Found",
                                                "Resource identified by path '" + req.getRequestURI() + "', does not exist.",
                                                serverName);
        res.setContentLength(bb.limit());
        res.setContentType("text/html");
        OutputBuffer out = res.getOutputBuffer();
        out.processingChars();
        out.write(bb.array(), bb.position(), bb.remaining());
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
     *  acknowledgement of the expectation, otherwise return <code>false</code>.
     *
     * @throws IOException if an error occurs sending the acknowledgement.
     */
    protected boolean sendAcknowledgment(final Request request,
            final Response response)
            throws IOException {

        if ("100-Continue".equals(request.getHeader("Expect"))) {
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
}
