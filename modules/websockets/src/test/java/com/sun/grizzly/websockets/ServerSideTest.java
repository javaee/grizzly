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

package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.util.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"StringContatenationInLoop"})
@Test
public class ServerSideTest {
    private static final int PORT = 1726;

    public static final int ITERATIONS = 1000;

    public void steadyFlow() throws IOException, InstantiationException, ExecutionException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        TrackingWebSocket socket = new TrackingWebSocket(String.format("ws://localhost:%s/echo", PORT), 5 * ITERATIONS);
        try {
            int count = 0;
            final Date start = new Date();
            while (count++ < ITERATIONS) {
                if (count % 100 == 0) {
                    System.out.printf("Running iteration %s of %s\n", count, ITERATIONS);
                }
                socket.send("test message: " + count);
                socket.send("let's try again: " + count);
                socket.send("3rd time's the charm!: " + count);
                socket.send("ok.  just one more: " + count);
                socket.send("now, we're done: " + count);
            }

            Assert.assertTrue(socket.waitOnMessages(), "All messages should come back: " + socket.getReceived());
            time("ServerSideTest.synchronous", start, new Date());

        } finally {
            if (socket != null) {
                socket.close();
            }
            thread.stopEndpoint();
        }
    }

    @SuppressWarnings({"StringContatenationInLoop"})
    public void sendAndWait() throws IOException, InstantiationException, InterruptedException, ExecutionException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        CountDownWebSocket socket = new CountDownWebSocket(String.format("ws://localhost:%s/echo", PORT));

        try {
            int count = 0;
            final Date start = new Date();
            while (count++ < ITERATIONS) {
                if (count % 100 == 0) {
                    System.out.printf("Running iteration %s of %s\n", count, ITERATIONS);
                }
                socket.send("test message " + count);
                socket.send("let's try again: " + count);
                socket.send("3rd time's the charm!: " + count);
                Assert.assertTrue(socket.countDown(), "Everything should come back");
                socket.send("ok.  just one more: " + count);
                socket.send("now, we're done: " + count);
                Assert.assertTrue(socket.countDown(), "Everything should come back");
            }
            time("ServerSideTest.asynchronous", start, new Date());
        } finally {
            if (socket != null) {
                socket.close();
            }
            thread.stopEndpoint();
        }
    }

    @Test
    public void multipleClients() throws IOException, InstantiationException, ExecutionException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));

        List<TrackingWebSocket> clients = new ArrayList<TrackingWebSocket>();
        try {
            final String address = String.format("ws://localhost:%s/echo", PORT);
            for (int x = 0; x < 5; x++) {
                clients.add(new TrackingWebSocket(address, x + "", 5 * ITERATIONS));
            }
            String[] messages = {
                    "test message",
                    "let's try again",
                    "3rd time's the charm!",
                    "ok.  just one more",
                    "now, we're done"
            };
            for (int count = 0; count < ITERATIONS; count++) {
                for (String message : messages) {
                    for (TrackingWebSocket socket : clients) {
                        socket.send(String.format("%s: count %s: %s", socket.getName(), count, message));
                    }
                }
            }
            for (TrackingWebSocket socket : clients) {
                Assert.assertTrue(socket.waitOnMessages(), "All messages should come back: " + socket.getReceived());
            }
        } finally {
            thread.stopEndpoint();
        }
    }

    @Test
    public void bigPayload() throws IOException, InstantiationException, ExecutionException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        final int count = 5;
        final CountDownLatch received = new CountDownLatch(count);
        ClientWebSocket socket = new ClientWebSocket(String.format("ws://localhost:%s/echo", PORT)) {
            @Override
            public void onMessage(DataFrame frame) {
                received.countDown();
            }
        };

        try {
            StringBuilder sb = new StringBuilder();
            while (sb.length() < 10000) {
                sb.append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus quis lectus odio, et" +
                        " dictum purus. Suspendisse id ante ac tortor facilisis porta. Nullam aliquet dapibus dui, ut" +
                        " scelerisque diam luctus sit amet. Donec faucibus aliquet massa, eget iaculis velit ullamcorper" +
                        " eu. Fusce quis condimentum magna. Vivamus eu feugiat mi. Cras varius convallis gravida. Vivamus" +
                        " et elit lectus. Aliquam egestas, erat sed dapibus dictum, sem ligula suscipit mauris, a" +
                        " consectetur massa augue vel est. Nam bibendum varius lobortis. In tincidunt, sapien quis" +
                        " hendrerit vestibulum, lorem turpis faucibus enim, non rhoncus nisi diam non neque. Aliquam eu" +
                        " urna urna, molestie aliquam sapien. Nullam volutpat, erat condimentum interdum viverra, tortor" +
                        " lacus venenatis neque, vitae mattis sem felis pellentesque quam. Nullam sodales vestibulum" +
                        " ligula vitae porta. Aenean ultrices, ligula quis dapibus sodales, nulla risus sagittis sapien," +
                        " id posuere turpis lectus ac sapien. Pellentesque sed ante nisi. Quisque eget posuere sapien.");
            }
            final String data = sb.toString();
            for (int x = 0; x < count; x++) {
                socket.send(data);
            }
            Assert.assertTrue(received.await(300, TimeUnit.SECONDS), "Message should come back");
        } finally {
            if (socket != null) {
                socket.close();
            }
            thread.stopEndpoint();
        }

    }

    private void time(String method, Date start, Date end) {
        final int total = 5 * ITERATIONS;
        final double time = (end.getTime() - start.getTime()) / 1000.0;
        System.out.printf("%s: sent %s messages in %.3fs for %.3f msg/s and %.4f s/msg\n", method, total, time,
                total / time, time / total);
    }

    private SelectorThread createSelectorThread(final int port, final Adapter adapter)
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

}
