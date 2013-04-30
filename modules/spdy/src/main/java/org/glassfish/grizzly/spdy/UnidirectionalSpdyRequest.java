/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.ProcessingState;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;

/**
 * This {@link HttpRequestPacket} represents unidirection SPDY stream request.
 * 
 * @author Alexey Stashok
 */
class UnidirectionalSpdyRequest extends HttpRequestPacket implements SpdyHeader {
    private static final Logger LOGGER = Grizzly.logger(UnidirectionalSpdyRequest.class);
    
    private static final ThreadCache.CachedTypeIndex<UnidirectionalSpdyRequest> CACHE_IDX =
            ThreadCache.obtainIndex(UnidirectionalSpdyRequest.class, 2);

    public static UnidirectionalSpdyRequest create() {
        UnidirectionalSpdyRequest spdyRequest =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (spdyRequest == null) {
            spdyRequest = new UnidirectionalSpdyRequest();
        }

        return spdyRequest.init();
    }
    
    /**
     * Returns {@link UnidirectionalSpdyRequest} builder.
     *
     * @return {@link Builder}.
     */
    public static Builder unidirectionalBuilder() {
        return new Builder();
    }
    
    private final ProcessingState processingState = new ProcessingState();
    
    /**
     * Char encoding parsed flag.
     */
    private boolean charEncodingParsed;
    private boolean contentTypeParsed;
    

    /**
     * Status code.
     */
    private HttpStatus unidirectionalStatusCode;
    
    /**
     * Status message.
     */
    private final DataChunk unidirectionalReasonPhraseC = DataChunk.newInstance();
    
    
    @Override
    public ProcessingState getProcessingState() {
        return processingState;
    }

    protected UnidirectionalSpdyRequest init() {
        setChunkingAllowed(true);
        
        return this;
    }

    @Override
    public SpdyStream getSpdyStream() {
        return SpdyStream.getSpdyStream(this);
    }
    
    @Override
    public String getCharacterEncoding() {
        if (characterEncoding != null || charEncodingParsed) {
            return characterEncoding;
        }

        getContentType(); // charEncoding is set as a side-effect of this call
        charEncodingParsed = true;

        return characterEncoding;
    }

    @Override
    public String getContentType() {
        if (!contentTypeParsed) {
            contentTypeParsed = true;

            if (contentType == null) {
                final DataChunk dc = headers.getValue(Header.ContentType);

                if (dc != null && !dc.isNull()) {
                    setContentType(dc.toString());
                }
            }
        }

        return super.getContentType();
    }

