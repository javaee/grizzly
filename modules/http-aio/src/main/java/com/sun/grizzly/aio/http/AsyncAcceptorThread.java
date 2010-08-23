/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.aio.http;

import com.sun.grizzly.Context;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.aio.TCPAIOHandler;
import com.sun.grizzly.aio.AIOController;
import com.sun.grizzly.aio.filter.AIOReadFilter;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.FileCacheFactory;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.StatsThreadPool;
import com.sun.grizzly.rcm.ResourceAllocationFilter;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.LoggerUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
/**
 * SelectorThread implementation based on AIO. Documentation to comes.
 * 
 * @author Jean-Francois Arcand
 */
public class AsyncAcceptorThread extends SelectorThread {


   // -------------------------------------------------------------- Init // 

    /**
     * Init the {@link ExecutorService}s used by the {@link WorkerThread}s.
     */
    @Override
    public void initThreadPool(){
        selectorThreads.put(port,this);
         
        System.setProperty("java.nio.channels.DefaultThreadPool.threadFactory"
                ,com.sun.grizzly.aio.http.WorkerThreadFactory.class.getName());
        logger.info("Swithching Grizzly Http Thread Pool to: " 
                + com.sun.grizzly.aio.http.WorkerThreadFactory.class.getName());      
        
        StatsThreadPool statsThreadPool = new StatsThreadPool(){

            @Override
            public synchronized void execute(Runnable runnable) {
            }

            @Override
            public void stop() {
            }
        };
        statsThreadPool.setMaximumPoolSize(
                StatsThreadPool.DEFAULT_MAX_THREAD_COUNT);

        setThreadPool(statsThreadPool);
    }
    
    /**
     * Initialize the Grizzly Framework classes.
     */
    @Override
    protected void initController(){
        if (controller == null){
            controller = new AIOController();
        }
        
        // Set the logger of the utils package to the same as this class.       
        LoggerUtils.setLogger(logger);
        // Set the controller logger
        AIOController.setLogger(logger);
        TCPAIOHandler aioHandler = new TCPAIOHandler(controller);
        aioHandler.setPort(port);
        ((AIOController)controller).addAIOHandler(aioHandler);
        
        final DefaultProtocolChain protocolChain = new DefaultProtocolChain(){
            @Override
            public void execute(Context ctx) throws Exception {
                if (rcmSupport) {
                    ByteBuffer byteBuffer =
                            (ByteBuffer)ctx.getAttribute
                            (ResourceAllocationFilter.BYTE_BUFFER);
                    // Switch ByteBuffer
                    if (byteBuffer != null){
                        ((WorkerThread)Thread.currentThread())
                            .setByteBuffer(byteBuffer);
                    }

                    if (protocolFilters.size() != 0){
                        int currentPosition = super.executeProtocolFilter(ctx);
                        super.postExecuteProtocolFilter(currentPosition, ctx);
                    }
                } else {
                    super.execute(ctx);
                }
            }              
        };        
        configureFilters(protocolChain);
         
        DefaultProtocolChainInstanceHandler instanceHandler 
                = new DefaultProtocolChainInstanceHandler(){        

            /**
             * Always return instance of ProtocolChain.
             */
            @Override
            public ProtocolChain poll(){
                return protocolChain;
            }

            /**
             * Pool an instance of ProtocolChain.
             */
            @Override
            public boolean offer(ProtocolChain instance){
                return true;
            }
        };

        controller.setProtocolChainInstanceHandler(instanceHandler);
    }
    
    
    /**
     * Create {@link AIOProcessorTask} objects and configure it to be ready
     * to proceed request.
     */
    @Override
    protected ProcessorTask newProcessorTask(boolean initialize){                                                      
        ProcessorTask task =
                new AIOProcessorTask(initialize, bufferResponse);
        return configureProcessorTask(task);       
    }


    /**
     * Initialize the {@link FileCacheFactory} associated with this instance
     */
    @Override
    protected void initFileCacheFactory(){
        if (fileCacheFactory != null) return;

        fileCacheFactory = FileCacheFactory.getFactory(port, AIOFileCache.class);
        FileCacheFactory.setIsEnabled(isFileCacheEnabled);
        fileCacheFactory.setLargeFileCacheEnabled(isLargeFileCacheEnabled);
        fileCacheFactory.setSecondsMaxAge(secondsMaxAge);
        fileCacheFactory.setMaxCacheEntries(maxCacheEntries);
        fileCacheFactory.setMinEntrySize(minEntrySize);
        fileCacheFactory.setMaxEntrySize(maxEntrySize);
        fileCacheFactory.setMaxLargeCacheSize(maxLargeFileCacheSize);
        fileCacheFactory.setMaxSmallCacheSize(maxSmallFileCacheSize);
        fileCacheFactory.setIsMonitoringEnabled(isMonitoringEnabled);
        fileCacheFactory.setHeaderBBSize(requestBufferSize);
    }

    
    /**
     * Adds and configures {@link ProtocolChain}'s filters
     * @param {@link ProtocolChain} to configure
     */
    @Override
    protected void configureFilters(ProtocolChain protocolChain) {
        if (portUnificationFilter != null) {
            protocolChain.addFilter(portUnificationFilter);
        } else {
            AIOReadFilter readFilter = new AIOReadFilter();
            protocolChain.addFilter(readFilter);
        }
        protocolChain.addFilter(createHttpParserFilter());
    }   

    
    /**
     * Create HTTP parser {@link ProtocolFilter}
     * @return HTTP parser {@link ProtocolFilter}
     */
    @Override
    protected ProtocolFilter createHttpParserFilter() {
        return new AIOHttpParser(algorithmClass, port);
    }
    
    
    
}
