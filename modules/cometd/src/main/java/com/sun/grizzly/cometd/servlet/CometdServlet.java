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

package com.sun.grizzly.cometd.servlet;

import com.sun.grizzly.cometd.BayeuxParser;
import com.sun.grizzly.cometd.CometdRequest;
import com.sun.grizzly.cometd.CometdResponse;
import com.sun.grizzly.cometd.EventRouter;
import com.sun.grizzly.cometd.EventRouterImpl;
import com.sun.grizzly.cometd.PublishInterceptor;
import java.io.InputStream;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Simple CometdChat that route Cometd Request to the EventRouter.
 *
 * @author Jeanfrancois Arcand
 * @author TAKAI, Naoto
 */
public class CometdServlet extends HttpServlet {

    /**
     * The Bayeux {@link CometHandler} implementation.
     */
    private final BayeuxParser bayeuxParser;
    /**
     * The EventRouter used to route JSON message.
     */
    private EventRouter eventRouter;

    public CometdServlet() {
        this(null);
    }

    public CometdServlet(PublishInterceptor publishInterceptor) {
        bayeuxParser = new BayeuxParser(publishInterceptor);
        eventRouter = new EventRouterImpl(bayeuxParser);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ; // Nothing
    }

    @Override
    public void doPost(HttpServletRequest hreq, HttpServletResponse hres)
            throws ServletException, IOException {

       CometdRequest cometdReq = new CometdRequest<HttpServletRequest>(hreq) {

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


        CometdResponse cometdRes = new CometdResponse<HttpServletResponse>(hres) {

            public void write(String s) throws IOException {
                response.getOutputStream().write(s.getBytes());
            }

            public void flush() throws IOException {
                // Do no flush for real with Servlet as the Bayexu client
                // is completely broken
                response.getOutputStream().flush();
            }

            public void setContentType(String s) {
                response.setContentType(s);
            }
        };
        eventRouter.route(cometdReq, cometdRes);
    }

    /**
     * return the current {@link EventRouter} implementation.
     * @return
     */
    public EventRouter getEventRouter() {
        return eventRouter;
    }
    
    /**
     * Set the {@link EventRouter}
     * @param eventRouter the {@link EventRouter}
     */
    public void setEventRouter(EventRouter eventRouter){
        this.eventRouter = eventRouter;
    }
    
}
