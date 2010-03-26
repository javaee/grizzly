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

import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.Ascii;
import com.sun.grizzly.http.util.ContentType;
import com.sun.grizzly.http.util.Cookies;
import com.sun.grizzly.http.util.MimeHeaders;
import com.sun.grizzly.http.util.Parameters;

/**
 * {@link HttpPacket}, which represents HTTP message header. There are 2 subtypes
 * of this class: {@link HttpRequest} and {@link HttpResponse}.
 *
 * @see HttpRequest
 * @see HttpResponse
 * 
 * @author Alexey Stashok
 */
public abstract class HttpHeader implements HttpPacket, MimeHeadersPacket {

    protected boolean isCommited;
    protected MimeHeaders headers = new MimeHeaders();
    protected BufferChunk protocolBC = BufferChunk.newInstance();
    protected boolean isChunked;
    protected long contentLength = -1;
    protected String charEncoding;
    protected boolean charEncodingParsed;
    protected boolean contentTypeParsed;
    protected String contentType;
    protected Cookies cookies = new Cookies(headers);


    /**
     * Returns <tt>true</tt>, if the current <tt>HttpHeader</tt> represent
     * HTTP request message, or <tt>false</tt> otherwise.
     * 
     * @return <tt>true</tt>, if the current <tt>HttpHeader</tt> represent
     * HTTP request message, or <tt>false</tt> otherwise.
     */
    public abstract boolean isRequest();

    /**
     * Returns <tt>true</tt>.
     * @return <tt>true</tt>.
     */
    @Override
    public final boolean isHeader() {
        return true;
    }

    /**
     * Returns <tt>true</tt>, if this {@link HttpPacket} content will be transferred
     * in chunking mode, or <tt>false</tt> if case of fixed-length message.
     * 
     * @return <tt>true</tt>, if this {@link HttpPacket} content will be transferred
     * in chunking mode, or <tt>false</tt> if case of fixed-length message.
     */
    public boolean isChunked() {
        return isChunked;
    }

    /**
     * Set <tt>true</tt>, if this {@link HttpPacket} content will be transferred
     * in chunking mode, or <tt>false</tt> if case of fixed-length message.
     *
     * @param isChunked  <tt>true</tt>, if this {@link HttpPacket} content
     * will be transferred in chunking mode, or <tt>false</tt> if case
     * of fixed-length message.
     */
    public void setChunked(boolean isChunked) {
        this.isChunked = isChunked;
    }

    /**
     * Get the content-length of this {@link HttpPacket}. Applicable only in case
     * of fixed-length HTTP message.
     * 
     * @return the content-length of this {@link HttpPacket}. Applicable only
     * in case of fixed-length HTTP message.
     */
    public long getContentLength() {
        if (contentLength == -1) {
            final BufferChunk contentLengthChunk =
                    headers.getValue("content-length");
            if (contentLengthChunk != null) {
                contentLength = Ascii.parseLong(contentLengthChunk);
            }
        }

        return contentLength;
    }

    /**
     * Set the content-length of this {@link HttpPacket}. Applicable only in case
     * of fixed-length HTTP message.
     *
     * @param contentLength  the content-length of this {@link HttpPacket}.
     * Applicable only in case of fixed-length HTTP message.
     */
    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    /**
     * Is this <tt>HttpHeader</tt> written? <tt>true</tt>, if this
     * <tt>HttpHeader</tt> has been already serialized, and only {@link HttpContent}
     * messages might be serialized for this {@link HttpPacket}.
     * 
     * @return  <tt>true</tt>, if this <tt>HttpHeader</tt> has been already
     * serialized, and only {@link HttpContent} messages might be serialized
     * for this {@link HttpPacket}.
     */
    public boolean isCommited() {
        return isCommited;
    }

    /**
     * Is this <tt>HttpHeader</tt> written? <tt>true</tt>, if this
     * <tt>HttpHeader</tt> has been already serialized, and only {@link HttpContent}
     * messages might be serialized for this {@link HttpPacket}.
     *
     * @param isCommited   <tt>true</tt>, if this <tt>HttpHeader</tt> has been
     * already serialized, and only {@link HttpContent} messages might be
     * serialized for this {@link HttpPacket}.
     */
    public void setCommited(boolean isCommited) {
        this.isCommited = isCommited;
    }


    // -------------------- encoding/type --------------------


