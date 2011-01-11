/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.http.util.DataChunk;

import java.util.Locale;
import org.glassfish.grizzly.http.util.Charsets;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.memory.Buffers;


/**
 * The {@link HttpHeader} object, which represents HTTP response message.
 *
 * @see HttpHeader
 * @see HttpRequestPacket
 *
 * @author Alexey Stashok
 */
public abstract class HttpResponsePacket extends HttpHeader {

    // ----------------------------------------------------- Instance Variables

    /**
     * The request that triggered this response.
     */
    private HttpRequestPacket request;

    /**
     * The {@link Locale} of the entity body being sent by this response.
     */
    private Locale locale;

    /**
     * The value of the <code>Content-Language</code> response header.
     */
    private String contentLanguage;


    /**
     * Status code.
     */
    protected HttpStatus httpStatus;

    /**
     * Status message.
     */
    private final DataChunk reasonPhraseC = DataChunk.newInstance();


    /**
     * Does this HttpResponsePacket represent an acknowledgment to
     * an Expect header.
     */
    private boolean acknowledgment;

    /**
     * Do we allow custom reason phrase.
     */
    private boolean allowCustomReasonPhrase = true;

    private String quotedCharsetValue = characterEncoding;
    /**
     * Has the charset been explicitly set.
     */
    protected boolean charsetSet = false;

    /**
     * Returns {@link HttpResponsePacket} builder.
     *
     * @return {@link Builder}.
     */
    public static Builder builder(HttpRequestPacket request) {
        return new Builder(request);
    }

    // ----------------------------------------------------------- Constructors
    protected HttpResponsePacket() {
        characterEncoding = Charsets.DEFAULT_CHARACTER_ENCODING;
        quotedCharsetValue = Charsets.DEFAULT_CHARACTER_ENCODING;
    }

    // -------------------- State --------------------

    /**
     * Gets the status code for this response.
     *
     * @return the status code for this response.
     */
    public int getStatus() {
        return getHttpStatus().getStatusCode();
    }
    
    /**
     * Gets the HTTP status for this response.
     *
     * @return the HTTP status for this response.
     */
    public HttpStatus getHttpStatus() {
        if (httpStatus == null) {
            httpStatus = HttpStatus.OK_200;
        }
        
        return httpStatus;
    }

    /**
     * Sets the status code for this response.
     *
     * @param status the status code for this response.
     */
    public void setStatus(final int status) {
        // the order is important here as statusDC.setXXX will reset the parsedIntStatus
        httpStatus = HttpStatus.getHttpStatus(status);
    }

    /**
     * Sets the status code for this response.
     *
     * @param status the status for this response.
     */
    public void setStatus(final HttpStatus status) {
        this.httpStatus = status;
    }

    /**
     * Returns <code>true</code> if custom status reason phrases are allowed for
     * this response, or <code>false</tt> otherwise.
     *
     * @return <code>true</code> if custom status reason phrases are allowed for
     * this response, or <code>false</tt> otherwise.
     */
    public final boolean isAllowCustomReasonPhrase() {
        return allowCustomReasonPhrase;
    }

    /**
     * Sets if the custom status reason phrases are allowed for
     * this response.
     *
     * @param allowCustomReasonPhrase <code>true</code> if custom status
     * reason phrases are allowed for this response, or <code>false</tt> otherwise.
     */
    public final void setAllowCustomReasonPhrase(final boolean allowCustomReasonPhrase) {
        this.allowCustomReasonPhrase = allowCustomReasonPhrase;
    }

    /**
     * Gets the custom status reason phrase for this response as {@link DataChunk}
     * (avoid creation of a String object}.
     *
     * @return the status reason phrase for this response as {@link DataChunk}
     * (avoid creation of a String object}.
     */
    public final DataChunk getReasonPhraseRawDC() {
        return reasonPhraseC;
    }

