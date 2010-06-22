/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.http.server.adapter;


import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;


import java.util.logging.Level;

/**
 * Base class to use when GrizzlyRequest/Response/InputStream/OutputStream
 * are needed to implement a customized HTTP container/extendion to the
 * http module. The {@link ServletAdapter} demonstrate and easy and powerfull
 * way of how to extend this class.
 *
 * The {@link com.sun.grizzly.http.server.apapter.GrizzlyAdapter} provides developpers with a simple and
 * consistent mechanism for extending the functionality of the HTTP WebServer and for bridging existing
 * http based technology like JRuby-on-Rail, Servlet, Bayeux Protocol or any
 * http based protocol.
 *
 * @author Jeanfrancois Arcand
 */
abstract public class GrizzlyAdapter extends StaticResourcesAdapter {

    protected static final int ADAPTER_NOTES = 31;
    protected static final boolean ALLOW_BACKSLASH = false;

    protected boolean chunkingDisabled = false;


    public GrizzlyAdapter() {
        super();
        commitErrorResponse = false;
    }


    /**
     * Create a new instance which will look for static pages located
     * under <tt>publicDirectory</tt> folder.
     * @param publicDirectory the folder where the static resource are located.
     */
    public GrizzlyAdapter(String publicDirectory) {
        super(publicDirectory);
        commitErrorResponse = false;
    }


    /**
     * <tt>true</tt> if static resource handling should be handled
     * by this class.
     */
    private boolean handleStaticResources = false;


    /**
     * Allow request that uses encoded slash.
     */
    private boolean allowEncodedSlash = false;


    /**
     * Is the URL decoded
     */
    private boolean decodeURL = true;


    /**
     * Wrap a {@link com.sun.grizzly.tcp.Request} and {@link com.sun.grizzly.tcp.Response} with associated high level
     * classes like {@link com.sun.grizzly.tcp.http11.GrizzlyRequest} and {@link com.sun.grizzly.tcp.http11.GrizzlyResponse}. The later
     * objects offer more high level API like {@link com.sun.grizzly.tcp.http11.GrizzlyInputStream},
     * {@link GrizzlyRead} etc.
     * @param req the {@link com.sun.grizzly.tcp.Request}
     * @param res the {@link com.sun.grizzly.tcp.Response}
     * @throws Exception
     */
    //@Override
    /*
    final public void service(HttpRequestPacket req, HttpResponsePacket res) throws Exception {

        // We need to set this value for every request as they are shared
        // amongs several GrizzlyAdapter
        //req.getURLDecoder().setAllowEncodedSlash(allowEncodedSlash);
        if (isHandleStaticResources()) {
            super.service(req, res);
            if (res.getStatus() == 404) {
                res.setStatus(200);
                res.setReasonPhrase("OK");
            } else {
                return;
            }
        }

        GrizzlyRequest request = (GrizzlyRequest) req.getNote(ADAPTER_NOTES);
        GrizzlyResponse response = (GrizzlyResponse) res.getNote(ADAPTER_NOTES);

        if (request == null) {
            // Create objects
            request = new GrizzlyRequest();
            request.setRequest(req);
            response = new GrizzlyResponse(chunkingDisabled, false);
            response.setResponse(res);

            // Link objects
            request.setResponse(response);
            response.setRequest(request);

            // Set as notes
            req.setNote(ADAPTER_NOTES, request);
            res.setNote(ADAPTER_NOTES, response);
        }

        try {
            if (decodeURL){
                // URI decoding
                MessageBytes decodedURI = req.decodedURI();
                decodedURI.duplicate(req.requestURI());
                try {
                    HttpRequestURIDecoder.decode(decodedURI, req.getURLDecoder());
                } catch (IOException ioe) {
                    res.setStatus(400);
                    res.setMessage("Invalid URI: " + ioe.getMessage());
                    return;
                }
            }
            request.parseSessionId();
            service(request,response);
        } catch (Throwable t) {
            logger.log(Level.SEVERE,"service exception",t);
            res.setStatus(500);
            res.setMessage("Internal Error");
            return;
        }
    }
    */


    /**
     * Once the {@link #service} method has been execyuted, the container will
     * call this method to allow any extension to clean up there associated
     * {@link com.sun.grizzly.tcp.http11.GrizzlyRequest} and {@link com.sun.grizzly.tcp.http11.GrizzlyResponse}.
     * @param request The  {@link com.sun.grizzly.tcp.http11.GrizzlyRequest}
     * @param response The  {@link com.sun.grizzly.tcp.http11.GrizzlyResponse}
     */
    //public void afterService(GrizzlyRequest request,
    //        GrizzlyResponse response) throws Exception{
    //}


    /**
     * Clean up the {@link com.sun.grizzly.tcp.Request} and {@link com.sun.grizzly.tcp.Response} object, and commit the
     * response, and then invoke the {@link #afterService} method to allow extension
     * of this class to clean their own objects.
     * @param req the {@link com.sun.grizzly.tcp.Request}
     * @param res the {@link com.sun.grizzly.tcp.Response}
     * @throws Exception
     */
    @Override
    final public void afterService(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
        //GrizzlyRequest request = (GrizzlyRequest) req.getNote(ADAPTER_NOTES);
        //GrizzlyResponse response = (GrizzlyResponse) res.getNote(ADAPTER_NOTES);
        try{
            if (request != null && response != null) {
                afterService(request,response);
            }
        } catch (Exception ex){
            logger.log(Level.SEVERE,"afterService", ex);
            throw ex;
        }
        try {
            if (response != null){
                response.finishResponse();
            }
            super.afterService(request, response);
        } catch (Throwable t) {
            logger.log(Level.SEVERE,"afterService exception",t);
        } finally {
            // Recycle the wrapper request and response
            if (request != null){
                request.recycle();
            }

            if (response != null){
                response.recycle();
            }
        }
    }


    /**
     * Called when the {@link com.sun.grizzly.http.server.apapter.GrizzlyAdapter}'s container is started by invoking
     * {@link GrizzlyWebServer#start} or when {@linl SelectorThread.start}. By default,
     * it does nothing.
     */
    public void start() {}


    /**
     * Invoked when the {@link GrizzlyWebServer} or {@link SelectorThread}
     * is stopped or removed. By default, this method does nothing. Just override
     * the method if you need to clean some resource.
     */
    public void destroy(){}


    /**
     * Return true if this class should handle static resources.
     * @return true if this class should handle static resources.
     */
    public boolean isHandleStaticResources() {
        return handleStaticResources;
    }


    /**
     * Enable static resource handling. Default is false.
     * @param handleStaticResources
     */
    public void setHandleStaticResources(boolean handleStaticResources) {
        this.handleStaticResources = handleStaticResources;
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
     * Should this class decode the URL
     */
    protected void setDecodeUrl(boolean decodeURL){
        this.decodeURL = decodeURL;
    }
}