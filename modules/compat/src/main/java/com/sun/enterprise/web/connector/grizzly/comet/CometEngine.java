/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * Main class allowing Comet support on top of Grizzly Asynchronous
 * Request Processing mechanism. This class is the entry point to any
 * component interested to execute Comet request style. Components can be
 * Servlets, JSP, JSF or pure Java class. A component interested to support
 * Comet request must do:
 *
 * (1) First, register the cometContext path on which Comet support will be applied:
 *     <code>CometEngine cometEngine = CometEngine.getEngine()</code>
 *     <code>CometContext cometContext = cometEngine.register(contextPath)</code>
 * (2) Second, add an instance of {@link CometHandler} to the
 *     {@link CometContext} returned by the register method:
 *     <code>cometContext.addCometHandler(handler);</code>
 * (3) Finally, you can invokeCometHandler other {@link CometHandler} by doing:
 *     <code>cometContext.invokeCometHandler(Object)(handler);</code>
 *
 * You can also select the stage where the request polling happens when
 * registering the cometContext path (see register(String,int);
 *
 *
 * @author Jeanfrancois Arcand
 * @deprecated - Use {@link CometEngine}
 */
public class CometEngine extends com.sun.grizzly.comet.CometEngine {
    
    protected final static CometEngine cometEngine = new CometEngine();

    
    /**
     * {@inheritDoc}
     */   
    public static CometEngine getEngine(){
        return cometEngine;
    }
    
    
    /**
     * {@inheritDoc}
     */   
    @Override
    public CometContext register(String contextPath){
        return register(contextPath,AFTER_SERVLET_PROCESSING);
    }
    
    
    /**
     * {@inheritDoc}
     */   
    @Override
    public CometContext register(String topic, int type){
        // Double checked locking used used to prevent the otherwise static/global 
        // locking, cause example code does heavy usage of register calls
        // for existing topics from http get calls etc.
        CometContext cometContext = (CometContext)activeContexts.get(topic);
        if (cometContext == null){
            synchronized(activeContexts){
                cometContext = (CometContext)activeContexts.get(topic);
                if (cometContext == null){
                    cometContext = (CometContext)cometContextCache.poll();
                    if (cometContext != null)
                        cometContext.setTopic(topic);                
                    if (cometContext == null){
                        cometContext = new CometContext(topic, type);
                        NotificationHandler notificationHandler 
                                = new DefaultNotificationHandler();
                        cometContext.setNotificationHandler(notificationHandler);
                        if (notificationHandler != null && (notificationHandler
                                    instanceof DefaultNotificationHandler)){
                            ((DefaultNotificationHandler)notificationHandler)
                                .setThreadPool(threadPool);
                        }
                    }
                    activeContexts.put(topic,cometContext);
                }                
            }
        }
        return cometContext;
    }
    
    
    /**
     * {@inheritDoc}
     */   
    @Override
    public CometContext getCometContext(String contextPath){
        return (CometContext)activeContexts.get(contextPath);
    }
    
    @Override
    protected boolean interrupt(final com.sun.grizzly.comet.CometTask task, 
            final boolean finishExecution) {
        return super.interrupt(task, finishExecution);
    }
    /**
     *
     * @param task
     * @param aptflush
     * @param cancelkey
     */
    @Override
    protected void flushPostExecute(final com.sun.grizzly.comet.CometTask task, 
            boolean finishExecution) {
        super.flushPostExecute(task, finishExecution);
    }

    /**
     * Interrupt a {@link CometHandler} by invoking {@link CometHandler#onInterrupt}
     */
    protected boolean interrupt(final CometTask task,final boolean finishExecution) {
        if (task != null && task.getCometContext().handlers().remove(task.getCometHandler()) != null){
            final SelectionKey key = task.getSelectionKey();
             // setting attachment non asynced to ensure grizzly dont keep calling us
            key.attach(System.currentTimeMillis());
            if (finishExecution){
                // dont want to do that in non selector thread:
                // canceled key wont get canceled again due to isvalid check
                key.cancel();

                task.setCallInterrupt(true);
                task.interruptFlushAPT = finishExecution;
                task.run();

            }else{
                interrupt0(task, finishExecution);
            }
            return true;
        }
        return false;
    }


    /**
     * interrupt logic in its own method, so it can be executed either async or sync.<br>
     * cometHandler.onInterrupt is performed async due to its functionality is unknown,
     * hence not safe to run in the performance critical selector thread.
     */
    @Override
    protected void interrupt0(com.sun.grizzly.comet.CometTask task,
            boolean finishExecution){
        if (finishExecution){
            try{
                ((CometHandler)task.getCometHandler()).onInterrupt((
                        (CometEvent)((CometContext)task.getCometContext()).eventInterrupt));
            }catch(IOException e) { }
        }
        flushPostExecute(task,finishExecution);
    }
}
