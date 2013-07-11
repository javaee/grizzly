/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import org.glassfish.grizzly.http.Protocol;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test HTTP CONNECT method processing
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class ConnectMethodTest {
    private static final int PORT = 18903;
    
    @Test
    public void testConnectHttp10() throws Exception {
        doTest(Protocol.HTTP_1_0);
    }

    @Test
    public void testConnectHttp11() throws Exception {
        doTest(Protocol.HTTP_1_1);
    }
    
    private void doTest(Protocol protocol) throws Exception {
        final int len = 8192;
        
        final HttpServer server = createWebServer(new HttpHandler() {

            @Override
            public void service(Request request, Response response) throws Exception {
                response.flush();
                
                final byte[] buffer = new byte[1024];
                final InputStream in = request.getInputStream();
                final OutputStream out = response.getOutputStream();
                
                int readTotal = 0;
                int read;
                
                while(readTotal < len &&
                        (read = in.read(buffer)) > 0) {
                    readTotal += read;
                    out.write(buffer, 0, read);
                }
                
                out.flush();
            }
        });
        
        server.start();

        Socket s = null;
        
        try {
            final String connectRequest = "CONNECT myserver " + protocol.getProtocolString() + "\r\n" +
                    "User-Agent: xyz\r\n" +
                    "Host: abc.com\r\n" +
                    "Proxy-authorization: basic aGVsbG86d29ybGQr=\r\n" +
                    "\r\n";
            
            s = new Socket("localhost", PORT);
            s.setSoTimeout(500000);
            
            final OutputStream os = s.getOutputStream();
            os.write(connectRequest.getBytes());
            os.flush();
            
            final InputStream inputStream = s.getInputStream();
            final BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String responseStatusLine = in.readLine();
            
            assertTrue(responseStatusLine, responseStatusLine.startsWith("HTTP/1.1 200"));
            
            String line;
            while((line = in.readLine()).length() > 0) {
                // iterating till "\r\n"
                System.out.println(line);
            }
                        
            final byte[] dummyContent = new byte[len];
            for (int i = 0; i < dummyContent.length; i++) {
                dummyContent[i] = (byte) ('0' + (i % 10));
            }
            
            os.write(dummyContent);
            os.flush();
            
            final byte[] responseContent = new byte[len];
            int offs = 0;
            
            while(offs < len) {
                final int bytesRead = 
                        inputStream.read(responseContent, offs, len - offs);
                offs += bytesRead;
            }
            
            final String s1 = new String(dummyContent);
            final String s2 = new String(responseContent);
            
            assertEquals(s1, s2);
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                }
            }
            
            server.shutdownNow();
        }
    }
    
    private HttpServer createWebServer(final HttpHandler httpHandler) {

        final HttpServer server = new HttpServer();
        final NetworkListener listener =
                new NetworkListener("grizzly",
                        NetworkListener.DEFAULT_NETWORK_HOST,
                        PORT);
        listener.getKeepAlive().setIdleTimeoutInSeconds(-1);
        server.addListener(listener);
        server.getServerConfiguration().addHttpHandler(httpHandler, "/");

        return server;
    }
}
