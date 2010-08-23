/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.cometd;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.comet.CometAsyncFilter;
import com.sun.grizzly.cometd.standalone.CometdAdapter;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.util.ExtendedThreadPool;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.logging.Logger;

import com.sun.grizzly.util.Utils;
import junit.framework.TestCase;

/**
 * Issue 243
 * @author Jeanfrancois Arcand
 */
public class InvalidRequestTest extends TestCase {
    public static final int PORT = 18890;
    private String host = "localhost";

    private static Logger logger = Logger.getLogger("grizzly.test");

    private SelectorThread st;

        /*
     * @see TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception{
        st = new SelectorThread();
        st.setPort(PORT);
        st.setAdapter(new CometdAdapter());
        st.initThreadPool();
        ((ExtendedThreadPool) st.getThreadPool()).setMaximumPoolSize(5);
        st.setEnableAsyncExecution(Utils.VERBOSE_TESTS);
        st.setBufferResponse(false);    
        st.setFileCacheIsEnabled(false);
        st.setLargeFileCacheEnabled(false);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter()); 
        st.setAsyncHandler(asyncHandler);
        st.setAdapter(new CometdAdapter());
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        
        try {
            st.listen();
        } catch (Exception ex) {
            ex.printStackTrace();
        }        
    }
    
    
    public void testInvalidBayeuxRequest() throws IOException {        
        try{
            Socket socket = new Socket(host, PORT);
            socket.setSoTimeout(30 * 1000);
            DataOutputStream os = new DataOutputStream(socket.getOutputStream());

            StringBuilder sb = new StringBuilder();
            sb.append("POST /index.html/cometd HTTP/1.1\r\n");
            sb.append("Host: localhost\r\n");
            sb.append("Content-type: text/json;charset=utf-8\r\n");
            sb.append("Content-Length: 109\r\n\r\n");
            sb.append("[{\"channel\": \"/meta/handshake\", \"clientId\": \"f81ba786e99809b0\", \"connectionType\": \"long-polling\", \"id\": \"1\"}]\r\n\r\n");
            os.write(sb.toString().getBytes());
            os.flush();

            InputStream is = socket.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line = null;

            while ((line = bis.readLine()) != null) {
                if (line.contains("false") && line.contains("error") && line.contains("501")) {
                    assertTrue(true);
                    return;
                }
            }  
        } finally {
            st.stopEndpoint();
        }
        fail("Wrong header response");
    }

    
    
    public void testConnectWithoutHandshake() throws IOException {
        
        Socket socket = new Socket(host, PORT);
        socket.setSoTimeout(30 * 1000);
        DataOutputStream os = new DataOutputStream(socket.getOutputStream());
        
        StringBuilder sb = new StringBuilder();
        sb.append("POST /index.html/cometd HTTP/1.1\r\n");
        sb.append("Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n");
        sb.append("Host: localhost\r\n");
        sb.append("Content-Length: 187\r\n\r\n");
        sb.append("message=%5B%7B%22channel%22%3A%20%22%2Fmeta%2Fconnect%22%2C%20%22clientId%22%3A%20%22f81ba786e99809b0%22%2C%20%22connectionType%22%3A%20%22long-polling%22%2C%20%22id%22%3A%20%221%22%7D%5D\r\n\r\n");
        os.write(sb.toString().getBytes());
        os.flush();
        
        InputStream is = socket.getInputStream();
        BufferedReader bis = new BufferedReader(new InputStreamReader(is));
        String line = null;

        int i=0;
        while ((line = bis.readLine()) != null) {
            Utils.dumpOut(i++ + ": " + line);
            if (line.contains("402::")){
                assertTrue(true);
                return;
            }
        }  
        fail("Wrong header response");
    }

}
