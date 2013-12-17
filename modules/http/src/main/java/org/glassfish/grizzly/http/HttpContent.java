/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.memory.Buffers;

/**
 * Object represents HTTP message content: complete or part.
 * The <tt>HttpContent</tt> object could be used both with
 * fixed-size and chunked HTTP messages.
 * To get the HTTP message header - call {@link HttpContent#getHttpHeader()}.
 *
 * To build <tt>HttpContent</tt> message, use {@link Builder} object, which
 * could be get following way: {@link HttpContent#builder(org.glassfish.grizzly.http.HttpHeader)}.
 *
 * @see HttpPacket
 * @see HttpHeader
 *
 * @author Alexey Stashok
 */
public class HttpContent extends HttpPacket
        implements org.glassfish.grizzly.Appendable<HttpContent> {
    
    private static final ThreadCache.CachedTypeIndex<HttpContent> CACHE_IDX =
            ThreadCache.obtainIndex(HttpContent.class, 16);

    /**
     * Returns <tt>true</tt> if passed {@link HttpPacket} is a <tt>HttpContent</tt>.
     *
     * @param httpPacket
     * @return <tt>true</tt> if passed {@link HttpPacket} is a <tt>HttpContent</tt>.
     */
    public static boolean isContent(final HttpPacket httpPacket) {
        return httpPacket instanceof HttpContent;
    }

    /**
     * Returns <tt>true</tt> if passed {@link HttpContent} is a <tt>BrokenHttpContent</tt>.
     *
     * @param httpContent
     * @return <tt>true</tt> if passed {@link HttpContent} is a <tt>BrokenHttpContent</tt>.
     */
    public static boolean isBroken(final HttpContent httpContent) {
        return httpContent instanceof HttpBrokenContent;
    }

    public static HttpContent create() {
        return create(null);
    }

    public static HttpContent create(final HttpHeader httpHeader) {
        return create(httpHeader, false);
    }

    public static HttpContent create(final HttpHeader httpHeader,
            final boolean isLast) {
        return create(httpHeader, isLast, Buffers.EMPTY_BUFFER);
    }
    
    public static HttpContent create(final HttpHeader httpHeader,
            final boolean isLast, Buffer content) {
        content = content != null ? content : Buffers.EMPTY_BUFFER;
        
        final HttpContent httpContent =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (httpContent != null) {
            httpContent.httpHeader = httpHeader;
            httpContent.isLast = isLast;
            httpContent.content = content;
            
            return httpContent;
        }

        return new HttpContent(httpHeader, isLast, content);
    }
    
    /**
     * Returns {@link HttpContent} builder.
     * 
     * @param httpHeader related HTTP message header
     * @return {@link Builder}.
     */
    public static Builder builder(final HttpHeader httpHeader) {
        return new Builder().httpHeader(httpHeader);
    }

    protected boolean isLast;
    
    protected Buffer content = Buffers.EMPTY_BUFFER;

    protected HttpHeader httpHeader;

    protected HttpContent() {
        this(null);
    }

    protected HttpContent(final HttpHeader httpHeader) {
        this.httpHeader = httpHeader;
    }

    protected HttpContent(final HttpHeader httpHeader, final boolean isLast,
            final Buffer content) {
        this.httpHeader = httpHeader;
        this.isLast = isLast;
        this.content = content;
    }
    
    /**
     * Get the HTTP message content {@link Buffer}.
     *
     * @return {@link Buffer}.
     */
    public Buffer getContent() {
        return content;
    }

    protected final void setContent(Buffer content) {
        this.content = content;
    }

    /**
     * Get the HTTP message header, associated with this content.
     *
     * @return {@link HttpHeader}.
     */
    @Override
    public final HttpHeader getHttpHeader() {
        return httpHeader;
    }

    /**
     * Return <tt>true</tt>, if the current content chunk is last,
     * or <tt>false</tt>, if there are content chunks to follow.
     * 
     * @return <tt>true</tt>, if the current content chunk is last,
     * or <tt>false</tt>, if there are content chunks to follow.
     */
    public boolean isLast() {
        return isLast;
    }

    public void setLast(boolean isLast) {
        this.isLast = isLast;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isHeader() {
        return false;
    }

    @Override
    public HttpContent append(final HttpContent element) {
        if (isLast) {
            throw new IllegalStateException("Can not append to a last chunk");
        }

        if (isBroken(element)) {
            return element;
        }
        
        final Buffer content2 = element.getContent();
        if (content2 != null && content2.hasRemaining()) {
            content = Buffers.appendBuffers(null, content, content2);
        }

        if (element.isLast()) {
            element.setContent(content);
            return element;
        }

        return this;
    }

    /**
     * Reset the internal state.
     */
    protected void reset() {
        isLast = false;
        content = Buffers.EMPTY_BUFFER;
        httpHeader = null;
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
     * <tt>HttpContent</tt> message builder.
     */
    public static class Builder<T extends Builder> {

        protected boolean last;
        protected Buffer content;
        protected HttpHeader httpHeader;

        protected Builder() {
        }

        /**
         * Set the {@link HttpHeader} associated with this content.
         *
         * @param httpHeader the {@link HttpHeader} associated with this content.
         *
         * @return this.
         */
        @SuppressWarnings({"unchecked"})
        public final T httpHeader(final HttpHeader httpHeader) {
            this.httpHeader = httpHeader;
            return (T) this;
        }

        /**
         * Set whether this <tt>HttpContent</tt> chunk is the last.
         *
         * @param last is this <tt>HttpContent</tt> chunk last.
         * @return <tt>Builder</tt>
         */
        @SuppressWarnings({"unchecked"})
        public final T last(boolean last) {
            this.last = last;
            return (T) this;
        }
        
        /**
         * Set the <tt>HttpContent</tt> chunk content {@link Buffer}.
         *
         * @param content the <tt>HttpContent</tt> chunk content {@link Buffer}.
         * @return <tt>Builder</tt>
         */
        @SuppressWarnings({"unchecked"})
        public final T content(Buffer content) {
            this.content = content;
            return (T) this;
        }

        /**
         * Build the <tt>HttpContent</tt> message.
         *
         * @return <tt>HttpContent</tt>
         */
        public HttpContent build() {
            if (httpHeader == null) {
                throw new IllegalStateException("No HttpHeader specified to associate with this HttpContent.");
            }
            HttpContent httpContent = create();
            httpContent.httpHeader = httpHeader;
            httpContent.setLast(last);
            if (content != null) {
                httpContent.setContent(content);
            }
            return httpContent;
        }

        public void reset() {
            last = false;
            content = null;
            httpHeader = null;
        }

        protected HttpContent create() {
            return HttpContent.create();
        }
    }
}
