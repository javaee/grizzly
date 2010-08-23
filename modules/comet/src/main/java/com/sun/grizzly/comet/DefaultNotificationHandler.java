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

package com.sun.grizzly.comet;

import com.sun.grizzly.Controller;
import com.sun.grizzly.comet.concurrent.DefaultConcurrentCometHandler;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default Notificationhandler that uses a thread pool dedicated to the CometEngine
 * to execute the notification process.<br>
 *
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
public class DefaultNotificationHandler implements NotificationHandler{
    
    private final static Logger logger = Controller.logger();

    private static final IllegalStateException ISEempty = new IllegalStateException();
    
    /**
     * The {@link ExecutorService} used to execute threaded notification.
     */
    protected ExecutorService threadPool;

    
    /**
     * <tt>true</tt> if the caller of CometContext.notify should block when 
     * notifying other CometHandler.
     *  false is default
     */
    protected boolean blockingNotification;

    /**
     * only used if blockingnotification == false and threadpool != null
     */
    private boolean spreadNotifyToManyToThreads = false;
    
    public DefaultNotificationHandler() {
    }

    /**
     * Set the {@link ExecutorService} used for notifying the CometHandler.
     */
    protected void setThreadPool(ExecutorService threadPool){
        this.threadPool = threadPool;
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
     * if true a notify to Iterator<CometHandler> will be spread into one runnable task for
     * each comethandler.
     * if false , all comethandlers notify  will be executed in 1 Runnable, after each other,
     *
     * @param spreadNotifyToManyToThreads
     */
    public void setSpreadNotifyToManyToThreads(boolean spreadNotifyToManyToThreads) {
        this.spreadNotifyToManyToThreads = spreadNotifyToManyToThreads;
    }




    /**
     * Notify all {@link CometHandler}. 
     * @param cometEvent the CometEvent used to notify CometHandler
     * @param iteratorHandlers An iterator over a list of CometHandler
     */
    public void notify(final CometEvent cometEvent,final Iterator<CometHandler> iteratorHandlers)
        throws IOException {
        if (!spreadNotifyToManyToThreads && !blockingNotification && threadPool != null ){
            threadPool.execute(new Runnable() {
                public void run() {
                    while(iteratorHandlers.hasNext()){
                        notify0(cometEvent, iteratorHandlers.next());
                    }
                }});
        }else {
            while(iteratorHandlers.hasNext()){
                notify(cometEvent,iteratorHandlers.next());
            }
        }
    }

    /**
     * Notify the {@link CometHandler}.
     * @param cometEvent cometEvent the CometEvent used to notify CometHandler
     * @param cometHandler
     */
    public void notify(final CometEvent cometEvent,final CometHandler cometHandler)
        throws IOException{
        if (blockingNotification || threadPool == null ){
            notify0(cometEvent,cometHandler);
        } else {
            threadPool.execute(new Runnable() {
                public void run() {
                    notify0(cometEvent, cometHandler);
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
    protected void notify0(CometEvent cometEvent,CometHandler cometHandler) {
        try{
            switch (cometEvent.getType()) {
                case CometEvent.INTERRUPT: 
                    cometHandler.onInterrupt(cometEvent); break;
                case CometEvent.NOTIFY:
                case CometEvent.READ:
                case CometEvent.WRITE:
                    if (cometHandler instanceof DefaultConcurrentCometHandler){
                        ((DefaultConcurrentCometHandler)cometHandler).enqueueEvent(cometEvent);
                        break;
                    }
                    if (cometEvent.getCometContext().isActive(cometHandler)){
                        synchronized(cometHandler){
                            cometHandler.onEvent(cometEvent);
                        }
                    }
                    break;
                case CometEvent.INITIALIZE:
                    cometHandler.onInitialize(cometEvent); break;
                case CometEvent.TERMINATE:
                    synchronized(cometHandler){
                        cometHandler.onTerminate(cometEvent); break;
                    }
                default:
                    throw ISEempty;
            }
        } catch (Throwable ex) {
            try {
                cometEvent.getCometContext().resumeCometHandler(cometHandler);
            } catch (Throwable t) {
                logger.log(Level.FINE, "Resume phase failed: ", t);
            }
            logger.log(Level.FINE, "Notification failed: ", ex);
        }
    }


}
