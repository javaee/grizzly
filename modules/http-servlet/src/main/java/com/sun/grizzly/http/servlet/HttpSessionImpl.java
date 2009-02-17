/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */
package com.sun.grizzly.http.servlet;

import com.sun.grizzly.tcp.http11.GrizzlySession;
import com.sun.grizzly.util.LoggerUtils;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * Basic {@link HttpSession} based on {@link GrizzlySession} support.
 * 
 * @author Jeanfrancois Arcand
 */
public class HttpSessionImpl implements HttpSession {

    /**
     * The real session objecty
     */
    private GrizzlySession session;
    /**
     * The ServletContext.
     */
    private ServletContextImpl contextImpl;
    /**
     * When this session was created.
     */
    private Long creationTime;
    /**
     * Last time the session was accessed.
     */
    private Long lastAccessed;
    /**
     * Is this session new.
     */
    private boolean isNew = true;

    /**
     * Create an HttpSession.
     * @param contextImpl
     */
    public HttpSessionImpl(ServletContextImpl contextImpl) {
        this.contextImpl = contextImpl;

        creationTime = System.currentTimeMillis();
        lastAccessed = creationTime;
    }

    /**
     * {@inheritDoc}
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * {@inheritDoc}
     */
    public String getId() {
        return session.getIdInternal();
    }

    /**
     * {@inheritDoc}
     */
    public long getLastAccessedTime() {
        return lastAccessed;
    }

    /**
     * Reset the timestamp.
     */
    protected void access() {
        lastAccessed = System.currentTimeMillis();
        session.setTimestamp(lastAccessed);
        isNew = false;
    }

    /**
     * {@inheritDoc}
     */
    public ServletContext getServletContext() {
        return contextImpl;
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxInactiveInterval(int sessionTimeout) {
        session.setTimestamp(sessionTimeout);
    }

    /**
     * {@inheritDoc}
     */
    public int getMaxInactiveInterval() {
        return (int) session.getSessionTimeout();
    }

    /**
     * {@inheritDoc}
     */
    public HttpSessionContext getSessionContext() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Object getAttribute(String key) {
        return session.getAttribute(key);
    }

    /**
     * {@inheritDoc}
     */
    public Object getValue(String value) {
        return session.getAttribute(value);
    }

    /**
     * {@inheritDoc}
     */
    public Enumeration getAttributeNames() {
        return session.atttibutes().keys();
    }

    /**
     * {@inheritDoc}
     */
    public String[] getValueNames() {
        return session.atttibutes().entrySet().toArray(new String[0]);
    }

    /**
     * {@inheritDoc}
     */
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
                LoggerUtils.getLogger().log(Level.WARNING, "", t);
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
                    LoggerUtils.getLogger().log(Level.WARNING, "", t);
                }
            }
        }

        // Notify interested application event listeners
        List listeners = contextImpl.getListeners();
        if (listeners.size() == 0) {
            return;
        }
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof HttpSessionAttributeListener)) {
                continue;
            }
            HttpSessionAttributeListener listener =
                    (HttpSessionAttributeListener) listeners.get(i);
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
                LoggerUtils.getLogger().log(Level.WARNING, "", t);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void putValue(String key, Object value) {
        setAttribute(key, value);
    }

    /**
     * {@inheritDoc}
     */
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

        List listeners = contextImpl.getListeners();
        if (listeners.size() == 0)
            return;
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners.get(i);
            try {
                if (event == null) {
                    event = new HttpSessionBindingEvent
                        (this, key, value);
                }
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                 LoggerUtils.getLogger().log(Level.WARNING, "", t);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void removeValue(String key) {
        removeAttribute(key);
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void invalidate() {
        session.atttibutes().clear();
        creationTime = 0L;
        isNew = true;

        List listeners = contextImpl.getListeners();
        if (listeners.size() > 0) {
            HttpSessionEvent event =
                    new HttpSessionEvent(this);
            for (int i = 0; i < listeners.size(); i++) {
                Object listenerObj = listeners.get(i);
                int j = (listeners.size() - 1) - i;
                if (!(listenerObj instanceof HttpSessionListener)) {
                    continue;
                }
                HttpSessionListener listener =
                        (HttpSessionListener) listenerObj;
                try {
                    listener.sessionDestroyed(event);
                } catch (Throwable t) {
                    LoggerUtils.getLogger().log(Level.WARNING, "", t);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isNew() {
        return isNew;
    }

    /**
     * Set the {@link GrizzlySession}
     */
    protected void setSession(GrizzlySession session) {
        this.session = session;
    }
    
    /**
     * Invoke to notify all registered {@link HttpSessionListener} of the 
     * session has just been created.
     */
    protected void notifyNew() {
        List listeners = contextImpl.getListeners();
        if (listeners.size() > 0) {
            HttpSessionEvent event =
                    new HttpSessionEvent(this);
            for (int i = 0; i < listeners.size(); i++) {
                Object listenerObj = listeners.get(i);
                if (!(listenerObj instanceof HttpSessionListener)) {
                    continue;
                }
                HttpSessionListener listener =
                        (HttpSessionListener) listenerObj;
                try {
                    listener.sessionCreated(event);
                } catch (Throwable t) {
                    LoggerUtils.getLogger().log(Level.WARNING, "", t);
                }
            }
        }
    }
}
