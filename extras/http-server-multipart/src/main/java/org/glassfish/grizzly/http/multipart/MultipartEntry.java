/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.multipart;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.io.NIOReader;
import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.http.util.Header;

/**
 * Abstraction represents single multipart entry, its functionality is pretty
 * similar to {@link Request}.
 * In order to read multipart entry data it's possible to use either {@link #getNIOInputStream()}
 * or {@link #getNIOReader()} depends on whether we want to operate with binary or
 * {@link String} data.
 * 
 * @since 2.0.1
 *
 * @author Alexey Stashok
 */
public class MultipartEntry {

    private static final String DEFAULT_CONTENT_TYPE =
            "text/plain; charset=US-ASCII";
    private static final String DEFAULT_CONTENT_ENCODING =
            "US-ASCII";

    private NIOInputStream requestInputStream;
    
    private final MultipartContext multipartContext;
    private final MultipartEntryNIOInputStream inputStream;
    private final MultipartEntryNIOReader reader;

    private final Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

    private String contentType = DEFAULT_CONTENT_TYPE;
    private ContentDisposition contentDisposition;

    private int availableBytes;

    // Previous (processed) line terminator bytes, which we're not sure about,
    // whether they are part of section boundary or multipart entry content
    private int reservedBytes;
    
    private boolean isFinished;

    private boolean isSkipping;
    
    /**
     * Using stream flag.
     */
    protected boolean usingInputStream = false;

    /**
     * Using writer flag.
     */
    protected boolean usingReader = false;

    /**
     * Is this entry multipart/mixed
     */
    private boolean isMultipart;

    /**
     * Have we parsed content-type and figured out whether it's multipart/mixed?
     */
    private boolean isMultipartParsed;

    MultipartEntry(final MultipartContext multipartContext) {
        inputStream = new MultipartEntryNIOInputStream(this);
        reader = new MultipartEntryNIOReader(this);
        this.multipartContext = multipartContext;
    }

    void initialize(final NIOInputStream parentInputStream) {
        this.requestInputStream = parentInputStream;
    }

    public NIOInputStream getNIOInputStream() {
        if (usingReader)
            throw new IllegalStateException("MultipartEntry is in the character mode");

        if (!usingInputStream) {
            inputStream.initialize(requestInputStream);
        }
        
        usingInputStream = true;
        
        return inputStream;
    }

    public NIOReader getNIOReader() {
        if (usingInputStream)
            throw new IllegalStateException("MultipartEntry is in the binary mode");

        if (!usingReader) {
            reader.initialize(requestInputStream, getEncoding());
        }

        usingReader = true;

        return reader;
    }

    /**
     * Get multipart processing context.
     * 
     * @return {@link MultipartContext}.
     */
    public MultipartContext getMultipartContext() {
        return multipartContext;
    }
    
    /**
     * Returns <tt>true</tt> if this is "multipart/*" multipart entry, or
     * <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt> if this is "multipart/*" multipart entry, or
     * <tt>false</tt> otherwise.
     */
    public boolean isMultipart() {
        if (!isMultipartParsed) {
            isMultipartParsed = true;

            isMultipart = contentType != null &&
                    contentType.toLowerCase().startsWith(
                    MultipartScanner.MULTIPART_CONTENT_TYPE);
        }

        return isMultipart;
    }

    /**
     * Get the multipart entry content-type.
     * @return the multipart entry content-type.
     */
    public String getContentType() {
        return contentType;
    }

    void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    /**
     * Get the multipart entry content-disposition.
     * @return the multipart entry content-disposition.
     */
    public ContentDisposition getContentDisposition() {
        return contentDisposition;
    }

    void setContentDisposition(final ContentDisposition contentDisposition) {
        this.contentDisposition = contentDisposition;
    }

    /**
     * Get the multipart entry header names.
     * @return the multipart entry header names.
     */
    public Set<String> getHeaderNames() {
        return headers.keySet();
    }

    /**
     * Get the multipart entry header value.
     * 
     * @param name multipart entry header name.
     * @return the multipart entry header value.
     */
    public String getHeader(final String name) {
        return headers.get(name);
    }

    void setHeader(final String name, final String value) {
        headers.put(name, value);
    }

    /**
     * Get the multipart entry header value.
     *
     * @param header entry header.
     * @return the multipart entry header value.
     *
     * @since 2.1.2
     */
    public String getHeader(final Header header) {
        return headers.get(header.toString());
    }

    /**
     *
     * @param header
     * @param value
     *
     * @since 2.1.2
     */
    void setHeader(final Header header, final String value) {
        headers.put(header.toString(), value);
    }

    /**
     * Skip the multipart entry processing.
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    public void skip() throws IOException {
        isSkipping = true;
        requestInputStream.skip(availableBytes);
        availableBytes = 0;
    }

    protected String getEncoding() {
        String contentEncoding = ContentType.getCharsetFromContentType(getContentType());
        return contentEncoding != null ? contentEncoding : DEFAULT_CONTENT_ENCODING;
    }

    void reset() {
        headers.clear();
        contentType = DEFAULT_CONTENT_TYPE;
        contentDisposition = null;
        availableBytes = 0;
        reservedBytes = 0;
        isFinished = false;
        isSkipping = false;
        usingInputStream = false;
        usingReader = false;
        inputStream.recycle();
        reader.recycle();
        isMultipartParsed = false;
    }

    void onFinished() throws Exception {
        isFinished = true;
        onDataReceived();
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    void onDataReceived() throws Exception {
        if (isSkipping) {
            try {
                requestInputStream.skip(availableBytes);
                availableBytes = 0;
            } catch (IOException e) {
                throw new IllegalStateException("Unexpected exception", e);
            }

            return;
        }

        if (usingInputStream) {
            inputStream.onDataCame();
        } else if (usingReader) {
            reader.onDataCame();
        }
    }

    boolean isFinished() {
        return isFinished;
    }

    int availableBytes() {
        return availableBytes;
    }

    void addAvailableBytes(final int delta) {
        availableBytes += delta;
    }

    /**
     * Get the previous (processed) line terminator bytes, which we're not sure about,
     * whether they are part of section boundary or multipart entry content
     * 
     * @return the previous (processed) line terminator bytes, which we're not sure about,
     * whether they are part of section boundary or multipart entry content
     */
    int getReservedBytes() {
        return reservedBytes;
    }

    /**
     * Set the previous (processed) line terminator bytes, which we're not sure about,
     * whether they are part of section boundary or multipart entry content
     *
     * @param reservedBytes the previous (processed) line terminator bytes,
     * which we're not sure about, whether they are part of section boundary or
     * multipart entry content
     */
    void setReservedBytes(int reservedBytes) {
        this.reservedBytes = reservedBytes;
    }
}
