/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.samples.spdy;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.spdy.SpdyAddOn;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;

/**
 * Utility class, which contains helper methods for server initialization.
 * 
 * @author Alexey Stashok
 */
public class ServerUtils {
    private static final String SMILEYS_ROOT = "./smileys";
    
    /**
     * Configure {@link HttpServer} based on passed parameters.
     * 
     * @param name {@link NetworkListener} name
     * @param host {@link NetworkListener} host
     * @param port {@link NetworkListener} port
     * @param isEnableFileCache
     * @param addons
     * @return {@link HttpServer}
     * 
     * @throws IOException 
     */
    protected static HttpServer configureServer(final String name,
            final String host, final int port, final boolean isEnableFileCache,
            final SpdyAddOn... addons)
            throws IOException {

        // Create NetworkListener
        final NetworkListener listener = new NetworkListener(name, host, port);
        
        // disable sendfile
        listener.setSendFileEnabled(false);
        // enable or disable filecache based on the passed parameter
        listener.getFileCache().setEnabled(isEnableFileCache);
        
        // enable security (required for NPN mode)
        listener.setSecure(true);
        listener.setSSLEngineConfig(ServerUtils.getServerSSLEngineConfigurator());
        
        for (SpdyAddOn addon : addons) {
            listener.registerAddOn(addon);
        }
        
        // create a basic server that listens on port 8080.
        final HttpServer server = new HttpServer();
        
        server.addListener(listener);
        
        final ServerConfiguration config = server.getServerConfiguration();

        // Initialize smileys folder
        final File smileysRootFolder = new File(SMILEYS_ROOT);
        
        // check if the smileys folder exists
        if (!smileysRootFolder.exists() || !smileysRootFolder.isDirectory()) {
            throw new IOException("The smileys folder doesn't exist");
        }
        
        // Add StaticHttpHandler responsible for serving static (smileys) images
        config.addHttpHandler(new StaticHttpHandler(smileysRootFolder.getAbsolutePath()));
        
        // Add SmileysHandler to serve initial html page, which references smileys
        config.addHttpHandler(new SmileysHandler("/", smileysRootFolder),
                "/getsmileys");

        return server;
    }
    
    /**
     * Initializes SSLEngine configurator.
     */
    protected static SSLEngineConfigurator getServerSSLEngineConfigurator() {
        SSLContextConfigurator sslContextConfigurator = createSSLContextConfigurator();

        if (sslContextConfigurator.validateConfiguration(true)) {
            return new SSLEngineConfigurator(
                    sslContextConfigurator.createSSLContext(),
                    false, false, false);
        } else {
            throw new IllegalStateException("Failed to validate SSLContextConfiguration.");
        }
    }
    
    /**
     * Initializes SSLContext configurator.
     */
    protected static SSLContextConfigurator createSSLContextConfigurator() {
        SSLContextConfigurator sslContextConfigurator =
                new SSLContextConfigurator();
        ClassLoader cl = SpdyServer.class.getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssl-cacerts.jks");
        if (cacertsUrl != null) {
            sslContextConfigurator.setTrustStoreFile(cacertsUrl.getFile());
            sslContextConfigurator.setTrustStorePass("changeit");
        }

        // override system properties
        URL keystoreUrl = cl.getResource("ssl-keystore.jks");
        if (keystoreUrl != null) {
            sslContextConfigurator.setKeyStoreFile(keystoreUrl.getFile());
            sslContextConfigurator.setKeyStorePass("changeit");
        }

        return sslContextConfigurator;
    }    
}
