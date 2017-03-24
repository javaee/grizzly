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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Build a request to be pushed.  This is based on Servlet 4.0's PushBuilder.
 *
 * According section 8.2 of RFC 7540, a promised request must be cacheable and
 * safe without a request body.
 *
 * <p>A PushBuilder is obtained by calling {@link
 * Request#newPushBuilder()}.  Each call to this method will
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
 * another push, however the path and conditional headers are cleared
 * before returning from {@link #push}.  All other values are retained
 * over calls to {@link #push()}.
 *
 * @since 2.3.30
 */
@SuppressWarnings("UnusedReturnValue")
public final class PushBuilder {

    private static final Header[] REMOVE_HEADERS = {
            Header.Cookie,
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
    MimeHeaders headers;
    String path;
    Request request;
    boolean sessionFromURL;
    List<Cookie> cookies;

    public PushBuilder(final Request request) {
        this.request = request;
        headers = new MimeHeaders();
        headers.copyFrom(request.getRequest().getHeaders());

        for (int i = 0, len = REMOVE_HEADERS.length; i < len; i++) {
            headers.removeHeader(REMOVE_HEADERS[i]);
        }
        headers.setValue(Header.Referer).setString(composeReferrerHeader(request));
        final Session session = request.getSession(false);
        if (session != null) {
            sessionId = session.getIdInternal();
        }
        if (sessionId == null) {
            sessionId = request.getRequestedSessionId();
        }
        sessionFromURL = request.isRequestedSessionIdFromURL();

        final Cookie[] requestCookies = request.getCookies();
        if (requestCookies != null) {
            cookies = new ArrayList<>(Arrays.asList(requestCookies));
        }

        final Cookie[] responseCookies = request.getResponse().getCookies();
        if (responseCookies != null) {
            if (cookies == null) {
                cookies = new ArrayList<>(responseCookies.length);
            }
            for (int i = 0, len = responseCookies.length; i < len; i++) {
                final Cookie c = responseCookies[i];
                if (c.getMaxAge() > 0) {
                    cookies.add(new Cookie(c.getName(), c.getValue()));
                } else {
                    for (int j = 0, jlen = cookies.size(); j < jlen; j++) {
                        if (cookies.get(j).getName().equals(c.getName())) {
                            cookies.remove(j);
                        }
                    }
                }
            }
        }

        if (cookies != null && !cookies.isEmpty()) {
            for (int i = 0, len = cookies.size(); i < len; i++) {
                final Cookie c = cookies.get(i);
                headers.addValue(Header.Cookie).setString(c.asClientCookieString());
            }
        }
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
        this.queryString = validate(queryString);
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
        this.sessionId = validate(sessionId);
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
     * <p>Set a request header to be used for the push.  If the builder has an
     * existing header with the same name, its value is overwritten.</p>
     *
     * @param name  The {@link Header} to set
     * @param value The header value to set
     *
     * @return this builder.
     */
    public PushBuilder setHeader(Header name, String value) {
        if (name != null && validValue(value)) {
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
     * <p>Add a request header to be used for the push.</p>
     *
     * @param name  The {@link Header} to add
     * @param value The header value to add
     *
     * @return this builder.
     */
    public PushBuilder addHeader(Header name, String value) {
        if (name != null && validValue(value)) {
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
        if (validValue(name)) {
            if (!Header.Referer.getLowerCase().equals(name.toLowerCase())) {
                headers.removeHeader(name);
            }
        }
        return this;
    }

    /**
     * <p>Remove the named request header.  If the header does not exist, take
     * no action.</p>
     *
     * @param name The {@link Header} to remove
     *
     * @return this builder.
     */
    public PushBuilder removeHeader(Header name) {
        if (name != null && Header.Referer != name) {
            headers.removeHeader(name);
        }
        return this;
    }

    /**
     * Set the URI path to be used for the push.  The path may start
     * with "/" in which case it is treated as an absolute path,
     * otherwise it is relative to the context path of the associated
     * request.  There is no path default and path(String) must
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
        this.path = validate(path);
        return this;
    }

    /**
     * Push a resource given the current state of the builder without blocking.
     * <p>
     * <p>Push a resource based on the current state of the PushBuilder.
     * Calling this method does not guarantee the resource will actually
     * be pushed, since it is possible the client can decline acceptance
     * of the pushed resource using the underlying HTTP/2 protocol.</p>
     *
     * <p>Before returning from this method, the builder has its path set to null
     * and all conditional headers removed. All other fields are left as
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

        if (sessionId != null) {
            if (sessionFromURL){
                pathLocal += ';' + request.getSessionCookieName() + '=' + sessionId;
            } else {
                headers.addValue(Header.Cookie)
                        .setString(new Cookie(
                                request.getSessionCookieName(), sessionId).asClientCookieString());
            }
        }

        path = pathLocal;

        request.getContext().notifyDownstream(PushEvent.create(this));
        path = null;
        for (int i = 0, len = CONDITIONAL_HEADERS.length; i < len; i++) {
            headers.removeHeader(CONDITIONAL_HEADERS[i]);
        }

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


    // -------------------------------------------------------- Private Methods


    private static boolean nameAndValueValid(final String name, final String value) {
        return validValue(name) && validValue(value);
    }

    private static boolean validValue(final String value) {
        return (value != null && !value.isEmpty());
    }

    private static String validate(final String value) {
        return ((validValue(value)) ? value : null);
    }

    private String composeReferrerHeader(final Request request) {
        final StringBuilder sb = new StringBuilder(64);
        final String queryString = request.getQueryString();
        sb.append(request.getRequestURL());
        if (queryString != null) {
            sb.append('?').append(queryString);
        }
        return sb.toString();
    }

}
