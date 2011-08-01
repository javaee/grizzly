/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"StringContatenationInLoop"})
@RunWith(Parameterized.class)
public class ServerSideTest extends BaseWebSocketTestUtilities {

    public static final int ITERATIONS = 50;
    private final Version version;

    public ServerSideTest(Version version) {
        this.version = version;
    }

    @Test
    public void steadyFlow() throws IOException, InstantiationException, ExecutionException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        TrackingWebSocket socket = null;
        try {
            socket = new TrackingWebSocket(version, String.format("ws://localhost:%s/echo", PORT), 5 * ITERATIONS);
            socket.connect();
            int count = 0;
            final Date start = new Date();
            final int marker = ITERATIONS / 5;
            while (count++ < ITERATIONS) {
/*
                if (count % marker == 0) {
                    System.out.printf("Running iteration %s of %s\n", count, ITERATIONS);
                }
*/
                socket.send("test message: " + count);
                socket.send("let's try again: " + count);
                socket.send("3rd time's the charm!: " + count);
                socket.send("ok.  just one more: " + count);
                socket.send("now, we're done: " + count);
            }

            Assert.assertTrue("All messages should come back: " + socket.getReceived(), socket.waitOnMessages());
//            time("ServerSideTest.steadyFlow (" + version + ")", start, new Date());

        } finally {
            if (socket != null) {
                socket.close();
            }
            thread.stopEndpoint();
        }
    }

    @Test
    public void single() throws IOException, InstantiationException, ExecutionException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        TrackingWebSocket socket = new TrackingWebSocket(version, String.format("ws://localhost:%s/echo", PORT), 1);
        socket.connect();
        try {
            int count = 0;
            final Date start = new Date();
            socket.send("test message: " + count);

            Assert.assertTrue("All messages should come back: " + socket.getReceived(), socket.waitOnMessages());
        } finally {
            if (socket != null) {
                socket.close();
            }
            thread.stopEndpoint();
        }
    }

    @SuppressWarnings({"StringContatenationInLoop"})
    @Test
    public void sendAndWait() throws IOException, InstantiationException, InterruptedException, ExecutionException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter(new EchoServlet()));
        CountDownWebSocket socket = new CountDownWebSocket(version, String.format("ws://localhost:%s/echo", PORT));
        socket.connect();

        try {
            int count = 0;
            final Date start = new Date();
            while (count++ < ITERATIONS) {
/*
                if (count % ITERATIONS / 5 == 0) {
                    System.out.printf("Running iteration %s of %s\n", count, ITERATIONS);
                }
*/
                socket.send("test message " + count);
                socket.send("let's try again: " + count);
                socket.send("3rd time's the charm!: " + count);
                Assert.assertTrue("Everything should come back", socket.countDown());
                socket.send("ok.  just one more: " + count);
                socket.send("now, we're done: " + count);
                Assert.assertTrue("Everything should come back", socket.countDown());
            }
//            time("ServerSideTest.sendAndWait (" + version + ")", start, new Date());
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
                final TrackingWebSocket socket = new TrackingWebSocket(version, address, x + "", 5 * ITERATIONS);
                socket.connect();
                clients.add(socket);
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
                Assert.assertTrue("All messages should come back: " + socket.getReceived(), socket.waitOnMessages());
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
        WebSocketClient socket = new WebSocketClient(String.format("ws://localhost:%s/echo", PORT), version) {
            @Override
            public void onMessage(String frame) {
                received.countDown();
            }
        };
        socket.connect();

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
            Assert.assertTrue("Message should come back", received.await(60, TimeUnit.SECONDS));
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
        System.out.printf("%s: sent %s messages in %.3fs for %.3f msg/s\n", method, total, time, total / time);
    }

}
