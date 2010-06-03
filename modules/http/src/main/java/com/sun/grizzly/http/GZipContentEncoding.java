/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly.http;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.http.TransferEncoding.ParsingResult;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.zip.GZipDecoder;
import com.sun.grizzly.zip.GZipEncoder;

/**
 * GZip {@link ContentEncoding} implementation, which compresses/decompresses
 * HTTP content using gzip algorithm.
 * 
 * @author Alexey Stashok
 */
public class GZipContentEncoding implements ContentEncoding {
    private static final String[] ALIASES = {"gzip", "deflate"};

    private final String name = "gzip";
    
    private final GZipDecoder decoder;
    private final GZipEncoder encoder;

    private final EncodingFilter encoderFilter;
    
    /**
     * Construct <tt>GZipContentEncoding</tt> using default buffer sizes.
     */
    public GZipContentEncoding() {
        this(512, 512);
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
                public boolean applyEncoding(HttpHeader httpPacket) {
                    return false;
                }
            };
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String[] getAliases() {
        return ALIASES;
    }

    @Override
    public boolean wantEncode(HttpHeader header) {
        return encoderFilter.applyEncoding(header);
    }

    @Override
    public ParsingResult decode(Connection connection, HttpContent httpContent) {
        final HttpHeader httpHeader = httpContent.getHttpHeader();

        final Buffer input = httpContent.getContent();
        final TransformationResult<Buffer, Buffer> result =
                decoder.transform(httpHeader, input);

        Buffer remainder = result.getExternalRemainder();

        if (remainder == null || !remainder.hasRemaining()) {
            input.tryDispose();
            remainder = null;
        } else {
            input.disposeUnused();
        }

        try {
            switch (result.getStatus()) {
                case COMPLETED: {
                    httpContent.setContent(result.getMessage());
                    return ParsingResult.create(httpContent, remainder);
                }

                case INCOMPLETED: {
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
        final TransformationResult<Buffer, Buffer> result =
                encoder.transform(httpHeader, input);

        input.dispose();

        try {
            switch (result.getStatus()) {
                case COMPLETED:
                case INCOMPLETED: {
                    Buffer encodedBuffer = result.getMessage();
                    if (httpHeader.getContentLength() < 0) {
                        final Buffer finishBuffer = encoder.finish(httpHeader);
                        encodedBuffer = BufferUtils.appendBuffers(
                                connection.getTransport().getMemoryManager(),
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
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (this.name != null ? this.name.hashCode() : 0);
        return hash;
    }
}
