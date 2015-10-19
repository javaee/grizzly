/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.Cookies;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.server.util.Globals;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;
import static org.glassfish.grizzly.servlet.ClientUtil.*;

/**
 * {@link HttpSessionTest}
 * 
 * @author <a href="mailto:marc.arens@open-xchange.com">Marc Arens</a>
 */
public class HttpSessionTest extends HttpServerAbstractTest {

    private static final int PORT = 12345;
    private static final String CONTEXT = "/test";
    private static final String SERVLETMAPPING = "/servlet";
    private static final String JSESSIONID_COOKIE_NAME = "JSESSIONID";
    private static final int MAX_INACTIVE_INTERVAL_SEC = 5;

    /**
     * Want to set the MaxInactiveInterval of the HttpSession in seconds via a HttpServletRequest/HttpSession.
     * @throws Exception 
     */
    public void testMaxInactiveIntervalNotExpired() throws Exception {
        try {
            startHttpServer(PORT);
            
            WebappContext ctx = new WebappContext("Test", CONTEXT);
            ServletRegistration servletRegistration = ctx.addServlet("intervalServlet", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    HttpSession httpSession1 = req.getSession(true);
                    httpSession1.setMaxInactiveInterval(MAX_INACTIVE_INTERVAL_SEC);
                    assertEquals(MAX_INACTIVE_INTERVAL_SEC, httpSession1.getMaxInactiveInterval());
                    HttpSession httpSession2 = req.getSession(true);
                    assertEquals(httpSession1, httpSession2);
                }
            });
            
            servletRegistration.addMapping(SERVLETMAPPING);
            ctx.deploy(httpServer);
            
            //build and send request
            Map<String, String> headers = new HashMap<String, String>();
            String cookieValue0 = "975159770778015515.OX0";
            headers.put("Cookie", JSESSIONID_COOKIE_NAME+"=" + cookieValue0);
            HttpPacket request = createRequest(CONTEXT+SERVLETMAPPING, PORT, headers);

