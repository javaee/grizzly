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

import com.sun.grizzly.ThreadCache;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.FastHttpDateFormat;

import java.io.IOException;


/**
 * The {@link HttpHeader} object, which represents HTTP response message.
 *
 * @see HttpHeader
 * @see HttpRequestPacket
 *
 * @author Alexey Stashok
 */
public class HttpResponsePacket extends HttpHeader {
    private static final ThreadCache.CachedTypeIndex<HttpResponsePacket> CACHE_IDX =
            ThreadCache.obtainIndex(HttpResponsePacket.class, 2);

    public static HttpResponsePacket create() {
        final HttpResponsePacket httpResponse =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (httpResponse != null) {
            return httpResponse;
        }

        return new HttpResponsePacket();
    }

    public static final int NON_PARSED_STATUS = Integer.MIN_VALUE;
    
    // ----------------------------------------------------- Instance Variables

    /**
     * Status code.
     */
    protected int parsedStatusInt = NON_PARSED_STATUS;
    protected BufferChunk statusBC = BufferChunk.newInstance();
    protected boolean committed;


    /**
     * Status message.
     */
    private BufferChunk reasonPhraseBC = BufferChunk.newInstance();


    /**
     * Returns {@link HttpResponsePacket} builder.
     *
     * @return {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    // ----------------------------------------------------------- Constructors
    protected HttpResponsePacket() {
    }

    // -------------------- State --------------------
    /**
     * Gets the status code for this response as {@link BufferChunk} (avoid
     * the status code parsing}.
     *
     * @return the status code for this response as {@link BufferChunk} (avoid
     * the status code parsing}.
     */
    public BufferChunk getStatusBC() {
        return statusBC;
    }

    /**
     * Gets the status code for this response.
     *
     * @return the status code for this response.
     */
    public int getStatus() {
        if (parsedStatusInt == NON_PARSED_STATUS) {
            parsedStatusInt = Integer.parseInt(statusBC.toString());
        }

        return parsedStatusInt;
    }
    
    /**
     * Sets the status code for this response.
     *
     * @param status the status code for this response.
     */
    public void setStatus(int status) {
        parsedStatusInt = status;
        statusBC.setString(Integer.toString(status));
    }


    /**
     * Gets the status reason phrase for this response as {@link BufferChunk}
     * (avoid creation of a String object}.
     *
     * @return the status reason phrase for this response as {@link BufferChunk}
     * (avoid creation of a String object}.
     */
    public BufferChunk getReasonPhraseBC() {
        return reasonPhraseBC;
    }

    /**
     * Gets the status reason phrase for this response.
     *
     * @return the status reason phrase for this response.
     */
    public String getReasonPhrase() {
        return reasonPhraseBC.toString();
    }


    /**
     * Sets the status reason phrase for this response.
     *
     * @param message the status reason phrase for this response.
     */
    public void setReasonPhrase(String message) {
        reasonPhraseBC.setString(message);
    }


    /**
     * TODO DOCS
     * @return
     */
    public boolean isCommitted() {
        return committed;
    }


    /**
     * TODO DOCS
     * @param committed
     */
    public void setCommitted(boolean committed) {
        if (this.committed) {
            return; // once committed, always committed
        }
        this.committed = committed;

        prepareResponse();

    }


    // --------------------


    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        statusBC.recycle();
        reasonPhraseBC.recycle();
        committed = false;

        super.reset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle() {
        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
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
                .append(" committed=").append(isCommited())
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


    public void finish() throws IOException {
        if (!isCommitted()) {
            setCommitted(true);
        }
    }

    
    @Override public void setCharacterEncoding(String enc) {
        super.setCharacterEncoding(enc);
    }


    // ------------------------------------------------------- Protected Methods


    protected void prepareResponse() {

        // Add date header
        if (!containsHeader("Date")) {
            String date = FastHttpDateFormat.getCurrentDate();
            addHeader("Date", date);
        }

        if (parsedStatusInt == NON_PARSED_STATUS) {
            setStatus(200);
        }

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
            // XXX XXX Need to construct Locale or something else
        }
        return false;
    }


    // ---------------------------------------------------------- Nested Classes


    /**
     * <tt>HttpResponsePacket</tt> message builder.
     */
    public static class Builder extends HttpHeader.Builder<Builder> {
        protected Builder() {
            packet = HttpResponsePacket.create();
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