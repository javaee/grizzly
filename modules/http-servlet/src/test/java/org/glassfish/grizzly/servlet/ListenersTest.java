/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.servlet;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;

public class ListenersTest extends HttpServerAbstractTest {

    public static final int PORT = 18088;


    // ------------------------------------------------------------ Test Methods

    /**
     * Regression test for GRIZZLY-1218 and GRIZZLY-1220.
     */
    public void testRequestListener() throws Exception {
        newHttpServer(PORT);
        WebappContext ctx = new WebappContext("Test", "/contextPath");
        ctx.addListener(RequestListener.class.getName());
        addServlet(ctx, "TestServlet", "/servletPath/*");
        ctx.deploy(httpServer);
        httpServer.start();
        HttpURLConnection conn = getConnection("/contextPath/servletPath/pathInfo", PORT);
        conn.getResponseCode();
        assertTrue(RequestListener.destroyed);
        assertTrue(RequestListener.initialized);

    }


    // --------------------------------------------------------- Private Methods


    private ServletRegistration addServlet(final WebappContext ctx,
                                               final String name,
                                               final String alias) {
            final ServletRegistration reg = ctx.addServlet(name, new HttpServlet() {

                @Override
                protected void doGet(
                        HttpServletRequest req, HttpServletResponse resp)
                        throws IOException {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setHeader("Path-Info", req.getPathInfo());
                    resp.setHeader("Request-Was", req.getRequestURI());
                    resp.setHeader("Servlet-Name", getServletName());
                    resp.getWriter().write(alias);
                }
            });
            reg.addMapping(alias);

            return reg;
        }


    // ---------------------------------------------------------- Nested Classes


    public static final class RequestListener implements ServletRequestListener {

        static boolean destroyed;
        static boolean initialized;

        @Override
        public void requestDestroyed(ServletRequestEvent servletRequestEvent) {
            destroyed = true;
        }

        @Override
        public void requestInitialized(ServletRequestEvent servletRequestEvent) {
            initialized = true;
        }

    }

}