            //get response update JSESSIONID if needed
            System.out.println("Sending Request with SessionId: "+headers.get("Cookie"));
            HttpContent response = sendRequest(request, 60, PORT);
            String cookieValue1 = getJSessionCookies(response);
            assertNotNull(cookieValue1);
            assertNotSame("Cookies have to be different", cookieValue0, cookieValue1);
            System.out.println("---");
            Thread.sleep(MAX_INACTIVE_INTERVAL_SEC * 1000 / 2);
            headers.clear();
            headers.put("Cookie", JSESSIONID_COOKIE_NAME+"="+cookieValue1);
            request = createRequest(CONTEXT+SERVLETMAPPING, PORT, headers);
            response = sendRequest(request, 60, PORT);
            String cookieValue2 = getJSessionCookies(response);
            assertNull(cookieValue2);
            
        } finally {
            stopHttpServer();
        }
    }
    
    /**
     * Want to set the MaxInactiveInterval of the HttpSession in seconds via a HttpServletRequest/HttpSession.
     * @throws Exception 
     */
    public void testMaxInactiveIntervalExpired() throws Exception {
        try {
            newHttpServer(PORT);
            httpServer.getServerConfiguration().setSessionTimeoutSeconds(
                    MAX_INACTIVE_INTERVAL_SEC);
            httpServer.start();
            
            WebappContext ctx = new WebappContext("Test", CONTEXT);
            ServletRegistration servletRegistration = ctx.addServlet("intervalServlet", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    HttpSession httpSession1 = req.getSession(true);
                    assertEquals(MAX_INACTIVE_INTERVAL_SEC, httpSession1.getMaxInactiveInterval());
                    HttpSession httpSession2 = req.getSession(true);
                    assertEquals(httpSession1, httpSession2);
                }
            });
            
            servletRegistration.addMapping(SERVLETMAPPING);
            ctx.deploy(httpServer);
            
            //build and send request
            Map<String, String> headers = new HashMap<String, String>();
            String cookieValue0 = "975159770778015515.OX0";
            headers.put("Cookie", JSESSIONID_COOKIE_NAME+"=" + cookieValue0);
            HttpPacket request = createRequest(CONTEXT+SERVLETMAPPING, PORT, headers);

            //get response update JSESSIONID if needed
            System.out.println("Sending Request with SessionId: "+headers.get("Cookie"));
            HttpContent response = sendRequest(request, 60, PORT);
            String cookieValue1 = getJSessionCookies(response);
            assertNotNull(cookieValue1);
            assertNotSame("Cookies have to be different", cookieValue0, cookieValue1);
            System.out.println("---");
            Thread.sleep(MAX_INACTIVE_INTERVAL_SEC * 1000 * 2);
            headers.clear();
            headers.put("Cookie", JSESSIONID_COOKIE_NAME+"="+cookieValue1);
            request = createRequest(CONTEXT+SERVLETMAPPING, PORT, headers);
            response = sendRequest(request, 60, PORT);
            String cookieValue2 = getJSessionCookies(response);
            assertNotNull(cookieValue2);
            assertNotSame("Cookies have to be different", cookieValue1, cookieValue2);
            
        } finally {
            stopHttpServer();
        }
    }
    
    public void testChangeSessionId() throws Exception {
        startHttpServer(PORT);

        WebappContext ctx = new WebappContext("Test", CONTEXT);
        ctx.addListener(new HttpSessionIdListener() {

            @Override
            public void sessionIdChanged(HttpSessionEvent e, String oldId) {
                HttpSession session = e.getSession();
                String sessionId = session.getId();
                if (!oldId.equals(sessionId)) {
                    session.setAttribute("A", 2);
                }
            }
        });
        
        ServletRegistration servletRegistration = ctx.addServlet("test", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
                HttpSession session = req.getSession(false);
                if (session == null) {
                    req.getSession(true).setAttribute("A", 1);
                } else if (Integer.valueOf(1).equals(session.getAttribute("A"))) {
                    req.changeSessionId();
                }
                Object a = req.getSession(false).getAttribute("A");
                res.addHeader("A", a.toString());
            }
        });

        servletRegistration.addMapping(SERVLETMAPPING);
        ctx.deploy(httpServer);

        try {
            final HttpPacket request1 = createRequest(CONTEXT + SERVLETMAPPING, PORT, null);
            final HttpContent response1 = sendRequest(request1, 10, PORT);
            
            Cookie[] cookies1 = getCookies(response1.getHttpHeader().getHeaders());
            
            assertEquals(1, cookies1.length);
            assertEquals(Globals.SESSION_COOKIE_NAME, cookies1[0].getName());
            
            String[] values1 = getHeaderValues(response1.getHttpHeader().getHeaders(), "A");
            assertEquals(1, values1.length);
            assertEquals("1", values1[0]);
                        
            final HttpPacket request2 = createRequest(CONTEXT + SERVLETMAPPING, PORT,
                    Collections.singletonMap(Header.Cookie.toString(),
                    Globals.SESSION_COOKIE_NAME + "=" + cookies1[0].getValue()));
            
            final HttpContent response2 = sendRequest(request2, 10, PORT);
            Cookie[] cookies2 = getCookies(response2.getHttpHeader().getHeaders());
            
            assertEquals(1, cookies2.length);
            assertEquals(Globals.SESSION_COOKIE_NAME, cookies2[0].getName());
            
            String[] values2 = getHeaderValues(response2.getHttpHeader().getHeaders(), "A");
            assertEquals(1, values2.length);
            assertEquals("2", values2[0]);

            assertTrue(!cookies1[0].getValue().equals(cookies2[0].getValue()));

            final HttpPacket request3 = createRequest(CONTEXT + SERVLETMAPPING, PORT,
                    Collections.singletonMap(Header.Cookie.toString(),
                    Globals.SESSION_COOKIE_NAME + "=" + cookies1[0].getValue()));

            final HttpContent response3 = sendRequest(request3, 10, PORT);
            Cookie[] cookies3 = getCookies(response3.getHttpHeader().getHeaders());
            
            assertEquals(1, cookies3.length);
            assertEquals(Globals.SESSION_COOKIE_NAME, cookies3[0].getName());
            
            String[] values3 = getHeaderValues(response3.getHttpHeader().getHeaders(), "A");
            assertEquals(1, values3.length);
            assertEquals("1", values3[0]);

            assertTrue(!cookies2[0].getValue().equals(cookies3[0].getValue()));

            final HttpPacket request4 = createRequest(CONTEXT + SERVLETMAPPING, PORT,
                    Collections.singletonMap(Header.Cookie.toString(),
                    Globals.SESSION_COOKIE_NAME + "=" + cookies2[0].getValue()));

            final HttpContent response4 = sendRequest(request4, 10, PORT);
            Cookie[] cookies4 = getCookies(response4.getHttpHeader().getHeaders());
            
            assertEquals(0, cookies4.length);
            
            String[] values4 = getHeaderValues(response4.getHttpHeader().getHeaders(), "A");
            assertEquals(1, values4.length);
            assertEquals("2", values4[0]);
        } finally {
            stopHttpServer();
        }
    }
    
    private String getJSessionCookies(HttpContent response) {
        HttpHeader responseHeader = response.getHttpHeader();
        MimeHeaders mimeHeaders = responseHeader.getHeaders();
        Iterable<String> values = mimeHeaders.values(Header.SetCookie);
        for (String value : values) {
            if(value.startsWith("JSESSIONID=")) {
                String jsessionCookieValue = value.substring(value.indexOf("=")+1);
                System.out.println("Updated JSESSIONID to: "+jsessionCookieValue);
                return jsessionCookieValue;
            }
        }
        
        return null;
    }
        
    private Cookie[] getCookies(MimeHeaders headers) {
        final Cookies cookies = new Cookies();
        cookies.setHeaders(headers, false);
        return cookies.get();
    }
    
    private String[] getHeaderValues(MimeHeaders headers, String name) {
        final List<String> values = new ArrayList<String>();
        
        for (String value : headers.values(name)) {
            values.add(value);
        }
        
        return values.toArray(new String[values.size()]);
    }
}
