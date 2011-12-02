/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for HTTP chunked Transfer-Encoding.
 * 
 * @author Alexey Stashok
 */
public class ChunkedEncodingTest {
    public static final int PORT = 18890;
    
    private static final String CRLF = "\r\n";    
    private SelectorThread st;

    @Before
    public void before() throws Exception {
        createSelectorThread();
    }

    @After
    public void after() throws Exception {
        SelectorThreadUtils.stopSelectorThread(st);
    }
    
    @Test
    public void checkTrailerHeaders() throws Exception {
        setAdapterAndListen(new TrailerCheckAdapter());
        
        // trailing headers
        final String[] req = new String[] {
            "POST / HTTP/1.1" + CRLF +
            "Host: any" + CRLF +
            "Transfer-encoding: chunked" + CRLF +
            "Content-Type: application/x-www-form-urlencoded" + CRLF +
            "Connection: close" + CRLF +
            CRLF +
            "3" + CRLF +
            "a=0" + CRLF +
            "4" + CRLF +
            "&b=1" + CRLF +
            "0" + CRLF +
            "x-trailer: Test",
            "TestTest0123456789abcdefghijABCDEFGHIJopqrstuvwxyz" + CRLF +
            CRLF };        

        final String reply = sendRequest(req);
        assertTrue("x-trailer header not match: " + reply,
                reply.indexOf("null7TestTestTest0123456789abcdefghijABCDEFGHIJopqrstuvwxyz") != 0);
    }

    @Test
    public void checkNoTrailerHeaders() throws Exception {
        setAdapterAndListen(new TrailerCheckAdapter());
        
        final String[] req =new String[] {
            "POST / HTTP/1.1" + CRLF
            + "Host: any" + CRLF
            + "Transfer-encoding: chunked" + CRLF
            + "Content-Type: application/x-www-form-urlencoded"
            + CRLF
            + "Connection: close" + CRLF
            + CRLF
            + "3" + CRLF
            + "a=0" + CRLF
            + "4" + CRLF
            + "&b=1" + CRLF
            + "0" + CRLF
            + CRLF};
        
        final String reply = sendRequest(req);
        assertTrue("x-trailer header not match: " + reply,
                reply.indexOf("null7null") != 0);
    }
    
    private String sendRequest(String[] request) throws SocketException, IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        
        OutputStream os = s.getOutputStream();
        for (String r : request) {
            os.write(r.getBytes());
            os.flush();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line = br.readLine()) != null) sb.append(line);
        
        return sb.toString();
    }
    
    private void createSelectorThread() throws Exception {
        st = new SelectorThread();

        st.setPort(PORT);
        st.setDisplayConfiguration(com.sun.grizzly.util.Utils.VERBOSE_TESTS);
    }
    
    private void setAdapterAndListen(Adapter adapter) throws Exception {
        st.setAdapter(adapter);
        st.listen();
        st.enableMonitoring();
    }

    private static class TrailerCheckAdapter extends GrizzlyAdapter {
        @Override
        public void service(GrizzlyRequest request,
                GrizzlyResponse response) throws Exception {
            response.setContentType("text/plain");
            PrintWriter pw = response.getWriter();
            // Header not visible yet, body not processed
            String value = request.getHeader("x-trailer");
            if (value == null) {
                value = "null";
            }
            pw.write(value);

            // Read the body - quick and dirty
            InputStream is = request.getInputStream();
            int count = 0;
            while (is.read() > -1) {
                count++;
            }

            pw.write(Integer.valueOf(count).toString());

            // Header should be visible now
            value = request.getHeader("x-trailer");
            if (value == null) {
                value = "null";
            }
            pw.write(value);
        }
    }
}
