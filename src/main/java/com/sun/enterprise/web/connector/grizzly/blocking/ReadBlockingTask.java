/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.web.connector.grizzly.blocking;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;

import com.sun.enterprise.web.connector.grizzly.DefaultProcessorTask;
import com.sun.enterprise.web.connector.grizzly.DefaultReadTask;
import com.sun.enterprise.web.connector.grizzly.Handler;
import com.sun.enterprise.web.connector.grizzly.PipelineStatistic;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.TaskContext;
import com.sun.enterprise.web.connector.grizzly.TaskEvent;
import com.sun.enterprise.web.connector.grizzly.algorithms.NoParsingAlgorithm;
import com.sun.enterprise.web.connector.grizzly.handlers.NoParsingHandler;

/**
 * Process a blocking socket. By default, SSL is using blocking mode.
 *
 * @author Jean-Francois Arcand
 */
public class ReadBlockingTask extends DefaultReadTask{
    
    /**
     * The <code>PipelineStatistic</code> objects used when gathering statistics.
     */
    protected PipelineStatistic pipelineStat;
            
    
    /**
     * If the <code>Task</code> handling an SSL based request.
     */
    protected boolean isSecure = false;
    
    
    /**
     * The <code>Handler</code> used to pre-process the request.
     */
    private Handler handler;
    
            
    public ReadBlockingTask(){
        type = READ_TASK;
        taskContext = new TaskContext();
        taskEvent = new TaskEvent(taskContext);
        taskEvent.setStatus(TaskEvent.START);
    }
    
    
    /**
     * Force this task to always use the same <code>ProcessorTask</code> 
     * instance.
     */
    public void attachProcessor(DefaultProcessorTask processorTask){
        try {
            handler = (Handler) Thread.currentThread().getContextClassLoader().loadClass(
                NoParsingAlgorithm.GF_NO_PARSING_HANDLER).newInstance();
        } catch (Exception e) {
            handler = new NoParsingHandler();
        }
        this.processorTask = processorTask;
        processorTask.setHandler(handler);  
    }
    
    
    /**
     * Dispatch an Http request to a <code>ProcessorTask</code>
     */
    @Override
    public void doTask() throws IOException {
        Socket socket = processorTask.getSocket();
        SelectorBlockingThread blockingSelector = 
                (SelectorBlockingThread)selectorThread;
        blockingSelector.setSocketOptions(socket);

        if (isSecure) {
            try {
                blockingSelector.getServerSocketFactory().handshake(socket);
            } catch (Throwable ex) {
                SelectorThread.getLogger().log(Level.FINE, "selectorThread.sslHandshakeException", ex);
                try {
                    socket.close();
                } catch (IOException ioe){
                    // Do nothing
                }
                taskEvent.setStatus(TaskEvent.COMPLETED);
                taskEvent(taskEvent);
                return;
            }
        }
        processorTask.addTaskListener(this);
        addTaskListener((ProcessorBlockingTask)processorTask);       
        handler.attachChannel(socket.getChannel());

        // Notify the ProcessorTask that we are ready to process the request.
        fireTaskEvent(taskEvent);
    }
    
    
    /**
     * Clear the current state and make this object ready for another request.
     */
    @Override
    public void recycle(){
        clearTaskListeners();
        taskEvent.setStatus(TaskEvent.START);                    
    }  
    
    
    /**
     * Gracefully close the blocking socket.
     */
    @Override
    protected void finishConnection(){
        Socket socket = processorTask.getSocket();

        if ( !isSecure ) {
            try{
                if (!socket.isInputShutdown()){
                    socket.shutdownInput();
                }
            } catch (IOException ioe){
                ;
            }
            try{
                if (!socket.isOutputShutdown()){
                    socket.shutdownOutput();
                }
            } catch (IOException ex){
                ;
            }
        }

        try{
            socket.close();   
        } catch (IOException ex){
            ;
        } finally {
            if (isMonitoringEnabled()) {
                getRequestGroupInfo().decreaseCountOpenConnections();
            }
        }
    } 

    
    /**
     * Receive notification from other <code>Task</code> and recycle this task.
     */
    public void taskEvent(TaskEvent event){
        if ( event.getStatus() == TaskEvent.COMPLETED){
            finishConnection();

            // We must recycle only if we are sure ProcessorTask has completed its
            // processing. If not, 
            if (recycle) {
                processorTask.recycle();
                recycle();
                selectorThread.returnTask(this);
            }
        }
    }


    /**
     * Return the current <code>Socket</code> used by this instance
     * @return socket the current <code>Socket</code> used by this instance
     */
    public Socket getSocket(){
        return processorTask.getSocket();
    }
    
    
    /**
     * Set the <code>PipelineStatistic</code> object used to gather statistic;
     */
    public void setPipelineStatistic(PipelineStatistic pipelineStatistic){
        pipelineStat = pipelineStatistic;
    }
    
    
    /**
     * Return the <code>PipelineStatistic</code> object used
     * to gather statistic;
     */
    public PipelineStatistic getPipelineStatistic(){
        return pipelineStat;
    }
    
    
    /**
     * Set the isSecure attribute.
     */
    public void setSecure(boolean isSecure){
        this.isSecure = isSecure;
    }
    
    
    /**
     * Return the isSecure.
     */
    public boolean getSecure(){
        return isSecure;
    }
}
