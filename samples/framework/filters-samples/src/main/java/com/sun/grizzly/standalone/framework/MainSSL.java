/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.standalone.framework;

/**
 * @author John Vieten 24.10.2008
 * @version 1.0
 */

import com.sun.grizzly.SSLConfig;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.URL;

/**
 * Simple SSL client server communication. Just shows how Custom Protocol can be used.
 * In this example n clients will log into a server  call
 * a time cosuming server method and broadcast to other clients
 *
 * @author John Vieten
 */
public class MainSSL {
    protected static Logger logger = Logger.getLogger("MainSSL");
    private final static int CLIENT_COUNT = 25;

    public static void main(String[] args) throws Exception {
        final SSLConfig sslConfig = createSSLConfig();
        long start_time=System.currentTimeMillis();
        Example_1_Server serverExample = new Example_1_Server(sslConfig);
        serverExample.init();
        serverExample.start();
        Example_1_Client[] clientArray = new Example_1_Client[CLIENT_COUNT];
        final CountDownLatch clientWorkDoneLatch = new CountDownLatch(clientArray.length);
        for (int i = 0; i < clientArray.length; i++) {
            clientArray[i] = new Example_1_Client("(" + i + ")", clientWorkDoneLatch, sslConfig);
        }

        for (int i = 0; i < clientArray.length; i++) {
            final int trackCount = i;
            final Example_1_Client example_1_client = clientArray[i];
            new Thread(new Runnable() {
                public void run() {
                    try {
                        example_1_client.start();

                    } catch (Throwable e) {
                        e.printStackTrace();
                        logger.log(Level.SEVERE, "Client (" + trackCount + ")", e);
                    }
                }
            }).start();

        }

        // wait till clients have done their work
        clientWorkDoneLatch.await();
        logger.log(Level.INFO, "MainSSL run " +(System.currentTimeMillis()-start_time) + " ms");
        serverExample.stop();

    }

    static SSLConfig createSSLConfig() {
        SSLConfig sslConfig = new SSLConfig();
        ClassLoader cl = MainSSL.class.getClassLoader();
        // override system properties
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = cacertsUrl.getFile();
        if (cacertsUrl != null) {
            sslConfig.setTrustStoreFile(trustStoreFile);
            sslConfig.setTrustStorePass("changeit");
        }

        logger.log(Level.INFO, "SSL certs path: " + trustStoreFile);

        // override system properties
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = keystoreUrl.getFile();
        if (keystoreUrl != null) {
            sslConfig.setKeyStoreFile(keyStoreFile);
            sslConfig.setKeyStorePass("changeit");
        }

        logger.log(Level.INFO, "SSL keystore path: " + keyStoreFile);
        SSLConfig.DEFAULT_CONFIG = sslConfig;
        return sslConfig;
    }

}
