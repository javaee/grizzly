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
import com.sun.grizzly.http.util.RequestURIRef;

/**
 * The {@link HttpHeader} object, which represents HTTP request message.
 *
 * @see HttpHeader
 * @see HttpResponse
 * 
 * @author Alexey Stashok
 */
public class HttpRequest extends HttpHeader {

    // ----------------------------------------------------- Instance Variables
    private BufferChunk methodBC = BufferChunk.newInstance();
    private RequestURIRef requestURIRef = new RequestURIRef();
    private BufferChunk queryBC = BufferChunk.newInstance();

    /**
     * Returns {@link HttpRequest} builder.
     *
     * @return {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // ----------------------------------------------------------- Constructors
    protected HttpRequest() {
        methodBC.setString("GET");
        queryBC.setString("");
    }

    // -------------------- Request data --------------------
    /**
     * Get the HTTP request method as {@link BufferChunk}
     * (avoiding creation of a String object). The result format is "GET|POST...".
     *
     * @return the HTTP request method as {@link BufferChunk}
     * (avoiding creation of a String object). The result format is "GET|POST...".
     */
    public BufferChunk getMethodBC() {
        return methodBC;
    }

    /**
     * Get the HTTP request method. The result format is "GET|POST...".
     *
     * @return the HTTP request method. The result format is "GET|POST...".
     */
    public String getMethod() {
        return methodBC.toString();
    }

    /**
     * Set the HTTP request method.
     * @param method the HTTP request method. Format is "GET|POST...".
     */
    public void setMethod(String method) {
        this.methodBC.setString(method);
    }

    /**
     * Returns the request URL of the HTTP request as {@link RequestURIRef}
     * (avoiding creation of a String object).
     * 
     * @return the request URL of the HTTP request as {@link RequestURIRef}
     * (avoiding creation of a String object).
     */
    public RequestURIRef getRequestURIRef() {
        return requestURIRef;
    }

    /**
     * Returns the request URL.
     *
     * @return the request URL.
     */
    public String getRequestURI() {
        return requestURIRef.getURI();
    }

    /**
     * Set the request URL.
     *
     * @param requestURI the request URL.
     */
    public void setRequestURI(String requestURI) {
        this.requestURIRef.setURI(requestURI);
    }

    /**
     * Returns the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     * The result is represented as {@link BufferChunk} (avoifing creation of a
     * String object).
     * 
     * @return the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     * The result is represented as {@link BufferChunk} (avoifing creation of a
     * String object).
     */
    public BufferChunk getQueryStringBC() {
        return queryBC;
    }

    /**
     * Returns the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     *
     * @return the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     */
    public String getQueryString() {
        return queryBC.toString();
    }

    // -------------------- Recycling -------------------- 
    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle() {
        super.recycle();

        requestURIRef.recycle();
        queryBC.recycle();
        methodBC.recycle();

        // XXX Do we need such defaults ?
        methodBC.setString("GET");

        queryBC.setString("");
        protocolBC.setString("HTTP/1.0");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isRequest() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("HttpRequest (method=").append(getMethod())
                .append(" url=").append(getRequestURI())
                .append(" protocol=").append(getProtocol())
                .append(" content-length=").append(getContentLength())
                .append(" headers=").append(getHeaders())
                .append(')');

        return sb.toString();
    }

    /**
     * <tt>HttpRequest</tt> message builder.
     */
    public static class Builder extends HttpHeader.Builder<Builder> {
        protected Builder() {
            packet = new HttpRequest();
        }

        /**
         * Set the HTTP request method.
         * @param method the HTTP request method. Format is "GET|POST...".
         */
        public Builder method(String method) {
            ((HttpRequest) packet).setMethod(method);
            return this;
        }

        /**
         * Set the request URL.
         *
         * @param requestURI the request URL.
         */
        public Builder uri(String uri) {
            ((HttpRequest) packet).setRequestURI(uri);
            return this;
        }

        /**
         * Build the <tt>HttpRequest</tt> message.
         *
         * @return <tt>HttpRequest</tt>
         */
        public final HttpRequest build() {
            return (HttpRequest) packet;
        }
    }
}
