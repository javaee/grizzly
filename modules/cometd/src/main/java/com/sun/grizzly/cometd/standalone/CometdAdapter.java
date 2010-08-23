/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.cometd.standalone;

import com.sun.grizzly.Controller;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.cometd.BayeuxParser;
import com.sun.grizzly.cometd.CometdRequest;
import com.sun.grizzly.cometd.CometdResponse;
import com.sun.grizzly.cometd.EventRouter;
import com.sun.grizzly.cometd.EventRouterImpl;
import com.sun.grizzly.cometd.PublishInterceptor;
import java.io.InputStream;
import java.io.IOException;

import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.util.logging.Level;

/**
 * Standalone Cometd implementation. This class is used when Cometd is enabled
 * from the Grizzly standalone WebServer. To enable it, just add:
 *
 * -Dcom.sun.grizzly.adapterClass=com.sun.grizzly.cometd.standalone.CometdAdapter
 *
 * @author Jeanfrancois Arcand
 */
public class CometdAdapter extends GrizzlyAdapter {

    /**
     * All request to that context-path will be considered as cometd enabled.
     */
    private String contextPath = "/cometd/cometd";
    /**
     * The Bayeux {@link CometHandler} implementation.
     */
    private final BayeuxParser bayeuxParser ;
    /**
     * The EventRouter used to route JSON message.
     */
    private final EventRouter eventRouter;
    public static final String COMETD_REQUEST = "request";
    public static final String COMETD_RESPONSE = "response";

    public CometdAdapter() {
        this(null);
    }

    public CometdAdapter(PublishInterceptor publishInterceptor) {
        super();
        bayeuxParser = new BayeuxParser(publishInterceptor);
        eventRouter  = new EventRouterImpl(bayeuxParser);
        setHandleStaticResources(true);
    }

    /**
     * Route the request to the cometd implementation. If the request point to
     * a static file, delegate the call to the Grizzly WebServer implementation.
     */
    @Override
    public void service(GrizzlyRequest request, GrizzlyResponse response) {
        try{
            CometdRequest cometdReq = (CometdRequest) request.getNote(COMETD_REQUEST);
            CometdResponse cometdRes = (CometdResponse) request.getNote(COMETD_RESPONSE);

            if (cometdReq == null) {
                cometdReq = new CometdRequest<GrizzlyRequest>(request) {

                    public String[] getParameterValues(String s) {
                        return request.getParameterValues(s);
                    }

                    public int getRemotePort() {
                        return request.getRemotePort();
                    }

                    public String getCharacterEncoding() {
                        return request.getCharacterEncoding();
                    }

                    public int getContentLength() {
                        return request.getContentLength();
                    }

                    public String getContentType() {
                        return request.getContentType();
                    }

                    public InputStream getInputStream() throws IOException {
                        return request.getInputStream();
                    }
                };
                request.setNote(COMETD_REQUEST, cometdReq);
            } else {
                cometdReq.setRequest(request);
            }

            if (cometdRes == null) {
                cometdRes = new CometdResponse<GrizzlyResponse>(response) {

                    public void write(String s) throws IOException {
                        response.getWriter().write(s);
                    }

                    public void flush() throws IOException {
                        response.getWriter().flush();
                    }

                    public void setContentType(String s) {
                        response.setContentType(s);
                    }
                };
                request.setNote(COMETD_RESPONSE, cometdRes);
            } else {
                cometdRes.setResponse(response);
            }

            eventRouter.route(cometdReq, cometdRes);
        } catch (IOException ex) {
            response.setStatus(404);
            Controller.logger().log(Level.FINE, "CometdAdapter exception", ex);
        }
    }

    /**
     * Return the comet context path used by this Adapter. By default, it return
     * "/cometd"
     */
    public String getContextPath() {
        return contextPath;
    }

    /**
     * Set the comet context path.
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }
}
