/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
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
    protected final MimeHeaders headers = new MimeHeaders();
    
    protected final DataChunk protocolC = DataChunk.newInstance();
    protected Protocol parsedProtocol;

    protected boolean isChunked;
    private final byte[] tmpContentLengthBuffer = new byte[20];
    private final byte[] tmpHeaderEncodingBuffer = new byte[512];
    
    protected long contentLength = -1;

    protected String characterEncoding;
    protected String quotedCharsetValue;
    /**
     * Has the charset been explicitly set.
     */
    protected boolean charsetSet = false;

//    private String defaultContentType;

    protected String contentType;

    protected boolean isExpectContent = true;

    protected boolean isSkipRemainder;
    
    /**
     * <tt>true</tt> if HTTP message payload is broken due to inappropriate
     * Transfer-Encoding or Content-Encoding settings.
     */
    protected boolean isContentBroken;
    
    protected boolean secure;

    protected final DataChunk upgrade = DataChunk.newInstance();

    private TransferEncoding transferEncoding;
    private final List<ContentEncoding> contentEncodings =
            new ArrayList<ContentEncoding>(2);
    // <tt>true</tt>, if content encodings for this headers were chosen
    private boolean isContentEncodingsSelected;

    private final AttributeHolder attributes =
            new IndexedAttributeHolder(Grizzly.DEFAULT_ATTRIBUTE_BUILDER);
    private AttributeHolder activeAttributes;

    Buffer headerBuffer;
    
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
     * @param isChunked  <tt>true</tt>, if this {@link HttpPacket} content
     * will be transferred in chunking mode, or <tt>false</tt> if case
     * of fixed-length message.
     */
    public void setChunked(boolean isChunked) {
        this.isChunked = isChunked;
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

    protected void setExpectContent(boolean isExpectContent) {
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
    
    public String getUpgrade() {
        return upgrade.toString();
    }

    public DataChunk getUpgradeDC() {
        return upgrade;
    }

    public void setUpgrade(String upgrade) {
        this.upgrade.setString(upgrade);
    }

    protected void makeUpgradeHeader() {
        if (!upgrade.isNull()) {
            headers.setValue(Header.Upgrade).set(upgrade);
        }
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
        this.contentLength = len;
        if (len < 0) {
            headers.removeHeader(Header.ContentLength);
        }
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
        if (contentLength < 0) {
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
            headers.getAndSetSerialized(idx, true);
            value.set(headers.getValue(idx));
        }
    }

    /**
     * @return the character encoding of this HTTP message.
     */
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    /**
     * Set the character encoding of this HTTP message.
     *
     * @param charset the encoding.
     */
    public void setCharacterEncoding(final String charset) {

        if (isCommitted())
            return;
        if (charset == null) {
            characterEncoding = null;
            quotedCharsetValue = null;
            charsetSet = false;
            return;
        }

        characterEncoding = charset;
        // START SJSAS 6316254
        quotedCharsetValue = charset;
        // END SJSAS 6316254
        charsetSet = true;
    }

    /**
     * Serializes Content-Type header into passed Buffer
     */
//    protected final Buffer serializeContentType(final MemoryManager memoryManager,
//            Buffer buffer) {
//
//        final int idx = headers.indexOf(Header.ContentType, 0);
//        final DataChunk value;
//        if (idx != -1 && !((value = headers.getValue(idx)).isNull())) {
//            if (contentType == null) {
//                contentType = value.toString();
//            }
//            
//            headers.getAndSetSerialized(idx, true);
//        }
//
//        if (contentType != null) {
//            buffer = put(memoryManager, buffer, Header.ContentType.getBytes());
//            buffer = put(memoryManager, buffer, HttpCodecFilter.COLON_BYTES);
//            buffer = put(memoryManager, buffer, contentType);
//
//            if (quotedCharsetValue != null && charsetSet) {
//                buffer = put(memoryManager, buffer, ";charset=");
//                buffer = put(memoryManager, buffer, quotedCharsetValue);
//            }
//            
//            buffer = put(memoryManager, buffer, HttpCodecFilter.CRLF_BYTES);
//        } else {
//            final String defaultType = getDefaultContentType();
//            if (defaultType != null) {
//                buffer = put(memoryManager, buffer, Header.ContentType.getBytes());
//                buffer = put(memoryManager, buffer, HttpCodecFilter.COLON_BYTES);
//                buffer = put(memoryManager, buffer, defaultType);
//                buffer = put(memoryManager, buffer, HttpCodecFilter.CRLF_BYTES);
//            }
//        }
//        
//        return buffer;
//    }

    /**
     * @return <code>true</code> if a content type has been set.
     */
    public boolean isContentTypeSet() {

        return (contentType != null
                    || characterEncoding != null
                    || headers.getValue(Header.ContentType) != null);

    }


    /**
     * @return the content type of this HTTP message.
     */
    public String getContentType() {
        String ret = contentType;

        if (ret != null
                && quotedCharsetValue != null
                && charsetSet) {
            final StringBuilder sb =
                    new StringBuilder(ret.length() + quotedCharsetValue.length() + 9);
            ret = sb.append(ret).append(";charset=").append(quotedCharsetValue).toString();
        }

        return ret;
    }

    /**
     * Sets the content type.
     *
     * This method must preserve any charset that may already have
     * been set via a call to request/response.setContentType(),
     * request/response.setLocale(), or request/response.setCharacterEncoding().
     *
     * @param type the content type
     */
    public void setContentType(final String type) {

        if (type == null) {
            contentType = null;
            return;
        }

        /*
         * Remove the charset param (if any) from the Content-Type, and use it
         * to set the response encoding.
         * The most recent response encoding setting will be appended to the
         * response's Content-Type (as its charset param) by getContentType();
         */
        boolean hasCharset = false;
        int semicolonIndex = -1;
        int index = type.indexOf(';');
        while (index != -1) {
            int len = type.length();
            semicolonIndex = index;
            index++;
            while (index < len && type.charAt(index) == ' ') {
                index++;
            }
            if (index+8 < len
                    && type.charAt(index) == 'c'
                    && type.charAt(index+1) == 'h'
                    && type.charAt(index+2) == 'a'
                    && type.charAt(index+3) == 'r'
                    && type.charAt(index+4) == 's'
                    && type.charAt(index+5) == 'e'
                    && type.charAt(index+6) == 't'
                    && type.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = type.indexOf(';', index);
        }

        if (!hasCharset) {
            contentType = type;
            return;
        }

        contentType = type.substring(0, semicolonIndex);
        String tail = type.substring(index+8);
        int nextParam = tail.indexOf(';');
        String charsetValue;
        if (nextParam != -1) {
            contentType += tail.substring(nextParam);
            charsetValue = tail.substring(0, nextParam);
        } else {
            charsetValue = tail;
        }

        // The charset value may be quoted, but must not contain any quotes.
        if (charsetValue != null && charsetValue.length() > 0) {
            charsetSet=true;
            // START SJSAS 6316254
            quotedCharsetValue = charsetValue;
            // END SJSAS 6316254
            characterEncoding = charsetValue.replace('"', ' ').trim();
        }
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
    public void setHeader(String name, String value) {
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
    public void setHeader(Header header, String value) {
        if (header == null || value == null) {
            return;
        }
        final String name = header.toString();
        if (handleSetSpecialHeaders(name, value)) {
            return;
        }

        headers.setValue(header).setString(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(String name, String value) {
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
    public void addHeader(Header header, String value) {
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
    public boolean containsHeader(String name) {
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
        characterEncoding = null;
//        defaultContentType = null;
        quotedCharsetValue = null;
        charsetSet = false;
        contentType = null;
        transferEncoding = null;
        isExpectContent = true;
        upgrade.recycle();
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

    protected final String handleGetSpecialHeader(final String name) {
        return ((isSpecialHeader(name)) ? getValueBasedOnHeader(name) : null);
    }

    protected final String handleGetSpecialHeader(final Header header) {
        return ((isSpecialHeader(header.toString())) ? getValueBasedOnHeader(header) : null);
    }

    protected final boolean handleSetSpecialHeaders(final String name, final String value) {
        return isSpecialHeader(name) && setValueBasedOnHeader(name, value);
    }

    protected final boolean handleSetSpecialHeaders(final Header header, final String value) {
        return isSpecialHeader(header.toString()) && setValueBasedOnHeader(header, value);
    }

    protected static boolean isSpecialHeader(final String name) {
        final char c = name.charAt(0);
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
            try {
                final long cLL = Long.parseLong(value);
                setContentLengthLong(cLL);
                return true;
            } catch (NumberFormatException ex) {
                // Do nothing - the spec doesn't have any "throws"
                // and the user might know what he's doing
                return false;
            }
        } else if (Header.Upgrade.toString().equalsIgnoreCase(name)) {
            setUpgrade(value);
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
            try {
                final long cLL = Long.parseLong(value);
                setContentLengthLong(cLL);
                return true;
            } catch (NumberFormatException ex) {
                // Do nothing - the spec doesn't have any "throws"
                // and the user might know what he's doing
                return false;
            }
        } else if (Header.Upgrade.equals(header)) {
            setUpgrade(value);
        }
        //if (name.equalsIgnoreCase("Content-Language")) {
        //    // TODO XXX XXX Need to construct Locale or something else
        //}
        return false;
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
    
//    protected String getDefaultContentType() {
//        return defaultContentType;
//    }
//
//    protected void setDefaultContentType(String defaultContentType) {
//        this.defaultContentType = defaultContentType;
//    }

    /**
     * <tt>HttpHeader</tt> message builder.
     */
    public static abstract class Builder<T extends Builder> {

        protected HttpHeader packet;

        /**
         * Set the HTTP message protocol version.
         * @param protocol {@link Protocol}
         */
        @SuppressWarnings({"unchecked"})
        public final T protocol(Protocol protocol) {
            packet.setProtocol(protocol);
            return (T) this;
        }

        /**
         * Set the HTTP message protocol version.
         * @param protocol protocol version in format "HTTP/1.x".
         */
        @SuppressWarnings({"unchecked"})
        public final T protocol(String protocol) {
            packet.getProtocolDC().setString(protocol);
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
        @SuppressWarnings({"unchecked"})
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
        @SuppressWarnings({"unchecked"})
        public final T contentLength(long contentLength) {
            packet.setContentLengthLong(contentLength);
            return (T) this;
        }

        /**
         * Set the content-type of this {@link HttpPacket}.
         *
         * @param contentType  the content-type of this {@link HttpPacket}.
         */
        @SuppressWarnings({"unchecked"})
        public final T contentType(String contentType) {
            packet.setContentType(contentType);
            return (T) this;
        }

        /**
         * Set the HTTP upgrade type.
         *
         * @param upgrade the type of upgrade.
         */
        @SuppressWarnings({"unchecked"})
        public final T upgrade(String upgrade) {
            packet.setUpgrade(upgrade);
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
            packet.addHeader(name, value);
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
            packet.addHeader(header, value);
            return (T) this;
        }
        
        /**
         * Sets the maximum number of headers allowed.
         */
        @SuppressWarnings({"unchecked"})
        public final T maxNumHeaders(int num) {
            packet.getHeaders().setMaxNumHeaders(num);
            return (T) this;
        }
        
    }
}
