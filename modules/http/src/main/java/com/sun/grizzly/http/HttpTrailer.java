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

import com.sun.grizzly.http.util.MimeHeaders;

/**
 *
 * @author oleksiys
 */
public class HttpTrailer extends HttpContent {
    public static Builder builder(HttpHeader httpHeader) {
        return new Builder(httpHeader);
    }

    private MimeHeaders headers;
    
    protected HttpTrailer() {
        this(null);
    }

    protected HttpTrailer(HttpHeader httpHeader) {
        super(httpHeader);
        headers = new MimeHeaders();
    }

    @Override
    public final boolean isLast() {
        return true;
    }

    // -------------------- Headers --------------------
    public MimeHeaders getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.getHeader(name);
    }

    public void setHeader(String name, String value) {
        headers.setValue(name).setString(value);
    }

    public void addHeader(String name, String value) {
        headers.addValue(name).setString(value);
    }

    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }

    protected void setHeaders(MimeHeaders mimeHeaders) {
        this.headers = mimeHeaders;
    }

    @Override
    public void recycle() {
        this.headers.recycle();
        super.recycle();
    }

    public static final class Builder extends HttpContent.Builder<Builder> {

        protected Builder(HttpHeader httpHeader) {
            super(httpHeader);
        }

        @Override
        protected HttpContent create(HttpHeader httpHeader) {
            return new HttpTrailer(httpHeader);
        }

        public final Builder headers(MimeHeaders mimeHeaders) {
            ((HttpTrailer) packet).setHeaders(mimeHeaders);
            return this;
        }

        public final Builder header(String name, String value) {
            ((HttpTrailer) packet).setHeader(name, value);
            return this;
        }

        @Override
        public final HttpTrailer build() {
            return (HttpTrailer) packet;
        }
    }
}
