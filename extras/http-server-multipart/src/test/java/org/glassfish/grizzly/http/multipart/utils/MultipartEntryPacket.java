/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.multipart.utils;

import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;

/**
 * MultipartEntry packet.
 * 
 * @author Alexey Stashok
 */
public class MultipartEntryPacket {

    public static Builder builder() {
        return new Builder();
    }

    private String contentDisposition;
    private String contentType;
    protected final MimeHeaders headers = new MimeHeaders();

    private Buffer content;

    private MultipartEntryPacket() {
    }

    public String getContentDisposition() {
        return contentDisposition;
    }

    public String getContentType() {
        return contentType;
    }

    public Buffer getContent() {
        return content;
    }

    public Iterable<String> getHeaderNames() {
        return headers.names();
    }

    public String getHeader(String name) {
        return headers.getHeader(name);
    }

    // ---------------------------------------------------------- Nested Classes


    /**
     * <tt>HttpRequestPacket</tt> message builder.
     */
    public static class Builder {
        private final MultipartEntryPacket packet;
        
        protected Builder() {
            packet = new MultipartEntryPacket();
        }

        public Builder contentDisposition(String contentDisposition) {
            packet.contentDisposition = contentDisposition;
            return this;
        }

        public Builder contentType(String contentType) {
            packet.contentType = contentType;
            return this;
        }

        public Builder header(String name, String value) {
            if ("content-disposition".equalsIgnoreCase(name)) {
                contentDisposition(value);
            } else if ("content-type".equalsIgnoreCase(name)) {
                contentType(value);
            } else {
                packet.headers.addValue(name).setString(value);
            }
            return this;
        }

        public Builder content(Buffer content) {
            packet.content = content;
            return this;
        }

        public Builder content(String content) {
            return content(content, getCharset());
        }

        public Builder content(String content, Charset charset) {
            packet.content = Buffers.wrap(null, content, charset);
            return this;
        }

        /**
         * Build the <tt>HttpRequestPacket</tt> message.
         *
         * @return <tt>HttpRequestPacket</tt>
         */
        public final MultipartEntryPacket build() {
            return (MultipartEntryPacket) packet;
        }

        private Charset getCharset() {
            String charset = null;
            if (packet.contentType != null) {
                charset = ContentType.getCharsetFromContentType(packet.contentType);
            }

            if (charset == null) {
                return Charsets.ASCII_CHARSET;
            }
            
            return Charsets.lookupCharset(charset);
        }

    }
}
