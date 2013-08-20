/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

/**
 * {@link HttpContent} message, which represents broken HTTP content.
 * {@link #isLast()} is always returns <tt>true</tt>,
 * {@link #getContent()} always throws {@link HttpBrokenContentException()}.
 * 
 * @see HttpContent#isBroken(org.glassfish.grizzly.http.HttpContent)
 * 
 * @author Alexey Stashok
 */
public class HttpBrokenContent extends HttpContent {
    private static final ThreadCache.CachedTypeIndex<HttpBrokenContent> CACHE_IDX =
            ThreadCache.obtainIndex(HttpBrokenContent.class, 1);

    public static HttpBrokenContent create() {
        return create(null);
    }

    public static HttpBrokenContent create(final HttpHeader httpHeader) {
        final HttpBrokenContent httpBrokenContent =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (httpBrokenContent != null) {
            httpBrokenContent.httpHeader = httpHeader;
            return httpBrokenContent;
        }

        return new HttpBrokenContent(httpHeader);
    }


    /**
     * Returns {@link HttpTrailer} builder.
     *
     * @return {@link Builder}.
     */
    public static Builder builder(final HttpHeader httpHeader) {
        return new Builder().httpHeader(httpHeader);
    }

    private Throwable exception;
    
    protected HttpBrokenContent(final HttpHeader httpHeader) {
        super(httpHeader);
    }

    /**
     * Returns {@link Throwable}, which describes the error.
     * @return {@link Throwable}, which describes the error.
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer getContent() {
        throw exception instanceof HttpBrokenContentException ?
                (HttpBrokenContentException) exception :
                new HttpBrokenContentException(exception);
    }
    
    /**
     * Always true <tt>true</tt> for the trailer message.
     * 
     * @return Always true <tt>true</tt> for the trailer message.
     */
    @Override
    public final boolean isLast() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        this.exception = null;
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
     * <tt>HttpTrailer</tt> message builder.
     */
    public static final class Builder extends HttpContent.Builder<Builder> {

        private Throwable cause;

        protected Builder() {
        }

        /**
         * Set the exception.
         * @param cause {@link Throwable}.
         */
        public final Builder error(final Throwable cause) {
            this.cause = cause;
            return this;
        }

        /**
         * Build the <tt>HttpTrailer</tt> message.
         *
         * @return <tt>HttpTrailer</tt>
         */
        @Override
        public final HttpBrokenContent build() {
            HttpBrokenContent httpBrokenContent = (HttpBrokenContent) super.build();
            if (cause == null) {
                throw new IllegalStateException("No cause specified");
            }
            httpBrokenContent.exception = cause;
            return httpBrokenContent;
        }

        @Override
        protected HttpContent create() {
            return HttpBrokenContent.create();
        }
    }
}
