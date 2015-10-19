/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.servlet.FilterRegistration;
import org.glassfish.grizzly.servlet.HttpServerAbstractTest;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.utils.Futures;

/**
 * Basic Servlet 3.1 non-blocking output tests.
 */
public class AsyncOutputTest extends HttpServerAbstractTest {
    private static final Logger LOGGER = Grizzly.logger(AsyncOutputTest.class);
    
    public static final int PORT = 18890 + 18;

    public void testNonBlockingOutputByteByByte() throws Exception {
        System.out.println("testNonBlockingOutputByteByByte");
        try {
            final int MAX_TIME_MILLIS = 10 * 1000;
            final FutureImpl<Boolean> blockFuture =
                    Futures.createSafeFuture();
            
            newHttpServer(PORT);
            
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "foobar", "/servletPath/*", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

                    final AsyncContext asyncCtx = req.startAsync();
                    
                    ServletOutputStream output = res.getOutputStream();
                    WriteListenerImpl writeListener = new WriteListenerImpl(asyncCtx);
                    output.setWriteListener(writeListener);

                    output.write("START\n".getBytes());
                    output.flush();

                    long count = 0;
                    System.out.println("--> Begin for loop");
                    boolean prevCanWrite;
                    final long startTimeMillis = System.currentTimeMillis();

                    while ((prevCanWrite = output.isReady())) {
                        writeData(output);
                        count++;

                        if (System.currentTimeMillis() - startTimeMillis > MAX_TIME_MILLIS) {
                            System.out.println("Error: can't overload output buffer");
                            return;
                        }
                    }
                    
                    blockFuture.result(Boolean.TRUE);
                    System.out.println("--> prevCanWriite = " + prevCanWrite
                            + ", count = " + count);
                }
                
                void writeData(ServletOutputStream output) throws IOException {
                    output.write((byte)'a');
                }
                
            });
            
            ctx.deploy(httpServer);
            httpServer.start();
            
            HttpURLConnection conn = createConnection("/contextPath/servletPath/pathInfo", PORT);
            
            BufferedReader input = null;
            String line;
            boolean expected = false;
            try {
                input = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                int count = 0;
                boolean first = true;
                while ((line = input.readLine()) != null) {
                    expected = expected || line.endsWith("onWritePossible");
                    System.out.println("\n " + (count++) + ": " + line.length());
                    int length = line.length();
                    int lengthToPrint = 20;
                    int end = ((length > lengthToPrint) ? lengthToPrint : length);
                    System.out.print(line.substring(0, end) + "...");
                    if (length > 20) {
                        System.out.println(line.substring(length - 20));
                    }
                    System.out.println();
                    if (first) {
                        System.out.println("Waiting for server to run into async output mode...");
                        blockFuture.get(60, TimeUnit.SECONDS);
                        first = false;
                    }
                }
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch(Exception ex) {
                }
            }

       } finally {
            stopHttpServer();
        }
    }
    
    public void testNonBlockingOutput() throws Exception {
        System.out.println("testNonBlockingOutput");
        try {
            final int MAX_TIME_MILLIS = 10 * 1000;
            final FutureImpl<Boolean> blockFuture =
                    Futures.createSafeFuture();
            
            newHttpServer(PORT);
            
            WebappContext ctx = new WebappContext("Test", "/contextPath");
            addServlet(ctx, "foobar", "/servletPath/*", new HttpServlet() {

                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

                    final AsyncContext asyncCtx = req.startAsync();

                    ServletOutputStream output = res.getOutputStream();
                    WriteListenerImpl writeListener = new WriteListenerImpl(asyncCtx);
                    output.setWriteListener(writeListener);

                    output.write("START\n".getBytes());
                    output.flush();

                    long count = 0;
                    System.out.println("--> Begin for loop");
                    boolean prevCanWrite;
                    final long startTimeMillis = System.currentTimeMillis();

                    while ((prevCanWrite = output.isReady())) {
                        writeData(output, count, 1024);
                        count++;

                        if (System.currentTimeMillis() - startTimeMillis > MAX_TIME_MILLIS) {
                            System.out.println("Error: can't overload output buffer");
                            return;
                        }
                    }
                    
                    blockFuture.result(Boolean.TRUE);
                    System.out.println("--> prevCanWriite = " + prevCanWrite
                            + ", count = " + count);
                }
                
                void writeData(ServletOutputStream output, long count, int len) throws IOException {
//                    System.out.println("--> calling writeData " + count);
                    char[] cs = String.valueOf(count).toCharArray();
                    byte[] b = new byte[len];
                    for (int i = 0; i < cs.length; i++) {
                        b[i] = (byte) cs[i];
                    }
                    Arrays.fill(b, cs.length, len, (byte) 'a');
                    output.write(b);
                }
                
            });
            
            ctx.deploy(httpServer);
            httpServer.start();
            
            HttpURLConnection conn = createConnection("/contextPath/servletPath/pathInfo", PORT);
            
            BufferedReader input = null;
            String line;
            boolean expected = false;
            try {
                input = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                int count = 0;
                boolean first = true;
                while ((line = input.readLine()) != null) {
                    expected = expected || line.endsWith("onWritePossible");
                    System.out.println("\n " + (count++) + ": " + line.length());
                    int length = line.length();
                    int lengthToPrint = 20;
                    int end = ((length > lengthToPrint) ? lengthToPrint : length);
                    System.out.print(line.substring(0, end) + "...");
                    if (length > 20) {
                        System.out.println(line.substring(length - 20));
                    }
                    System.out.println();
                    if (first) {
                        System.out.println("Waiting for server to run into async output mode...");
                        blockFuture.get(60, TimeUnit.SECONDS);
                        first = false;
                    }
                }
            } finally {
                try {
                    if (input != null) {
                        input.close();
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
                EnumSet.of(DispatcherType.REQUEST),
                alias);

        return reg;
    }
    
    static class WriteListenerImpl implements WriteListener {
        private final AsyncContext asyncCtx;

        private WriteListenerImpl(AsyncContext asyncCtx) {
            this.asyncCtx = asyncCtx;
        }

        @Override
        public void onWritePossible() {
            try {
                final ServletOutputStream output = asyncCtx.getResponse().getOutputStream();
                
                String message = "onWritePossible";
                System.out.println("--> " + message);
                output.write(message.getBytes());
                asyncCtx.complete();
            } catch (Throwable t) {
                onError(t);
            }
        }

        @Override
        public void onError(final Throwable t) {
            LOGGER.log(Level.WARNING, "Unexpected error", t);
            asyncCtx.complete();
        }
    }
}
