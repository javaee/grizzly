/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.compression.zip.GZipDecoder;
import org.glassfish.grizzly.compression.zip.GZipEncoder;

/**
 * GZip {@link ContentEncoding} implementation, which compresses/decompresses
 * HTTP content using gzip algorithm.
 * 
 * @author Alexey Stashok
 */
public class GZipContentEncoding implements ContentEncoding {
    public static final int DEFAULT_IN_BUFFER_SIZE = 512;
    public static final int DEFAULT_OUT_BUFFER_SIZE = 512;

    private static final String[] ALIASES = {"gzip", "deflate"};

    public static final String NAME = "gzip";
    
    private final GZipDecoder decoder;
    private final GZipEncoder encoder;

    private final EncodingFilter encoderFilter;
    
    /**
     * Construct <tt>GZipContentEncoding</tt> using default buffer sizes.
     */
    public GZipContentEncoding() {
        this(DEFAULT_IN_BUFFER_SIZE, DEFAULT_OUT_BUFFER_SIZE);
    }

    /**
     * Construct <tt>GZipContentEncoding</tt> using specific buffer sizes.
     * @param inBufferSize input buffer size
     * @param outBufferSize output buffer size
     */
    public GZipContentEncoding(int inBufferSize, int outBufferSize) {
        this(inBufferSize, outBufferSize, null);
    }

    /**
     * Construct <tt>GZipContentEncoding</tt> using specific buffer sizes.
     * @param inBufferSize input buffer size
     * @param outBufferSize output buffer size
     * @param encoderFilter {@link EncodingFilter}, which will decide if
     *          <tt>GZipContentEncoding</tt> should be applied to encode specific
     *          {@link HttpHeader} packet.
     */
    public GZipContentEncoding(int inBufferSize, int outBufferSize,
            EncodingFilter encoderFilter) {
        this.decoder = new GZipDecoder(inBufferSize);
        this.encoder = new GZipEncoder(outBufferSize);

        if (encoderFilter != null) {
            this.encoderFilter = encoderFilter;
        } else {
            this.encoderFilter = new EncodingFilter() {
                @Override
                public boolean applyEncoding(final HttpHeader httpPacket) {
                    return false;
                }

                @Override
                public boolean applyDecoding(final HttpHeader httpPacket) {
                    return true;
                }
            };
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String[] getAliases() {
        return ALIASES.clone();
    }
    
    public static String[] getGzipAliases() {
        return ALIASES.clone();
    }

    @Override
    public final boolean wantDecode(final HttpHeader header) {
        return encoderFilter.applyDecoding(header);
    }

    @Override
    public final boolean wantEncode(final HttpHeader header) {
        return encoderFilter.applyEncoding(header);
    }

    @Override
    public ParsingResult decode(final Connection connection,
            final HttpContent httpContent) {
        final HttpHeader httpHeader = httpContent.getHttpHeader();

        final Buffer input = httpContent.getContent();
        final TransformationResult<Buffer, Buffer> result =
                decoder.transform(httpHeader, input);

        Buffer remainder = result.getExternalRemainder();

        if (remainder == null || !remainder.hasRemaining()) {
            input.tryDispose();
            remainder = null;
        } else {
            input.shrink();
        }

        try {
            switch (result.getStatus()) {
                case COMPLETE: {
                    httpContent.setContent(result.getMessage());
                    return ParsingResult.create(httpContent, remainder);
                }

                case INCOMPLETE: {
                    return ParsingResult.create(null, remainder);
                }

                case ERROR: {
                    throw new IllegalStateException("GZip decode error. Code: "
                            + result.getErrorCode() + " Description: "
                            + result.getErrorDescription());
                }

                default:
                    throw new IllegalStateException("Unexpected status: " +
                            result.getStatus());
            }
        } finally {
            result.recycle();
        }
    }

    @Override
    public HttpContent encode(Connection connection, HttpContent httpContent) {
        final HttpHeader httpHeader = httpContent.getHttpHeader();
        
        final Buffer input = httpContent.getContent();

        final boolean isLast = httpContent.isLast();
        if (!(isLast || input.hasRemaining())) {
            // the content is empty and is not last
            return httpContent;
        }

        final TransformationResult<Buffer, Buffer> result =
                encoder.transform(httpHeader, input);

        input.tryDispose();

        try {
            switch (result.getStatus()) {
                case COMPLETE:
                case INCOMPLETE: {
                    Buffer encodedBuffer = result.getMessage();
                    if (isLast) {
                        final Buffer finishBuffer = encoder.finish(httpHeader);
                        encodedBuffer = Buffers.appendBuffers(
                                connection.getMemoryManager(),
                                encodedBuffer, finishBuffer);
                    }
                    if (encodedBuffer != null) {
                        httpContent.setContent(encodedBuffer);
                        return httpContent;
                    } else {
                        return null;
                    }
                }

                case ERROR: {
                    throw new IllegalStateException("GZip decode error. Code: "
                            + result.getErrorCode() + " Description: "
                            + result.getErrorDescription());
                }

                default:
                    throw new IllegalStateException("Unexpected status: " +
                            result.getStatus());
            }
        } finally {
            result.recycle();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GZipContentEncoding other = (GZipContentEncoding) obj;
        return getName().equals(other.getName());

    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (getName().hashCode());
        return hash;
    }
}
