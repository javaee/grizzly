/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.servlet.async;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.ReadListener;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.servlet.FilterRegistration;
import org.glassfish.grizzly.servlet.HttpServerAbstractTest;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;

/**
 * Basic Servlet 3.1 non-blocking input tests.
 */
public class AsyncInputTest extends HttpServerAbstractTest {
    private static final Logger LOGGER = Grizzly.logger(AsyncInputTest.class);
    
    public static final int PORT = 18890 + 17;

    public void testNonBlockingInput() throws IOException {
        System.out.println("testNonBlockingInput");
        try {
            newHttpServer(PORT);
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "foobar", "/servletPath/*", new HttpServlet() {

                @Override
                protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
                    ServletOutputStream output = res.getOutputStream();
                    ServletInputStream input = req.getInputStream();
                    
                    final AsyncContext asyncCtx = req.startAsync();
                    
                    final byte[] buffer = new byte[1024];
                    
                    ReadListener readListener = new ReadListenerImpl(asyncCtx, buffer);
                    input.setReadListener(readListener);
                    
                    int len = -1;
                    while (input.isReady() && ((len = input.read(buffer)) != -1)) {
                        output.write(buffer, 0, len);
                    }
                }
            });
            
            ctx.deploy(httpServer);
            httpServer.start();
            
            HttpURLConnection conn = createConnection("/contextPath/servletPath/pathInfo", PORT);
            conn.setChunkedStreamingMode(5);
            conn.setDoOutput(true);
            conn.connect();

            BufferedReader input = null;
            BufferedWriter output = null;
            boolean expected;
            try {
                output = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream()));
                try {
                    String data = "Hello";
                    output.write(data);
                    output.flush();
                    int sleepInSeconds = 3;
                    System.out.println("Sleeping " + sleepInSeconds + " seconds");
                    Thread.sleep(sleepInSeconds * 1000);
                    data = "World";
                    output.write(data);
                    output.flush();
                    output.close();
                } catch(Exception ex) {
                }
                input = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                
                String line;
                while ((line = input.readLine()) != null) {
                    System.out.println(line);
                    expected = line.endsWith("-onAllDataRead");
                    if (expected) {
                        break;
                    }
                }
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch(Exception ex) {
                }

                try {
                    if (output != null) {
                        output.close();
                    }
                } catch(Exception ex) {
                }
            }
        } finally {
            stopHttpServer();
        }
    }
    
    private ServletRegistration addServlet(final WebappContext ctx,
            final String name,
            final String alias,
            Servlet servlet
            ) {
        
        final ServletRegistration reg = ctx.addServlet(name, servlet);
        reg.addMapping(alias);

        return reg;
    }
    
    private FilterRegistration addFilter(final WebappContext ctx,
            final String name,
            final String alias,
            final Filter filter
            ) {
        
        final FilterRegistration reg = ctx.addFilter(name, filter);
        reg.addMappingForUrlPatterns(
                EnumSet.<DispatcherType>of(DispatcherType.REQUEST),
                alias);

        return reg;
    }
    
    private static class ReadListenerImpl implements ReadListener {
        private final AsyncContext asyncCtx;
        private final byte[] buffer;
        
        private ReadListenerImpl(AsyncContext asyncCtx, byte[] buffer) {
            this.asyncCtx = asyncCtx;
            this.buffer = buffer;
        }

        @Override
        public void onDataAvailable() {
            try {
                ServletInputStream input = asyncCtx.getRequest().getInputStream();
                ServletOutputStream output = asyncCtx.getResponse().getOutputStream();

                output.print("onDataAvailable-");
                int len = -1;
                while (input.isReady() && ((len = input.read(buffer)) != -1)) {
                    output.write(buffer, 0, len);
                }
            } catch (Throwable t) {
                onError(t);
            }
        }

        @Override
        public void onAllDataRead() {
            try {
                ServletInputStream input = asyncCtx.getRequest().getInputStream();
                ServletOutputStream output = asyncCtx.getResponse().getOutputStream();

                output.println("-onAllDataRead");
                
                asyncCtx.complete();
            } catch (Throwable t) {
                onError(t);
            }
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.log(Level.WARNING, "Unexpected error", t);
            asyncCtx.complete();
        }
    }
}
