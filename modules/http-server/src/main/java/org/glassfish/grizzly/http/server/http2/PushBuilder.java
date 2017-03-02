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

import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.Session;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;


/**
 * Build a request to be pushed.  This is based on Servlet 4.0's PushBuilder.
 *
 * According section 8.2 of RFC 7540, a promised request must be cacheable and
 * safe without a request body.
 *
 * <p>A PushBuilder is obtained by calling {@link
 * Request#getPushBuilder()}.  Each call to this method will
 * return a new instance of a PushBuilder based off the current {@code
 * HttpServletRequest}.  Any mutations to the returned PushBuilder are
 * not reflected on future returns.</p>
 *
 * <p>The instance is initialized as follows:</p>
 *
 * <ul>
 *
 * <li>The method is initialized to "GET"</li>
 *
 * <li>The existing request headers of the current {@link Request}
 * are added to the builder, except for:
 *
 * <ul>
 *   <li>Conditional headers (eg. If-Modified-Since)
 *   <li>Range headers
 *   <li>Expect headers
 *   <li>Authorization headers
 *   <li>Referrer headers
 * </ul>
 *
 * </li>
 *
 * <li>The {@link Request#getRequestedSessionId()} value,
 * unless at the time of the call {@link
 * Request#getSession(boolean)} has previously been called to
 * create a new {@link Session}, in which case the new session ID
 * will be used as the PushBuilder's requested session ID. The source of
 * the requested session id will be the same as for the request</li>
 *
 * <li>The Referer(sic) header will be set to {@link
 * Request#getRequestURL()} plus any {@link
 * Request#getQueryString()} </li>
 *
 * <li>If {@link Response#addCookie(Cookie)} has been called
 * on the associated response, then a corresponding Cookie header will be added
 * to the PushBuilder, unless the {@link Cookie#getMaxAge()} is &lt;=0, in which
 * case the Cookie will be removed from the builder.</li>
 *
 * <li>If this request has has the conditional headers If-Modified-Since
 * or If-None-Match, then the {@link #isConditional()} header is set to
 * true.</li>
 *
 * </ul>
 *
 * <p>The {@link #path} method must be called on the {@code PushBuilder}
 * instance before the call to {@link #push}.  Failure to do so must
 * cause an exception to be thrown from {@link
 * #push}, as specified in that method.</p>
 *
 * <p>A PushBuilder can be customized by chained calls to mutator
 * methods before the {@link #push()} method is called to initiate an
 * asynchronous push request with the current state of the builder.
 * After the call to {@link #push()}, the builder may be reused for
 * another push, however the implementation must make it so the {@link
 * #path(String)}, {@link #eTag(String)} and {@link
 * #lastModified(String)} values are cleared before returning from
 * {@link #push}.  All other values are retained over calls to {@link
 * #push()}.
 *
 * @since 2.3.30
 */
public class PushBuilder {

    private static final Header[] REMOVE_HEADERS = {
            Header.ETag,
            Header.IfModifiedSince,
            Header.IfNoneMatch,
            Header.IfRange,
            Header.IfUnmodifiedSince,
            Header.IfMatch,
            Header.LastModified,
            Header.Referer,
            Header.AcceptRanges,
            Header.Range,
            Header.AcceptRanges,
            Header.ContentRange,
            Header.Authorization,
            Header.ProxyAuthenticate,
            Header.ProxyAuthorization,
            Header.WWWAuthenticate
    };

    private static final Header[] CONDITIONAL_HEADERS = {
            Header.IfModifiedSince,
            Header.IfNoneMatch,
            Header.IfRange,
            Header.IfUnmodifiedSince,
            Header.IfMatch,
    };

    Method method = Method.GET;
    String queryString;
    String sessionId;
    boolean conditional;
    MimeHeaders headers;
    String path;
    String eTag;
    String lastModified;
    Request request;

