/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Default {@link SessionManager} implementation.
 */
public class DefaultSessionManager implements SessionManager {
    
    /**
     * @return <tt>DefaultSessionManager</tt> singleton
     */
    public static SessionManager instance() {
        return LazyHolder.INSTANCE;
    }
    
    // Lazy initialization of DefaultSessionManager
    private static class LazyHolder {
        private static final DefaultSessionManager INSTANCE = new DefaultSessionManager();
    }
    
    /**
     * Not Good. We need a better mechanism. TODO: Move Session Management out
     * of here
     */
    private final ConcurrentMap<String, Session> sessions
            = DataStructures.<String, Session>getConcurrentMap();

    private final Random rnd = new Random();

    /**
     * Scheduled Thread that clean the cache every XX seconds.
     */
    private final ScheduledThreadPoolExecutor sessionExpirer
            = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    final Thread t = new Thread(r, "Grizzly-HttpSession-Expirer");
                    t.setDaemon(true);
                    return t;
                }
            });

    {
        sessionExpirer.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
                Map.Entry<String, Session> entry;
                while (iterator.hasNext()) {
                    entry = iterator.next();
                    final Session session = entry.getValue();

                    if (!session.isValid()
                            || (session.getSessionTimeout() > 0
                            && currentTime - session.getTimestamp() > session.getSessionTimeout())) {
                        session.setValid(false);
                        iterator.remove();
                    }
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private DefaultSessionManager() {
    }
    
    @Override
    public Session getSession(final Request request,
            String requestedSessionId) {

        if (requestedSessionId != null) {
            final Session session = sessions.get(requestedSessionId);
            if (session != null && session.isValid()) {
                return session;
            }
        }
        
        return null;

    }
    
    @Override
    public Session createSession(final Request request) {
        final Session session = new Session();
        
        String requestedSessionId;
        do {
            requestedSessionId = String.valueOf(generateRandomLong());
            session.setIdInternal(requestedSessionId);
        } while (sessions.putIfAbsent(requestedSessionId, session) != null);

        return session;
    }

    @Override
    public String changeSessionId(final Request request, final Session session) {
        final String oldSessionId = session.getIdInternal();
        final String newSessionId = String.valueOf(generateRandomLong());

        session.setIdInternal(newSessionId);

        sessions.remove(oldSessionId);
        sessions.put(newSessionId, session);
        return oldSessionId;
    }

    @Override
    public void configureSessionCookie(final Request request,
            final Cookie cookie) {
    }

    /**
     * Returns pseudorandom positive long value.
     */
    private long generateRandomLong() {
        return (rnd.nextLong() & 0x7FFFFFFFFFFFFFFFl);
    }
}
