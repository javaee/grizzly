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

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.tcp.Request;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Test
public class FragmentationTest extends BaseWebSocketTest {
    private static final int PORT = 1726;

    public void fragment() throws IOException, InstantiationException, InterruptedException {
        final SelectorThread thread = createSelectorThread(PORT, new ServletAdapter());
        final WebSocketApplication app = new WebSocketApplication() {
            @Override
            public boolean isApplicationRequest(Request request) {
                return true;
            }

            @Override
            public void onFragment(WebSocket socket, boolean last, byte[] bytes) {
                socket.stream(last, bytes, 0, bytes.length);
            }
        };
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            WebSocketEngine.getEngine().register(app);

            final CountDownLatch latch = new CountDownLatch(1);
            WebSocketClient client = new WebSocketClient(String.format("ws://localhost:%s/echo", PORT), new WebSocketAdapter() {
                @Override
                public void onFragment(WebSocket socket, boolean last, byte[] bytes) {
                    try {
                        baos.write(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                    if(last) {
                        latch.countDown();
                    }
                }
            });

            StringBuilder sb = new StringBuilder();
            while (sb.length() < 1000) {
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
            final String text = sb.toString();
            final ByteArrayInputStream data = new ByteArrayInputStream(text.getBytes());

            final int size = 700;
            while(data.available() > 0) {
                byte[] bytes = new byte[Math.min(size, data.available())];
                data.read(bytes);
                client.stream(bytes.length < size, bytes, 0, bytes.length);
            }

            Assert.assertTrue(latch.await(60, TimeUnit.SECONDS));

            Assert.assertEquals(new String(baos.toByteArray()), text);
        } finally {
            thread.stopEndpoint();
            WebSocketEngine.getEngine().unregister(app);
        }
    }
}
