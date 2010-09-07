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

package com.sun.grizzly.web;

import com.sun.grizzly.http.server.GrizzlyAdapter;
import com.sun.grizzly.http.server.GrizzlyListener;
import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;
import com.sun.grizzly.http.server.GrizzlyWebServer;
import com.sun.grizzly.impl.SafeFutureImpl;
import junit.framework.TestCase;

import javax.net.SocketFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class HttpContinueTest extends TestCase {

    private static final int PORT = 9495;


    // ------------------------------------------------------------ Test Methods

    public void test100Continue() throws Exception {

        final SafeFutureImpl<String> future = new SafeFutureImpl<String>();
        GrizzlyWebServer server = createServer(new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                future.result(request.getParameter("a"));
            }

        }, "/path");

        Socket s = null;
        try {
            server.start();
            s = SocketFactory.getDefault().createSocket("localhost", PORT);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            out.write("POST /path HTTP/1.1\r\n".getBytes());
            out.write(("Host: localhost:" + PORT + "\r\n").getBytes());
            out.write("Content-Type: application/x-www-form-urlencoded\r\n".getBytes());
            out.write("Content-Length: 7\r\n".getBytes());
            out.write("Expect: 100-Continue\r\n".getBytes());
            out.write("\r\n".getBytes());

            StringBuilder sb = new StringBuilder();
            for (;;) {
                int i = in.read();
                if (i == '\r') {
                    in.mark(6);
                    if (in.read() == '\n' && in.read() == '\r' && in.read() == '\n') {
                        break;
                    } else {
                        in.reset();
                    }
                } else {
                    sb.append((char) i);
                }
            }

            assertEquals("HTTP/1.1 100 Continue", sb.toString().trim());

            // send post data now that we have clearance
            out.write("a=hello\r\n\r\n".getBytes());
            assertEquals("hello", future.get(10, TimeUnit.SECONDS));
            sb.setLength(0);
            for (;;) {
                int i = in.read();
                if (i == '\r') {
                    break;
                } else {
                    sb.append((char) i);
                }
            }

            assertEquals("HTTP/1.1 200 OK", sb.toString().trim());
        } finally {
            server.stop();
            if (s != null) {
                s.close();
            }
        }

    }

    public void testExpectationIgnored() throws Exception {

        GrizzlyWebServer server = createServer(null);

        Socket s = null;
        try {
            server.start();
            s = SocketFactory.getDefault().createSocket("localhost", PORT);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            StringBuilder post = new StringBuilder();
            post.append("POST /path HTTP/1.1\r\n");
            post.append("Host: localhost:").append(PORT).append("\r\n");
            post.append("Expect: 100-Continue\r\n");
            post.append("Content-Type: application/x-www-form-urlencoded\r\n");
            post.append("Content-Length: 7\r\n");
            post.append("\r\n");
            post.append("a=hello\r\n\r\n");

            out.write(post.toString().getBytes());

            StringBuilder sb = new StringBuilder();
            for (;;) {
                int i = in.read();
                if (i == '\r') {
                    break;
                } else {
                    sb.append((char) i);
                }
            }

            assertEquals("HTTP/1.1 404 Not Found", sb.toString().trim());

        } finally {
            server.stop();
            if (s != null) {
                s.close();
            }
        }

    }


    public void testFailedExpectation() throws Exception {

        GrizzlyWebServer server = createServer(null);

        Socket s = null;
        try {
            server.start();
            s = SocketFactory.getDefault().createSocket("localhost", PORT);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            out.write("POST /path HTTP/1.1\r\n".getBytes());
            out.write(("Host: localhost:" + PORT + "\r\n").getBytes());
            out.write("Content-Type: application/x-www-form-urlencoded\r\n".getBytes());
            out.write("Content-Length: 7\r\n".getBytes());
            out.write("Expect: 100-Continue-Extension\r\n".getBytes());
            out.write("\r\n".getBytes());

            StringBuilder sb = new StringBuilder();
            for (;;) {
                int i = in.read();
                if (i == '\r') {
                    break;
                } else {
                    sb.append((char) i);
                }
            }

            assertEquals("HTTP/1.1 417 Expectation Failed", sb.toString().trim());

        } finally {
            server.stop();
            if (s != null) {
                s.close();
            }
        }

    }


    // --------------------------------------------------------- Private Methods


    private GrizzlyWebServer createServer(final GrizzlyAdapter adapter,
                                          final String... mappings) {

        GrizzlyWebServer server = new GrizzlyWebServer();
        GrizzlyListener listener =
                new GrizzlyListener("grizzly",
                                    GrizzlyListener.DEFAULT_NETWORK_HOST,
                                    PORT);
        server.addListener(listener);
        if (adapter != null) {
            server.getServerConfiguration().addGrizzlyAdapter(adapter, mappings);
        }
        return server;

    }

}
