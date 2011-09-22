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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Test simple Ajp communication usecases.
 *
 * @author Alexey Stashok
 * @author Justin Lee
 */
public class BasicAjpTest extends AjpTestBase {

    @Test
    public void testStaticRequests() throws IOException, InstantiationException {
        configureHttpServer(new StaticResourcesAdapter("src/test/resources"));

        final String[] files = {/*"/ajpindex.html", */"/ajplarge.html"};
        for (String file : files) {
            try {
                requestFile(file);
            } catch (Exception e) {
                throw new RuntimeException("Testing file " + file + ": " + e.getMessage(), e);
            }
        }
    }

    @Test
    public void testDynamicRequests() throws IOException, InstantiationException {
        final String message = "Test Message";
        final StringBuilder builder = new StringBuilder();
        while (builder.length() < 10000) {
            builder.append(message);
        }
        for (String test : new String[]{message, builder.toString()}) {
            dynamicRequest(test);
        }
    }

    private void requestFile(String file) throws IOException {
        AjpForwardRequestPacket forward = new AjpForwardRequestPacket("GET", file, PORT, 0);
        send(forward.toBuffer());
/*
        boolean wait = true;
        while (wait) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
*/
        List<AjpResponse> responses = AjpMessageUtils.parseResponse(readResponses());

        final Iterator<AjpResponse> iterator = responses.iterator();
        AjpResponse next = iterator.next();
        Assert.assertEquals("Testing file " + file, 200, next.getResponseCode());
        Assert.assertEquals("Testing file " + file, "OK", next.getResponseMessage());

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        do {
            next = iterator.next();
            if (next.getType() == AjpConstants.JK_AJP13_SEND_BODY_CHUNK) {
                stream.write(next.getBody());
            }
        } while (next.getType() == AjpConstants.JK_AJP13_SEND_BODY_CHUNK);
        Assert.assertArrayEquals("Testing file " + file, readFile("src/test/resources" + file), stream.toByteArray());

        Assert.assertEquals("Testing file " + file, AjpConstants.JK_AJP13_END_RESPONSE, next.getType());
    }

    private void dynamicRequest(final String message) throws IOException, InstantiationException {
        try {
            configureHttpServer(new GrizzlyAdapter() {
                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                    response.setContentLength(message.length());
                    response.setContentType("text");
                    response.setLocale(Locale.US);
                    for (int i = 1; i <= 10; i++) {
                        response.addHeader("grizzly", "request" + i);
                    }

                    response.getOutputBuffer().write(message);
                }
            });
            AjpForwardRequestPacket forward = new AjpForwardRequestPacket("GET", "/bob", PORT, 0);
            send(forward.toBuffer());
            List<AjpResponse> responses = AjpMessageUtils.parseResponse(readResponses());

            final Iterator<AjpResponse> iterator = responses.iterator();
            AjpResponse next = iterator.next();
            Assert.assertEquals(200, next.getResponseCode());
            Assert.assertEquals("OK", next.getResponseMessage());
            Assert.assertEquals("Should get all the grizzly headers back", 10,
                    Collections.list(next.getHeaders().values("grizzly")).size());

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            do {
                next = iterator.next();
                if (next.getType() == AjpConstants.JK_AJP13_SEND_BODY_CHUNK) {
                    stream.write(next.getBody());
                }
            } while (next.getType() == AjpConstants.JK_AJP13_SEND_BODY_CHUNK);
            Assert.assertEquals(message, new String(stream.toByteArray()));

            Assert.assertEquals(AjpConstants.JK_AJP13_END_RESPONSE, next.getType());
        } finally {
            after();
        }
    }

    public void testPingPong() throws Exception {
        configureHttpServer(new StaticResourcesAdapter("src/test/resources"));
        final ByteBuffer request = ByteBuffer.allocate(12);
        request.put((byte) 0x12);
        request.put((byte) 0x34);
        request.putShort((short) 1);
        request.put(AjpConstants.JK_AJP13_CPING_REQUEST);
        request.flip();

        send(request);
        final ByteBuffer response = readResponses();
        Assert.assertEquals((byte) 'A', response.get());
        Assert.assertEquals((byte) 'B', response.get());
        Assert.assertEquals((short) 1, response.getShort());
        Assert.assertEquals(AjpConstants.JK_AJP13_CPONG_REPLY, response.get());
    }

    public static void main(String[] args) throws Exception {
        BasicAjpTest test = new BasicAjpTest();
        test.configureHttpServer(new StaticResourcesAdapter("src/test/resources"));
    }
}
