/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.servlet;

import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.Session;
import org.glassfish.grizzly.localization.LogMessages;

/**
 * Basic {@link HttpSession} based on {@link Session} support.
 * 
 * @author Jeanfrancois Arcand
 */
@SuppressWarnings("deprecation")
public class HttpSessionImpl implements HttpSession {

    private static final Logger LOGGER = Grizzly.logger(HttpSessionImpl.class);
    /**
     * The real session object
     */
    private final Session session;
    /**
     * The ServletContext.
     */
    private final WebappContext contextImpl;

    /**
     * Create an HttpSession.
     * @param contextImpl
     * @param session internal session object
     */
    public HttpSessionImpl(final WebappContext contextImpl,
            final Session session) {
        this.contextImpl = contextImpl;
        this.session = session;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCreationTime() {
        if (!session.isValid()) {
            throw new IllegalStateException("The session was invalidated");
        }
        
        return session.getCreationTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return session.getIdInternal();
    }

    /**
     * Is the current Session valid?
     * @return true if valid.
     */
    protected boolean isValid() {
        return session.isValid();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastAccessedTime() {
        if (!session.isValid()) {
            throw new IllegalStateException("The session was invalidated");
        }

        return session.getTimestamp();
    }

    /**
     * Reset the timestamp.
     */
    protected void access() {
        session.access();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServletContext getServletContext() {
        return contextImpl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxInactiveInterval(int sessionTimeout) {
        if (sessionTimeout < 0) {
            sessionTimeout = -1;
        } else {
            sessionTimeout = sessionTimeout * 1000;
        }
        
        session.setSessionTimeout(sessionTimeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxInactiveInterval() {
        long sessionTimeout = session.getSessionTimeout();
        if (sessionTimeout < 0) {
            return -1;
        }

        sessionTimeout /= 1000;
        if (sessionTimeout > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(sessionTimeout + " cannot be cast to int.");
        }
        
        return (int) sessionTimeout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public javax.servlet.http.HttpSessionContext getSessionContext() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String key) {
        return session.getAttribute(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValue(String value) {
        return session.getAttribute(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(session.attributes().keySet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getValueNames() {
        return session.attributes().entrySet().toArray(
                new String[session.attributes().size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttribute(String key, Object value) {
        
         // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(key);
            return;
        }
        
        Object unbound = session.getAttribute(key);
        session.setAttribute(key, value);

        // Call the valueUnbound() method if necessary
        if ((unbound != null) && (unbound != value) &&
                (unbound instanceof HttpSessionBindingListener)) {
            try {
                ((HttpSessionBindingListener) unbound).valueUnbound(new HttpSessionBindingEvent(this, key));
            } catch (Throwable t) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_SESSION_LISTENER_UNBOUND_ERROR(unbound.getClass().getName()));
                }
            }
        }
        // Construct an event with the new value
        HttpSessionBindingEvent event = null;

        // Call the valueBound() method if necessary
        if (value instanceof HttpSessionBindingListener) {
            if (value != unbound) {
                event = new HttpSessionBindingEvent(this, key, value);
                try {
                    ((HttpSessionBindingListener) value).valueBound(event);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.log(Level.WARNING,
                                LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_SESSION_LISTENER_BOUND_ERROR(value.getClass().getName()));
                    }
                }
            }
        }

        // Notify interested application event listeners
        EventListener[] listeners = contextImpl.getEventListeners();
        if (listeners.length == 0) {
            return;
        }
        for (int i = 0, len = listeners.length; i < len; i++) {
            if (!(listeners[i] instanceof HttpSessionAttributeListener)) {
                continue;
            }
            HttpSessionAttributeListener listener =
                    (HttpSessionAttributeListener) listeners[i];
            try {
                if (unbound != null) {
                    if (event == null) {
                        event = new HttpSessionBindingEvent(this, key, unbound);
                    }
                    listener.attributeReplaced(event);
                } else {
                    if (event == null) {
                        event = new HttpSessionBindingEvent(this, key, value);
                    }
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_ATTRIBUTE_LISTENER_ADD_ERROR("HttpSessionAttributeListener", listener.getClass().getName()),
                               t);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putValue(String key, Object value) {
        setAttribute(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(String key) {
        Object value = session.removeAttribute(key);
  
        if (value == null) {
            return;
        }

        // Call the valueUnbound() method if necessary
        HttpSessionBindingEvent event = null;
        if (value instanceof HttpSessionBindingListener) {
            event = new HttpSessionBindingEvent(this,key, value);
            ((HttpSessionBindingListener) value).valueUnbound(event);
        }

        EventListener[] listeners = contextImpl.getEventListeners();
        if (listeners.length == 0)
            return;
        for (int i = 0, len = listeners.length; i < len; i++) {
            if (!(listeners[i] instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners[i];
            try {
                if (event == null) {
                    event = new HttpSessionBindingEvent
                        (this, key, value);
                }
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_ATTRIBUTE_LISTENER_REMOVE_ERROR("HttpSessionAttributeListener", listener.getClass().getName()),
                               t);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeValue(String key) {
        removeAttribute(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void invalidate() {
        session.setValid(false);
        session.attributes().clear();

        EventListener[] listeners = contextImpl.getEventListeners();
        if (listeners.length > 0) {
            HttpSessionEvent event =
                    new HttpSessionEvent(this);
            for (int i = 0, len = listeners.length; i < len; i++) {
                Object listenerObj = listeners[i];
                if (!(listenerObj instanceof HttpSessionListener)) {
                    continue;
                }
                HttpSessionListener listener =
                        (HttpSessionListener) listenerObj;
                try {
                    listener.sessionDestroyed(event);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.log(Level.WARNING,
                                   LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_DESTROYED_ERROR("sessionDestroyed", "HttpSessionListener", listener.getClass().getName()),
                                   t);
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNew() {
        if (!session.isValid()) {
            throw new IllegalStateException("The session was invalidated");
        }
        
        return session.isNew();
    }

    /**
     * Invoke to notify all registered {@link HttpSessionListener} of the 
     * session has just been created.
     */
    protected void notifyNew() {
        EventListener[] listeners = contextImpl.getEventListeners();
        if (listeners.length > 0) {
            HttpSessionEvent event =
                    new HttpSessionEvent(this);
            for (int i = 0, len = listeners.length; i < len; i++) {
                Object listenerObj = listeners[i];
                if (!(listenerObj instanceof HttpSessionListener)) {
                    continue;
                }
                HttpSessionListener listener =
                        (HttpSessionListener) listenerObj;
                try {
                    listener.sessionCreated(event);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.log(Level.WARNING,
                                   LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_INITIALIZED_ERROR("sessionCreated", "HttpSessionListener", listener.getClass().getName()),
                                   t);
                    }
                }
            }
        }
    }
    
    /**
     * Invoke to notify all registered {@link HttpSessionListener} of the 
     * session has just been created.
     */
    protected void notifyIdChanged(final String oldId) {
        EventListener[] listeners = contextImpl.getEventListeners();
        if (listeners.length > 0) {
            HttpSessionEvent event =
                    new HttpSessionEvent(this);
            for (int i = 0, len = listeners.length; i < len; i++) {
                Object listenerObj = listeners[i];
                if (!(listenerObj instanceof HttpSessionIdListener)) {
                    continue;
                }
                HttpSessionIdListener listener =
                        (HttpSessionIdListener) listenerObj;
                try {
                    listener.sessionIdChanged(event, oldId);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.log(Level.WARNING,
                                   LogMessages.WARNING_GRIZZLY_HTTP_SERVLET_CONTAINER_OBJECT_INITIALIZED_ERROR("sessionCreated", "HttpSessionListener", listener.getClass().getName()),
                                   t);
                    }
                }
            }
        }
    }    
}
