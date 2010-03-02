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
package com.sun.grizzly.http.core;

import com.sun.grizzly.http.util.Ascii;
import com.sun.grizzly.http.util.MimeHeaders;
import com.sun.grizzly.http.util.BufferChunk;

/**
 *
 * @author oleksiys
 */
public abstract class HttpHeader implements HttpPacket {

    protected boolean isCommited;
    
    protected MimeHeaders headers = new MimeHeaders();
    protected BufferChunk protocolBC = BufferChunk.newInstance();

    protected boolean isChunked;

    protected long contentLength;

    public abstract boolean isRequest();

    @Override
    public final boolean isHeader() {
        return true;
    }

    public boolean isChunked() {
        return isChunked;
    }

    public void setChunked(boolean isChunked) {
        this.isChunked = isChunked;
    }

    public long getContentLength() {
        if (contentLength == -1) {
            final BufferChunk contentLengthChunk =
                    headers.getValue("content-length");
            if (contentLengthChunk != null) {
                contentLength = Ascii.parseLong(contentLengthChunk.getBuffer(),
                        contentLengthChunk.getStart(),
                        contentLengthChunk.getEnd() - contentLengthChunk.getStart());
            }
        }

        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }
    
    public boolean isCommited() {
        return isCommited;
    }

    public void setCommited(boolean isCommited) {
        this.isCommited = isCommited;
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

    // Common HTTP packet attributes
    public BufferChunk getProtocolBC() {
        return protocolBC;
    }

    public String getProtocol() {
        return getProtocolBC().toString();
    }

    public void setProtocol(String protocol) {
        this.protocolBC.setString(protocol);
    }

    public final HttpContent.Builder httpContentBuilder() {
        return HttpContent.builder(this);
    }

    public HttpTrailer.Builder httpTrailerBuilder() {
        return HttpTrailer.builder(this);
    }

    public void recycle() {
        protocolBC.recycle();
        headers.clear();
        isCommited = false;
        isChunked = false;
        contentLength = -1;
    }

    public static abstract class Builder<T extends Builder> {

        protected HttpHeader packet;

        public final T protocol(String protocol) {
            packet.setProtocol(protocol);
            return (T) this;
        }

        public final T chunked(boolean isChunked) {
            packet.setChunked(isChunked);
            return (T) this;
        }

        public final T contentLength(long contentLength) {
            packet.setContentLength(contentLength);
            return (T) this;
        }

        public final T header(String name, String value) {
            packet.addHeader(name, value);
            return (T) this;
        }
    }
}
