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
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;

/**
 * TODO: Documentation
 */
public class PushEvent implements FilterChainEvent {

    private static final ThreadCache.CachedTypeIndex<PushEvent> CACHE_IDX =
            ThreadCache.obtainIndex(PushEvent.class, 8);

    public static final Object TYPE = PushEvent.class.getName();

    private Method method;
    private String queryString;
    private String sessionId;
    private boolean conditional;
    private boolean secure;
    private MimeHeaders headers;
    private String path;
    private String eTag;
    private String lastModified;
    private String referrer;
    private HttpHeader originalHeader;

    // ----------------------------------------------------------- Constructors


    private PushEvent() {
    }


    // ------------------------------------------ Methods from FilterChainEvent


    @Override
    public Object type() {
        return TYPE;
    }


    // --------------------------------------------------------- Public Methods


    public static PushEvent create(final PushBuilder builder) {
        PushEvent pushEvent =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (pushEvent == null) {
            pushEvent = new PushEvent();
        }

        return pushEvent.init(builder);
    }

    public Method getMethod() {
        return method;
    }

    public String getQueryString() {
        return queryString;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isConditional() {
        return conditional;
    }

    public MimeHeaders getHeaders() {
        return headers;
    }

    public String getPath() {
        return path;
    }

    public String getETag() {
        return eTag;
    }

    public String getLastModified() {
        return lastModified;
    }

    public boolean isSecure() {
        return secure;
    }

    public String getReferrer() {
        return referrer;
    }

    public HttpHeader getOriginalHeader() {
        return originalHeader;
    }

    public void recycle() {
        method = null;
        queryString = null;
        sessionId = null;
        conditional = false;
        secure = false;
        headers.recycle();
        path = null;
        eTag = null;
        lastModified = null;
        referrer = null;
        originalHeader = null;
    }


    // -------------------------------------------------------- Private Methods


    private PushEvent init(final PushBuilder builder) {
        method = builder.method;
        queryString = builder.queryString;
        sessionId = builder.sessionId;
        conditional = builder.conditional;
        headers = new MimeHeaders();
        headers.copyFrom(builder.headers);
        path = builder.path;
        eTag = builder.eTag;
        lastModified = builder.lastModified;
        secure = builder.request.isSecure();
        referrer = composeReferrerHeader(builder.request);
        originalHeader = builder.request.getRequest();
        return this;
    }

    private String composeReferrerHeader(final Request request) {
        return new StringBuilder(64).append(request.isSecure() ? "https" : "http")
                .append("://")
                .append(request.getHeader(Header.Host))
                .append(request.getRequestURI())
                .toString();
    }

}