    /**
     * TODO: docs
     * Get the character encoding used for this request.
     */
    public String getCharacterEncoding() {

        if (charEncoding != null || charEncodingParsed) {
            return charEncoding;
        }

        charEncoding = ContentType.getCharsetFromContentType(getContentType());
        charEncodingParsed = true;

        return charEncoding;
        
    }


    /**
     * TODO DOCS
     * @param enc
     */
    public void setCharacterEncoding(String enc) {
        this.charEncoding = enc;
    }


    /**
     * TODO DOCS
     * @param len
     */
    public void setContentLength(int len) {
        this.contentLength = len;
    }


    /**
     * TODO DOCS
     * @return
     */
    public String getContentType() {

        if (!contentTypeParsed) {
            contentTypeParsed = true;

            BufferChunk bc = headers.getValue("content-type");

            if (bc == null || bc.isNull()) {
                return null;
            }
            contentType = bc.toString();
        }
        return contentType;

    }


    /**
     * TODO DOCS
     * @param type
     */
    public void setContentType(String type) {
        contentType = type;
    }


    // -------------------- Headers --------------------

    
    /**
     * {@inheritDoc}
     */
    @Override
    public MimeHeaders getHeaders() {
        return headers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(String name) {
        return headers.getHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(String name, String value) {
        headers.setValue(name).setString(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(String name, String value) {
        headers.addValue(name).setString(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }

    /**
     * Get the HTTP message protocol version as {@link BufferChunk}
     * (avoiding creation of a String object). The result format is "HTTP/1.x".
     * 
     * @return the HTTP message protocol version as {@link BufferChunk}
     * (avoiding creation of a String object). The result format is "HTTP/1.x".
     */
    public BufferChunk getProtocolBC() {
        return protocolBC;
    }

    /**
     * Get the HTTP message protocol version. The result format is "HTTP/1.x".
     *
     * @return the HTTP message protocol version. The result format is "HTTP/1.x".
     */
    public String getProtocol() {
        return getProtocolBC().toString();
    }

    /**
     * Set the HTTP message protocol version.
     * @param protocol protocol version in format "HTTP/1.x".
     */
    public void setProtocol(String protocol) {
        this.protocolBC.setString(protocol);
    }

    /**
     * Get the HTTP message content builder.
     *
     * @return {@link HttpContent.Builder}.
     */
    public final HttpContent.Builder httpContentBuilder() {
        return HttpContent.builder(this);
    }

    /**
     * Get the HTTP message trailer-chunk builder.
     *
     * @return {@link HttpTrailer.Builder}.
     */
    public HttpTrailer.Builder httpTrailerBuilder() {
        return HttpTrailer.builder(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle() {
        protocolBC.recycle();
        headers.clear();
        cookies.recycle();
        isCommited = false;
        isChunked = false;
        contentLength = -1;
        charEncoding = null;
        charEncodingParsed = false;
        contentType = null;
        contentTypeParsed = false;
    }

    /**
     * <tt>HttpHeader</tt> message builder.
     */
    public static abstract class Builder<T extends Builder> {

        protected HttpHeader packet;

        /**
         * Set the HTTP message protocol version.
         * @param protocol protocol version in format "HTTP/1.x".
         */
        public final T protocol(String protocol) {
            packet.setProtocol(protocol);
            return (T) this;
        }

        /**
         * Set <tt>true</tt>, if this {@link HttpPacket} content will be transferred
         * in chunking mode, or <tt>false</tt> if case of fixed-length message.
         *
         * @param isChunked  <tt>true</tt>, if this {@link HttpPacket} content
         * will be transferred in chunking mode, or <tt>false</tt> if case
         * of fixed-length message.
         */
        public final T chunked(boolean isChunked) {
            packet.setChunked(isChunked);
            return (T) this;
        }

        /**
         * Set the content-length of this {@link HttpPacket}. Applicable only in case
         * of fixed-length HTTP message.
         *
         * @param contentLength  the content-length of this {@link HttpPacket}.
         * Applicable only in case of fixed-length HTTP message.
         */
        public final T contentLength(long contentLength) {
            packet.setContentLength(contentLength);
            return (T) this;
        }

        /**
         * Add the HTTP mime header.
         *
         * @param name the mime header name.
         * @param value the mime header value.
         */
        public final T header(String name, String value) {
            packet.addHeader(name, value);
            return (T) this;
        }
    }
}
