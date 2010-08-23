/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.httpclient;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import static com.sun.grizzly.httpclient.HttpMethod.GET;
import static com.sun.grizzly.httpclient.HttpProtocolVersion.HTTP_1_1;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Grizzly Http Client GET tests
 *
 * @author Hubert Iwaniuk
 * @since May 22, 2009
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class HttpClientGetTest {
    private GrizzlyWebServer gws;

    @Before public void before() throws IOException {
        gws = new GrizzlyWebServer(8080);
        gws.addGrizzlyAdapter(
            new GrizzlyAdapter() {
                public void service(
                    final GrizzlyRequest request,
                    final GrizzlyResponse response) {
                    try {
                        response.getWriter().write("Alright");
                        response.getWriter().flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, new String[]{"/1"});
        gws.start();
    }

    @After public void after() {
        gws.stop();
    }

    @Test public void testSimpleGet() throws Exception {

        GrizzlyHttpClient client = new GrizzlyHttpClient();

        try {
            client.start();

            final HttpClientRequest clientRequest = new HttpClientRequest(
                GET, "http://localhost:8080/1", HTTP_1_1);
            final Future<HttpClientResponse> resp = client.call(clientRequest);

            assertNotNull("Future returned by GrizzlyHttpClient.call should not be null.", resp);
            final HttpClientResponse response = resp.get(1, TimeUnit.SECONDS);
            assertNotNull("Response got from Future should not be null.", response);
        } finally {
            client.stop();
        }
    }
}
