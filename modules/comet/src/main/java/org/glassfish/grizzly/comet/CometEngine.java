/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.comet;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Main class allowing Comet support on top of Grizzly Asynchronous Request Processing mechanism. This class is the
 * entry point to any component interested to execute Comet request style. Components can be Servlets, JSP, JSF or pure
 * Java class. A component interested to support Comet request must do:
 * <pre><code>
 * (1) First, register the topic on which Comet support will be applied:
 *     CometEngine cometEngine = CometEngine.getEngine()
 *     CometContext cometContext = cometEngine.register(topic)
 * (2) Second, add an instance of {@link CometHandler} to the
 *     {@link CometContext} returned by the register method:
 *     {@link CometContext#addCometHandler}. Executing this operation
 *     will tells Grizzly to suspend the response.
 * (3) Finally, you can {@link CometContext#notify} other {@link CometHandler}
 *     to share information between {@link CometHandler}. When notified,
 *     {@link CometHandler} can decides to push back the data, resume the
 *     response, or simply ignore the content of the notification.
 * </code></pre>
 * You can also select the stage where the suspension of the response happens when registering the {@link
 * CometContext}'s topic (see {@link #register}), which can be before, during or after invoking a <code>Servlet</code>
 *
 * There is known limitation related to <tt>HTTP pipelining</tt>, it can't work
 * properly when {@link CometContext#isDetectClosedConnections()} is enabled.
 * So if you want to support <tt>HTTP pipelining</tt>, the closed connection
 * detection mechanism should be disabled via {@link CometContext#setDetectClosedConnections(boolean)}.
 * 
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
public class CometEngine {
    // Disable suspended connection time out for a {@link CometContext#setExpirationDelay}
    @Deprecated
    public final static int DISABLE_SUSPEND_TIMEOUT = -1;
    // Disable client detection close for a {@link CometContext#setExpirationDelay}
    @Deprecated
    public final static int DISABLE_CLIENT_DISCONNECTION_DETECTION = 0;
    /**
     * The token used to support BEFORE_REQUEST_PROCESSING polling.
     */
    @Deprecated
    public final static int BEFORE_REQUEST_PROCESSING = 0;
    /**
     * The token used to support AFTER_SERVLET_PROCESSING polling.
     */
    @Deprecated
    public final static int AFTER_SERVLET_PROCESSING = 1;
    /**
     * The token used to support BEFORE_RESPONSE_PROCESSING polling.
     */
    @Deprecated
    public final static int AFTER_RESPONSE_PROCESSING = 2;
    /**
     * Main logger
     */
    protected final static Logger logger = Logger.getLogger(CometEngine.class.getName());
    /**
     * The {@link ExecutorService} used to execute
     */
    protected ExecutorService threadPool;
    /**
     * The single instance of this class.
     */
    private final static CometEngine cometEngine = new CometEngine();
    /**
     * The current active {@link CometContext} keyed by context path.
     */
    protected final Map<String, CometContext> activeContexts;
    /**
     * Is Grizzly ARP enabled? By default we set it to false.
     */
    private boolean isCometSupported;

    /**
     * Create a singleton and initialize all lists required.
     */
    protected CometEngine() {
        activeContexts = DataStructures.<String, CometContext>getConcurrentMap(16, 0.75f, 64);
    }

    /**
     * Return true if comet is enabled.
     */
    protected boolean isCometEnabled() {
        return isCometSupported;
    }

    public void setCometSupported(boolean supported) {
        isCometSupported = supported;
    }

    /**
     * Return a singleton of this Class.
     *
     * @return CometEngine the singleton.
     */
    public static CometEngine getEngine() {
        return cometEngine;
    }

    /**
     * Calls through to {@link #deregister(String)}.
     *
     * @deprecated
     */
    @Deprecated
    public CometContext unregister(String topic) {
        return deregister(topic);
    }

    /**
     * Deregister the {@link CometHandler} to the list of the {@link CometContext}. Invoking this method will invoke all
     * {@link CometHandler#onTerminate(CometEvent)} before removing the associated {@link CometContext}. Invoking that
     * method will also resume the underlying connection associated with the {@link CometHandler}, similar to what
     * {@link CometContext#resumeCometHandler(CometHandler)} do.
     */
    public CometContext deregister(String topic) {
        CometContext cometContext = activeContexts.remove(topic);
        if (cometContext != null) {
            cometContext.recycle();
        }
        return cometContext;
    }

    /**
     * Register a context path with this {@link CometEngine}. The {@link CometContext} returned will be of type
     * <code>type</code>.
     *
     * @param topic the context path used to create the {@link CometContext}
     * @param type when the request will be suspended
     *
     * @return CometContext a configured {@link CometContext}.
     * @deprecated Use {@link #register(String)} instead
     */
    public <E> CometContext<E> register(String topic, int type) {
        return register(topic);
    }

    /**
     * Register a context path with this {@link CometEngine}. The {@link CometContext} returned will be of type
     * AFTER_SERVLET_PROCESSING, which means the request target (most probably a Servlet) will be executed first and
     * then polled.
     *
     * @param topic the context path used to create the {@link CometContext}
     *
     * @return CometContext a configured {@link CometContext}.
     */
    public <E> CometContext<E> register(String topic) {
        return register(topic, DefaultNotificationHandler.class);
    }


    /**
     * Instantiate a new {@link CometContext}.
     *
     * @param topic the topic the new {@link CometContext} will represent.
     * @return a new {@link CometContext} if not already created, or the existing one.
     */
    @SuppressWarnings({"unchecked"})
    public <E> CometContext<E> register(String topic, Class<? extends NotificationHandler> notificationClass) {
        // Double checked locking used used to prevent the otherwise static/global 
        // locking, cause example code does heavy usage of register calls
        // for existing topics from http get calls etc.
        CometContext<E> cometContext = activeContexts.get(topic);
        if (cometContext == null) {
            synchronized (activeContexts) {
                cometContext = activeContexts.get(topic);
                if (cometContext == null) {
                    cometContext = new CometContext<E>(this, topic);
                    NotificationHandler notificationHandler;
                    try {
                        notificationHandler = notificationClass.newInstance();
                    } catch (Throwable t) {
                        if (logger.isLoggable(Level.SEVERE)) {
                            logger.log(Level.SEVERE,
                                LogMessages.SEVERE_GRIZZLY_COMET_ENGINE_INVALID_NOTIFICATION_HANDLER_ERROR(
                                    notificationClass.getName()), t);
                        }
                        notificationHandler = new DefaultNotificationHandler();
                    }
                    cometContext.setNotificationHandler(notificationHandler);
                    if (notificationHandler != null && notificationHandler instanceof DefaultNotificationHandler) {
                        ((DefaultNotificationHandler) notificationHandler).setThreadPool(threadPool);
                    }
                    activeContexts.put(topic, cometContext);
                }

            }
        }
        return cometContext;
    }

    /**
     * Return the {@link CometContext} associated with the topic.
     *
     * @param topic the topic used to creates the {@link CometContext}
     */
    @SuppressWarnings({"unchecked"})
    public <E> CometContext<E> getCometContext(String topic) {
        return activeContexts.get(topic);
    }

    /**
     * Interrupt a {@link CometHandler} by invoking {@link CometHandler#onInterrupt}
     *
     * @param handler The {@link CometHandler} encapsulating the suspended connection.
     * @param finishExecution Finish the current execution.
     *
     * @see CometContext#interrupt(CometHandler, boolean)
     * @deprecated use the CometContext version
     */
    @SuppressWarnings({"deprecation"})
    protected boolean interrupt(CometHandler handler, boolean finishExecution) throws IOException {
        final CometContext cometContext = handler.getCometContext();
        final boolean removed = cometContext.removeCometHandler(handler, finishExecution);
        if (removed && !finishExecution) {
            interrupt0(handler, finishExecution);
        }
        return removed;
    }

    /**
     * Interrupt logic in its own method, so it can be executed either async or sync.<br> cometHandler.onInterrupt is
     * performed async due to its functionality is unknown, hence not safe to run in the performance critical selector
     * thread.
     *
     * @param handler The {@link CometHandler} encapsulating the suspended connection.
     * @param finishExecution Finish the current execution.
     *
     * @see CometContext#interrupt0(CometHandler, boolean)
     * @deprecated use the CometContext version
     */
    protected void interrupt0(CometHandler handler, boolean finishExecution) throws IOException {
        if (finishExecution) {
            try {
                handler.onInterrupt(handler.getCometContext().eventInterrupt);
            } catch (IOException e) {
            }
        }
        handler.getResponse().finish();
    }

    /**
     * Return the current logger.
     */
    public static Logger logger() {
        return logger;
    }
}
