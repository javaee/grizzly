/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http.EncodingFilter;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpUtils;
import org.glassfish.grizzly.http.util.MimeHeaders;

public class CompressionEncodingFilter implements EncodingFilter {
    private final CompressionLevel compressionLevel;
    private final int compressionMinSize;
    private final String[] compressableMimeTypes;
    private final String[] noCompressionUserAgents;
    private final String[] aliases;

    public CompressionEncodingFilter(CompressionLevel compressionLevel,
            int compressionMinSize,
            String[] compressableMimeTypes,
            String[] noCompressionUserAgents,
            String[] aliases) {
        this.compressionLevel = compressionLevel;
        this.compressionMinSize = compressionMinSize;
        this.compressableMimeTypes = compressableMimeTypes;
        this.noCompressionUserAgents = noCompressionUserAgents;
        this.aliases = aliases;
    }

    @Override
    public boolean applyEncoding(HttpHeader httpPacket) {
        switch (compressionLevel) {
            case OFF:
                return false;
            default:
                // Compress only since HTTP 1.1
                if (org.glassfish.grizzly.http.Protocol.HTTP_1_1 != httpPacket.getProtocol()) {
                    return false;
                }

                // If at least one encoding has been already selected
                // skip this one
                if (!httpPacket.getContentEncodings().isEmpty()) {
                    return false;
                }

                final HttpResponsePacket responsePacket = (HttpResponsePacket) httpPacket;

                final MimeHeaders responseHeaders = responsePacket.getHeaders();
                // Check if content is already encoded (no matter which encoding)
                final DataChunk contentEncodingMB =
                        responseHeaders.getValue(Header.ContentEncoding);
                if (contentEncodingMB != null && !contentEncodingMB.isNull()) {
                    return false;
                }

                final MimeHeaders requestHeaders = responsePacket.getRequest().getHeaders();

                if (!userAgentRequestsCompression(requestHeaders)) return false;

                // If force mode, always compress (test purposes only)
                if (compressionLevel == CompressionLevel.FORCE) {
                    responsePacket.setChunked(true);
                    responsePacket.setContentLength(-1);
                    return true;
                }
                // Check for incompatible Browser
                if (noCompressionUserAgents.length > 0) {
                    final DataChunk userAgentValueDC =
                            requestHeaders.getValue(Header.UserAgent);
                    if (userAgentValueDC != null &&
                            indexOf(noCompressionUserAgents, userAgentValueDC) != -1) {
                        return false;
                    }
                }
                // Check if sufficient len to trig the compression
                final long contentLength = responsePacket.getContentLength();
                if (contentLength == -1
                        || contentLength >= compressionMinSize) {

                    boolean found = true;
                    // Check for compatible MIME-TYPE
                    if (compressableMimeTypes.length > 0) {
                        found = indexOfStartsWith(compressableMimeTypes,
                                responsePacket.getContentType()) != -1;
                    }

                    if (found) {
                        responsePacket.setChunked(true);
                        responsePacket.setContentLength(-1);
                        return true;
                    }
                }

                return false;
        }
    }

    private boolean userAgentRequestsCompression(MimeHeaders requestHeaders) {
        // Check if browser support gzip encoding
        final DataChunk acceptEncodingDC =
                requestHeaders.getValue(Header.AcceptEncoding);
        if (acceptEncodingDC == null) {
            return false;
        }
        String alias = null;
        int idx;
        for (int i = 0, len = aliases.length; i < len; i++) {
            alias = aliases[i];
            idx = acceptEncodingDC.indexOf(alias, 0);
            if (idx != -1) {
                break;
            }
            alias = null;
        }

        if (alias == null) {
            return false;
        }

        // we only care about q=0/q=0.0.  If present, the user-agent
        // doesn't support this particular compression.
        int qvalueStart = acceptEncodingDC.indexOf(';', alias.length());
        if (qvalueStart != -1) {
            qvalueStart = acceptEncodingDC.indexOf('=', qvalueStart);
            final int qvalueEnd = acceptEncodingDC.indexOf(',', qvalueStart);
            if (HttpUtils.convertQValueToFloat(acceptEncodingDC,
                    qvalueStart + 1,
                    qvalueEnd) == 0.0f) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean applyDecoding(HttpHeader httpPacket) {
        return false;
    }

    private static int indexOf(String[] aliases, DataChunk dc) {
        for (int i = 0; i < aliases.length; i++) {
            final String alias = aliases[i];
            if (dc.indexOf(alias, 0) != -1) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfStartsWith(String[] aliases, String s) {
        for (int i = 0; i < aliases.length; i++) {
            final String alias = aliases[i];
            if (s.startsWith(alias)) {
                return i;
            }
        }
        return -1;
    }
}
