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

package com.sun.grizzly.http;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.buf.ByteChunk;
import junit.framework.TestCase;
import org.junit.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GWSLoadTest extends TestCase {
    private static final Logger logger = Logger.getLogger("grizzly");
    public static final int CLIENT_NUM = 2;
    private static final AtomicInteger done = new AtomicInteger(CLIENT_NUM);
    private static Exception exception;
    private static int PORT = 6667;

    public void testLoadAsync() throws Throwable {

        DefaultThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 1000 * 60 * 5;
        GrizzlyWebServer gws = new GrizzlyWebServer(PORT, "", false);

        final SelectorThread thread = gws.getSelectorThread();
        thread.setCompression("on");
//        thread.setSendBufferSize(1024 * 1024);
        gws.addGrizzlyAdapter(new LoadTestAdapter(), new String[]{"/"});
        done.compareAndSet(CLIENT_NUM, CLIENT_NUM);
        try {
            gws.start();
            for (int index = 0; index < CLIENT_NUM; index++) {
                new Thread(new Client()).start();
            }
            while (/*true || */done.get() > 0 && exception == null) {
                Thread.sleep(100);
            }
            if (exception != null) {
                logger.log(Level.INFO, exception.getMessage(), exception);
            }
            Assert.assertSame("Shouldn't get exception on server", null, exception);
        } finally {
            gws.stop();
        }
    }

    public void testLoadSynchronous() throws Throwable {

        DefaultThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 1000 * 60 * 5;
        GrizzlyWebServer gws = new GrizzlyWebServer(PORT, "", false);

        final SelectorThread thread = gws.getSelectorThread();
        thread.setCompression("on");
//        thread.setSendBufferSize(1024 * 1024);
        gws.addGrizzlyAdapter(new LoadTestAdapter(), new String[]{"/"});
        try {
            gws.start();
            for (int index = 0; index < CLIENT_NUM; index++) {
                new Client().run();
            }
            if (exception != null) {
                logger.log(Level.INFO, exception.getMessage(), exception);
            }
            Assert.assertSame("Shouldn't get exception on server", null, exception);
        } finally {
            gws.stop();
        }
    }

    private static class LoadTestAdapter extends GrizzlyAdapter {
        private final int len;
        private final ByteChunk chunk;

        public LoadTestAdapter() throws UnsupportedEncodingException {
            StringBuilder text = new StringBuilder(new Date() + " ");
            for (int index = 0; index < 1000; index++) {
                text.append("0123456789");
            }
            byte b[] = text.toString().getBytes("UTF-8");
            chunk = new ByteChunk();
            len = b.length;
            chunk.setBytes(b, 0, len);
        }

        @Override
        public void service(GrizzlyRequest grizzlyRequest, GrizzlyResponse grizzlyResponse) {
            grizzlyResponse.setContentType("text/html");
//                grizzlyResponse.setStatus(500);
            try {
                grizzlyResponse.setCharacterEncoding("UTF-8");
                grizzlyResponse.setContentLength(len);
                grizzlyResponse.getResponse().doWrite(chunk);
            } catch (IOException e) {
                exception = e;
                done.getAndSet(0);
                System.out.println("GWSLoadTest$LoadTestAdapter.service");
            }
        }

    }

    private class Client implements Runnable {
        public void run() {
            try {
                Socket socket = new Socket("localhost", PORT);
                try {
                    final OutputStream out = socket.getOutputStream();
                    out.write("GET / HTTP/1.1\n".getBytes());
                    out.write(("Host: localhost:\n" + PORT).getBytes());
                    out.write("accept-encoding: gzip\n".getBytes());
                    out.write("Connection: close\n".getBytes());
                    out.write("\n".getBytes());
                    out.flush();
                    final InputStream stream = socket.getInputStream();
                    final byte[] b = new byte[1024];
                    int read;
                    while ((read = stream.read(b)) != -1) {
                        final String s = new String(b, 0, read).trim();
//                        System.out.println("GWSLoadTest$Client.run: s = " + s.substring(0, 100));
                        Assert.assertFalse("".equals(s));
//                        Thread.sleep(50);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    done.getAndSet(0);
                    Assert.fail(e.getMessage());
                } finally {
                    socket.close();
                }
            } catch (IOException e) {
                done.getAndSet(0);
                exception = e;
            } finally {
                final int i = done.decrementAndGet();
            }
        }
    }

    private static void main(String[] args) throws IOException, InterruptedException {

        DefaultThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 1000 * 60 * 5;
        GrizzlyWebServer gws = new GrizzlyWebServer(PORT, "", false);

        final SelectorThread thread = gws.getSelectorThread();
        thread.setCompression("on");
//        thread.setSendBufferSize(1024 * 1024);
        gws.addGrizzlyAdapter(new LoadTestAdapter(), new String[]{"/"});
        gws.start();
        System.out.println("Listening on port " + PORT);
        while(true) {
            Thread.sleep(1000);
            if(exception != null) {
                exception.printStackTrace();
            }
        }
    }
}
