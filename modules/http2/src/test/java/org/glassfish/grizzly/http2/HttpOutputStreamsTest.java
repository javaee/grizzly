/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.http2;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;

import static org.junit.Assert.*;

@SuppressWarnings("Duplicates")
@RunWith(Parameterized.class)
public class HttpOutputStreamsTest extends AbstractHttp2Test {

    private static final int PORT = 8004;

    private static final char[] ALPHA = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    private final boolean isSecure;
    private final boolean priorKnowledge;
    
    public HttpOutputStreamsTest(final boolean isSecure, final boolean priorKnowledge) {
        this.isSecure = isSecure;
        this.priorKnowledge = priorKnowledge;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> configure() {
        return AbstractHttp2Test.configure();
    }

    // --------------------------------------------------------- Character Tests


    @Test
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
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = content.length; i < len; i++) {
                    writer.write(content[i]);
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
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
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = content.length; i < len; i++) {
                    writer.write(content[i]);
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter003() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        doTest(s, sb.toString());

        s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter004() throws Exception {

        final StringBuilder sb = buildBuffer(8194); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        doTest(s, sb.toString());

        s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter005() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    writer.write(sb.charAt(i));
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter006() throws Exception {

        final StringBuilder sb = buildBuffer(8194); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    writer.write(sb.charAt(i));
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter007() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter008() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter009() throws Exception {

        int len = 1024 * 31 + 8; // boundary + 8
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter010() throws Exception {

        final int len = 1024 * 33;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter011() throws Exception {

        final int len = 1024 * 33;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());
    }


    @Test
    public void testCharacter012() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter013() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());
    }


    @Test
    public void testCharacter014() throws Exception {

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
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = content.length; i < len; i++) {
                    writer.write(content[i]);
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter015() throws Exception {

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
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = content.length; i < len; i++) {
                    writer.write(content[i]);
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter016() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        doTest(s, sb.toString());

        s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter017() throws Exception {

        final StringBuilder sb = buildBuffer(8194); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        doTest(s, sb.toString());

        s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter018() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    writer.write(sb.charAt(i));
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter019() throws Exception {

        final StringBuilder sb = buildBuffer(8194); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    writer.write(sb.charAt(i));
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter020() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter021() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter022() throws Exception {

        int len = 1024 * 31 + 8; // boundary + 8
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter023() throws Exception {

        final int len = 1024 * 33;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter024() throws Exception {

        final int len = 1024 * 33;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());
    }


    @Test
    public void testCharacter025() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());

    }


    @Test
    public void testCharacter026() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());
    }

    @Test
    public void testCharacter027() throws Exception {

        final StringBuilder sb = buildBuffer(2002); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override
            public void doWrite(Response response)
                    throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString());
            }
        };

        doTest(s, sb.toString());

        s = new WriteStrategy() {
            @Override
            public void doWrite(Response response)
                    throws IOException {
                Writer writer = response.getWriter();
                writer.write(sb.toString().toCharArray());
            }
        };

        doTest(s, sb.toString());

    }

    // ------------------------------------------------------------ Binary Tests


