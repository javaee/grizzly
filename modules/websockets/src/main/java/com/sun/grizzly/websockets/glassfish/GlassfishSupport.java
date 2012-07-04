/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.grizzly.websockets.glassfish;

import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Glassfish support class.
 * 
 * Currently this class is responsible for sharing Glassfish {@link HttpSession}s
 * between web container and websocket applications.
 * 
 * @author Alexey Stashok
 */
public class GlassfishSupport {

    private final GlassfishContext context;
    private final GlassfishManager manager;
    private final HttpServletRequest request;
    private GlassfishSession session;

    public GlassfishSupport() {
        manager = null;
        request = null;
        context = null;
    }

    public GlassfishSupport(final Object context,
            final Object wrapper, HttpServletRequest request) {

        GlassfishContext internalContext = null;

        try {
            internalContext = new GlassfishContext(wrapper);
        } catch (Exception e) {
            try {
                internalContext = new GlassfishContext(context);
            } catch (Exception ee) {
            }
        }

        this.context = internalContext;
        manager = this.context.getManager();
        this.request = request;
    }

    public boolean isValid() {
        return manager != null;
    }

    public HttpSession getSession(boolean create) {
        GlassfishSession gfSession = doGetSession(create);
        if (gfSession != null) {
            return gfSession.getSession();
        } else {
            return null;
        }
    }

    private GlassfishSession doGetSession(boolean create) {
        // There cannot be a session if no context has been assigned yet
        if (!isValid()) {
            return (null);
        }

        // Return the current session if it exists and is valid
        if ((session != null) && !session.isValid()) {
            session = null;
        }
        if (session != null) {
            return (session);
        }

        final String requestedSessionId = request.getRequestedSessionId();

        if (requestedSessionId != null) {
            try {
                session = manager.findSession(requestedSessionId, request);
            } catch (Exception e) {
                session = null;
            }

            if ((session != null) && !session.isValid()) {
                session = null;
            }
            if (session != null) {
                session.access();
                return (session);
            }
        }

        // Create a new session if requested and the response is not committed
        if (!create) {
            return (null);
        }

        // START S1AS8PE 4817642
        if (requestedSessionId != null && context.getReuseSessionID()) {
            session = manager.createSession(requestedSessionId);
            manager.removeFromInvalidatedSessions(requestedSessionId);
            // END S1AS8PE 4817642
            // START GlassFish 896
        } else {
            // END S1AS8PE 4817642
            // Use the connector's random number generator (if any) to generate
            // a session ID. Fallback to the default session ID generator if
            // the connector does not implement one.
            session = manager.createSession();
            // START S1AS8PE 4817642
        }
        // END S1AS8PE 4817642

        if (session != null) {
            session.access();
            return (session);
        } else {
            return (null);
        }
    }

    private static class GlassfishSession {

        private volatile static Boolean isChecked;
        private static Method isValidMethod;
        private static Method getSessionMethod;
        private static Method accessMethod;
        private final Object session;

        public GlassfishSession(Object session) {
            this.session = session;

            check();
        }

        public boolean isValid() {
            return (Boolean) exec(isValidMethod);
        }

        public HttpSession getSession() {
            return (HttpSession) exec(getSessionMethod);
        }

        public void access() {
            exec(accessMethod);
        }

        private Object exec(Method m, Object... params) {
            try {
                return m.invoke(session, params);
            } catch (Throwable e) {
                throw new IllegalStateException("Can't call method '" + m.getName() + "'", e);
            }
        }

        private void check() {
            if (isChecked == null) {
                synchronized (GlassfishSession.class) {
                    if (isChecked == null) {
                        try {
                            isValidMethod = session.getClass().getMethod("isValid");
                            getSessionMethod = session.getClass().getMethod("getSession");
                            accessMethod = session.getClass().getMethod("access");
                            isChecked = true;
                        } catch (Throwable t) {
                            isChecked = false;
                            throw new IllegalStateException("GlassfishSession can't be initialized", t);
                        }
                    }
                }
            } else if (!isChecked) {
                throw new IllegalStateException("GlassfishSession can't be initialized");
            }
        }
    }

    private static class GlassfishManager {

        private volatile static Boolean isChecked;
        private static Method findSessionMethod;
        private static Method createSession0Method;
        private static Method createSession1Method;
        // For PersistentManagerBase only
        private static Method removeFromInvalidatedSessionsMethod;
        private final Object manager;

        private GlassfishManager(Object manager) {
            this.manager = manager;
            check();
        }

        public GlassfishSession findSession(String requestedSessionId, HttpServletRequest request) {
            return wrapSession(exec(findSessionMethod, requestedSessionId, request));
        }

        public GlassfishSession createSession() {
            return wrapSession(exec(createSession0Method));
        }

        public GlassfishSession createSession(String requestedSessionId) {
            return wrapSession(exec(createSession1Method, requestedSessionId));
        }

        private void removeFromInvalidatedSessions(String requestedSessionId) {
            // check if it's PersistentManagerBase
            if (removeFromInvalidatedSessionsMethod != null) {
                exec(removeFromInvalidatedSessionsMethod, requestedSessionId);
            }
        }

        private Object exec(Method m, Object... params) {
            try {
                return m.invoke(manager, params);
            } catch (Throwable e) {
                throw new IllegalStateException("Can't call method '" + m.getName() + "'", e);
            }
        }

        private GlassfishSession wrapSession(Object session) {
            return session != null ? new GlassfishSession(session) : null;
        }

        private void check() {
            if (isChecked == null) {
                synchronized (GlassfishManager.class) {
                    if (isChecked == null) {
                        try {
                            findSessionMethod = manager.getClass().getMethod("findSession", String.class, HttpServletRequest.class);
                            createSession0Method = manager.getClass().getMethod("createSession");
                            createSession1Method = manager.getClass().getMethod("createSession", String.class);

                            try {
                                removeFromInvalidatedSessionsMethod = manager.getClass().getDeclaredMethod("removeFromInvalidatedSessions", String.class);
                            } catch (Throwable e) {
                                // optional method
                            }

                            isChecked = true;
                        } catch (Throwable t) {
                            isChecked = false;
                            throw new IllegalStateException("GlassfishSessionManager can't be initialized", t);
                        }
                    }
                }
            } else if (!isChecked) {
                throw new IllegalStateException("GlassfishSessionManager can't be initialized");
            }
        }
    }

    private static class GlassfishContext {

        private static Method getManagerMethod;
        private static Method getReuseSessionIDMethod;
        private final Object context;

        private GlassfishContext(Object context) {
            this.context = context;
            check();
        }

        public GlassfishManager getManager() {
            return new GlassfishManager(exec(getManagerMethod));
        }

        public boolean getReuseSessionID() {
            return (Boolean) exec(getReuseSessionIDMethod);
        }

        private Object exec(Method m, Object... params) {
            try {
                return m.invoke(context, params);
            } catch (Throwable e) {
                throw new IllegalStateException("Can't call method '" + m.getName() + "'", e);
            }
        }

        private void check() {
            try {
                getManagerMethod = context.getClass().getMethod("getManager");
                getReuseSessionIDMethod = context.getClass().getMethod("getReuseSessionID");
            } catch (Throwable t) {
                throw new IllegalStateException("GlassfishContext can't be initialized", t);
            }
        }
    }
}
