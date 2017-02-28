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

import org.glassfish.grizzly.http.Method;

/**
 * TODO: Documentation
 */
public class PushBuilder {

        /**
         * <p>Set the method to be used for the push.</p>
         *
         * @throws NullPointerException if the argument is {@code null}
         *
         * @throws IllegalArgumentException if the argument is the empty String,
         *         or any non-cacheable or unsafe methods defined in RFC 7231,
         *         which are POST, PUT, DELETE, CONNECT, OPTIONS and TRACE.
         *
         * @param method the method to be used for the push.
         * @return this builder.
         */
        public PushBuilder method(Method method) {
            return this;
        }

        /** Set the query string to be used for the push.
         *
         * Will be appended to any query String included in a call to {@link
         * #path(String)}.  Any duplicate parameters must be preserved. This
         * method should be used instead of a query in {@link #path(String)}
         * when multiple {@link #push()} calls are to be made with the same
         * query string.
         * @param  queryString the query string to be used for the push.
         * @return this builder.
         */
        public PushBuilder queryString(String queryString) {
            return this;
        }

        /** Set the SessionID to be used for the push.
         * The session ID will be set in the same way it was on the associated request (ie
         * as a cookie if the associated request used a cookie, or as a url parameter if
         * the associated request used a url parameter).
         * Defaults to the requested session ID or any newly assigned session id from
         * a newly created session.
         * @param sessionId the SessionID to be used for the push.
         * @return this builder.
         */
        public PushBuilder sessionId(String sessionId) {
            return this;
        }

        /** Set if the request is to be conditional.
         * If the request is conditional, any available values from {@link #eTag(String)} or
         * {@link #lastModified(String)} will be set in the appropriate headers. If the request
         * is not conditional, then eTag and lastModified values are ignored.
         * Defaults to true if the associated request was conditional.
         * @param  conditional true if the push request is conditional
         * @return this builder.
         */
        public PushBuilder conditional(boolean conditional) {
            return this;
        }

        /**
         * <p>Set a request header to be used for the push.  If the builder has an
         * existing header with the same name, its value is overwritten.</p>
         *
         * @param name The header name to set
         * @param value The header value to set
         * @return this builder.
         */
        public PushBuilder setHeader(String name, String value) {
            return this;
        }

        /**
         * <p>Add a request header to be used for the push.</p>
         * @param name The header name to add
         * @param value The header value to add
         * @return this builder.
         */
        public PushBuilder addHeader(String name, String value) {
            return this;
        }

        /**
         * <p>Remove the named request header.  If the header does not exist, take
         * no action.</p>
         *
         * @param name The name of the header to remove
         * @return this builder.
         */
        public PushBuilder removeHeader(String name) {
            return this;
        }

        /**
         * Set the URI path to be used for the push.  The path may start
         * with "/" in which case it is treated as an absolute path,
         * otherwise it is relative to the context path of the associated
         * request.  There is no path default and {@link #path(String)} must
         * be called before every call to {@link #push()}.  If a query
         * string is present in the argument {@code path}, its contents must
         * be merged with the contents previously passed to {@link
         * #queryString}, preserving duplicates.
         *
         * @param path the URI path to be used for the push, which may include a
         * query string.
         * @return this builder.
         */
        public PushBuilder path(String path) {
            return this;
        }

        /**
         * Set the eTag to be used for conditional pushes.
         * The eTag will be used only if {@link #isConditional()} is true.
         * Defaults to no eTag.  The value is nulled after every call to
         * {@link #push()}
         * @param eTag the eTag to be used for the push.
         * @return this builder.
         */
        public PushBuilder eTag(String eTag) {
            return this;
        }

        /**
         * Set the last modified date to be used for conditional pushes.
         * The last modified date will be used only if {@link
         * #isConditional()} is true.  Defaults to no date.  The value is
         * nulled after every call to {@link #push()}
         * @param lastModified the last modified date to be used for the push.
         * @return this builder.
         * */
        public PushBuilder lastModified(String lastModified) {
            return this;
        }

        /** Push a resource given the current state of the builder,
         * returning immediately without blocking.
         *
         * <p>Push a resource based on the current state of the PushBuilder.
         * Calling this method does not guarantee the resource will actually
         * be pushed, since it is possible the client can decline acceptance
         * of the pushed resource using the underlying HTTP/2 protocol.</p>

         * <p>If {@link #isConditional()} is true and an eTag or
         * lastModified value is provided, then an appropriate conditional
         * header will be generated. If both an eTag and lastModified value
         * are provided only an If-None-Match header will be generated. If
         * the builder has a session ID, then the pushed request will
         * include the session ID either as a Cookie or as a URI parameter
         * as appropriate. The builders query string is merged with any
         * passed query string.</p>

         * <p>Before returning from this method, the builder has its path,
         * eTag and lastModified fields nulled. All other fields are left as
         * is for possible reuse in another push.</p>
         *
         * @throws IllegalArgumentException if the method set expects a
         * request body (eg POST)
         *
         * @throws IllegalStateException if there was no call to {@link
         * #path} on this instance either between its instantiation or the
         * last call to {@code push()} that did not throw an
         * IllegalStateException.
         */
        public void push() {

        }

        /**
         * Return the method to be used for the push.
         *
         * @return the method to be used for the push.
         */
        public String getMethod() {
            return null;
        }

        /**
         * Return the query string to be used for the push.
         *
         * @return the query string to be used for the push.
         */
        public String getQueryString() {
            return null;
        }

        /**
         * Return the SessionID to be used for the push.
         *
         * @return the SessionID to be used for the push.
         */
        public String getSessionId() {
            return null;
        }

        /**
         * Return if the request is to be conditional.
         *
         * @return if the request is to be conditional.
         */
        public boolean isConditional() {
            return false;
        }

        /**
         * Return the set of header to be used for the push.
         *
         * @return the set of header to be used for the push.
         */
        public Iterable<String> getHeaderNames() {
            return null;
        }

        /**
         * Return the header of the given name to be used for the push.
         *
         * @return the header of the given name to be used for the push.
         */
        public String getHeader(String name) {
            return null;
        }

        /**
         * Return the URI path to be used for the push.
         *
         * @return the URI path to be used for the push.
         */
        public String getPath() {
            return null;
        }

        /**
         * Return the eTag to be used for conditional pushes.
         *
         * @return the eTag to be used for conditional pushes.
         */
        public String getETag() {
            return null;
        }

        /**
         * Return the last modified date to be used for conditional pushes.
         *
         * @return the last modified date to be used for conditional pushes.
         */
        public String getLastModified() {
            return null;
        }

}
