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

package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.Utils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SimpleWebSocketServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    public static final String RESPONSE_TEXT = "Nothing to see";
    private WebSocketApplication app;

    public SimpleWebSocketServlet() {
        app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
                return request.requestURI().equals("/simple");
            }

            @Override
            public void onMessage(WebSocket socket, String data) {
                System.out.println("message received: " + data);
            }

            @Override
            public void onClose(WebSocket socket, DataFrame frame) {
                System.out.println("socket: " + socket + " closed");
            }
        };
        WebSocketEngine.getEngine().register(app);
    }

    @Override
    public void destroy() {
        WebSocketEngine.getEngine().unregister(app);
        super.destroy();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain; charset=iso-8859-1");
        resp.getWriter().write(RESPONSE_TEXT);
        resp.getWriter().flush();
    }

    private static SelectorThread createSelectorThread(final int port, final Adapter adapter)
            throws IOException, InstantiationException {
        SelectorThread st = new SelectorThread();

        st.setSsBackLog(8192);
        st.setCoreThreads(2);
        st.setMaxThreads(2);
        st.setPort(port);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        st.setAdapter(adapter);
        st.setAsyncHandler(new DefaultAsyncHandler());
        st.setEnableAsyncExecution(true);
        st.getAsyncHandler().addAsyncFilter(new WebSocketAsyncFilter());
        st.setTcpNoDelay(true);
        st.listen();

        return st;
    }

    public static void main(String... args) throws IOException, InstantiationException, InterruptedException {
        createSelectorThread(8051, new ServletAdapter(new SimpleWebSocketServlet()));

        final CountDownLatch latch = new CountDownLatch(1);
        WebSocketClient ws = new WebSocketClient("ws://localhost:8051/simple", new WebSocketAdapter() {
            @Override
            public void onConnect(WebSocket ws) {
                super.onConnect(ws);
                latch.countDown();
            }
        });
        latch.await(10, TimeUnit.SECONDS);
        
        ws.send("message send on socket open " + new Date());
        ws.send("message 2 " + new Date());
        ws.close();
    }
}
