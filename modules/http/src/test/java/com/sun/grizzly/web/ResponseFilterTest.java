/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */
package com.sun.grizzly.http;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;
import junit.framework.TestCase;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.ResponseFilter;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.buf.ByteChunk;

/**
 * Units test that exercise the {@link Response#addResponseFilter}
 * @author Jeanfrancois Arcand
 */
public class ResponseFilterTest extends TestCase {

    public static final int PORT = 18890;
    private static Logger logger = Logger.getLogger("grizzly.test");
    private WebFilter webFilter;
    private TCPNIOTransport transport;

    public void initTransport(Adapter adapter) {
        transport = TransportFactory.getInstance().createTCPTransport();

        WebFilterConfig webConfig = new WebFilterConfig();
        webConfig.setAdapter(adapter);
        webConfig.setDisplayConfiguration(true);

        webFilter = new WebFilter("response-filter-test", webConfig);
        webFilter.enableMonitoring();

        transport.getFilterChain().add(new TransportFilter());
        transport.getFilterChain().add(webFilter);
        try {
            webFilter.initialize();
            transport.bind(PORT);
            transport.start();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void stopTransport() throws IOException {
        transport.stop();
        TransportFactory.getInstance().close();
    }

    public void testResponseFilter() throws IOException {
        System.out.println("Test: testResponseFilter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        final String testString = "Added after invoking Adapter";
        final byte[] testData = testString.getBytes();

        try {
            initTransport(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    //res.flushHeaders();
                    res.addResponseFilter(new ResponseFilter() {

                        public void filter(ByteChunk bc) {
                            try {
                                bc.append(testData, 0, testData.length);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    });
                    ByteChunk bc = new ByteChunk();
                    bc.append("7777777\n".getBytes(), 0, "7777777\n".length());
                    res.doWrite(bc);
                    res.flush();
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    try {
                        super.afterService(req, res);
                        return;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(500000);
            OutputStream os = s.getOutputStream();

            System.out.println(("GET / HTTP/1.1\n"));
            os.write(("GET / HTTP/1.1\n").getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());

            try {
                InputStream is = new DataInputStream(s.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line = null;
                while ((line = br.readLine()) != null) {
                    System.out.println("-> " + line);
                    if (line.contains(testString)) {
                        assertTrue(true);
                        return;
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                assertFalse(false);
            }
        } finally {
            stopTransport();
            pe.shutdown();
        }
    }

    public void testCompleteNewBCResponseFilter() throws IOException {
        System.out.println("Test: testCompleteNewBCResponseFilter");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        try {
            initTransport(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    //res.flushHeaders();
                    res.addResponseFilter(new ResponseFilter() {

                        public void filter(ByteChunk bc) {
                            bc.setBytes("AppendingNewBytes".getBytes(), 0,
                                    "AppendingNewBytes".getBytes().length);
                        }
                    });
                    ByteChunk bc = new ByteChunk();
                    bc.append("7777777\n".getBytes(), 0, "7777777\n".length());
                    res.doWrite(bc);
                    res.flush();
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    try {
                        super.afterService(req, res);
                        return;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(500000);
            OutputStream os = s.getOutputStream();

            System.out.println(("GET / HTTP/1.1\n"));
            os.write(("GET / HTTP/1.1\n").getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());

            try {
                InputStream is = new DataInputStream(s.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line = null;
                while ((line = br.readLine()) != null) {
                    System.out.println("-> " + line);
                    if (line.contains("AppendingNewBytes")) {
                        assertTrue(true);
                        return;
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                assertFalse(false);
            }
        } finally {
            stopTransport();
            pe.shutdown();
        }
    }

    public void testComplexByteChunkManipulation() throws IOException {
        System.out.println("Test: testComplexByteChunkManipulation");
        final ScheduledThreadPoolExecutor pe = new ScheduledThreadPoolExecutor(1);
        try {
            initTransport(new StaticResourcesAdapter() {

                @Override
                public void service(final Request req, final Response res) throws IOException {
                    //res.flushHeaders();
                    res.addResponseFilter(new ResponseFilter() {

                        public void filter(ByteChunk bc) {
                            bc.recycle();
                            bc.setBytes("AppendingNewBytes".getBytes(), 0,
                                    "AppendingNewBytes".getBytes().length);
                        }
                    });
                    ByteChunk bc = new ByteChunk();
                    bc.append("7777777\n".getBytes(), 0, "7777777\n".length());
                    res.doWrite(bc);
                    res.flush();
                }

                @Override
                public void afterService(final Request req, final Response res) {
                    try {
                        super.afterService(req, res);
                        return;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            Socket s = new Socket("localhost", PORT);
            s.setSoTimeout(500000);
            OutputStream os = s.getOutputStream();

            System.out.println(("GET / HTTP/1.1\n"));
            os.write(("GET / HTTP/1.1\n").getBytes());
            os.write(("Host: localhost:" + PORT + "\n").getBytes());
            os.write("\n".getBytes());

            try {
                InputStream is = new DataInputStream(s.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line = null;
                System.out.println("READING LINES");
                while ((line = br.readLine()) != null) {
                    System.out.println("-> " + line);
                    if (line.contains("AppendingNewBytes")) {
                        assertTrue(true);
                        return;
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                assertFalse(false);
            }
        } finally {
            stopTransport();
            pe.shutdown();
        }
    }
}
