/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.enterprise.web.connector.grizzly.comet;

import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.Pipeline;
import com.sun.enterprise.web.connector.grizzly.TaskBase;
import com.sun.enterprise.web.connector.grizzly.comet.concurrent.DefaultConcurrentCometHandler;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default NotificationHandler that uses the same a Grizzly Pipeline
 * to execute the notification process.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultNotificationHandler implements NotificationHandler {

    private final static Logger logger = SelectorThread.logger();
    /**
     * The {@link Pipeline} used to execute threaded notification.
     */
    protected Pipeline pipeline;
    /**
     * <tt>true</tt> if the caller of CometContext.notify should block when 
     * notifying other CometHandler.
     */
    protected boolean blockingNotification = false;

    /**
     * Set the {@link Pipeline} used for notifying the CometHandler.
     */
    protected void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Return <tt>true</tt> if the invoker of notify() should block when
     * notifying Comet Handlers.
     */
    public boolean isBlockingNotification() {
        return blockingNotification;
    }

    /**
     * Set to <tt>true</tt> if the invoker of notify() should block when
     * notifying Comet Handlers.
     */
    public void setBlockingNotification(boolean blockingNotification) {
        this.blockingNotification = blockingNotification;
    }

    /**
     * Notify all {@link CometHandler}. 
     * @param cometEvent the CometEvent used to notify CometHandler
     * @param iteratorHandlers An iterator over a list of CometHandler
     */
    @SuppressWarnings("unchecked")
    public void notify(final CometEvent cometEvent, final Iterator<CometHandler> iteratorHandlers) throws IOException {
        if (blockingNotification || pipeline == null) {
            notify0(cometEvent, iteratorHandlers);
        } else {
            pipeline.addTask(new TaskBase() {

                public void doTask() throws IOException {
                    notify0(cometEvent, iteratorHandlers);
                }
            });
        }
    }

    protected void notify0(CometEvent cometEvent, Iterator<CometHandler> iteratorHandlers)
            throws IOException {
        CometHandler cometHandler;
        while (iteratorHandlers.hasNext()) {
            cometHandler = iteratorHandlers.next();
            try {
                notify0(cometEvent, cometHandler);
            } catch (Throwable ex) {
                try {
                    cometEvent.getCometContext().resumeCometHandler(cometHandler, true);
                } catch (Throwable t) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINEST, "Resume phase failed: ", t);
                    }
                }
                logger.log(Level.WARNING, "Notification failed: ", ex);
            }
        }
    }

    /**
     * Notify a single {@link CometHandler}. 
     * @param cometEvent the CometEvent used to notify CometHandler
     * @param cometHandler a CometHandler
     */
    @SuppressWarnings("unchecked")
    public void notify(final CometEvent cometEvent, final CometHandler cometHandler)
            throws IOException {
        if (blockingNotification || pipeline == null) {
            notify0(cometEvent, cometHandler);
        } else {
            pipeline.addTask(new TaskBase() {

                public void doTask() throws IOException {
                    try {
                        notify0(cometEvent, cometHandler);
                    } catch (Throwable ex) {
                        try {
                            cometEvent.getCometContext().resumeCometHandler(cometHandler, true);
                        } catch (Throwable t) {
                            logger.log(Level.FINEST, "Resume phase failed: ", ex);
                        }
                        logger.log(Level.WARNING, "Notification failed: ", ex);
                    }
                }
            });
        }
    }

    /**
     * Notify a {@link CometHandler}.
     * 
     * CometEvent.INTERRUPT -> <code>CometHandler.onInterrupt</code>
     * CometEvent.NOTIFY -> <code>CometHandler.onEvent</code>
     * CometEvent.INITIALIZE -> <code>CometHandler.onInitialize</code>
     * CometEvent.TERMINATE -> <code>CometHandler.onTerminate</code>
     * CometEvent.READ -> <code>CometHandler.onEvent</code>
     * CometEvent.WRITE -> <code>CometHandler.onEvent</code>
     *
     * @param attachment An object shared amongst {@link CometHandler}. 
     * @param cometHandler The CometHandler to invoke. 
     */
    protected void notify0(CometEvent cometEvent, CometHandler cometHandler)
            throws IOException {
        try {

            switch (cometEvent.getType()) {
                case CometEvent.INTERRUPT:
                    cometHandler.onInterrupt(cometEvent);
                    break;
                case CometEvent.NOTIFY:
                case CometEvent.READ:
                case CometEvent.WRITE:
                    if (cometHandler instanceof DefaultConcurrentCometHandler) {
                        ((DefaultConcurrentCometHandler) cometHandler).enqueueEvent(cometEvent);
                        break;
                    }
                    if (cometEvent.getCometContext().isActive(cometHandler)) {
                        synchronized (cometHandler) {
                            cometHandler.onEvent(cometEvent);
                        }
                    }
                    break;

                case CometEvent.INITIALIZE:
                    cometHandler.onInitialize(cometEvent);
                    break;
                case CometEvent.TERMINATE:
                    synchronized (cometHandler) {
                        cometHandler.onTerminate(cometEvent);
                    }
                    break;
                default:
                    throw new IllegalStateException();
            }
        } catch (Throwable ex) {
            try {
                cometEvent.getCometContext().resumeCometHandler(cometHandler);
            } catch (Throwable t) {
                logger.log(Level.FINE, "Resume phase failed:", t);
            }
            logger.log(Level.FINE, "Notification failed:", ex);
        }
    }
}
