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

import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.SocketException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
/**
 * Tests server behavior when ErrorHandler throws NPE
 * 
 * @author Alexey Stashok
 */
public class ErrorHandlerTest {
    public static final int PORT = 18890;
    
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
    public void testNPEThrownFromErrorHandler() throws Exception {
        st.setCoreThreads(5);
        st.setMaxThreads(5);
        st.setErrorHandler(new ErrorHandler() {

            public void onParsingError(Response response) {
                throw new NullPointerException("test NPE");
            }
        });
        
        setAdapterAndListen(new GrizzlyAdapter() {

            @Override
            public void service(GrizzlyRequest request,
                    GrizzlyResponse response) throws Exception {
            }
        });
        
        final String expectedErrorReply = "HTTP/1.1 400 Bad Request";
        final String expectedOkReply = "HTTP/1.1 200 OK";
        
        for (int i = 0; i < 1000; i++) {
            assertEquals(expectedErrorReply, sendBadRequest());
        }
        
        final String reply = sendGoodRequest();
        assertEquals(expectedOkReply, reply);
    }
    
    private String sendBadRequest() throws IOException {
        return sendRequest(false);
    }

    private String sendGoodRequest() throws IOException {
        return sendRequest(true);
    }

    private String sendRequest(final boolean isSendHostHeader) throws SocketException, IOException {
        Socket s = new Socket("localhost", PORT);
        s.setSoTimeout(5000);
        
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
        pw.println("GET / HTTP/1.1");
        if (isSendHostHeader) {
            pw.println("Host: localhost:" + PORT);
        }
        pw.println("Connection: close");
        pw.println();
        pw.flush();

        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        String statusLine = br.readLine();
        while(br.readLine() != null);
        
        return statusLine;
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
    
}