    public PushBuilder(final Request request) {
        headers = new MimeHeaders();
        headers.copyFrom(request.getRequest().getHeaders());
        for (int i = 0, len = CONDITIONAL_HEADERS.length; i < len; i++) {
            if (headers.contains(CONDITIONAL_HEADERS[i])) {
                conditional = true;
                break;
            }
        }
        for (int i = 0, len = REMOVE_HEADERS.length; i < len; i++) {
            headers.removeHeader(REMOVE_HEADERS[i]);
        }
        this.request = request;
    }

    /**
     * <p>Set the method to be used for the push.</p>
     *
     * @param method the method to be used for the push.
     *
     * @return this builder.
     *
     * @throws NullPointerException     if the argument is {@code null}
     * @throws IllegalArgumentException if the argument is the empty String,
     *                                  or any non-cacheable or unsafe methods defined in RFC 7231,
     *                                  which are POST, PUT, DELETE, CONNECT, OPTIONS and TRACE.
     */
    public PushBuilder method(Method method) {
        if (method == null) {
            throw new NullPointerException();
        }
        if (Method.GET.equals(method) || Method.HEAD.equals(method)) {
            this.method = method;
        } else {
            throw new IllegalArgumentException();
        }
        return this;
    }

    /**
     * Set the query string to be used for the push.
     * <p>
     * Will be appended to any query String included in a call to {@link
     * #path(String)}.  Any duplicate parameters must be preserved. This
     * method should be used instead of a query in {@link #path(String)}
     * when multiple {@link #push()} calls are to be made with the same
     * query string.
     *
     * @param queryString the query string to be used for the push.
     *
     * @return this builder.
     */
    public PushBuilder queryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    /**
     * Set the SessionID to be used for the push.
     * The session ID will be set in the same way it was on the associated request (ie
     * as a cookie if the associated request used a cookie, or as a url parameter if
     * the associated request used a url parameter).
     * Defaults to the requested session ID or any newly assigned session id from
     * a newly created session.
     *
     * @param sessionId the SessionID to be used for the push.
     *
     * @return this builder.
     */
    public PushBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    /**
     * Set if the request is to be conditional.
     * If the request is conditional, any available values from {@link #eTag(String)} or
     * {@link #lastModified(String)} will be set in the appropriate headers. If the request
     * is not conditional, then eTag and lastModified values are ignored.
     * Defaults to true if the associated request was conditional.
     *
     * @param conditional true if the push request is conditional
     *
     * @return this builder.
     */
    public PushBuilder conditional(boolean conditional) {
        this.conditional = conditional;
        return this;
    }

    /**
     * <p>Set a request header to be used for the push.  If the builder has an
     * existing header with the same name, its value is overwritten.</p>
     *
     * @param name  The header name to set
     * @param value The header value to set
     *
     * @return this builder.
     */
    public PushBuilder setHeader(String name, String value) {
        if (nameAndValueValid(name, value)) {
            headers.setValue(name).setString(value);
        }
        return this;
    }

    /**
     * <p>Add a request header to be used for the push.</p>
     *
     * @param name  The header name to add
     * @param value The header value to add
     *
     * @return this builder.
     */
    public PushBuilder addHeader(String name, String value) {
        if (nameAndValueValid(name, value)) {
            headers.addValue(name).setString(value);
        }
        return this;
    }