    /**
     * Gets the HTTP status for this unidirectional SPDY request.
     *
     * @return the HTTP status for this unidirectional SPDY request.
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public HttpStatus getUnidirectionalHttpStatus() {
        checkUnidirectional();
        return unidirectionalStatusCode;
    }

    /**
     * Sets the status code for this unidirectional SPDY request.
     *
     * @param status the status for this response.
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public void setUnidirectionalStatus(
            final HttpStatus unidirectionalStatusCode) {
        checkUnidirectional();
        this.unidirectionalStatusCode = unidirectionalStatusCode;
    }
    
    /**
     * Gets the status code for this unidirectional SPDY request.
     *
     * @return the status code for this unidirectional SPDY request.
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public int getUnidirectionalStatus() {
        checkUnidirectional();
        return getUnidirectionalHttpStatus().getStatusCode();
    }
    
    /**
     * Sets the status code for this unidirectional SPDY request.
     *
     * @param status the status code for this unidirectional SPDY request.
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public void setUnidirectionalStatus(final int status) {
        checkUnidirectional();
        // the order is important here as statusDC.setXXX will reset the parsedIntStatus
        unidirectionalStatusCode = HttpStatus.getHttpStatus(status);
    }
    
    /**
     * Gets the custom status reason phrase for this unidirectional SPDY request
     * as {@link DataChunk} (avoid creation of a String object).
     *
     * @return the status reason phrase for this unidirectional SPDY request
     * as {@link DataChunk} (avoid creation of a String object).
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public final DataChunk getUnidirectionalReasonPhraseRawDC() {
        checkUnidirectional();
        return unidirectionalReasonPhraseC;
    }

    /**
     * Gets the status reason phrase for this unidirectional SPDY request
     * as {@link DataChunk} (avoid creation of a String object).
     * This implementation takes into consideration the custom reason phrase.
     * If its value is not null - then the returned result will be equal to
     * {@link #getReasonPhraseRawDC()}, otherwise if custom reason phrase is
     * null - the default reason phrase for the HTTP response
     * {@link #getStatus()} will be returned.
     *
     * @return the status reason phrase for this unidirectional SPDY request
     * as {@link DataChunk} (avoid creation of a String object).
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public final DataChunk getUnidirectionalReasonPhraseDC() {
        checkUnidirectional();
        if (isUnidirectionalCustomReasonPhraseSet()) {
            return unidirectionalReasonPhraseC;
        } else {
            unidirectionalReasonPhraseC.setBytes(
                    unidirectionalStatusCode.getReasonPhraseBytes());
            return unidirectionalReasonPhraseC;
        }
    }

    /**
     * Gets the status reason phrase for this unidirectional SPDY request.
     *
     * @return the status reason phrase for this unidirectional SPDY request.
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public final String getUnidirectionalReasonPhrase() {
        checkUnidirectional();
        return getUnidirectionalReasonPhraseDC().toString();
    }

    /**
     * Sets the status reason phrase for this unidirectional SPDY request.
     *
     * @param message the status reason phrase for this unidirectional SPDY request.
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public void setUnidirectionalReasonPhrase(final String message) {
        checkUnidirectional();
        unidirectionalReasonPhraseC.setString(message);
    }

    /**
     * Returns <tt>true</tt> if custom reason phrase is set for this
     * unidirectional SPDY request, or <tt>false</tt> otherwise.
     * 
     * @throws {@link IllegalStateException} if this SPDY request is not unidirectional.
     */
    public final boolean isUnidirectionalCustomReasonPhraseSet() {
        checkUnidirectional();
        return !unidirectionalReasonPhraseC.isNull();
    }

    private void checkUnidirectional() {
        final SpdyStream spdyStream = getSpdyStream();
        
        if (spdyStream != null && !spdyStream.isUnidirectional()) {
            throw new IllegalStateException("This is not unidirectional SPDY request");
        }
    }

    @Override
    protected void reset() {
        charEncodingParsed = false;
        contentTypeParsed = false;
        unidirectionalStatusCode = null;
        unidirectionalReasonPhraseC.recycle();
        
        processingState.recycle();
        
        super.reset();
    }

    @Override
    public void recycle() {
        reset();

        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public void setExpectContent(final boolean isExpectContent) {
        super.setExpectContent(isExpectContent);
    }
    
    /**
     * <tt>UnidirectionalSpdyRequest</tt> message builder.
     */
    public static class Builder extends
            HttpHeader.Builder<UnidirectionalSpdyRequest.Builder> {
        
        protected Builder() {
            packet = UnidirectionalSpdyRequest.create();
        }

        /**
         * Set the request URI.
         *
         * @param uri the request URI.
         */
        public UnidirectionalSpdyRequest.Builder uri(final String uri) {
            ((HttpRequestPacket) packet).setRequestURI(uri);
            return this;
        }

        /**
         * Set the unidirectional request status (used in server push).
         *
         * @param status the request {@link HttpStatus}.
         */
        public UnidirectionalSpdyRequest.Builder status(final HttpStatus status) {
            ((UnidirectionalSpdyRequest) packet).setUnidirectionalStatus(status);
            return this;
        }

        /**
         * Set the unidirectional request status (used in server push).
         *
         * @param status the status code
         */
        public UnidirectionalSpdyRequest.Builder status(final int status) {
            status(HttpStatus.getHttpStatus(status));
            return this;
        }

        /**
         * Set the unidirectional request status (used in server push).
         *
         * @param status the status code
         * @param reasonPhrase the reason phrase
         */
        public UnidirectionalSpdyRequest.Builder status(final int status,
                final String reasonPhrase) {
            status(status);
            ((UnidirectionalSpdyRequest) packet).setUnidirectionalReasonPhrase(reasonPhrase);
            
            return this;
        }

        /**
         * Build the <tt>UnidirectionalSpdyRequest</tt> message.
         *
         * @return <tt>UnidirectionalSpdyRequest</tt>
         */
        public final UnidirectionalSpdyRequest build() {
            return (UnidirectionalSpdyRequest) packet;
        }
    }
}
