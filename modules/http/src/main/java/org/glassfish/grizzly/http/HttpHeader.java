/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2016 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http.util.ContentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.http.util.ContentType.SettableContentType;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HeaderValue;
import org.glassfish.grizzly.http.util.HttpUtils;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.utils.Charsets;

/**
 * {@link HttpPacket}, which represents HTTP message header. There are 2 subtypes
 * of this class: {@link HttpRequestPacket} and {@link HttpResponsePacket}.
 *
 * @see HttpRequestPacket
 * @see HttpResponsePacket
 * 
 * @author Alexey Stashok
 */
public abstract class HttpHeader extends HttpPacket
        implements MimeHeadersPacket, AttributeStorage {
    
    private final static byte[] CHUNKED_ENCODING_BYTES =
            Constants.CHUNKED_ENCODING.getBytes(Charsets.ASCII_CHARSET);

    protected boolean isCommitted;
    protected final MimeHeaders headers;
    
    protected final DataChunk protocolC = DataChunk.newInstance();
    protected Protocol parsedProtocol;

    protected boolean isChunked;
    private final byte[] tmpContentLengthBuffer = new byte[20];
    private final byte[] tmpHeaderEncodingBuffer = new byte[512];
    
    protected long contentLength = -1;

    protected final SettableContentType contentType = ContentType.newSettableContentType();

    protected boolean isExpectContent = true;

    protected boolean isSkipRemainder;
    
    /**
     * <tt>true</tt> if HTTP message payload is broken due to inappropriate
     * Transfer-Encoding or Content-Encoding settings.
     */
    protected boolean isContentBroken;
    
    protected boolean secure;

    /**
     * <tt>true</tt> if parser has to ignore "Transfer-Encoding" and
     * "Content-Encoding" headers and act as none of them were specified.
     */
    private boolean isIgnoreContentModifiers;
    
    protected final DataChunk upgrade = DataChunk.newInstance();

    private TransferEncoding transferEncoding;
    private final List<ContentEncoding> contentEncodings =
            new ArrayList<ContentEncoding>(2);
    // <tt>true</tt>, if content encodings for this headers were chosen
    private boolean isContentEncodingsSelected;

    private final AttributeHolder attributes =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createUnsafeAttributeHolder();
    private AttributeHolder activeAttributes;

    Buffer headerBuffer;

    /**
     * Is chunking allowed to be used or not.
     */
    private boolean chunkingAllowed;

    public HttpHeader() {
        this(new MimeHeaders());
    }

    protected HttpHeader(MimeHeaders headers) {
        this.headers = headers;
    }
    
    void setHeaderBuffer(final Buffer headerBuffer) {
        this.headerBuffer = headerBuffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeHolder getAttributes() {
        if (activeAttributes == null) {
            activeAttributes = attributes;
        }
        
        return activeAttributes;
    }    

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
     * Returns <tt>this</tt> HttpHeader object.
     * @return <tt>this</tt> HttpHeader object.
     */
    @Override
    public HttpHeader getHttpHeader() {
        return this;
    }

    public abstract ProcessingState getProcessingState();

    /**
     * @return the parsing state of this HTTP header, or <tt>null</tt> if the
     *      message is complete or shouldn't be parsed at all
     */
    protected HttpPacketParsing getParsingState() {
        return null;
    }

    protected void addContentEncoding(ContentEncoding contentEncoding) {
        contentEncodings.add(contentEncoding);
    }

    protected List<ContentEncoding> getContentEncodings(final boolean isModifiable) {
        if (isModifiable) {
            return contentEncodings;
        } else {
            return Collections.unmodifiableList(contentEncodings);
        }
    }

    public List<ContentEncoding> getContentEncodings() {
        return getContentEncodings(false);
    }

    protected final boolean isContentEncodingsSelected() {
        return isContentEncodingsSelected;
    }
    
    protected final void setContentEncodingsSelected(final boolean isContentEncodingsSelected) {
        this.isContentEncodingsSelected = isContentEncodingsSelected;
    }

    /**
     * Get the {@link TransferEncoding}, responsible for the parsing/serialization of the HTTP message content
     * 
     * @return the {@link TransferEncoding}, responsible for the parsing/serialization of the HTTP message content
     */
    public TransferEncoding getTransferEncoding() {
        return transferEncoding;
    }

    /**
     * Set the {@link TransferEncoding}, responsible for the parsing/serialization of the HTTP message content.
     *
     * @param transferEncoding the {@link TransferEncoding}, responsible for the parsing/serialization of the HTTP message content.
     */
    protected void setTransferEncoding(TransferEncoding transferEncoding) {
        this.transferEncoding = transferEncoding;
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
     * NOTE:  If the protocol version of this header is 1.0 or older, chunking
     *        will be disabled regardless of the value passed.
     *
     * @param isChunked  <tt>true</tt>, if this {@link HttpPacket} content
     * will be transferred in chunking mode, or <tt>false</tt> if case
     * of fixed-length message.
     */
    public void setChunked(boolean isChunked) {
        if (getProtocol().compareTo(Protocol.HTTP_1_1) >= 0) { // HTTP/1.1 and later
            this.isChunked = isChunked;
            if (isChunked) {
                headers.removeHeader(Header.ContentLength);
            }
        } else {
            this.isChunked = false;
        }
    }

    /**
     * Returns <tt>true</tt>, if HTTP message, represented by this header still
     * expects additional content basing either on content-length or chunking
     * information. <tt>false</tt> is returned if content no additional content
     * data is expected.
     * Note: this method could be used only when we <b>parse</b> the HTTP message
     * 
     * @return <tt>true</tt>, if HTTP message, represented by this header still
     * expects additional content basing either on content-length or chunking
     * information. <tt>false</tt> is returned if content no additional content
     * data is expected.
     */
    public boolean isExpectContent() {
        return isExpectContent;
    }

    public void setExpectContent(boolean isExpectContent) {
        this.isExpectContent = isExpectContent;
    }

    /**
     * Returns <tt>true</tt>, if either application or HTTP core part is not
     * interested in parsing the rest of this HTTP message content and waits
     * for the next HTTP message to come on this {@link org.glassfish.grizzly.Connection}.
     * Otherwise returns <tt>false</tt>.
     * 
     * @return <tt>true</tt>, if either application or HTTP core part is not
     * interested in parsing the rest of this HTTP message content and waits
     * for the next HTTP message to come on this {@link org.glassfish.grizzly.Connection}.
     * Otherwise returns <tt>false</tt>.
     */
    public boolean isSkipRemainder() {
        return isSkipRemainder;
    }

    /**
     * Set flag, which is set to <tt>true</tt>, means that we're not
     * interested in parsing the rest of this HTTP message content and wait
     * for the next HTTP message to come on this {@link org.glassfish.grizzly.Connection}.
     *
     * @param isSkipRemainder <tt>true</tt> means that we're not
     * interested in parsing the rest of this HTTP message content and wait
     * for the next HTTP message to come on this {@link org.glassfish.grizzly.Connection}.
     */
    public void setSkipRemainder(boolean isSkipRemainder) {
        this.isSkipRemainder = isSkipRemainder;
    }

    /**
     * Returns <tt>true</tt>, if HTTP packet payload
     * was detected as broken due to unexpected error occurred during 
     * Transfer-Encoding or Content-Encoding processing.
     * Otherwise returns <tt>false</tt>.
     * 
     * @return <tt>true</tt>, if HTTP packet payload
     * was detected as broken due to unexpected error occurred during 
     * Transfer-Encoding or Content-Encoding processing.
     * Otherwise returns <tt>false</tt>.
     */
    public boolean isContentBroken() {
        return isContentBroken;
    }

    /**
     * Set flag, which is set to <tt>true</tt>, means that HTTP packet payload
     * was detected as broken due to unexpected error occurred during 
     * Transfer-Encoding or Content-Encoding processing.
     *
     * @param isBroken <tt>true</tt>, means that HTTP packet payload
     * was detected as broken due to unexpected error occurred during 
     * Transfer-Encoding or Content-Encoding processing.
     */
    public void setContentBroken(final boolean isBroken) {
        this.isContentBroken = isBroken;
    }
    
    /**
     * @return the "Upgrade" header value.
     */
    public final String getUpgrade() {
        return upgrade.toString();
    }

    /**
     * @return the "Upgrade" header value.
     */
    public DataChunk getUpgradeDC() {
        return upgrade;
    }

    /**
     * Sets the "Upgrade" header value
     * @param upgrade
     */
    public final void setUpgrade(final String upgrade) {
        this.upgrade.setString(upgrade);
    }

    /**
     * Propagate the "Upgrade" value to headers.
     */
    protected void makeUpgradeHeader() {
        if (!upgrade.isNull()) {
            headers.setValue(Header.Upgrade).set(upgrade);
        }
    }

    /**
     * @return <tt>true</tt> if parser has to ignore "Transfer-Encoding" and
     * "Content-Encoding" headers and act as none of them were specified.
     */
    public boolean isIgnoreContentModifiers() {
        return isIgnoreContentModifiers;
//                || (!upgrade.isNull() &&
//                !upgrade.startsWith("h2c", 0)); // don't ignore content modifiers for HTTP2 upgrade
    }

    /**
     * Set <tt>true</tt> if parser has to ignore "Transfer-Encoding" and
     * "Content-Encoding" headers and act as none of them were specified.
     * 
     * @param isIgnoreContentModifiers 
     */
    public void setIgnoreContentModifiers(boolean isIgnoreContentModifiers) {
        this.isIgnoreContentModifiers = isIgnoreContentModifiers;
    }
    
    /**
     * Makes sure content-length header is present.
     * 
     * @param defaultLength default content-length value.
     */
    protected void makeContentLengthHeader(final long defaultLength) {
        if (contentLength != -1) {
            final int start =
                    HttpUtils.longToBuffer(contentLength, tmpContentLengthBuffer);
            headers.setValue(Header.ContentLength).setBytes(
                    tmpContentLengthBuffer, start,
                    tmpContentLengthBuffer.length);
        } else if (defaultLength != -1) {
            final int start = HttpUtils.longToBuffer(defaultLength, tmpContentLengthBuffer);
            final int idx = headers.indexOf(Header.ContentLength, 0);
            if (idx == -1) {
                headers.addValue(Header.ContentLength).setBytes(
                    tmpContentLengthBuffer, start,
                    tmpContentLengthBuffer.length);
            } else if (headers.getValue(idx).isNull()) {
                headers.getValue(idx).setBytes(
                    tmpContentLengthBuffer, start,
                    tmpContentLengthBuffer.length);
            }
        }
    }

    /**
     * Get the content-length of this {@link HttpPacket}. Applicable only in case
     * of fixed-length HTTP message.
     * 
     * @return the content-length of this {@link HttpPacket}. Applicable only
     * in case of fixed-length HTTP message.
     */
    public long getContentLength() {
        return contentLength;
    }


    /**
     * Set the length of this HTTP message.
     *
     * @param len the length of this HTTP message.
     */
    public void setContentLength(final int len) {
        setContentLengthLong(len);
    }

    /**
     * Set the content-length of this {@link HttpPacket}. Applicable only in case
     * of fixed-length HTTP message.
     *
     * @param contentLength  the content-length of this {@link HttpPacket}.
     * Applicable only in case of fixed-length HTTP message.
     */
    public void setContentLengthLong(final long contentLength) {
        this.contentLength = contentLength;
        final boolean negativeLength = (contentLength < 0);
        if (negativeLength) {
            headers.removeHeader(Header.ContentLength);
        }
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
    public boolean isCommitted() {
        return isCommitted;
    }

    /**
     * Is this <tt>HttpHeader</tt> written? <tt>true</tt>, if this
     * <tt>HttpHeader</tt> has been already serialized, and only {@link HttpContent}
     * messages might be serialized for this {@link HttpPacket}.
     *
     * @param isCommitted   <tt>true</tt>, if this <tt>HttpHeader</tt> has been
     * already serialized, and only {@link HttpContent} messages might be
     * serialized for this {@link HttpPacket}.
     */
    public void setCommitted(final boolean isCommitted) {
        this.isCommitted = isCommitted;
    }


    // -------------------- encoding/type --------------------

    /**
     * Makes sure transfer-encoding header is present.
     *
     * @param defaultValue default transfer-encoding value.
     */
    protected void makeTransferEncodingHeader(final String defaultValue) {
        final int idx = headers.indexOf(Header.TransferEncoding, 0);
        
        if (idx == -1) {
            headers.addValue(Header.TransferEncoding).setBytes(
                    CHUNKED_ENCODING_BYTES);
        }
    }

    /**
     * Obtain content-encoding value and mark it as serialized.
     *
     * @param value container for the content-type value.
     */
    protected void extractContentEncoding(final DataChunk value) {
        final int idx = headers.indexOf(Header.ContentEncoding, 0);

        if (idx != -1) {
            headers.setSerialized(idx, true);
            value.set(headers.getValue(idx));
        }
    }

    /**
     * @return the character encoding of this HTTP message.
     */
    public String getCharacterEncoding() {
        return contentType.getCharacterEncoding();
    }

    /**
     * Set the character encoding of this HTTP message.
     *
     * @param charset the encoding.
     */
    public void setCharacterEncoding(final String charset) {

        if (isCommitted())
            return;
        
        contentType.setCharacterEncoding(charset);
    }

    /**
     * Return <code>true</code> if chunking is allowed for this header.
     *
     * @return <code>true</code> if chunking is allowed for this header.
     * @since 3.0
     */
    public boolean isChunkingAllowed() {
        return chunkingAllowed;
    }

    /**
     * Indicate whether or not chunking may be used by this header.
     *
     * @param chunkingAllowed <code>true</code> if chunked transfer-encoding
     *                        is allowed, otherwise returns  <code>false</code>.
     * @since 3.0
     */
    public void setChunkingAllowed(boolean chunkingAllowed) {
        this.chunkingAllowed = chunkingAllowed;
    }

    /**
     * @return <code>true</code> if a content type has been set.
     */
    public boolean isContentTypeSet() {
        return contentType.isMimeTypeSet() ||
                headers.getValue(Header.ContentType) != null;
    }


    /**
     * @return the content type of this HTTP message.
     */
    public String getContentType() {
        return contentType.get();
    }

    /**
     * Sets the content type.
     *
     * This method must preserve any charset that may already have
     * been set via a call to request/response.setContentType(),
     * request/response.setLocale(), or request/response.setCharacterEncoding().
     *
     * @param contentType the content type
     */
    public void setContentType(final String contentType) {
        this.contentType.set(contentType);
    }
    
    /**
     * Sets the content type.
     *
     * This method must preserve any charset that may already have
     * been set via a call to request/response.setContentType(),
     * request/response.setLocale(), or request/response.setCharacterEncoding().
     * 
     * This method copies the passed contentType state into this <tt>ContentType</tt>.
     *
     * @param contentType the content type
     */
    public void setContentType(final ContentType contentType) {
        this.contentType.set(contentType);
    }
    
    /**
     * @return {@link ContentType} holder
     */
    protected ContentType getContentTypeHolder() {
        return contentType;
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
    public String getHeader(final String name) {
        if (name == null) {
            return null;
        }
        
        String result = handleGetSpecialHeader(name);
        return (result != null) ? result : headers.getHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(final Header header) {
        if (header == null) {
            return null;
        }
        String result = handleGetSpecialHeader(header);
        return (result != null) ? result : headers.getHeader(header);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(final String name, final String value) {
        if (name == null || value == null) {
            return;
        }
        if (handleSetSpecialHeaders(name, value)) return;

        headers.setValue(name).setString(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(final String name, final HeaderValue value) {
        if (name == null || value == null || !value.isSet()) {
            return;
        }
        if (handleSetSpecialHeaders(name, value)) return;

        value.serializeToDataChunk(headers.setValue(name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(final Header header, final String value) {
        if (header == null || value == null) {
            return;
        }
        if (handleSetSpecialHeaders(header, value)) {
            return;
        }

        headers.setValue(header).setString(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(final Header header, final HeaderValue value) {
        if (header == null || value == null || !value.isSet()) {
            return;
        }
        if (handleSetSpecialHeaders(header, value)) {
            return;
        }

        value.serializeToDataChunk(headers.setValue(header));
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(final String name, final String value) {
        if (name == null || value == null) {
            return;
        }
        if (handleSetSpecialHeaders(name, value)) {
            return;
        }
        
        headers.addValue(name).setString(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(final String name, final HeaderValue value) {
        if (name == null || value == null || !value.isSet()) {
            return;
        }
        if (handleSetSpecialHeaders(name, value)) {
            return;
        }
        
        value.serializeToDataChunk(headers.setValue(name));
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(final Header header, final String value) {
        if (header == null || value == null) {
            return;
        }
        if (handleSetSpecialHeaders(header, value)) {
            return;
        }

        headers.addValue(header).setString(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(final Header header, final HeaderValue value) {
        if (header == null || value == null || !value.isSet()) {
            return;
        }
        if (handleSetSpecialHeaders(header, value)) {
            return;
        }

        value.serializeToDataChunk(headers.setValue(header));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(final String name) {
        if (name == null) {
            return false;
        }
        final String result = handleGetSpecialHeader(name);
        
        return result != null || headers.getHeader(name) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(final Header header) {
        if (header == null) {
            return false;
        }
        final String result = handleGetSpecialHeader(header);
        return result != null || headers.getHeader(header) != null;
    }

    /**
     * Get the HTTP message protocol version as {@link DataChunk}
     * (avoiding creation of a String object). The result format is "HTTP/1.x".
     * 
     * @return the HTTP message protocol version as {@link DataChunk}
     * (avoiding creation of a String object). The result format is "HTTP/1.x".
     */
    public DataChunk getProtocolDC() {
        // potentially the value might be changed, so we need to parse it again
        parsedProtocol = null;
        return protocolC;
    }

    /**
     * Get the HTTP message protocol version. The result format is "HTTP/1.x".
     *
     * @return the HTTP message protocol version. The result format is "HTTP/1.x".
     */
    public String getProtocolString() {
        if (parsedProtocol == null) {
            return getProtocolDC().toString();
        }

        return parsedProtocol.getProtocolString();
    }

    /**
     * Get HTTP protocol version.
     * @return {@link Protocol}.
     */
    public Protocol getProtocol() {
        if (parsedProtocol != null) {
            return parsedProtocol;
        }

        parsedProtocol = Protocol.valueOf(protocolC);
        return parsedProtocol;
    }

    /**
     * Set the HTTP message protocol version.
     * @param protocol {@link Protocol}
     */
    public void setProtocol(Protocol protocol) {
        parsedProtocol = protocol;
    }

    /**
     * @return <code>true</code> if this HTTP message is being transmitted
     *  in a secure fashion, otherwise returns <code>false</code>.
     */
    public boolean isSecure() {
        return secure;
    }

    /**
     * Sets the secure status of this HTTP message.
     *
     * @param secure <code>true</code> if secure, otherwise <code>false</code>.
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
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
     * Reset the internal state.
     */
    protected void reset() {
        isContentEncodingsSelected = false;
        secure = false;
        isSkipRemainder = false;
        isContentBroken = false;
        if (activeAttributes != null) {
            activeAttributes.recycle();
            activeAttributes = null;
        }
        protocolC.recycle();
        parsedProtocol = null;
        contentEncodings.clear();
        headers.clear();
        isCommitted = false;
        isChunked = false;
        contentLength = -1;
        contentType.reset();
        chunkingAllowed = false;
        transferEncoding = null;
        isExpectContent = true;
        upgrade.recycle();
        isIgnoreContentModifiers = false;
        if (headerBuffer != null) {
            headerBuffer.dispose();
            headerBuffer = null;
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle() {
        reset();
    }

    private final String handleGetSpecialHeader(final String name) {
        return ((isSpecialHeader(name)) ? getValueBasedOnHeader(name) : null);
    }

    private final String handleGetSpecialHeader(final Header header) {
        return ((isSpecialHeader(header.toString())) ? getValueBasedOnHeader(header) : null);
    }

    private final boolean handleSetSpecialHeaders(final String name, final String value) {
        return isSpecialHeaderSet(name) && setValueBasedOnHeader(name, value);
    }

    private final boolean handleSetSpecialHeaders(final String name, final HeaderValue value) {
        return isSpecialHeaderSet(name) && setValueBasedOnHeader(name, value.get());
    }
    
    private final boolean handleSetSpecialHeaders(final Header header, final String value) {
        return isSpecialHeaderSet(header.toString()) && setValueBasedOnHeader(header, value);
    }

    private final boolean handleSetSpecialHeaders(final Header header, final HeaderValue value) {
        return isSpecialHeaderSet(header.toString()) && setValueBasedOnHeader(header, value.get());
    }
    
    private static boolean isSpecialHeader(final String name) {
        return isSpecialHeader(name.charAt(0));
    }

    private static boolean isSpecialHeaderSet(final String name) {
        final char c = name.charAt(0);
        return (isSpecialHeader(c) || (c == 'T' || c == 't'));
    }

    private static boolean isSpecialHeader(final char c) {
        return (c == 'C' || c == 'c' || c == 'U' || c == 'u');
    }



    public byte[] getTempHeaderEncodingBuffer() {
        return tmpHeaderEncodingBuffer;
    }

    // --------------------------------------------------------- Private Methods

    private String getValueBasedOnHeader(final Header header) {
        if (Header.ContentType.equals(header)) {
            final String value = getContentType();
            if (value != null) {
                return value;
            }
        } else if (Header.ContentLength.equals(header)) {
            final long value = getContentLength();
            if (value >= 0) {
                return Long.toString(value);
            }
        } else if (Header.Upgrade.equals(header)) {
            return getUpgrade();
        }
        return null;
    }

    private String getValueBasedOnHeader(final String name) {
        if (Header.ContentType.toString().equalsIgnoreCase(name)) {
            final String value = getContentType();
            if (value != null) {
                return value;
            }
        } else if (Header.ContentLength.toString().equalsIgnoreCase(name)) {
            final long value = getContentLength();
            if (value >= 0) {
                return Long.toString(value);
            }
        } else if (Header.Upgrade.toString().equalsIgnoreCase(name)) {
            return getUpgrade();
        }
        return null;
    }


    /**
     * Set internal fields for special header names.
     * Called from set/addHeader.
     * Return true if the header is special, no need to set the header.
     */
    private boolean setValueBasedOnHeader(final String name, final String value) {
        if (Header.ContentType.toString().equalsIgnoreCase(name)) {
            setContentType(value);
            return true;
        } else if (Header.ContentLength.toString().equalsIgnoreCase(name)) {
            headers.removeHeader(Header.TransferEncoding);
            setChunked(false);
            return setContentLenth(value);
        } else if (Header.Upgrade.toString().equalsIgnoreCase(name)) {
            setUpgrade(value);
        } else if (Header.TransferEncoding.toString().equalsIgnoreCase(name)) {
            if ("chunked".equalsIgnoreCase(value)) {
                setContentLengthLong(-1);
                setChunked(true);
            }
            return true;
        }
        //if (name.equalsIgnoreCase("Content-Language")) {
        //    // TODO XXX XXX Need to construct Locale or something else
        //}
        return false;
    }


    /**
     * Set internal fields for special header names.
     * Called from set/addHeader.
     * Return true if the header is special, no need to set the header.
     */
    private boolean setValueBasedOnHeader(final Header header, final String value) {
        if (Header.ContentType.equals(header)) {
            setContentType(value);
            return true;
        } else if (Header.ContentLength.equals(header)) {
            headers.removeHeader(Header.TransferEncoding);
            setChunked(false);
            return setContentLenth(value);
        } else if (Header.Upgrade.equals(header)) {
            setUpgrade(value);
        } else if (Header.TransferEncoding.equals(header)) {
            if ("chunked".equalsIgnoreCase(value)) {
                setContentLengthLong(-1);
                setChunked(true);
            }
            return true;
        }
        //if (name.equalsIgnoreCase("Content-Language")) {
        //    // TODO XXX XXX Need to construct Locale or something else
        //}
        return false;
    }


    private boolean setContentLenth(String value) {
        try {
            final long cLL = Long.parseLong(value);
            setContentLengthLong(cLL);
            return true;
        } catch (NumberFormatException ex) {
            // Do nothing - the spec doesn't have any "throws"
            // and the user might know what he's doing
            return false;
        }
    }
    
    /**
     * Flush internal fields for special header names to the headers map.
     */
    protected void flushSpecialHeaders() {
        if (contentLength >= 0) {
            headers.setValue(Header.ContentLength).setString(String.valueOf(contentLength));
        }
        
        final String ct = getContentType();
        if (ct != null) {
            headers.setValue(Header.ContentType).setString(String.valueOf(ct));
        }
        
        if (!upgrade.isNull()) {
            headers.setValue(Header.Upgrade).setString(upgrade.toString());
        }
    }
    
    /**
     * <tt>HttpHeader</tt> message builder.
     */
    public static abstract class Builder<T extends Builder> {

        protected Protocol protocol;
        protected String protocolString;
        protected Boolean chunked;
        protected Long contentLength;
        protected String contentType;
        protected String upgrade;
        protected MimeHeaders mimeHeaders;

        /**
         * Set the HTTP message protocol version.
         * @param protocol {@link Protocol}
         */
        @SuppressWarnings({"unchecked"})
        public final T protocol(Protocol protocol) {
            this.protocol = protocol;
            protocolString = null;
            return (T) this;
        }

        /**
         * Set the HTTP message protocol version.
         * @param protocolString protocol version in format "HTTP/1.x".
         */
        @SuppressWarnings({"unchecked"})
        public final T protocol(String protocolString) {
            this.protocolString = protocolString;
            protocol = null;
            return (T) this;
        }

        /**
         * Set <tt>true</tt>, if this {@link HttpPacket} content will be transferred
         * in chunking mode, or <tt>false</tt> if case of fixed-length message.
         *
         * @param chunked  <tt>true</tt>, if this {@link HttpPacket} content
         * will be transferred in chunking mode, or <tt>false</tt> if case
         * of fixed-length message.
         */
        @SuppressWarnings({"unchecked"})
        public final T chunked(boolean chunked) {
            this.chunked = chunked;
            contentLength = null;
            return (T) this;
        }

        /**
         * Set the content-length of this {@link HttpPacket}. Applicable only in case
         * of fixed-length HTTP message.
         *
         * @param contentLength  the content-length of this {@link HttpPacket}.
         * Applicable only in case of fixed-length HTTP message.
         */
        @SuppressWarnings({"unchecked"})
        public final T contentLength(long contentLength) {
            this.contentLength = contentLength;
            chunked = null;
            return (T) this;
        }

        /**
         * Set the content-type of this {@link HttpPacket}.
         *
         * @param contentType  the content-type of this {@link HttpPacket}.
         */
        @SuppressWarnings({"unchecked"})
        public final T contentType(String contentType) {
            this.contentType = contentType;
            return (T) this;
        }

        /**
         * Set the HTTP upgrade type.
         *
         * @param upgrade the type of upgrade.
         */
        @SuppressWarnings({"unchecked"})
        public final T upgrade(String upgrade) {
            this.upgrade = upgrade;
            return (T) this;
        }

        /**
         * Add the HTTP mime header.
         *
         * @param name the mime header name.
         * @param value the mime header value.
         */
        @SuppressWarnings({"unchecked"})
        public final T header(String name, String value) {
            if (mimeHeaders == null) {
                mimeHeaders = new MimeHeaders();
            }
            mimeHeaders.addValue(name).setString(value);
            handleSpecialHeaderAdd(name, value);
            return (T) this;
        }

        /**
         * Remove the specified header from this builder.  This method is only
         * useful if using the same builder to create multiple objects.
         *
         * @param header the mime header.
         * @return this
         */
        @SuppressWarnings({"unchecked"})
        public final T removeHeader(String header) {
            if (mimeHeaders != null) {
                mimeHeaders.removeHeader(header);
                handleSpecialHeaderRemove(header);
            }
            return (T) this;
        }

        /**
         * Add the HTTP mime header.
         *
         * @param header the mime {@link Header}.
         * @param value the mime header value.
         */
        @SuppressWarnings({"unchecked"})
        public final T header(Header header, String value) {
            if (mimeHeaders == null) {
                mimeHeaders = new MimeHeaders();
            }
            mimeHeaders.addValue(header).setString(value);
            handleSpecialHeaderAdd(header.toString(), value);

            return (T) this;
        }

        /**
         * Remove the specified header from this builder.  This method is only
         * useful if using the same builder to create multiple objects.
         *
         * @param header the mime {@link Header}.
         * @return this
         */
        @SuppressWarnings({"unchecked"})
        public final T removeHeader(Header header) {
            if (mimeHeaders != null) {
                mimeHeaders.removeHeader(header);
                handleSpecialHeaderRemove(header.toString());
            }
            return (T) this;
        }
        
        /**
         * Sets the maximum number of headers allowed.
         */
        @SuppressWarnings({"unchecked"})
        public final T maxNumHeaders(int maxHeaders) {
            if (mimeHeaders == null) {
                mimeHeaders = new MimeHeaders();
            }
            mimeHeaders.setMaxNumHeaders(maxHeaders);
            return (T) this;
        }

        public HttpHeader build() {
            HttpHeader httpHeader = create();
            if (protocol != null) {
                httpHeader.setProtocol(protocol);
            }
            if (protocolString != null) {
                httpHeader.protocolC.setString(protocolString);
            }
            if (chunked != null) {
                httpHeader.setChunked(chunked);
            }
            if (contentLength != null) {
                httpHeader.setContentLengthLong(contentLength);
            }
            if (contentType != null) {
                httpHeader.setContentType(contentType);
            }
            if (upgrade != null) {
                httpHeader.setUpgrade(upgrade);
            }
            if (mimeHeaders != null && mimeHeaders.size() > 0) {
                httpHeader.getHeaders().copyFrom(mimeHeaders);
            }

            return httpHeader;
        }

        public void reset() {
            protocol = null;
            protocolString = null;
            chunked = null;
            contentLength = null;
            contentType = null;
            upgrade = null;
            mimeHeaders.recycle();
        }

        protected abstract HttpHeader create();

        private void handleSpecialHeaderAdd(final String name,
                                            final String value) {
            final char c = name.charAt(0);
            boolean isC = (c == 'c' || c == 'C');
            if (isC && Header.ContentLength.toString().equals(name)) {
                contentLength = Long.parseLong(value);
                return;
            }
            boolean isU = (c == 'u' || c == 'U');
            if (isU && Header.Upgrade.toString().equals(name)) {
                upgrade = value;
            }
        }

        private void handleSpecialHeaderRemove(final String name) {
            final char c = name.charAt(0);
            boolean isC = (c == 'c' || c == 'C');
            if (isC && Header.ContentLength.toString().equals(name)) {
                contentLength = null;
                return;
            }
            boolean isU = (c == 'u' || c == 'U');
            if (isU && Header.Upgrade.toString().equals(name)) {
                upgrade = null;
            }
        }
        
    }
}
