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

package com.sun.enterprise.web.connector.grizzly.comet;

import com.sun.grizzly.comet.CometTask;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;

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
   
    private final CometEvent eventInitialize;
   
    private final CometEvent eventInterrupt;
    
    /**
     * {@inheritDoc}
     */ 
    public CometContext(String contextPath, int continuationType) {
        super(contextPath, continuationType);
        this.eventInterrupt   = new CometEvent<E>(CometEvent.INTERRUPT,this);
        this.eventInitialize  = new CometEvent<E>(CometEvent.INITIALIZE,this);
    }
    
    
    protected void setTopic(String topic){
        this.topic = topic;
    }
    
    /**
     * {@inheritDoc}
     */ 
    public int addCometHandler(CometHandler handler, boolean completeExecution){
        return super.addCometHandler(handler, completeExecution);
    }
    
    /**
     * {@inheritDoc}
     */     
    public int addCometHandler(CometHandler handler){
        return super.addCometHandler(handler);
    }
    
    /**
     * {@inheritDoc}
     */     
    @Override
    public CometHandler getCometHandler(int hashCode){
        return (CometHandler) super.getCometHandler(hashCode);
    }      
    
    /**
     * {@inheritDoc}
     */ 
    public void removeCometHandler(CometHandler handler){
        super.removeCometHandler(handler);
    }  

    /**
     * {@inheritDoc}
     */     
    @Override
    public boolean removeCometHandler(int hashCode){
        return super.removeCometHandler(hashCode);
    }
    
    /**
     * {@inheritDoc}
     */ 
    public void resumeCometHandler(CometHandler handler){   
        super.resumeCometHandler(handler);
    } 
    
    /**
     * {@inheritDoc}
     */ 
    @Override
    public void notify(final E attachment) throws IOException {
        CometEvent event = new CometEvent<E>(CometEvent.NOTIFY,this);
        event.attach(attachment);
        Iterator<com.sun.grizzly.comet.CometHandler> iterator = handlers.keySet().iterator();
        notificationHandler.setBlockingNotification(blockingNotification);
        notificationHandler.notify((com.sun.grizzly.comet.CometEvent)event,iterator);
        resetSuspendIdleTimeout();           
    }

    
    /**
     * {@inheritDoc}
     */ 
    public boolean isActive(CometHandler cometHandler){
        return super.isActive(cometHandler);
    }
    
    
    /**
     * {@inheritDoc}
     */  
    @Override
    public void notify(final E attachment,final int eventType,final int cometHandlerID) 
            throws IOException{   
        CometHandler cometHandler = getCometHandler(cometHandlerID);
  
        if (cometHandler == null){
            throw new IllegalStateException(INVALID_COMET_HANDLER);
        }
        CometEvent event = new CometEvent<E>(eventType,this);
        event.attach(attachment);
        
        notificationHandler.setBlockingNotification(blockingNotification);        
        notificationHandler.notify(event,cometHandler);
        if (event.getType() == CometEvent.TERMINATE 
            || event.getType() == CometEvent.INTERRUPT) {
            resumeCometHandler(cometHandler);
        } else {
            resetSuspendIdleTimeout(); 
        }
    }

    /**
     * {@inheritDoc}
     */     
    @Override
    protected void initialize(com.sun.grizzly.comet.CometHandler handler) throws IOException {
        ((com.sun.enterprise.web.connector.grizzly.comet.CometHandler)handler).onInitialize(eventInitialize); 
    }
    
    /**
     * Interrupt a {@link CometHandler} by invoking {@link CometHandler#onInterrupt}
     */
    @Override
    protected boolean interrupt(CometTask task,boolean removecomethandler, boolean resume) {
        boolean status = true;
        try{
            if (removecomethandler){
                status = (handlers.remove(task.getCometHandler()) != null);
                if (status && resume){
                    ((com.sun.enterprise.web.connector.grizzly.comet.CometHandler)
                            task.getCometHandler()).onInterrupt(eventInterrupt);
                }else{
                    logger.finer(ALREADY_REMOVED);
                }
            }
        } catch (Throwable ex){
            status = false;
            logger.log(Level.FINE,"Unable to interrupt",ex);            
        }finally{
            activeTasks.remove(task);
            return status;
        }
    }
}
    
