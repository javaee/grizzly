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

import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.CompressionConfig.CompressionMode;
import org.glassfish.grizzly.http.CompressionConfig.CompressionModeI;
import org.glassfish.grizzly.http.EncodingFilter;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;

import java.util.Arrays;


public class CompressionEncodingFilter implements EncodingFilter {
    private final CompressionConfig compressionConfig;
    private final String[] aliases;

    public CompressionEncodingFilter(final CompressionConfig compressionConfig,
            final String[] aliases) {
        this.compressionConfig = new CompressionConfig(compressionConfig);
        this.aliases = Arrays.copyOf(aliases, aliases.length);
    }
    
    /**
     * 
     * @param compressionMode
     * @param compressionMinSize
     * @param compressableMimeTypes
     * @param noCompressionUserAgents
     * @param aliases 
     */
    public CompressionEncodingFilter(CompressionModeI compressionMode,
            int compressionMinSize,
            String[] compressableMimeTypes,
            String[] noCompressionUserAgents,
            String[] aliases) {
        
        final CompressionMode mode;
        if (compressionMode instanceof CompressionMode) {
            mode = (CompressionMode) compressionMode;
        } else {
            // backwards compatibility
            assert (compressionMode instanceof CompressionLevel);
            mode = ((CompressionLevel) compressionMode).normalize();
        }

        compressionConfig = new CompressionConfig(mode, compressionMinSize,
                null, null);
        compressionConfig.setCompressableMimeTypes(compressableMimeTypes);
        compressionConfig.setNoCompressionUserAgents(noCompressionUserAgents);
        
        this.aliases = Arrays.copyOf(aliases, aliases.length);
    }

    @Override
    public boolean applyEncoding(final HttpHeader httpPacket) {
        if (httpPacket.isRequest()) {
            assert httpPacket instanceof HttpRequestPacket;
            return false;
        }
        
        assert httpPacket instanceof HttpResponsePacket;
        return canCompressHttpResponse((HttpResponsePacket) httpPacket,
                compressionConfig, aliases);
    }

    @Override
    public boolean applyDecoding(final HttpHeader httpPacket) {
        if(! httpPacket.isRequest()) {
            return false;
        }
        
        assert httpPacket instanceof HttpRequestPacket;
        return canDecompressHttpRequest((HttpRequestPacket) httpPacket, 
                aliases);
    }
    
    /**
     * Returns <tt>true</tt> if the {@link HttpResponsePacket} could be
     * compressed, or <tt>false</tt> otherwise.
     * The method checks if client supports compression and if the resource,
     * that we are about to send matches {@link CompressionConfig} configuration.
     */
    protected static boolean canCompressHttpResponse(
            final HttpResponsePacket response,
            final CompressionConfig compressionConfig,
            final String[] aliases) {
        
        // If at least one encoding has been already selected
        // skip this one
        if (!response.getContentEncodings().isEmpty()) {
            return false;
        }

        final MimeHeaders responseHeaders = response.getHeaders();
        // Check if content is already encoded (no matter which encoding)
        final DataChunk contentEncodingMB =
                responseHeaders.getValue(Header.ContentEncoding);
        if (contentEncodingMB != null && !contentEncodingMB.isNull()) {
            return false;
        }

        if (!CompressionConfig.isClientSupportCompression(compressionConfig,
                response.getRequest(), aliases)) {
            return false;
        }
        
        // If force mode, always compress (test purposes only)
        if (compressionConfig.getCompressionMode() == CompressionMode.FORCE) {
            response.setChunked(true);
            response.setContentLength(-1);
            return true;
        }
                
        // Check if sufficient len to trig the compression
        final long contentLength = response.getContentLength();
        if (contentLength == -1
                || contentLength >= compressionConfig.getCompressionMinSize()) {

            if (compressionConfig.checkMimeType(response.getContentType())) {
                response.setChunked(true);
                response.setContentLength(-1);
                return true;
            }
        }

        return false;
    }
    
    /**
     * Returns <tt>true</tt> if the {@link HttpResponsePacket} could be
     * compressed, or <tt>false</tt> otherwise.
     * The method checks if client supports compression and if the resource,
     * that we are about to send matches {@link CompressionConfig} configuration.
     */
    protected static boolean canDecompressHttpRequest(
            final HttpRequestPacket request,
            final String[] aliases) {
        
        String contentEncoding = request.getHeader(Header.ContentEncoding);
        
        // If no header is present, assume request is not encoded, so don't decode
        if (contentEncoding == null) {
            return false;
        }
        
        // If content encoding is set to one of the aliases supported by this
        // filter, decoding should happen.
        contentEncoding = contentEncoding.trim();
        for (String alias : aliases) {
            if (alias.equals(contentEncoding)) {
                return true;
            }
        }
        return false;
    }
}
