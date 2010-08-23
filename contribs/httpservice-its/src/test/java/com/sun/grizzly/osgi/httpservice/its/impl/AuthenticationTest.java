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

package com.sun.grizzly.osgi.httpservice.its.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.ops4j.pax.exam.CoreOptions.*;
import org.ops4j.pax.exam.Option;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.*;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * Integration testing of Authentication.
 *
 * @author Hubert Iwaniuk
 * @since Feb 9, 2009
 */
@RunWith(JUnit4TestRunner.class)
public class AuthenticationTest {

    @Configuration
    public static Option[] configuration() {
        return options(
                repositories(
                        repository("http://repository.springsource.com/maven/bundles/external"),
                        repository("http://repository.ops4j.org/maven2"),
                        repository("http://repo1.maven.org/maven2/")
                ),
                logProfile(),
                frameworks(
                        felix()
                ),
                systemProperty("org.osgi.service.http.port").value("8989"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-http-servlet").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-http").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-utils").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-framework").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-portunif").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-rcm").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly.osgi").artifactId("grizzly-httpservice").version(
                        "1.9.11-SNAPSHOT")
        );
    }

    @Test
    public void authentication(final BundleContext bc)
            throws InterruptedException, ServletException, NamespaceException, IOException {
        final ServiceTracker tracker = new ServiceTracker(bc, HttpService.class.getName(), null);
        tracker.open();
        tracker.waitForService(1000);
        HttpService hs = (HttpService) tracker.getService();
        try {
            HttpContext context = new HttpContext() {
                public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response)
                        throws IOException {
                    // TODO is it a bug HttpServletRequest.BASIC_AUTH.equals(request.getAuthType()) doesn't provide info if Authorization header present?
                    String auth = request.getHeader("Authorization");
                    if (auth != null) {
                        String userpassEncoded = auth.substring(6);
                        sun.misc.BASE64Decoder dec = new sun.misc.BASE64Decoder();
                        String userpassDecoded = new String(dec.decodeBuffer(userpassEncoded));
                        return "tester:pass".equals(userpassDecoded);
                    } else {
                        response.setHeader("WWW-Authenticate", "BASIC realm=\"Testing OSGi\"");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        return false;
                    }
                }

                public URL getResource(String s) {
                    return bc.getBundle().getResource(s);
                }

                public String getMimeType(String s) {
                    return null;
                }
            };
            hs.registerServlet("/testServlet", new GenericServlet() {
                @Override
                public void service(ServletRequest req, ServletResponse resp) throws ServletException, IOException {
                    HttpServletResponse response = (HttpServletResponse) resp;
                    response.setStatus(200);
                    response.getWriter().write("Grizzly");
                }
            }, null, context);

            URL url2 = new URL("http://localhost:8989/testServlet");
            HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
            Assert.assertEquals("Status code should be the same.", 401, conn2.getResponseCode());

            // provide authentication details, should 200
            Authenticator.setDefault(new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication("tester", "pass".toCharArray());
                }
            });
            URL url1 = new URL("http://localhost:8989/testServlet");
            HttpURLConnection conn1 = (HttpURLConnection) url1.openConnection();
            Assert.assertEquals("Status code should be the same.", 200, conn1.getResponseCode());

        } finally {
            tracker.close();
        }
    }
}
