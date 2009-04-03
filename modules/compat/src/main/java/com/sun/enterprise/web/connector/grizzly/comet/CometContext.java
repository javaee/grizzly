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

import com.sun.grizzly.util.WorkerThreadImpl;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;

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
     * Resume the Comet request and remove it from the active {@link CometHandler} list. Once resumed,
     * a CometHandler must never manipulate the {@link HttpServletRequest} or {@link HttpServletResponse} as
     * those object will be recycled and may be re-used to serve another request.
     *
     * If you cache them for later reuse by another thread there is a
     * possibility to introduce corrupted responses next time a request is made.
     * @param handler The CometHandler to resume.
     * @return <tt>true</tt> if the operation succeeded.
     */
    public boolean resumeCometHandler(CometHandler handler){
        boolean status = interrupt(handlers.get(handler),false,true,false,false);
        if (status){
            try {
                handler.onTerminate(eventTerminate);
            } catch (IOException ex) { }
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
    public boolean isActive(CometHandler cometHandler){
        return super.isActive(cometHandler);
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
     * {@inheritDoc}
     */     
    @Override
    protected void initialize(com.sun.grizzly.comet.CometHandler handler) throws IOException {
        ((com.sun.enterprise.web.connector.grizzly.comet.CometHandler)handler).onInitialize(eventInitialize); 
    }
    
    /**
     * Interrupt a {@link CometHandler} by invoking {@link CometHandler#onInterrupt}
     */
    protected boolean interrupt(final CometTask task,
            final boolean notifyInterrupt, final boolean flushAPT,
            final boolean cancelkey, boolean asyncExecution) {
        if (task != null && handlers.remove(task.getCometHandler()) != null){
            final SelectionKey key = task.getSelectionKey();
             // setting attachment non asynced to ensure grizzly dont keep calling us
            key.attach(System.currentTimeMillis());
            if (asyncExecution){
                if (cancelkey){
                    // dont want to do that in non selector thread:
                    // canceled key wont get canceled again due to isvalid check
                    key.cancel();
                }
                task.callInterrupt = true;
                task.interruptFlushAPT = flushAPT;
                ((WorkerThreadImpl)Thread.currentThread()).
                        getPendingIOhandler().addPendingIO(task);

            }else{
                interrupt0(task, notifyInterrupt, flushAPT, cancelkey);
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
            boolean notifyInterrupt, boolean flushAPT, boolean cancelkey){
        if (notifyInterrupt){
            try{
                ((CometHandler)task.getCometHandler()).onInterrupt(((CometEvent)eventInterrupt));
            }catch(IOException e) { }
        }
        CometEngine.getEngine().flushPostExecute(task,flushAPT,cancelkey);
    }
}

