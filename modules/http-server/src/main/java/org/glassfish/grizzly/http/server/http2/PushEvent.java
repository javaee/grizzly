/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.http2;

import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.MimeHeaders;


/**
 * A {@link FilterChainEvent} to trigger an HTTP/2 push promise and trigger a new request
 *  to be sent upstream to generate a response for said push promise.
 */
public class PushEvent implements FilterChainEvent {

    private static final ThreadCache.CachedTypeIndex<PushEvent> CACHE_IDX =
            ThreadCache.obtainIndex(PushEvent.class, 8);

    public static final Object TYPE = PushEvent.class.getName();

    private String method;
    private MimeHeaders headers = new MimeHeaders();
    private String path;
    private HttpRequestPacket httpRequest;


    // ----------------------------------------------------------- Constructors


    private PushEvent() {
    }


    // ------------------------------------------ Methods from FilterChainEvent


    @Override
    public Object type() {
        return TYPE;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Construct a new {@link PushEvent} based on the values contained within the
     * provided {@link PushBuilder}.
     */
    public static PushEvent create(final PushBuilder builder) {
        PushEvent pushEvent =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (pushEvent == null) {
            pushEvent = new PushEvent();
        }

        return pushEvent.init(builder);
    }

    /**
     * @return the HTTP Method of the push request.
     */
    public String getMethod() {
        return method;
    }

    /**
     * @return the headers of the push request.
     */
    public MimeHeaders getHeaders() {
        return headers;
    }

    /**
     * @return the path of the push request.
     */
    public String getPath() {
        return path;
    }

    /**
     * @return the {@link HttpRequestPacket} of the original request.  This is necessary in order to lookup
     *  the parent stream.
     */
    public HttpHeader getHttpRequest() {
        return httpRequest;
    }

    /**
     * This should be called by the entity generating the actual push and container requests.
     * Developers using this event can ignore this.
     */
    public void recycle() {
        method = null;
        headers.recycle();
        path = null;
        httpRequest = null;
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    /**
     * @return a new {@link PushEventBuilder} for constructing a {@link PushEvent} with all of the necessary
     *  values to generate a push and container request.
     */
    public static PushEventBuilder builder() {
        return new PushEventBuilder();
    }


    // -------------------------------------------------------- Private Methods


    private static PushEvent create(final PushEventBuilder builder) {
        PushEvent pushEvent =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (pushEvent == null) {
            pushEvent = new PushEvent();
        }

        return pushEvent.init(builder);
    }


    private PushEvent init(final PushBuilder builder) {
        method = builder.method;
        headers.copyFrom(builder.headers);
        path = builder.path;
        httpRequest = builder.request.getRequest();
        return this;
    }

    private PushEvent init(final PushEventBuilder builder) {
        method = builder.method;
        headers.copyFrom(builder.headers);
        path = builder.path;
        httpRequest = builder.httpRequest;
        return this;
    }

    // --------------------------------------------------------- Nested Classes


    /**
     * Construct a new {@link PushEvent}.  Any missing required values will result
     * in an exception when {@link #build()} is invoked;
     */
    public static final class PushEventBuilder {
        private String method = Method.GET.getMethodString();
        private MimeHeaders headers = new MimeHeaders();
        private String path;
        private HttpRequestPacket httpRequest;

        private PushEventBuilder() {
        }

        /**
         * The push method.  Defaults to {@link Method#GET}.
         *
         * @return this
         *
         * @throws NullPointerException if no value is provided.
         * @throws IllegalArgumentException if a {@link Method} other
         *  than {@link Method#GET} or {@link Method#HEAD} are provided.
         */
        public PushEventBuilder method(final String val) {
            if (method == null) {
                throw new NullPointerException();
            }
            if (Method.POST.getMethodString().equals(method)
                    || Method.PUT.getMethodString().equals(method)
                    || Method.DELETE.getMethodString().equals(method)
                    || Method.CONNECT.getMethodString().equals(method)
                    || Method.OPTIONS.getMethodString().equals(method)
                    || Method.TRACE.getMethodString().equals(method)) {
                throw new IllegalArgumentException();
            }
            this.method = val;
            return this;
        }

        /**
         * The headers of the push request.
         *
         * @return this
         *
         * @throws NullPointerException if no {@link MimeHeaders} is provided.
         */
        public PushEventBuilder headers(final MimeHeaders val) {
            if (val == null) {
                throw new NullPointerException();
            }
            headers.copyFrom(val);
            return this;
        }

        /**
         * The path of the push request.
         *
         * @return this
         */
        public PushEventBuilder path(final String val) {
            path = validate(val);
            return this;
        }

        /**
         * The {@link HttpRequestPacket} of the original request.  This is necessary in order to lookup
         *  the parent stream.
         *
         * @return this
         *
         * @throws NullPointerException if no {@link HttpRequestPacket} is provided.
         */
        public PushEventBuilder httpRequest(final HttpRequestPacket val) {
            if (val == null) {
                throw new NullPointerException();
            }
            httpRequest = val;
            return this;
        }

        /**
         * @return a new PushEvent based on the provided values.
         *
         * @throws IllegalArgumentException if no value has been provided by invoking
         *  {@link #path(String)}, {@link #httpRequest(HttpRequestPacket)},
         *  or {@link #headers(MimeHeaders)}.
         *
         */
        public PushEvent build() {
            if (path == null || httpRequest == null || headers == null) {
                throw new IllegalArgumentException();
            }
            return PushEvent.create(this);
        }


        // ---------------------------------------------------- Private Methods


        private static String validate(final String val) {
            return ((val != null && !val.isEmpty()) ? val : null);
        }
    }
}
