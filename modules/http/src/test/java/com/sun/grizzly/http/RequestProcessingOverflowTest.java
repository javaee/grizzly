/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.http;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import java.io.BufferedOutputStream;
import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

public class RequestProcessingOverflowTest extends TestCase {


    // ------------------------------------------------------ Test Setup/Destroy


    private static final int PORT = 7777;
    private static final String MESSAGE = "Hello world!";

    private GrizzlyWebServer webServer;

    @Override
    protected void setUp() throws Exception {
        webServer = new GrizzlyWebServer(PORT);
        webServer.addGrizzlyAdapter(new ContentEncodingTest.MessageAdapter(MESSAGE),
                new String[]{"/test"});
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


    // ------------------------------------------------------------ Test Methods

    /**
     * Issue: https://grizzly.dev.java.net/issues/show_bug.cgi?id=487
     */
    public void testLargeRequestURI() {

        Socket s = initClientSocket();

        final int bufferSize = 16384;
        try {
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), bufferSize);

            // Per the issue description:
            //
            //  Try to produce a simple HTTP request like
            //
            //     GET /foo/bar/loooooooooooooooooooong HTTP/1.0
            //     Host: somehost
            //
            //
            //  where "loooooooooooooooooooong" has so many 'o' characters such
            //  that the whole
            //  request has a size >8192 bytes.

            StringBuilder sb = new StringBuilder(bufferSize);
            sb.append("GET /test/l");
            for (int i = 0; i < 8200; i++) {
                sb.append('o');
            }
            sb.append("g HTTP/1.1\n");
            out.write(sb.toString().getBytes());
            out.write("Host: localhost\n".getBytes());
            out.write("\n".getBytes());
            out.flush();
        } catch (SocketException ignored) {
            // If it's SocketException - probably it's "broken pipe",
            // which occurred cause the server closed connection before
            // read out entire request.
            // It's expected. Let the client read the response
        } catch (IOException e) {
            fail("Unable to complete test request to local test server: " + e.toString());
        }

        try {
            InputStream in = s.getInputStream();
            BufferedReader reader =
                  new BufferedReader(new InputStreamReader(in));
            String responseStatus = reader.readLine();
            String control = "HTTP/1.1 414 Request-URI Too Long";
            assertEquals(control, control, responseStatus);
        } catch (IOException e) {
            fail("Unable to read response from local test server: " + e.toString());
        }

    }


    public void testLargeRequestHeader() {

        Socket s = initClientSocket();
        final int bufferSize = 16384;

        try {
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), bufferSize);

            StringBuilder sb = new StringBuilder(bufferSize);
            sb.append("Host: lo");
            for (int i = 0; i < 8200; i++) {
                sb.append('o');
            }
            sb.append("calhost\n");
            out.write("GET /test HTTP/1.1\n".getBytes());
            out.write(sb.toString().getBytes());
            out.write("\n".getBytes());
            out.flush();
        } catch (SocketException ignored) {
            // If it's SocketException - probably it's "broken pipe",
            // which occurred cause the server closed connection before
            // read out entire request.
            // It's expected. Let the client read the response
        } catch (IOException e) {
            fail("Unable to complete test request to local test server: " + e.toString());
        }

        try {
            InputStream in = s.getInputStream();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in));
            String responseStatus = reader.readLine();
            String control = "HTTP/1.1 400 Bad Request";
            assertEquals(control, control, responseStatus);
        } catch (IOException e) {
            fail("Unable to read response from local test server: " + e.toString());
        }
        
    }


    // --------------------------------------------------------- Private Methods


    private static Socket initClientSocket() {

        Socket s = null;
        try {
            s = new Socket("localhost", PORT);
        } catch (Exception e) {
            fail("Unable to establish connection to local test server: " + e.toString());
        }

        try {
            s.setSoTimeout(30 * 1000);
        } catch (SocketException se) {
            fail("Unable to set SoTimeout: " + se.toString());
        }

        return s;

    }
}