    @Test
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
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = content.length; i < len; i++) {
                    out.write(content[i]);
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary002() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    out.write(sb.charAt(i));
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary003() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    out.write(sb.charAt(i));
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary004() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                out.write(sb.toString().getBytes());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary005() throws Exception {

        int len = 1024 * 31 + 8; // boundary + 8
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                out.write(sb.toString().getBytes());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary006() throws Exception {

        final int len = 1024 * 33;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary007() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());

    }

    @Test
    public void testBinary008() throws Exception {

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
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = content.length; i < len; i++) {
                    out.write(content[i]);
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary009() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    out.write(sb.charAt(i));
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary010() throws Exception {

        final StringBuilder sb = buildBuffer(8192); // boundary + 2

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                for (int i = 0, len = sb.length(); i < len; i++) {
                    out.write(sb.charAt(i));
                }
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary011() throws Exception {

        int len = 1024 * 31; // boundary
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                out.write(sb.toString().getBytes());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary012() throws Exception {

        int len = 1024 * 31 + 8; // boundary + 8
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
                  throws IOException {
                OutputStream out = response.getOutputStream();
                out.write(sb.toString().getBytes());
            }
        };

        doTest(s, sb.toString());

    }


    @Test
    public void testBinary013() throws Exception {

        final int len = 1024 * 33;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());

    }


    public void testBinary014() throws Exception {

        final int len = 1024 * 9 * 10;
        final StringBuilder sb = buildBuffer(len);

        WriteStrategy s = new WriteStrategy() {
            @Override public void doWrite(Response response)
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

        doTest(s, sb.toString());

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


    private void doTest(WriteStrategy strategy,
                        String expectedResult)
    throws Exception {

        final HttpServer server = createServer("/tmp", PORT, isSecure,
                HttpHandlerRegistration.of(new TestHttpHandler(strategy), "/*"));
        
        
        final FutureImpl<String> parseResult = SafeFutureImpl.create();
        TCPNIOTransport ctransport = TCPNIOTransportBuilder.newInstance().build();
        try {
            server.start();

            FilterChain clientFilterChain = createClientFilterChainAsBuilder(
                    isSecure, priorKnowledge, new ClientFilter(parseResult)).build();
                                
            ctransport.setProcessor(clientFilterChain);

            ctransport.start();

            Future<Connection> connectFuture = ctransport.connect("localhost", PORT);
            String res = null;
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                res = parseResult.get(60, TimeUnit.SECONDS);
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.closeSilently();
                }
            }
            assertEquals(expectedResult, res);

        } finally {
            server.shutdownNow();
            ctransport.shutdownNow();
        }
    }


    private interface WriteStrategy {

        void doWrite(Response response) throws IOException;

    }


    private static final class TestHttpHandler extends HttpHandler {

        private final WriteStrategy strategy;

        // -------------------------------------------------------- Constructors


        public TestHttpHandler(WriteStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public void service(Request req, Response res) throws Exception {

            res.setStatus(HttpStatus.OK_200);
            strategy.doWrite(res);

        }
    }


    private static class ClientFilter extends BaseFilter {
        private final static Logger logger = Grizzly.logger(ClientFilter.class);

        private final CompositeBuffer buf = CompositeBuffer.newBuffer();

        private final FutureImpl<String> completeFuture;

        // number of bytes downloaded
        private volatile int bytesDownloaded;


        // -------------------------------------------------------- Constructors


        public ClientFilter(FutureImpl<String> completeFuture) {

            this.completeFuture = completeFuture;

        }


        // ------------------------------------------------ Methods from Filters


        @Override
        public NextAction handleConnect(final FilterChainContext ctx) throws IOException {

            final Http2Session c = Http2Session.get(ctx.getConnection());
            if (c != null) { // we're going over TLS
                c.getHttp2State().addReadyListener(new Http2State.ReadyListener() {
                    @Override
                    public void ready(Http2Session http2Session) {
                        sendRequest(ctx);
                        ctx.resume(ctx.getStopAction());
                    }
                });
                return ctx.getSuspendAction();
            } else {
                sendRequest(ctx);
                return ctx.getStopAction();
            }
        }

        private void sendRequest(final FilterChainContext ctx) {
            // Build the HttpRequestPacket, which will be sent to a server
            // We construct HTTP request version 1.1 and specifying the URL of the
            // resource we want to download
            final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                    .uri("/path").protocol(Protocol.HTTP_1_1)
                    .header("Host", "localhost:" + PORT).build();

            // Write the request asynchronously
            ctx.write(HttpContent.builder(httpRequest)
                    .content(Buffers.EMPTY_BUFFER)
                    .last(true)
                    .build());
        }


        @Override
        public NextAction handleRead(FilterChainContext ctx)
              throws IOException {
            try {
                // Cast message to a HttpContent
                final HttpContent httpContent = ctx.getMessage();

                logger.log(Level.FINE, "Got HTTP response chunk");

                // Get HttpContent's Buffer
                final Buffer buffer = httpContent.getContent();

                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "HTTP content size: {0}", buffer.remaining());
                }
                if (buffer.remaining() > 0) {
                    bytesDownloaded += buffer.remaining();

                    buf.append(buffer);

                }

                if (httpContent.isLast()) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Response complete: {0} bytes", bytesDownloaded);
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
                completeFuture.failure(new IOException("Connection was closed. Bytes downloaded: " + bytesDownloaded));
            }

        }
    }


}
