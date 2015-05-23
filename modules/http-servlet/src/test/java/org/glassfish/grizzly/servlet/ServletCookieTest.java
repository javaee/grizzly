/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.servlet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static junit.framework.Assert.assertEquals;
import org.glassfish.grizzly.http.HttpPacket;

/**
 * Verify that request processing isn't broken by malformed Cookies
 * 
 * @author <a href="mailto:marc.arens@open-xchange.com">Marc Arens</a>
 */
public class ServletCookieTest extends HttpServerAbstractTest {

    private static final int PORT = 12345;
    private static final String CONTEXT = "/test";
    private static final String SERVLETMAPPING = "/servlet";
    private static final String FIRST_COOKIE_NAME = "firstCookie";
    private static final String FIRST_COOKIE_VALUE = "its_a_me-firstCookie";
    private static final String SECOND_COOKIE_NAME = "secondCookie";
    private static final String SECOND_COOKIE_VALUE = "{\"a\": 1,\"Version\":2}";
    private static final String THIRD_COOKIE_NAME = "thirdCookie";
    private static final String THIRD_COOKIE_VALUE = "its_a_me-thirdCookie";
    
    /**
     * Assert basic cookie parsing 
     * @throws Exception 
     */
    public void testServletCookieParsing() throws Exception {
        
        try {
            startHttpServer(PORT);

            WebappContext ctx = new WebappContext("Test", CONTEXT);
            ServletRegistration servletRegistration = ctx.addServlet("intervalServlet", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    Cookie[] cookies = req.getCookies();
                    assertEquals(3, cookies.length);
                    Cookie currentCookie = cookies[0];
                    assertEquals(FIRST_COOKIE_NAME, currentCookie.getName());
                    assertEquals(FIRST_COOKIE_VALUE, currentCookie.getValue());
                    
                    currentCookie = cookies[1];
                    assertEquals(SECOND_COOKIE_NAME, currentCookie.getName());
                    /* The cookie isn't read completely but instead of throwing
                     * an Exception we discard the remainder and continue request
                     * processing while logging an error.
                     */
                    assertEquals("{\"a\": 1", currentCookie.getValue());
                    
                    currentCookie = cookies[2];
                    assertEquals(THIRD_COOKIE_NAME, currentCookie.getName());
                    assertEquals(THIRD_COOKIE_VALUE, currentCookie.getValue());
                }
            });

            servletRegistration.addMapping(SERVLETMAPPING);
            ctx.deploy(httpServer);

            //build and send request
            StringBuilder sb = new StringBuilder(256);
            sb.append(FIRST_COOKIE_NAME).append("=").append(FIRST_COOKIE_VALUE);
            sb.append(";");
            sb.append(SECOND_COOKIE_NAME).append("=").append(SECOND_COOKIE_VALUE);
            sb.append(";");
            sb.append(THIRD_COOKIE_NAME).append("=").append(THIRD_COOKIE_VALUE);
                    
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Cookie", sb.toString());
            HttpPacket request = ClientUtil.createRequest(CONTEXT + SERVLETMAPPING, PORT, headers);
            ClientUtil.sendRequest(request, 60, PORT);
        } finally {
            stopHttpServer();
        }
    }

}
