/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.aio;

import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListener;
import com.sun.grizzly.DefaultConnectorHandlerPool;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.util.ConcurrentLinkedQueuePool;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.State;
import com.sun.grizzly.util.StateHolder;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link Controller} that support NIO.2 (Asychronous I/O). 
 * 
 * <p><pre><code>
 *     private Controller createController(int port) {
        final ProtocolFilter aioReadFilter = new AIOReadFilter();
        final ProtocolFilter echoFilter = new AIOEchoFilter();

        final AIOController aioConroller = new AIOController();

        TCPAIOHandler selectorHandler = new TCPAIOHandler(aioConroller);
        selectorHandler.setPort(port);

        aioConroller.addAIOHandler(selectorHandler);

        aioConroller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler() {

                    @Override
                    public ProtocolChain poll() {
                        ProtocolChain protocolChain = protocolChains.poll();
                        if (protocolChain == null) {
                            protocolChain = new DefaultProtocolChain();
                            protocolChain.addFilter(aioReadFilter);
                            protocolChain.addFilter(echoFilter);
                        }
                        return protocolChain;
                    }
                });

        return aioConroller;
    }
 * </code></pre></p>
 * @author Jeanfrancois Arcand
 */
public class AIOController extends Controller {
    
    /**
     * A cached list of Context. Context are by default stateless.
     */
    private ConcurrentLinkedQueuePool<AIOContext> contexts;
        
        
    /**
     * The set of <code>AIOHandler</code>s used by this instance. 
     * If not set, the instance of the {@link TCPAIOHandler} will be added by default.
     */
    protected Queue<AIOHandler> aioHandlers;
    
    
    /**
     * Controller constructor
     */
    public AIOController() {
        contexts = new ConcurrentLinkedQueuePool<AIOContext>() {
            @Override
            public AIOContext newInstance() {
                return new AIOContext();
            }
        };
        
        stateHolder = new StateHolder<State>(true);
        initializeDefaults();
    }
    
    
    /**
     * This method initializes this Controller's default thread pool,
     * default ProtocolChainInstanceHandler, default AHandler(s)
     * and default ConnectorHandlerPool.  These defaults can be overridden
     * after this Controller constructor is called and before calling 
     * Controller.start() using this Controller's mutator methods to
     * set a different thread pool, ProtocolChainInstanceHandler,
     * {@link AIOHandler}(s) or ConnectorHandlerPool.
     */
    private void initializeDefaults() {
        if (threadPool == null) {
            threadPool = new DefaultThreadPool();
        }
        if (instanceHandler == null) {
            instanceHandler = new DefaultProtocolChainInstanceHandler();
        }
        if (aioHandlers == null){
            aioHandlers =  DataStructures.getCLQinstance(AIOHandler.class);
        }
        if (connectorHandlerPool == null) {
            connectorHandlerPool = new DefaultConnectorHandlerPool(this);
        }
    }

    
    /**
     * Register a SelectionKey.
     * @param key <tt>SelectionKey</tt> to register
     * @deprecated - no needed with AIO
     */
    @Override    
    public void registerKey(SelectionKey key){
    }
    
    
    /**
     * @deprecated - no needed with AIO
     */
    @Override
    public void registerKey(SelectionKey key, int ops){
    }
    
    
    /**
     * @deprecated - no needed with AIO
     */
    @Override
    public void registerKey(SelectionKey key, int ops, Protocol protocol){
    }
    
    
    /**
     * @deprecated - no needed with AIO
     */
    @Override
    public void cancelKey(SelectionKey key){
    }

    
    /**
     * Get an instance of a {@link AIOContext}
     * @param key {@link SelectionKey}. Always null.
     * @return {@link AIOContext}
     */   
    @Override
    public AIOContext pollContext(){
        AIOContext ctx = contexts.poll();
        ctx.setController(this);
        return ctx;
    }
    
    /**
     * Return a Context to the pool
     * @param ctx - the {@link AIOContext}
     */
    public void returnContext(AIOContext ctx){
        ctx.recycle();
        contexts.offer(ctx);
    }

    
     /**
     * Add a {@link AIOHandler}
     * @param selectorHandler - the {@link AIOHandler}
     */
    public void addAIOHandler(AIOHandler ioHandler){
        aioHandlers.add(ioHandler);
        if (stateHolder.getState(false) != null && 
                !State.STOPPED.equals(stateHolder.getState())) {
            
            if (readySelectorHandlerCounter != null) {
                readySelectorHandlerCounter.incrementAndGet();
            }
            if (stoppedSelectorHandlerCounter != null) {
                stoppedSelectorHandlerCounter.incrementAndGet();
            }
            startIOHandlerRunner(ioHandler, true);
        }
    }
    
    /**
     * Return an {@link AIOHandler} for the specified {@link Protocol}
     * @param protocol
     * @return an {@link AIOHandler} for the specified {@link Protocol}
     */
    public AIOHandler getAIOHandler(Protocol protocol){
        for (AIOHandler ioHandler: aioHandlers){
            if (ioHandler.protocol() == protocol){
                return ioHandler;
            }
        }
        return null;
    }
    
    
    /**
     * Return all {@link AIOHandler}
     * @return all {@link AIOHandler}
     */
    public Queue getAIOHandlers(){
        return aioHandlers;
    }
    
    /**
     * Remove a {@link AIOHandler}
     */    
    public void removeAIOHandler(AIOHandler aioHandler) {
        if (aioHandlers.remove(aioHandler)){
            aioHandler.shutdown();
        }
    }
    
    
    /**
     * Execute this {@link Controller}.
     */
    @Override
    public void run() {
        try{
            start();
        } catch(IOException e){
            notifyException(e);
            throw new RuntimeException(e.getCause());
        }
    }
    
    /**
     * Start this {@link Controller}. If the thread pool and/or Handler has not
     * been defined, the default will be used.
     */
    @Override
    public void start() throws IOException {

        // if aioHandlers were not set by user explicitly,
        // add TCPSelectorHandler by default
        if (aioHandlers.isEmpty()) {
            TCPAIOHandler aioHandler = new TCPAIOHandler(this);
            aioHandlers.offer(aioHandler);
        }
        
        stateHolder.setState(State.STARTED);
        notifyStarted();

        int selectorHandlerCount = aioHandlers.size();
        readySelectorHandlerCounter = new AtomicInteger(selectorHandlerCount);
        stoppedSelectorHandlerCounter = new AtomicInteger(selectorHandlerCount);
        
        for (AIOHandler ioHandler : aioHandlers) {
            startIOHandlerRunner(ioHandler, selectorHandlerCount > 1);
        }
        waitUntilSelectorHandlersStop();
        aioHandlers.clear();
        if (threadPool != null){
            threadPool.shutdown();
        }
        attributes = null;

        // Notify Controller listeners
        for (ControllerStateListener stateListener : stateListeners) {
            stateListener.onStopped();
        }
    }
    

    /**
     * Starts {@link AIOHandlerRunner}
     * @param aoHandler
     * @param isRunAsync 
     */
    private void startIOHandlerRunner(AIOHandler ioHandler, 
            boolean isRunAsync) {
           
        notifyReady();
        
        final AIOHandlerRunner ioHandlerRunner = new AIOHandlerRunner(this); 
        ioHandlerRunner.setIOHandler(ioHandler);
        if (ioHandler.getThreadPool() == null){
            ioHandler.setThreadPool(threadPool);
        }
        ioHandlerRunner.run();
    }
    
}
