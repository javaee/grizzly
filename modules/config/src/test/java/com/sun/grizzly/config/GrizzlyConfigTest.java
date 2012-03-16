/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.config.dom.Http;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.ThreadPool;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

/**
 * Created Jan 5, 2009
 *
 * @author <a href="mailto:justin.d.lee@oracle.com">Justin Lee</a>
 */
@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
@Test
public class GrizzlyConfigTest extends BaseGrizzlyConfigTest {
    public void processConfig() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        try {
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
            final String content = getContent(new URL("http://localhost:38082").openConnection());
            final String content2 = getContent(new URL("http://localhost:38083").openConnection());
            Assert.assertEquals(content, "<html><body>You've found the server on port 38082</body></html>");
            Assert.assertEquals(content2, "<html><body>You've found the server on port 38083</body></html>");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void references() {
        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        try {
            final List<NetworkListener> list = grizzlyConfig.getConfig().getNetworkListeners().getNetworkListener();
            final NetworkListener listener = list.get(0);
            boolean found = false;
            for (NetworkListener ref : listener.findProtocol().findNetworkListeners()) {
                found |= ref.getName().equals(listener.getName());
            }
            Assert.assertTrue(found, "Should find the NetworkListener in the list of references from Protocol");
            found = false;
            for (NetworkListener ref : listener.findTransport().findNetworkListeners()) {
                found |= ref.getName().equals(listener.getName());
            }
            Assert.assertTrue(found, "Should find the NetworkListener in the list of references from Transport");
            found = false;
            for (NetworkListener ref : listener.findThreadPool().findNetworkListeners()) {
                found |= ref.getName().equals(listener.getName());
            }
            Assert.assertTrue(found, "Should find the NetworkListener in the list of references from ThreadPool");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void defaults() {
        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config.xml");
        try {
            final ThreadPool threadPool = grizzlyConfig.getConfig().getNetworkListeners().getThreadPool().get(0);
            final Http http = grizzlyConfig.getConfig().getNetworkListeners().getNetworkListener().get(0).findHttpProtocol().getHttp();
            Assert.assertEquals(threadPool.getMaxThreadPoolSize(), "5");
            Assert.assertEquals(http.getCompressableMimeType(), "text/html,text/xml,text/plain");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

//    public void ajp() {
//        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-ajp.xml");
//        try {
//            Assert.assertEquals(
//                    grizzlyConfig.getConfig().getNetworkListeners().getNetworkListener().get(0).getJkEnabled(), "true");
//        } finally {
//            grizzlyConfig.shutdown();
//        }
//    }

    public void ssl() throws URISyntaxException, IOException {
        configure();
        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config-ssl.xml");
        try {
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
            Assert.assertEquals(getContent(new URL("https://localhost:38082").openConnection()),
                "<html><body>You've found the server on port 38082</body></html>");
            Assert.assertEquals(getContent(new URL("https://localhost:38083").openConnection()),
                "<html><body>You've found the server on port 38083</body></html>");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void sslEmpty() throws URISyntaxException, IOException {
        configure();
        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config-ssl-empty.xml");
        try {
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
            Assert.assertEquals(getContent(new URL("https://localhost:38083").openConnection()),
                "<html><body>You've found the server on port 38083</body></html>");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void customSecurePasswordProviders() throws URISyntaxException, IOException {
        configure();
        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config-ssl-passprovider.xml");
        try {
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
            Assert.assertEquals(getContent(new URL("https://localhost:38082").openConnection()),
                "<html><body>You've found the server on port 38082</body></html>");
            Assert.assertEquals(getContent(new URL("https://localhost:38083").openConnection()),
                "<html><body>You've found the server on port 38083</body></html>");
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    private void configure() throws URISyntaxException {
        ClassLoader cl = getClass().getClassLoader();
        URL cacertsUrl = cl.getResource("cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        URL keystoreUrl = cl.getResource("keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        System.setProperty("javax.net.ssl.trustStore", trustStoreFile);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.keyStore", keyStoreFile);
        System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    }

    public void schemeOverride() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config-scheme-override.xml");
            grizzlyConfig.setupNetwork();
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setGrizzlyAdapter(listener, new GrizzlyAdapter() {

                    @Override
                    public void service(GrizzlyRequest request,
                            GrizzlyResponse response) throws Exception {
                        response.getWriter().write(request.getScheme());
                    }
                });
            }
            
            final String content = getContent(new URL("http://localhost:38082").openConnection());
            final String content2 = getContent(new URL("http://localhost:38083").openConnection());
            
            Assert.assertEquals("http", content);
            Assert.assertEquals("https", content2);
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void badConfig() {
        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config-bad.xml");
        try {
            grizzlyConfig.setupNetwork();
        } finally {
            grizzlyConfig.shutdown();
        }
    }

    public void timeoutDisabled() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = new GrizzlyConfig("grizzly-config-timeout-disabled.xml");
        try {
            grizzlyConfig.setupNetwork();
        } finally {
            grizzlyConfig.shutdown();
        }
    }
    
    public void testMissingTransport() throws Exception {
        GrizzlyConfig config = new GrizzlyConfig("bad-transport.xml");
        try {
             config.setupNetwork();                        
            fail("No exception thrown");
        } catch (GrizzlyConfigException gce) {
            assertEquals("GRIZZLY0084: Unable to find transport [tcpp] defined by listener [http-listener].", gce.getMessage());
        } finally {
            config.shutdown();
        }
    }

    public void testMissingThreadPool() throws Exception {
        GrizzlyConfig config = new GrizzlyConfig("bad-threadpool.xml");
        try {
            config.setupNetwork();
            fail("No exception thrown");
        } catch (GrizzlyConfigException gce) {
            assertEquals("GRIZZLY0085: Unable to find thread pool [defaultThreadPooll] defined by listener [http-listener].", gce.getMessage());
        } finally {
            config.shutdown();
        }
    }

    public void testMissingProtocol() throws Exception {
            GrizzlyConfig config = new GrizzlyConfig("bad-protocol.xml");
            try {
                config.setupNetwork();
                fail("No exception thrown");
            } catch (GrizzlyConfigException gce) {
                assertEquals("GRIZZLY0083: Unable to find protocol [httpp] defined by listener [http-listener].", gce.getMessage());
            } finally {
                config.shutdown();
            }
        }
}