    /**
     * Gets the status reason phrase for this response as {@link DataChunk}
     * (avoid creation of a String object}. This implementation takes into
     * consideration the {@link #isAllowCustomReasonPhrase()} property - if the
     * custom reason phrase is allowed and it's value is not null - then the
     * returned result will be equal to {@link #getReasonPhraseRawDC()}, otherwise
     * if custom reason phrase is disallowed or its value is null - the default
     * reason phrase for the HTTP response {@link #getStatus()} will be returned.
     *
     * @return the status reason phrase for this response as {@link DataChunk}
     * (avoid creation of a String object}.
     */
    public final DataChunk getReasonPhraseDC() {
        if (allowCustomReasonPhrase && !reasonPhraseC.isNull()) {
            return reasonPhraseC;
        } else {
            final Buffer b = Buffers.wrap(null, httpStatus.getReasonPhraseBytes());
            reasonPhraseC.setBuffer(b, b.position(), b.limit());
            return reasonPhraseC;
        }
    }

    /**
     * Gets the status reason phrase for this response.
     *
     * @return the status reason phrase for this response.
     */
    public final String getReasonPhrase() {
        return getReasonPhraseDC().toString();
    }

    /**
     * Sets the status reason phrase for this response.
     *
     * @param message the status reason phrase for this response.
     */
    public void setReasonPhrase(String message) {
        reasonPhraseC.setString(message);
    }

    public void setReasonPhrase(Buffer reason) {
        reasonPhraseC.setBuffer(reason, reason.position(), reason.limit());
    }

