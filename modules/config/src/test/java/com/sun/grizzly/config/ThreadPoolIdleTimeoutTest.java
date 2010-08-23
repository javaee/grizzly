/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.config;

import java.io.IOException;
import java.net.URL;
//import javax.servlet.Servlet;
//import javax.servlet.ServletException;
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;

import org.testng.annotations.Test;

@Test
public class ThreadPoolIdleTimeoutTest extends GrizzlyServletTestBase {
//    private String host = "localhost";
//    private String contextPath = getContextPath();
//    private String servletPath = getServletPath();
//
//    @Override
//    protected String getConfigFile() {
//        return "grizzly-config-timeout-disabled.xml";
//    }
//
//    protected Servlet getServlet() {
//        return new HttpServlet() {
//            @Override
//            protected void service(final HttpServletRequest req, final HttpServletResponse resp)
//                throws ServletException, IOException {
//                doGet(req, resp);
//            }
//
//            @Override
//            protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
//                throws ServletException, IOException {
//                doGet(req, resp);
//            }
//
//            @Override
//            public void doGet(HttpServletRequest request, HttpServletResponse response)
//                throws ServletException, IOException {
//                try {
//                    Thread.sleep(10000);
//                    response.setContentType("text/plain");
//                    response.getWriter().println("Here's your content.");
//                    response.flushBuffer();
//                } catch (InterruptedException ie) {
//                    throw new ServletException(ie.getMessage(), ie);
//                }
//            }
//        };
//    }
//
//    @Test
//    public void noTimeout() throws IOException {
//        Utils.dumpOut("ThreadPoolIdleTimeoutTest.noTimeout");
//        timeoutCheck(38084);
//    }
//
//    @Test(expectedExceptions = {IOException.class})
//    public void timeout() throws IOException {
//        Utils.dumpOut("ThreadPoolIdleTimeoutTest.timeout");
//        timeoutCheck(38085);
//    }
//
//    private void timeoutCheck(final int port) throws IOException {
//        new URL("http://" + host + ":" + port + contextPath + servletPath).getContent();
//    }

}
