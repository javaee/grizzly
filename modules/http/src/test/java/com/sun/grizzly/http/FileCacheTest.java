/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http;

import com.sun.grizzly.Controller;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileCacheTest extends TestCase {
    private static final int PORT = 50000;
    private List<SelectorThread> threads = new ArrayList<SelectorThread>();
    private Set<InetAddress> addresses = new HashSet<InetAddress>();

    /**
     * Sets up the fixture, for example, open a network connection.
     * This method is called before a test is executed.
     */
    @Override
    protected void setUp() throws Exception {
        InetAddress local = InetAddress.getLocalHost();
        addresses.add(local);
        final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()) {
            final Enumeration<InetAddress> inetAddresses = interfaces.nextElement().getInetAddresses();
            while(inetAddresses.hasMoreElements()) {
                final InetAddress address = inetAddresses.nextElement();
                if(address instanceof Inet4Address && address.isReachable(2000)) {
                    addresses.add(address);
                }
            }
        }

        if (addresses.size() > 1) {
            for (InetAddress address : addresses) {
                threads.add(setUpThread(address)) ;
            }
        }
    }

    /**
     * Tears down the fixture, for example, close a network connection.
     * This method is called after a test is executed.
     */
    @Override
    protected void tearDown() throws Exception {
        for (SelectorThread thread : threads) {
            thread.stopEndpoint();
        }
    }

    public void testCache() throws IOException {
        int count = 100;
        while (count-- > 0) {
            for (SelectorThread thread : threads) {
                checkContent(thread.getAddress());
            }
        }
    }

    private void checkContent(final InetAddress host) throws IOException {
        final String address = host.getCanonicalHostName();
        final URLConnection urlConnection = new URL("http://" + address + ":" + PORT).openConnection();
        final String got = getContent(urlConnection);
        final String expected = String.format("<html><body>You've found the %s server</body></html>", address);
        Assert.assertEquals(got, expected);
    }

    protected String getContent(URLConnection connection) {
        try {
            InputStream inputStream = connection.getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);
            try {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[1024];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            } finally {
                if (reader != null) {
                    reader.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }

        return "";
    }

    private SelectorThread setUpThread(final InetAddress address) throws IOException {
        final SelectorThread thread = new SelectorThread();
        thread.setController(new Controller());
        thread.setDisplayConfiguration(true);
        thread.setAddress(address);
        thread.setPort(PORT);
        thread.setFileCacheIsEnabled(true);
        setRootFolder(thread, address);
        SelectorThreadUtils.startSelectorThread(thread);

        return thread;
    }

    protected void setRootFolder(SelectorThread thread, InetAddress address) {
        final String host = address.getCanonicalHostName();
        final String path = System.getProperty("java.io.tmpdir", "/tmp") + "/filecachetest-" + host;
        File dir = new File(path);
        dir.mkdirs();

        final StaticResourcesAdapter adapter = new StaticResourcesAdapter(path);
        FileWriter writer;
        try {
            writer = new FileWriter(new File(dir, "index.html"));
            try {
                writer.write("<html><body>You've found the " + host + " server</body></html>");
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
        thread.setAdapter(adapter);
    }

}
