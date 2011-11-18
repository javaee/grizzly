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
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main object used by {@link CometHandler}. 
 * The {@link CometContext} is always available for {@link CometHandler}
 * and can be used to invokeCometHandler other {@link CometHandler}.
 *
 * Attributes can be added/removed the same way <code>HttpServletSession</code> 
 * is doing. It is not recommended to use attributes if this 
 * {@link CometContext} is not shared amongs multiple
 * context path (uses HttpServletSession instead).
 *
 * @author Jeanfrancois Arcand
 * @deprecated use {@link CometContext}
 */
public class CometContext<E> extends com.sun.grizzly.comet.CometContext<E>{
   
    protected final CometEvent eventInitialize;
   
    protected final CometEvent eventInterrupt;

    protected final CometEvent eventTerminate;
    
    /**
     * {@inheritDoc}
     */ 
    public CometContext(String contextPath, int continuationType) {
        super(contextPath, continuationType);
        this.eventInterrupt   = new CometEvent(CometEvent.INTERRUPT,this);
        this.eventInitialize  = new CometEvent(CometEvent.INITIALIZE,this);
        this.eventTerminate   = new CometEvent(CometEvent.TERMINATE,this,this);
    }
    
    
    protected void setTopic(String topic){
        this.topic = topic;
    }
    
    /**
     * {@inheritDoc}
     */     
    @Override
    public CometHandler getCometHandler(int hashCode){
        return (CometHandler) super.getCometHandler(hashCode);
    }      
    

    /**
     * Resume the Comet request and remove it from the active {@link CometHandler} list. Once resumed,
     * a CometHandler must never manipulate the <code>HttpServletRequest</code> or <code>HttpServletResponse</code> as
     * those object will be recycled and may be re-used to serve another request.
     *
     * If you cache them for later reuse by another thread there is a
     * possibility to introduce corrupted responses next time a request is made.
     * @param handler The CometHandler to resume.
     * @return <tt>true</tt> if the operation succeeded.
     */
    @Override
    public boolean resumeCometHandler(com.sun.grizzly.comet.CometHandler handler){
        boolean status = CometEngine.getEngine().interrupt(handlers.get(handler),false);
        if (status){
            try {
                handler.onTerminate(eventTerminate);
            } catch (IOException ignored) { }
        }
        return status;
    }
    
    /**
     * {@inheritDoc}
     */ 
    @Override
    public void notify(final Object attachment) throws IOException {
        CometEvent event = new CometEvent(CometEvent.NOTIFY,this);
        event.attach(attachment);
        Iterator<com.sun.grizzly.comet.CometHandler> iterator = handlers.keySet().iterator();
        notificationHandler.setBlockingNotification(blockingNotification);
        notificationHandler.notify((com.sun.grizzly.comet.CometEvent)event,iterator);
        resetSuspendIdleTimeout();
    }

    /**
     * {@inheritDoc}
     */  
    @Override
    public void notify(final Object attachment,final int eventType,final int cometHandlerID)
            throws IOException{   
        CometHandler cometHandler = getCometHandler(cometHandlerID);
  
        if (cometHandler == null){
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        CometEvent event = new CometEvent(eventType,this);
        event.attach(attachment);
        
        notificationHandler.setBlockingNotification(blockingNotification);        
        notificationHandler.notify(event,cometHandler);
        if (event.getType() != CometEvent.TERMINATE
            && event.getType() != CometEvent.INTERRUPT) {
            resetSuspendIdleTimeout(); 
        }
    }
    
    /**
     * Return the internal list of active {@link CometHandler}
     * @return Return the internal list of active {@link CometHandler}
     */
    @Override
    protected ConcurrentHashMap<com.sun.grizzly.comet.CometHandler,com.sun.grizzly.comet.CometTask> handlers(){
        return handlers;
    }
    
    /**
     * {@inheritDoc}
     */     
    @Override
    protected void initialize(com.sun.grizzly.comet.CometHandler handler) throws IOException {
        ((com.sun.enterprise.web.connector.grizzly.comet.CometHandler)handler).onInitialize(eventInitialize); 
    }
    
}

