/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.CompressionConfig.CompressionMode;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.Charsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class CompressionEncodingFilterTest {
    public enum HeaderType {String, Buffer, Chars}

    private final HeaderType headerType;
    private final Random r = new Random();
    
    public CompressionEncodingFilterTest(HeaderType headerType) {
        this.headerType = headerType;
    }

    @Parameters
    public static Collection<Object[]> getHeaderTypes() {
        return Arrays.asList(new Object[][]{
                    {HeaderType.String},
                    {HeaderType.Buffer},
                    {HeaderType.Chars}
                });
    }
    
    // ------------------------------------------------------------ Test Methods
    
    @Test
    public void testAcceptEncodingProcessing() throws Exception {

        final CompressionEncodingFilter filter =
                new CompressionEncodingFilter(CompressionMode.ON,
                                              1,
                                              new String[0],
                                              new String[0],
                                              new String[] {"gzip"});
        HttpRequestPacket request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "gzip");
        HttpResponsePacket response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).build();
        assertTrue(filter.applyEncoding(response));
        
        request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "foo, gzip;q=1.0, foo2");
        response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).build();
        assertTrue(filter.applyEncoding(response));
        
        request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "foo, gzip; q=1.0, foo2");
        response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).build();
        assertTrue(filter.applyEncoding(response));

        request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "foo, gzip;q=0, foo2");
        response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).build();
        assertFalse(filter.applyEncoding(response));

        request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "foo, gzip; q=0, foo2");
        response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).build();
        assertFalse(filter.applyEncoding(response));

        request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "compress; q=0.5, gzip;q=1.0");
        response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).build();
        assertTrue(filter.applyEncoding(response));

        // Check double-compression
        request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "foo, gzip;q=1.0, foo2");
        response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).header(Header.ContentEncoding, "gzip").build();
        assertFalse(filter.applyEncoding(response));
    }

    @Test
    public void testMinSizeSetting() throws Exception {

        final CompressionEncodingFilter filter =
                new CompressionEncodingFilter(CompressionMode.ON,
                                              1024,
                                              new String[0],
                                              new String[0],
                                              new String[] {"gzip"});
        HttpRequestPacket request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "compress;q=0.5, gzip;q=1.0");
        HttpResponsePacket response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).contentLength(1023).build();
        assertFalse(filter.applyEncoding(response));
        
        request = setAcceptEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "compress;q=0.5, gzip;q=1.0");
        response = HttpResponsePacket.builder(request).protocol(Protocol.HTTP_1_1).contentLength(1024).build();
        assertTrue(filter.applyEncoding(response));
    }

    @Test
    public void testContentEncodingProcessing() throws Exception {
        
        final CompressionEncodingFilter filter =
                new CompressionEncodingFilter(CompressionMode.ON,
                                              1,
                                              new String[0],
                                              new String[0],
                                              new String[] {"gzip"});
        
        // Valid gzip compression
        HttpRequestPacket request = setContentEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "gzip");
        assertTrue(filter.applyDecoding(request));
        
        // Other encoding header
        request = setContentEncoding(
                HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build(),
                "identity");
        assertFalse(filter.applyDecoding(request));
        
        // No header - assume uncompressed
        request = HttpRequestPacket.builder().method(Method.GET).protocol(Protocol.HTTP_1_1).uri("/").build();
        assertFalse(filter.applyDecoding(request));
    }
    
    private HttpRequestPacket setAcceptEncoding(HttpRequestPacket request, String acceptEncoding) {
        return setHeader(request, Header.AcceptEncoding, acceptEncoding);
    }

    private HttpRequestPacket setContentEncoding(HttpRequestPacket request, String contentEncoding) {
        return setHeader(request, Header.ContentEncoding, contentEncoding);
    }

    private HttpRequestPacket setHeader(HttpRequestPacket request, Header header, String headerValue) {
        switch (headerType) {
            case String: {
                request.addHeader(header, headerValue);
                break;
            }
            case Buffer: {
                final byte[] encodingBytes =
                        headerValue.getBytes(Charsets.ASCII_CHARSET);
                
                final byte[] array = new byte[2048];
                final int offs = r.nextInt(array.length - encodingBytes.length);
                System.arraycopy(encodingBytes, 0, array, offs, encodingBytes.length);
                final Buffer b = Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, array);
                
                request.getHeaders().addValue(header)
                        .setBuffer(b, offs, offs + encodingBytes.length);
                break;
            }
                
            case Chars: {
                final char[] array = new char[2048];
                final int offs = r.nextInt(array.length - headerValue.length());
                
                headerValue.getChars(0, headerValue.length(), array, offs);
                
                request.getHeaders().addValue(header)
                        .setChars(array, offs, offs + headerValue.length());
                break;
            }
        }
        
        return request;
    }
}
