/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.servlet.httpupgrade;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.StringTokenizer;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;
import junit.framework.TestCase;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.servlet.WebappContext;

/**
 * Test Servlet 3.1 upgrade mechanism
 *
 * @author Alexey Stashok
 */
public class HttpUpgradeTest extends TestCase {

    public static final int PORT = 18890 + 19;
    private static final String CRLF = "\r\n";

    public void testEchoProtocolUpgrade() throws Exception {
        final String expectedResponse = "HelloWorld";
        final String contextRoot = "/test";
        final String upgradeServlet = "/echoProtocol";

        HttpServer httpServer = null;
        Socket s = null;

        InputStream input = null;
        OutputStream output = null;
        try {

            httpServer = HttpServer.createSimpleServer("./", PORT);

            WebappContext ctx = new WebappContext("Test", contextRoot);
            final ServletRegistration reg = ctx.addServlet("EchoProtocol",
                    new HttpServlet() {
                        @Override
                        protected void doPost(HttpServletRequest req, HttpServletResponse res)
                                throws ServletException, IOException {
                            if ("echo".equals(req.getHeader("Upgrade"))) {
                                res.setStatus(101);
                                res.setHeader("Upgrade", "echo");
                                res.setHeader("Connection", "Upgrade");
                                System.out.println("upgraded to use EchoHttpUpgradeHandler");
                                EchoProtocolUpgradeHandler handler = req.upgrade(EchoProtocolUpgradeHandler.class);
                                handler.setDelimiter("/");
                            } else {
                                res.getWriter().println("No upgrade: " + req.getHeader("Upgrade"));
                            }
                        }
            });
            reg.addMapping(upgradeServlet);

            ctx.deploy(httpServer);
            httpServer.start();

            s = new Socket("localhost", PORT);
            output = s.getOutputStream();
            try {
                String reqStr = "POST " + contextRoot + upgradeServlet + " HTTP/1.1" + CRLF;
                reqStr += "User-Agent: Java/1.6.0_33" + CRLF;
                reqStr += "Host: localhost:" + PORT + CRLF;
                reqStr += "Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2" + CRLF;
                reqStr += "Upgrade: echo" + CRLF;
                reqStr += "Connection: Upgrade\r\n";
                reqStr += "Content-type: application/x-www-form-urlencoded" + CRLF;
                reqStr += CRLF;
                output.write(reqStr.getBytes());

                writeChunk(output, "Hello");
                int sleepInSeconds = 1;
                System.out.format("Sleeping %d sec\n", sleepInSeconds);
                Thread.sleep(sleepInSeconds * 1000);
                writeChunk(output, "World");
                writeChunk(output, null);
            } catch (Exception ex) {
            }
            input = s.getInputStream();
            // read data without using readLine
            byte b[] = new byte[1024];
            StringBuilder sb = new StringBuilder();
            do {
                int len = input.read(b);
                if (len == -1) {
                    break;
                }
                
                String line = new String(b, 0, len);
                sb.append(line);
                if (sb.indexOf("World") > 0) {
                    break;
                }
            } while (true);

            StringTokenizer tokens = new StringTokenizer(sb.toString(), CRLF);
            String line = null;
            while (tokens.hasMoreTokens()) {
                line = tokens.nextToken();
            }

            assertTrue(line.contains("/"));
            assertTrue(line.indexOf("/") < line.indexOf("d"));
            assertEquals(expectedResponse, line.replace("/", ""));
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception ignored) {
            }

            try {
                if (output != null) {
                    output.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (s != null) {
                    s.close();
                }
            } catch (Exception ignored) {
            }
            
            if (httpServer != null) {
                httpServer.shutdownNow();
            }
        }
    }

    private static void writeChunk(OutputStream out, String data) throws IOException {
        if (data != null) {
            out.write(data.getBytes());
        }
        out.flush();
    }

    public static class EchoProtocolUpgradeHandler implements HttpUpgradeHandler {
        private String delimiter = "/";
        
        public void init(WebConnection wc) {
            System.out.println("EchoProtocolHandler.init");
            try {
                ServletInputStream input = wc.getInputStream();
                ServletOutputStream output = wc.getOutputStream();
                ReadListenerImpl readListener = new ReadListenerImpl(delimiter, input, output);
                input.setReadListener(readListener);

                int b;
                while (input.isReady() && ((b = input.read()) != -1)) {
                    System.out.print((char) b);
                    output.write(b);
                }
                output.flush();

            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void destroy() {
            System.out.println("--> destroy");
        }

        public void setDelimiter(String delimiter) {
            this.delimiter = delimiter;
        }

        public String getDelimiter() {
            return delimiter;
        }

        static class ReadListenerImpl implements ReadListener {

            ServletInputStream input = null;
            ServletOutputStream output = null;
            String delimiter = null;

            ReadListenerImpl(String d, ServletInputStream in, ServletOutputStream out) {
                delimiter = d;
                input = in;
                output = out;
            }

            public void onDataAvailable() {
                try {
                    StringBuilder sb = new StringBuilder();
                    System.out.println("--> onDataAvailable");
                    int len;
                    byte b[] = new byte[1024];
                    while (input.isReady()
                            && (len = input.read(b)) != -1) {
                        String data = new String(b, 0, len);
                        System.out.println("--> " + data);
                        sb.append(data);
                    }
                    output.print(delimiter + sb.toString());
                    output.flush();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }

            public void onAllDataRead() {
                try {
                    System.out.println("--> onAllDataRead");
                    output.println("-onAllDataRead");
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            }

            public void onError(final Throwable t) {
                System.out.println("--> onError");
                //t.printStackTrace();
            }
        }
    }
}
