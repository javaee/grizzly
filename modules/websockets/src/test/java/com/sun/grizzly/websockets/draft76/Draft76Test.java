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

package com.sun.grizzly.websockets.draft76;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.websockets.BaseWebSocketTestUtilities;
import com.sun.grizzly.websockets.EchoServlet;
import com.sun.grizzly.websockets.WebSocketApplication;
import com.sun.grizzly.websockets.WebSocketEngine;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Draft76Test extends BaseWebSocketTestUtilities {
    @Test
    public void sampleHandShake() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        final WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
                return request.requestURI().equals("/demo");
            }

            @Override
            public List<String> getSupportedProtocols(List<String> subProtocols) {
                final List<String> list = new ArrayList<String>();
                if (subProtocols.contains("sample")) {
                    list.add("sample");
                }
                return list;
            }
        };
        WebSocketEngine.getEngine().register(app);
        Socket socket = new Socket("localhost", PORT);
        try {
            final OutputStream outputStream = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(outputStream);
            writer.write("GET /demo HTTP/1.1\n" +
                    "Host: example.com\n" +
                    "Connection: Upgrade\n" +
                    "Sec-WebSocket-Key2: 12998 5 Y3 1  .P00\n" +
                    "Sec-WebSocket-Protocol: sample\n" +
                    "Upgrade: WebSocket\n" +
                    "Sec-WebSocket-Key1: 4 @1  46546xW%0l 1 5\n" +
                    "Origin: http://example.com\n" +
                    "\n" +
                    "^n:ds[4U\n\n");
            writer.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            int count = 0;
            while (!"".equals(line = reader.readLine())) {
                builder.append(line + "\n");
            }
            char[] key = new char[16];
            reader.read(key);
            Assert.assertEquals("HTTP/1.1 101 WebSocket Protocol Handshake\n" +
                    "Upgrade: WebSocket\n" +
                    "Connection: Upgrade\n" +
                    "Sec-WebSocket-Location: ws://example.com/demo\n" +
                    "Sec-WebSocket-Origin: http://example.com\n" +
                    "Sec-WebSocket-Protocol: sample\n" +
                    "\n" +
                    "8jKS'y:G*Co,Wxa-", builder.toString() + "\n" + new String(key));
        } finally {
            if (socket != null) {
                socket.close();
            }
            thread.stopEndpoint();
            WebSocketEngine.getEngine().unregister(app);
        }
    }

}