    /**
     * Sets the content type.
     *
     * This method must preserve any response charset that may already have
     * been set via a call to response.setContentType(), response.setLocale(),
     * or response.setCharacterEncoding().
     *
     * @param type the content type
     */
    @Override
    public void setContentType(String type) {

        int semicolonIndex = -1;

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
        int len = type.length();
        int index = type.indexOf(';');
        while (index != -1) {
            semicolonIndex = index;
            index++;
            while (index < len && Character.isSpace(type.charAt(index))) {
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

    @Override
    public String getContentType() {

        String ret = contentType;

        if (ret != null
                /* SJSAS 6316254
                && characterEncoding != null
                */
                // START SJSAS 6316254
                && quotedCharsetValue != null
                // END SJSAS 6316254
                && charsetSet) {
            /* SJSAS 6316254
            ret = ret + ";charset=" + characterEncoding;
            */
            // START SJSAS 6316254
            ret = ret + ";charset=" + quotedCharsetValue;
            // END SJSAS 6316254
        }

        return ret;
    }
    
    /**
     * @return the request that triggered this response
     */
    public HttpRequestPacket getRequest() {
        return request;
    }


    /**
     * @return <code>true</code> if this response packet is intended
     *  as an acknowledgment to an expectation from a client request.
     */
    public boolean isAcknowledgement() {
        return acknowledgment;
    }


    /**
     * Mark this packet as an acknowledgment to a client expectation.
     *
     * @param acknowledgement <code>true</code> if this packet is an
     *  acknowledgment to a client expectation.
     */
    public void setAcknowledgement(boolean acknowledgement) {
        this.acknowledgment = acknowledgement;
    }


    /**
     * Mark this packet as having been acknowledged.
     */
    public void acknowledged() {
        request.requiresAcknowledgement(false);
        acknowledgment = false;
        httpStatus = null;
        reasonPhraseC.recycle();
    }


    // --------------------


    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        httpStatus = null;
        acknowledgment = false;
        allowCustomReasonPhrase = true;
        reasonPhraseC.recycle();
        locale = null;
        contentLanguage = null;
        request = null;
        charsetSet = false;

        super.reset();
        
        characterEncoding = Charsets.DEFAULT_CHARACTER_ENCODING;
        quotedCharsetValue = Charsets.DEFAULT_CHARACTER_ENCODING;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isRequest() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("HttpResponsePacket (status=").append(getStatus())
                .append(" reason=").append(getReasonPhrase())
                .append(" protocol=").append(getProtocol())
                .append(" content-length=").append(getContentLength())
                .append(" headers=").append(getHeaders())
                .append(" committed=").append(isCommitted())
                .append(')');
        
        return sb.toString();
    }


    /**
     * @inheritDoc
     */
    @Override public void setHeader(String name, String value) {
        char c = name.charAt(0);
        if((c=='C' || c=='c') && checkSpecialHeader(name, value)) {
            return;
        }
        super.setHeader(name, value);
    }

    
    /**
     * @inheritDoc
     */
    @Override public void addHeader(String name, String value) {
        char c = name.charAt(0);
        if((c=='C' || c=='c') && checkSpecialHeader(name, value)) {
            return;
        }
        super.addHeader(name, value);
    }


    /**
     * @return the {@link Locale} of this response.
     */
    public Locale getLocale() {
        return locale;
    }


    /**
     * Called explicitly by user to set the Content-Language and
     * the default encoding
     */
    public void setLocale(Locale locale) {

        if (locale == null) {
            return;  // throw an exception?
        }

        // Save the locale for use by getLocale()
        this.locale = locale;

        // Set the contentLanguage for header output
        contentLanguage = locale.getLanguage();
        if ((contentLanguage != null) && (contentLanguage.length() > 0)) {
            String country = locale.getCountry();
            StringBuilder value = new StringBuilder(contentLanguage);
            if ((country != null) && (country.length() > 0)) {
                value.append('-');
                value.append(country);
            }
            contentLanguage = value.toString();
        }

    }


    /**
     * @return the value that will be used by the <code>Content-Language</code>
     *  response header
     */
    public String getContentLanguage() {
        return contentLanguage;
    }


    /**
     * Set the value that will be used by the <code>Content-Language</code>
     * response header.
     */
    public void setContentLanguage(String contentLanguage) {
        this.contentLanguage = contentLanguage;
    }


    // ------------------------------------------------- Package Private Methods


    /**
     * Associates the request that triggered this response.
     * @param request the request that triggered this response
     */
    public void setRequest(HttpRequestPacket request) {
        this.request = request;
    }


    // --------------------------------------------------------- Private Methods


    /**
     * Set internal fields for special header names.
     * Called from set/addHeader.
     * Return true if the header is special, no need to set the header.
     */
    private boolean checkSpecialHeader(String name, String value) {
        // XXX Eliminate redundant fields !!!
        // ( both header and in special fields )
        if (name.equalsIgnoreCase("Content-Type")) {
            setContentType(value);
            return true;
        }
        if (name.equalsIgnoreCase("Content-Length")) {
            try {
                int cL = Integer.parseInt(value);
                setContentLength(cL);
                return true;
            } catch (NumberFormatException ex) {
                // Do nothing - the spec doesn't have any "throws"
                // and the user might know what he's doing
                return false;
            }
        }
        if (name.equalsIgnoreCase("Content-Language")) {
            // TODO XXX XXX Need to construct Locale or something else
        }
        return false;
    }


    // ---------------------------------------------------------- Nested Classes

    /**
     * <tt>HttpResponsePacket</tt> message builder.
     */
    public static class Builder extends HttpHeader.Builder<Builder> {
        protected Builder(HttpRequestPacket request) {
            packet = request.getResponse();
            if (packet == null) {
                packet = HttpResponsePacketImpl.create();
                ((HttpResponsePacket) packet).setRequest(request);
                packet.setSecure(request.isSecure());
            }
        }

        /**
         * Sets the status code for this response.
         *
         * @param status the status code for this response.
         */
        public Builder status(int status) {
            ((HttpResponsePacket) packet).setStatus(status);
            return this;
        }

        /**
         * Sets the status reason phrase for this response.
         *
         * @param reasonPhrase the status reason phrase for this response.
         */
        public Builder reasonPhrase(String reasonPhrase) {
            ((HttpResponsePacket) packet).setReasonPhrase(reasonPhrase);
            return this;
        }

        /**
         * Build the <tt>HttpResponsePacket</tt> message.
         *
         * @return <tt>HttpResponsePacket</tt>
         */
        public final HttpResponsePacket build() {
            return (HttpResponsePacket) packet;
        }
    }
}
