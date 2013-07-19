/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.rcm;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import java.io.IOException;

import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Date;
import junit.framework.TestCase;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;

/**
 * Basic RCM test.
 *
 * @author Jeanfrancois Arcand
 */
public class RCMTest extends TestCase {

    private TCPNIOTransport transport;
    static int port = 18891;
    static String folder = ".";

    public RCMTest() {
    }

    /*
     * @see TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final ResourceAllocationFilter parser = new ResourceAllocationFilter();

        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(parser);
        filterChainBuilder.add(new BaseFilter() {

            /**
             * <code>CharBuffer</code> used to store the HTML response, containing
             * the headers and the body of the response.
             */
            private CharBuffer reponseBuffer = CharBuffer.allocate(4096);
            /**
             * Encoder used to encode the HTML response
             */
            private CharsetEncoder encoder =
                    Charset.forName("UTF-8").newEncoder();

            @Override
            public NextAction handleRead(FilterChainContext ctx)
                    throws IOException {
                final Buffer requestBuffer = (Buffer) ctx.getMessage();
                final Connection connection = ctx.getConnection();

                final MemoryManager memoryManager = ctx.getMemoryManager();

                requestBuffer.limit(requestBuffer.position());

                reponseBuffer.clear();
                reponseBuffer.put("HTTP/1.1 200 OK\r\n");
                appendHeaderValue("Content-Type", "text/html");
                appendHeaderValue("Content-Length", 0 + "");
                appendHeaderValue("Date", new Date().toString());
                appendHeaderValue("RCMTest", "passed");
                appendHeaderValue("Connection", "Close");
                reponseBuffer.put("\r\n\r\n");
                reponseBuffer.flip();
                ByteBuffer rBuf = encoder.encode(reponseBuffer);

                final Buffer writeBuffer = Buffers.wrap(memoryManager, rBuf);
                ctx.write(writeBuffer, null);

                connection.closeSilently();

                return ctx.getStopAction();
            }

            /**
             * Utility to add headers to the HTTP response.
             */
            private void appendHeaderValue(String name, String value) {
                reponseBuffer.put(name);
                reponseBuffer.put(": ");
                reponseBuffer.put(value);
                reponseBuffer.put("\r\n");
            }
        });

        transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        transport.bind(port);
        transport.start();
    }

    /*
     * @see TestCase#tearDown()
     */
    @Override
    protected void tearDown() throws Exception {
        transport.shutdownNow();
    }

    public void testRCM() {
        try {
            Socket s = new Socket("127.0.0.1", port);
            OutputStream os = s.getOutputStream();
            s.setSoTimeout(10000);

            os.write(("GET /index.html HTTP/1.0\n").getBytes());
            os.write("\n".getBytes());

            InputStream is = s.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line = null;

            int index;
            while ((line = bis.readLine()) != null) {
                if (line.startsWith("RCMTest")) {
                    assertTrue(true);
                    return;
                }
            }
        } catch (Throwable ex) {
            System.out.println("Unable to connect to: " + port);
            ex.printStackTrace();
        }
        fail("Wrong header response");
    }
}
