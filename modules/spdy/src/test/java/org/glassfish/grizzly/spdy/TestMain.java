/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;

/**
 *
 * @author oleksiys
 */
public class TestMain {
    public static void main(String[] args) throws IOException {

        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();
        SSLEngineConfigurator serverSSLEngineConfigurator;

        if (sslContextConfigurator.validateConfiguration(true)) {
            serverSSLEngineConfigurator =
                    new SSLEngineConfigurator(sslContextConfigurator.createSSLContext(),
                    false, false, false);
        } else {
            throw new IllegalStateException("Failed to validate SSLContextConfiguration.");
        }

        HttpServer server = HttpServer.createSimpleServer("/tmp", 7070);
        NetworkListener listener = server.getListener("grizzly");
        listener.setSecure(true);
        listener.setSSLEngineConfig(serverSSLEngineConfigurator);

        listener.registerAddOn(new SpdyAddOn());

        server.getServerConfiguration().addHttpHandler(
        new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws Exception {
                response.setContentType("text/html");

                final Writer w = response.getWriter();
                StringBuilder sb = new StringBuilder(128);
                sb.append("<html><head><title>SPDY Test</title></head><body>");
                sb.append("Hello!<br />");
                sb.append("<img src=\"/test.jpg\" />");
                sb.append("<img src=\"/test.jpg\" />");
                sb.append("<img src=\"/test.jpg\" />");
                sb.append("<img src=\"/test.jpg\" />");
                sb.append("</body></html>");
                response.setContentLength(sb.length());
                w.write(sb.toString());
            }
        }, "/test");


        try {
            server.start();
            System.out.println("Press any key to stop ...");
            //noinspection ResultOfMethodCallIgnored
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
    
    // --------------------------------------------------------- Private Methods

    
    private static SSLContextConfigurator createSSLContextConfigurator() {
        SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        ClassLoader cl = SpdyHandlerFilter.class.getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
            sslContextConfigurator.setTrustStorePass("changeit");
        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfigurator.setKeyStorePass("changeit");
        }

        return sslContextConfigurator;
    }
    
}