    /**
     * <p>Remove the named request header.  If the header does not exist, take
     * no action.</p>
     *
     * @param name The name of the header to remove
     *
     * @return this builder.
     */
    public PushBuilder removeHeader(String name) {
        if (nameValid(name)) {
            headers.removeHeader(name);
        }
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
     *             query string.
     *
     * @return this builder.
     */
    public PushBuilder path(String path) {
        this.path = path;
        return this;
    }

    /**
     * Set the eTag to be used for conditional pushes.
     * The eTag will be used only if {@link #isConditional()} is true.
     * Defaults to no eTag.  The value is nulled after every call to
     * {@link #push()}
     *
     * @param eTag the eTag to be used for the push.
     *
     * @return this builder.
     */
    public PushBuilder eTag(String eTag) {
        this.eTag = eTag;
        return this;
    }

    /**
     * Set the last modified date to be used for conditional pushes.
     * The last modified date will be used only if {@link
     * #isConditional()} is true.  Defaults to no date.  The value is
     * nulled after every call to {@link #push()}
     *
     * @param lastModified the last modified date to be used for the push.
     *
     * @return this builder.
     */
    public PushBuilder lastModified(String lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    /**
     * Push a resource given the current state of the builder without blocking.
     * <p>
     * <p>Push a resource based on the current state of the PushBuilder.
     * Calling this method does not guarantee the resource will actually
     * be pushed, since it is possible the client can decline acceptance
     * of the pushed resource using the underlying HTTP/2 protocol.</p>
     * <p>
     * <p>If {@link #isConditional()} is true and an eTag or
     * lastModified value is provided, then an appropriate conditional
     * header will be generated. If both an eTag and lastModified value
     * are provided only an If-None-Match header will be generated. If
     * the builder has a session ID, then the pushed request will
     * include the session ID either as a Cookie or as a URI parameter
     * as appropriate. The builders query string is merged with any
     * passed query string.</p>
     * <p>
     * <p>Before returning from this method, the builder has its path,
     * eTag and lastModified fields nulled. All other fields are left as
     * is for possible reuse in another push.</p>
     *
     * @throws IllegalStateException    if there was no call to {@link
     *                                  #path} on this instance either between its instantiation or the
     *                                  last call to {@code push()} that did not throw an
     *                                  IllegalStateException.
     */
    public void push() {
        if (path == null) {
            throw new IllegalStateException();
        }

        if (!request.isPushEnabled()) { // push support may have been disabled...
            return;
        }

        String pathLocal = ((path.charAt(0) == '/') ? path : request.getContextPath() + '/' + path);
        if (queryString != null) {
            pathLocal += ((pathLocal.indexOf('?') != -1)
                    ? '&' + queryString
                    : '?' + queryString);
        }

        path = pathLocal;

        if (conditional) {
            if (eTag != null) {
                headers.addValue(Header.IfNoneMatch).setString(eTag);
            } else if (lastModified != null) {
                headers.addValue(Header.IfModifiedSince).setString(lastModified);
            }
        }

        request.getContext().notifyDownstream(PushEvent.create(this));
        eTag = null;
        lastModified = null;
        path = null;
        headers.removeHeader(Header.IfNoneMatch);
        headers.removeHeader(Header.IfModifiedSince);

    }

    /**
     * Return the method to be used for the push.
     *
     * @return the method to be used for the push.
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Return the query string to be used for the push.
     *
     * @return the query string to be used for the push.
     */
    public String getQueryString() {
        return queryString;
    }

    /**
     * Return the SessionID to be used for the push.
     *
     * @return the SessionID to be used for the push.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Return if the request is to be conditional.
     *
     * @return if the request is to be conditional.
     */
    public boolean isConditional() {
        return conditional;
    }

    /**
     * Return the set of header to be used for the push.
     *
     * @return the set of header to be used for the push.
     */
    public Iterable<String> getHeaderNames() {
        return headers.names();
    }

    /**
     * Return the header of the given name to be used for the push.
     *
     * @return the header of the given name to be used for the push.
     */
    public String getHeader(String name) {
        return headers.getHeader(name);
    }

    /**
     * Return the URI path to be used for the push.
     *
     * @return the URI path to be used for the push.
     */
    public String getPath() {
        return path;
    }

    /**
     * Return the eTag to be used for conditional pushes.
     *
     * @return the eTag to be used for conditional pushes.
     */
    public String getETag() {
        return eTag;
    }

    /**
     * Return the last modified date to be used for conditional pushes.
     *
     * @return the last modified date to be used for conditional pushes.
     */
    public String getLastModified() {
        return lastModified;
    }


    // -------------------------------------------------------- Private Methods


    private static boolean nameAndValueValid(final String name, final String value) {
        return nameValid(name) && value != null && !value.isEmpty();
    }

    private static boolean nameValid(final String name) {
        return (name != null && !name.isEmpty());
    }

}
