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

package com.sun.grizzly;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Default ProtocolChain implementation.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultProtocolChain implements ProtocolChain, ReinvokeAware {
    
    public enum Phase {EXECUTE, POST_EXECUTE};
    
    /**
     * The list of ProtocolFilter this chain will invoke.
     */
    protected final List<ProtocolFilter> protocolFilters;
    
    /**
     * The list of {@link EventHandler}s, which will be notified about this
     * {@link ProtocolChain} events
     */
    protected final List<EventHandler> eventHandlers;
    
    /**
     * <tt>true</tt> if a pipelined execution is required. A pipelined execution
     * occurs when a ProtocolFilter implementation set the 
     * ProtocolFilter.READ_SUCCESS as an attribute to a Context. When this 
     * attribute is present, the ProtocolChain will not release the current
     * running Thread and will re-execute all its ProtocolFilter. 
     */
    protected boolean continousExecution = false;
    
    
    public DefaultProtocolChain() {
        protocolFilters = new ArrayList<ProtocolFilter>(4);
        //ArrayList is faster then HashSet for small datasets.
        eventHandlers = new ArrayList<EventHandler>(4);
    }
    
    
    /**
     * Execute this ProtocolChain.
     * @param ctx {@link Context}
     * @throws java.lang.Exception 
     */
    public void execute(Context ctx) throws Exception {
        execute(ctx,0);
    }
    
    
    /**
     * Execute this ProtocolChain.
     * @param ctx {@link Context}
     * @param firstFilter The first filter to be invoked.
     * @throws java.lang.Exception 
     */
    public void execute(Context ctx, int firstFilter) throws Exception {
        if (!protocolFilters.isEmpty()) {
            boolean reinvokeChain = true;
            while (reinvokeChain){
                int currentPosition = executeProtocolFilter(ctx,firstFilter);
                reinvokeChain = postExecuteProtocolFilter(currentPosition, ctx);
            }
        }
    }    
    
    /**
     * Execute the ProtocolFilter.execute method. If a ProtocolFilter.execute
     * return false, avoid invoking the next ProtocolFilter.
     * @param ctx {@link Context}
     * @return position of next {@link ProtocolFilter} to exexute
     */
    protected int executeProtocolFilter(Context ctx) {
        return executeProtocolFilter(ctx,0);
    }
    
    
    /**
     * Execute the ProtocolFilter.execute method. If a ProtocolFilter.execute
     * return false, avoid invoking the next ProtocolFilter.
     * @param ctx {@link Context}
     * @param firstFilter The first filter position to be invoked. 
     * @return position of next {@link ProtocolFilter} to exexute
     */
    protected int executeProtocolFilter(Context ctx, int firstFilter) {
        boolean invokeNext;
        int size = protocolFilters.size();
        int currentPosition = 0;
        ProtocolFilter protocolFilter = null;
        
        for (int i=firstFilter; i < size; i++) {
            try {
                protocolFilter = protocolFilters.get(i);
                invokeNext = protocolFilter.execute(ctx);
            } catch (Exception ex){
                invokeNext = false;
                i--;
                Controller.logger().log(Level.SEVERE,
                        "ProtocolChain exception",ex);
                notifyException(Phase.EXECUTE, protocolFilter, ex);
            }
            
            currentPosition = i;
            if ( !invokeNext ) break;
        }
        return currentPosition;
    }
    
    
    /**
     * Execute the ProtocolFilter.postExcute.
     * @param currentPosition position in list of {@link ProtocolFilter}s
     * @param ctx {@link Context}
     * @return false, always false
     */
    protected boolean postExecuteProtocolFilter(int currentPosition,Context ctx) {
        boolean invokeNext = true;
        ProtocolFilter tmpHandler = null;
        boolean reinvokeChain = false;
        for (int i = currentPosition; i > -1; i--){
            try{
                tmpHandler = protocolFilters.get(i);
                invokeNext = tmpHandler.postExecute(ctx);                 
            } catch (Exception ex){
                Controller.logger().log(Level.SEVERE,
                        "ProtocolChain exception",ex);
                notifyException(Phase.POST_EXECUTE, tmpHandler, ex);
            }
            if ( !invokeNext ) {
               break;
            }
        }
        
        ProtocolChainInstruction postInstruction =
                (ProtocolChainInstruction) ctx.removeAttribute(
                PROTOCOL_CHAIN_POST_INSTRUCTION);
        
        if (postInstruction != null &&
                postInstruction == ProtocolChainInstruction.REINVOKE) {
            reinvokeChain = true;
        } else if (continousExecution
            && (Boolean)ctx.removeAttribute(ProtocolFilter.SUCCESSFUL_READ) 
                == Boolean.TRUE) {
            reinvokeChain = true;    
        } 

        return reinvokeChain;
    }
    
    
    /**
     * Remove a ProtocolFilter.
     * @param theFilter the ProtocolFilter to remove
     * @return removed ProtocolFilter
     */
    public boolean removeFilter(ProtocolFilter theFilter) {
        return protocolFilters.remove(theFilter);
    }
    
    
    /**
     * Add the {@link ProtocolFilter} to this {@link ProtocolChain}
     * @param protocolFilter to add
     * @return 
     */
    public boolean addFilter(ProtocolFilter protocolFilter) {
        return protocolFilters.add(protocolFilter);
    }
    
    
    /**
     * Insert a ProtocolFilter at position pos.
     * @param pos 
     * @param protocolFilter 
     */
    public void addFilter(int pos, ProtocolFilter protocolFilter){
        protocolFilters.add(pos,protocolFilter);
    }
    
    
    /**
     *Insert a ProtocolFilter at position pos.
     * @param pos - position in this ProtocolChain
     * @param protocolFilter - {@link ProtocolFilter} to insert
     * @return {@link ProtocolFilter} that was set
     */
    public ProtocolFilter setProtocolFilter(int pos,
            ProtocolFilter protocolFilter) {
        return protocolFilters.set(pos,protocolFilter);
    }
    
    
    /**
     * Set to <tt>true</tt> if the current {@link ExecutorService} can
     * re-execute its ProtocolFilter(s) after a successful execution. Enabling
     * this property is useful for protocol that needs to support pipelined
     * message requests as the ProtocolFilter are automatically re-executed, 
     * avoiding the overhead of releasing the current Thread, registering 
     * back the SelectionKey to the SelectorHandler and waiting for a new
     * NIO event. 
     * 
     * Some protocols (like http) can get the http headers in one
     * SocketChannel.read, parse the message and then get the next http message 
     * on the second SocketChannel.read(). Not having to release the Thread
     * and re-execute the ProtocolFilter greatly improve performance.
     * @param continousExecution true to enable continuous execution.
     *        (default is false).
     */
    public void setContinuousExecution(boolean continousExecution){
        this.continousExecution = continousExecution;
        for (ProtocolFilter filter : protocolFilters){
            if (filter instanceof ReinvokeAware){
                ((ReinvokeAware) filter).setContinuousExecution(continousExecution);
                break;
            }
        }
    }
    
    
    /**
     * Return <tt>true</tt> if the current {@link ExecutorService} can
     * re-execute its ProtocolFilter after a successful execution. 
     */    
    public boolean isContinuousExecution(){
        return continousExecution;
    }
    
    
    /**
     * Add the {@link EventHandler}
     * @param eventHandler 
     * @return true, if {@link EventHandler} was added, false otherwise
     */
    public boolean addEventHandler(EventHandler eventHandler) {
        return eventHandlers.add(eventHandler);
    }

    
    /**
     * Remove the {@link EventHandler}.
     * @param eventHandler the <code>ProtocolFilter<code> to remove
     * @return true, if {@link EventHandler} was removed, false otherwise
     */
    public boolean removeEventHandler(EventHandler eventHandler) {
        return eventHandlers.remove(eventHandler);
    }
    
    /**
     * Notifies all {@link EventHandler}s about exception, which occured
     * @param phase execution <code>Phase</code>, where exception occured
     * @param filter {@link ProtocolFilter}, where exception occured
     * @param throwable actual exception
     */
    protected void notifyException(Phase phase, ProtocolFilter filter, 
            Throwable throwable) {
        for(int i=0;i<eventHandlers.size();i++) {
            try {
                eventHandlers.get(i).onException(phase, filter, throwable);
            } catch(Exception e) {
                Controller.logger().log(Level.SEVERE,
                        "ProtocolChain notifyException exception", e);

            }
        }
    }
    
    /**
     * Interface, which introduces handler, which will be notified about event,
     * happened on {@link ProtocolChain}
     */
    public interface EventHandler {
        public void onException(Phase phase, ProtocolFilter filter, 
                Throwable throwable);
    }
}
