/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.lzma.compression.lzma.Decoder;
import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import junit.framework.TestCase;

/**
 * Test HTTP compression algorithms
 * 
 * @author Alexey Stashok
 */
public class ContentEncodingTest extends TestCase {
    private static final int PORT = 7777;
    private static final String MESSAGE = "Hello world!";
    
    private GrizzlyWebServer webServer;

    @Override
    protected void setUp() throws Exception {
        webServer = new GrizzlyWebServer(PORT);
        webServer.addGrizzlyAdapter(new MessageAdapter(MESSAGE), new String[] {"/hello"});
        webServer.getSelectorThread().setCompression("force");
        webServer.start();
    }

    @Override
    protected void tearDown() throws Exception {
        if (webServer != null) {
            try {
                webServer.stop();
            } finally {
                webServer = null;
            }
        }
    }

    public void testGzip() throws IOException {
        InputStream is = null;
        
        URL url = new URL("http://localhost:".concat(String.valueOf(PORT)).concat("/hello"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestProperty("Accept-Encoding", "gzip");
            assertEquals(200, connection.getResponseCode());
            assertEquals("gzip", connection.getContentEncoding());

            is = new GZIPInputStream(connection.getInputStream());
            byte[] buffer = new byte[4096];
            int length = 0;
            int c;
            while((c = is.read()) != -1) {
                buffer[length++] = (byte) c;
            }

            assertEquals(MESSAGE, new String(buffer, 0, length));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }

            connection.disconnect();
        }
    }

    public void testGzipDeflate() throws IOException {
        InputStream is = null;

        URL url = new URL("http://localhost:".concat(String.valueOf(PORT)).concat("/hello"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestProperty("Accept-Encoding", "unknown, gzip, deflate");
            assertEquals(200, connection.getResponseCode());
            assertEquals("gzip", connection.getContentEncoding());

            is = new GZIPInputStream(connection.getInputStream());
            byte[] buffer = new byte[4096];
            int length = 0;
            int c;
            while((c = is.read()) != -1) {
                buffer[length++] = (byte) c;
            }

            assertEquals(MESSAGE, new String(buffer, 0, length));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }

            connection.disconnect();
        }
    }
    
    // Enable, after implementing LZMA
    public void testLzma() throws IOException {
        DataInputStream is = null;

        URL url = new URL("http://localhost:".concat(String.valueOf(PORT)).concat("/hello"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestProperty("Accept-Encoding", "lzma");
            assertEquals(200, connection.getResponseCode());
            assertEquals("lzma", connection.getContentEncoding());

            is = new DataInputStream(connection.getInputStream());

            int propertiesSize = 5;
            byte[] properties = new byte[propertiesSize];
            is.readFully(properties);

            Decoder decoder = new Decoder();
            if (!decoder.SetDecoderProperties(properties)) {
                throw new IOException("Incorrect stream properties");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if (!decoder.Code(is, baos, -1)) {
                throw new IOException("Error in data stream");
            }

            baos.close();
            byte[] output = baos.toByteArray();

            assertEquals(MESSAGE, new String(output));
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }

            connection.disconnect();
        }
    }

    public static class MessageAdapter extends GrizzlyAdapter {
        private final String message;

        public MessageAdapter(String message) {
            this.message = message;
        }

        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            try {
                response.getWriter().print(message);
            } catch (Exception e) {
                response.setStatus(500);
            }
        }

    }
}
