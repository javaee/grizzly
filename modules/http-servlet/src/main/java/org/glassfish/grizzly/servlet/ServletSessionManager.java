/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.servlet;

import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.server.DefaultSessionManager;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Session;
import org.glassfish.grizzly.http.server.SessionManager;
import org.glassfish.grizzly.http.server.util.Globals;

/**
 * The Servlet-aware {@link SessionManager} implementation.
 */
public class ServletSessionManager implements SessionManager {
    /**
     * @return <tt>DefaultSessionManager</tt> singleton
     */
    public static SessionManager instance() {
        return LazyHolder.INSTANCE;
    }

    // Lazy initialization of ServletSessionManager
    private static class LazyHolder {
        private static final ServletSessionManager INSTANCE = new ServletSessionManager();
    }

    private final SessionManager defaultManager = DefaultSessionManager.instance();

    private String sessionCookieName = Globals.SESSION_COOKIE_NAME;

    private ServletSessionManager() {
    }

    @Override
    public Session getSession(final Request request,
            final String requestedSessionId) {
        return defaultManager.getSession(request, requestedSessionId);
    }

    @Override
    public Session createSession(final Request request) {
        return defaultManager.createSession(request);
    }

    @Override
    public String changeSessionId(final Request request, final Session session) {
        return defaultManager.changeSessionId(request, session);
    }

    @Override
    public void configureSessionCookie(final Request request, final Cookie cookie) {
        defaultManager.configureSessionCookie(request, cookie);

        final HttpServletRequestImpl servletRequest =
                ServletHandler.getServletRequest(request);

        assert servletRequest != null;

        final javax.servlet.SessionCookieConfig cookieConfig =
                servletRequest.getContextImpl().getSessionCookieConfig();

        if (cookieConfig.getDomain() != null) {
            cookie.setDomain(cookieConfig.getDomain());
        }
        if (cookieConfig.getPath() != null) {
            cookie.setPath(cookieConfig.getPath());
        }
        if (cookieConfig.getComment() != null) {
            cookie.setVersion(1);
            cookie.setComment(cookieConfig.getComment());
        }

        cookie.setSecure(cookieConfig.isSecure());
        cookie.setHttpOnly(cookieConfig.isHttpOnly());
        cookie.setMaxAge(cookieConfig.getMaxAge());
    }

    @Override
    public void setSessionCookieName(final String name) {
        if (name != null && !name.isEmpty()) {
            sessionCookieName = name;
        }
    }

    @Override
    public String getSessionCookieName() {
        return sessionCookieName;
    }
}
