/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http2.frames;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http2.compression.HeadersEncoder;

/**
 * The builder for compressed headers used by {@link SynStreamFrame},
 * {@link SynReplyFrame}, {@link HeadersFrame}.
 * 
 * @see SynStreamFrame
 * @see SynReplyFrame
 * @see HeadersFrame
 * 
 * @author Alexey Stashok
 */
public final class CompressedHeadersBuilder {
    
    private final Map<String, String> headers = new HashMap<String, String>();

    private CompressedHeadersBuilder() {
    }
    
    /**
     * Returns the {@link CompressedHeadersBuilder} instance.
     */
    public static CompressedHeadersBuilder newInstance() {
        return new CompressedHeadersBuilder();
    }
    
    /**
     * Set the content-length of this header. Applicable only in
     * case of fixed-length HTTP message.
     *
     * @param contentLength the content-length of this {@link HttpPacket}.
     * Applicable only in case of fixed-length HTTP message.
     */
    public final CompressedHeadersBuilder contentLength(long contentLength) {
        return header(Header.ContentLength, String.valueOf(contentLength));
    }

    /**
     * Set the content-type of this header.
     *
     * @param contentType the content-type of this {@link HttpPacket}.
     */
    public final CompressedHeadersBuilder contentType(String contentType) {
        return header(Header.ContentType, String.valueOf(contentType));
    }

    /**
     * Set the the HTTP method for this request.
     * (e.g. "GET", "POST", "HEAD", etc).
     *
     * @param method the method of this header.
     */
    public final CompressedHeadersBuilder method(final Method method) {
        return method(method.getMethodString());
    }

    /**
     * Set the the HTTP method for this request.
     * (e.g. "GET", "POST", "HEAD", etc).
     *
     * @param method the method of this header.
     */
    public final CompressedHeadersBuilder method(String method) {
        return header(":method", method);
    }

    /**
     * Set the url-path for required url with "/" prefixed.
     * (See RFC1738 [RFC1738]).
     * For example, for "http://www.google.com/search?q=dogs" the path would be
     * "/search?q=dogs".
     *
     * @param path the path of this header.
     */
    public final CompressedHeadersBuilder path(String path) {
        return header(":path", path);
    }

    /**
     * Set the the HTTP version of this request (e.g. "HTTP/1.1").
     *
     * @param version the HTTP version of this header.
     */
    public final CompressedHeadersBuilder version(Protocol version) {
        return version(version.getProtocolString());
    }

    /**
     * Set the the HTTP version of this request (e.g. "HTTP/1.1").
     *
     * @param version the HTTP version of this header.
     */
    public final CompressedHeadersBuilder version(String version) {
        return header(":version", version);
    }
    
    /**
     * Set the the hostport (See RFC1738 [RFC1738]) portion of the URL for this
     * request header (e.g. "www.google.com:1234").
     * This header is the same as the HTTP 'Host' header.
     *
     * @param host the hostport.
     */
    public final CompressedHeadersBuilder host(String host) {
        return header(":host", host);
    }

    /**
     * Set the scheme portion of the URL for this request header (e.g. "https").
     *
     * @param path the path of this header.
     */
    public final CompressedHeadersBuilder scheme(String scheme) {
        return header(":scheme", scheme);
    }
    
    /**
     * Set the HTTP response status code (e.g. 200 or 404).
     *
     * @param path the path of this header.
     */
    public final CompressedHeadersBuilder status(int status) {
        return status(String.valueOf(status));
    }

    /**
     * Set the HTTP response status code (e.g. "200" or "200 OK")
     *
     * @param path the path of this header.
     */
    public final CompressedHeadersBuilder status(HttpStatus status) {
        final StringBuilder sb = new StringBuilder();
        sb.append(status.getStatusCode()).append(' ')
                .append(new String(status.getReasonPhraseBytes(),
                org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET));
        
        return status(sb.toString());
    }

    /**
     * Set the HTTP response status code (e.g. "200" or "200 OK")
     *
     * @param path the path of this header.
     */
    public final CompressedHeadersBuilder status(final String status) {
        return header(":status", status);
    }
    
    /**
     * Add the SPDY/HTTP mime header.
     *
     * @param name the mime header name.
     * @param value the mime header value.
     */
    public final CompressedHeadersBuilder header(String name, String value) {
        headers.put(name.toLowerCase(Locale.US), value);
        return this;
    }

    /**
     * Add the SPDY/HTTP mime header.
     *
     * @param header the mime {@link Header}.
     * @param value the mime header value.
     */
    public final CompressedHeadersBuilder header(Header header, String value) {
        headers.put(header.getLowerCase(), value);
        return this;
    }
    
    public Buffer build(final HeadersEncoder encoder) throws IOException {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            encoder.encodeHeader(entry.getKey(), entry.getValue());
        }
        
        return encoder.flushHeaders();
    }
}
