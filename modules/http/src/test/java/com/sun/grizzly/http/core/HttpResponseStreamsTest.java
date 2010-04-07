/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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

package com.sun.grizzly.http.core;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainBuilder;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.http.HttpClientFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpFilter;
import com.sun.grizzly.http.HttpRequest;
import com.sun.grizzly.http.HttpResponse;
import com.sun.grizzly.http.HttpServerFilter;
import com.sun.grizzly.http.io.OutputBuffer;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.ByteBuffersBuffer;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.ChunkingFilter;
import junit.framework.TestCase;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpResponseStreamsTest extends TestCase {

    private static final int PORT = 8003;

    private static final char[] ALPHA = "abcdefghijklmnopqrstuvwxyz".toCharArray();


    // --------------------------------------------------------- Character Tests


    public void testCharacter001() throws Exception {

        final String[] content = new String[] {
              "abcdefg",
              "hijk",
              "lmnopqrs",
              "tuvwxyz"
        };

        StringBuilder sb = new StringBuilder();
        for (int i = 0, len = content.length; i < len; i++) {
            sb.append(content[i]);
        }

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = content.length; i < len; i++) {
                    writer.write(content[i]);
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter002() throws Exception {

        final char[][] content = new char[][] {
              "abcdefg".toCharArray(),
              "hijk".toCharArray(),
              "lmnopqrs".toCharArray(),
              "tuvwxyz".toCharArray()
        };

        StringBuilder sb = new StringBuilder();
        for (int i = 0, len = content.length; i < len; i++) {
            sb.append(content[i]);
        }

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = content.length; i < len; i++) {
                    writer.write(content[i]);
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter003() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary 

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

        s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter004() throws Exception {

        final StringBuilder sb = buildBuffer(8194); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

        s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter005() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    writer.write(sb.charAt(i));
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter006() throws Exception {

        final StringBuilder sb = buildBuffer(8194); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    writer.write(sb.charAt(i));
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter007() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter008() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter009() throws Exception {

        int len = 1024 * 31 + 8; // boundary + 8
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter010() throws Exception {

        final int len = 1024 * 33; 
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                // write in 3k chunks
                int off = 0;
                int writeLen = 1024 * 3;
                int count = len / writeLen;
                String s = sb.toString();
                for (int i = 0; i < count; i++) {
                    writer.write(s, off, writeLen);
                    off += writeLen;
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter011() throws Exception {

        final int len = 1024 * 33; 
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                // write in 3k chunks
                int off = 0;
                int writeLen = 1024 * 3;
                int count = len / writeLen;
                String s = sb.toString();
                for (int i = 0; i < count; i++) {
                    char[] buf = new char[writeLen];
                    s.getChars(off, off + writeLen, buf, 0);
                    writer.write(buf, 0, writeLen);
                    off += writeLen;
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());
    }


    public void testCharacter012() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                // write in 9k chunks
                int off = 0;
                int writeLen = 1024 * 9;
                int count = len / writeLen;
                String s = sb.toString();
                for (int i = 0; i < count; i++) {
                    writer.write(s, off, writeLen);
                    off += writeLen;
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testCharacter013() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                Writer writer = response.getWriter();
                // write in 9k chunks
                int off = 0;
                int writeLen = 1024 * 9;
                int count = len / writeLen;
                String s = sb.toString();
                for (int i = 0; i < count; i++) {
                    char[] buf = new char[writeLen];
                    s.getChars(off, off + writeLen, buf, 0);
                    writer.write(buf, 0, writeLen);
                    off += writeLen;
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());
    }


    // ------------------------------------------------------------ Binary Tests


    public void testBinary001() throws Exception {

        final byte[][] content = new byte[][] {
              "abcdefg".getBytes("UTF-8"),
              "hijk".getBytes("UTF-8"),
              "lmnopqrs".getBytes("UTF-8"),
              "tuvwxyz".getBytes("UTF-8")
        };

        StringBuilder sb = new StringBuilder();
        for (int i = 0, len = content.length; i < len; i++) {
            sb.append(new String(content[i], "UTF-8"));
        }

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = content.length; i < len; i++) {
                    out.write(content[i]);
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testBinary002() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    out.write(sb.charAt(i));
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testBinary003() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    out.write(sb.charAt(i));
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testBinary004() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                out.write(sb.toString().getBytes());
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testBinary005() throws Exception {

        int len = 1024 * 31 + 8; // boundary + 8
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                out.write(sb.toString().getBytes());
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testBinary006() throws Exception {

        final int len = 1024 * 33;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                // write in 3k chunks
                int off = 0;
                int writeLen = 1024 * 3;
                int count = len / writeLen;
                byte[] s = sb.toString().getBytes();
                for (int i = 0; i < count; i++) {
                    out.write(s, off, writeLen);
                    off += writeLen;
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    public void testBinary007() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(HttpResponse response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                // write in 9k chunks
                int off = 0;
                int writeLen = 1024 * 9;
                int count = len / writeLen;
                byte[] s = sb.toString().getBytes();
                for (int i = 0; i < count; i++) {
                    out.write(s, off, writeLen);
                    off += writeLen;
                }
            }
        };

        SimpleHttpResponseFilter f = new SimpleHttpResponseFilter(s);
        doTest(f, sb.toString());

    }


    // --------------------------------------------------------- Private Methods


    private StringBuilder buildBuffer(int len) {
        final StringBuilder sb = new StringBuilder(len);
        for (int i = 0, j = 0; i < len; i++, j++) {
            if (j > 25) {
                j = 0;
            }
            sb.append(ALPHA[j]);
        }
        return sb;
    }


    private void doTest(SimpleHttpResponseFilter filter,
                        String expectedResult)
    throws Exception {

        final FutureImpl<String> parseResult = FutureImpl.create();
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        filterChainBuilder.add(new TransportFilter());
        filterChainBuilder.add(new ChunkingFilter(2));
        filterChainBuilder.add(new HttpServerFilter());
        filterChainBuilder.add(filter);
        FilterChain filterChain = filterChainBuilder.build();

        TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();
        transport.setProcessor(filterChain);
        TCPNIOTransport ctransport = TransportFactory.getInstance().createTCPTransport();
        try {
            transport.bind(PORT);
            transport.start();

            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(2));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new ClientFilter(parseResult));
            ctransport.setProcessor(clientFilterChainBuilder.build());

            ctransport.start();

            Future<Connection> connectFuture = ctransport.connect("localhost", PORT);
            String res = null;
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                res = parseResult.get();
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }
            assertEquals(expectedResult, res);

        } finally {
            transport.stop();
            ctransport.stop();
            TransportFactory.getInstance().close();
        }
    }


    private interface WriteStrategy {

        void doWrite(HttpResponse response) throws IOException;

    }


    private static class SimpleHttpResponseFilter extends BaseFilter {

        private WriteStrategy strategy;

        public SimpleHttpResponseFilter(WriteStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws
              IOException {

            final HttpContent httpContent = (HttpContent) ctx.getMessage();

            if (!httpContent.isLast()) {
                // no action until we have all content
                return ctx.getStopAction();
            }

            HttpResponse response = HttpResponse.builder().chunked(true).
                  protocol(HttpFilter.HTTP_1_1).status(200).reasonPhrase("OK").build();
            response.setOutputBuffer(new OutputBuffer(response, ctx));

            strategy.doWrite(response);

            response.finish();

            return ctx.getStopAction();
        }
    }


    private static class ClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private ByteBuffersBuffer buf = ByteBuffersBuffer.create();

        private FutureImpl<String> completeFuture;

        // number of bytes downloaded
        private volatile int bytesDownloaded;


        // -------------------------------------------------------- Constructors


        public ClientFilter(FutureImpl<String> completeFuture) {

            this.completeFuture = completeFuture;

        }


        // ------------------------------------------------ Methods from Filters


        @Override
        public NextAction handleConnect(FilterChainContext ctx)
              throws IOException {
            // Build the HttpRequest, which will be sent to a server
            // We construct HTTP request version 1.1 and specifying the URL of the
            // resource we want to download
            final HttpRequest httpRequest = HttpRequest.builder().method("GET")
                  .uri("/path").protocol(HttpFilter.HTTP_1_1)
                  .header("Host", "localhost").build();
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                           "Connected... Sending the request: " + httpRequest);
            }

            // Write the request asynchronously
            ctx.write(httpRequest);

            // Return the stop action, which means we don't expect next filter to process
            // connect event
            return ctx.getStopAction();
        }


        @Override
        public NextAction handleRead(FilterChainContext ctx)
              throws IOException {
            try {
                // Cast message to a HttpContent
                final HttpContent httpContent = (HttpContent) ctx.getMessage();

                logger.log(Level.FINE, "Got HTTP response chunk");

                // Get HttpContent's Buffer
                final Buffer buffer = httpContent.getContent();

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                               "HTTP content size: " + buffer.remaining());
                }
                if (buffer.remaining() > 0) {
                    bytesDownloaded += buffer.remaining();

                    buf.append(buffer);

                }

                if (httpContent.isLast()) {
                    // it's last HttpContent - we close the local file and
                    // notify about download completion
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                                   "Response complete: "
                                   + bytesDownloaded
                                   + " bytes");
                    }
                    completeFuture.result(buf.toStringContent());
                    close();
                }
            } catch (IOException e) {
                close();
            }

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
              throws IOException {
            close();
            return ctx.getStopAction();
        }

        private void close() throws IOException {
           
            if (!completeFuture.isDone()) {
                //noinspection ThrowableInstanceNeverThrown
                completeFuture.failure(new IOException("Connection was closed"));
            }

        }
    }


}
