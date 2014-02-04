/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2014 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.web.connector.grizzly.async;

import com.sun.enterprise.web.connector.grizzly.AsyncExecutor;
import com.sun.enterprise.web.connector.grizzly.AsyncTask;
import com.sun.enterprise.web.connector.grizzly.DefaultProcessorTask;
import com.sun.enterprise.web.connector.grizzly.ProcessorTask;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.TaskBase;
import com.sun.enterprise.web.connector.grizzly.TaskEvent;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A <code>Task</code> that wraps the execution of an asynchronous execution
 * of a <code>ProcessorTask</code>. Internaly, this class invoke the associated
 * <code>AsyncExecutor</code> method to execute the <code>ProcessorTask</code>
 * lifecycle operations.
 *
 * @author Jeanfrancois Arcand
 */
public class AsyncProcessorTask extends TaskBase implements AsyncTask {
    private static final Logger LOGGER =
            Logger.getLogger(AsyncProcessorTask.class.getName());
    private static final Level LOG_LEVEL = Level.FINEST;

    /**
     * The <code>AsyncExecutor</code> which drive the execution of the 
     * <code>ProcesssorTask</code>
     */
    private AsyncExecutor asyncExecutor;

    
    /**
     * The current execution stage.
     */
    private int stage = AsyncTask.PRE_EXECUTE;
    
    /**
     * Execute the <code>AsyncExecutor</code> based on the <code>stage</code>
     * of the <code>ProcessorTask</code> execution.
     */
    public void doTask() throws IOException {
        boolean continueExecution = true;
        while (continueExecution) {
            try {
                if (LOGGER.isLoggable(LOG_LEVEL)) {
                    LOGGER.log(LOG_LEVEL, "doTask apt={0} stage={1}", new Object[]{this, stage});
                }
                
                switch (stage) {
                    case AsyncTask.PRE_EXECUTE:
                        continueExecution = asyncExecutor.preExecute();
                        if (!continueExecution) {
                            asyncExecutor.getAsyncHandler().returnTask(this);
                            return;
                        } else {
                            stage = AsyncTask.INTERRUPTED;
                        }
                        break;              
                    case AsyncTask.INTERRUPTED:
                        stage = AsyncTask.POST_EXECUTE;
                        continueExecution = asyncExecutor.interrupt();
                        break;
                    case AsyncTask.EXECUTE:
                        stage = AsyncTask.POST_EXECUTE;
                        continueExecution = asyncExecutor.execute();
                        break;
                    case AsyncTask.POST_EXECUTE:    
                        continueExecution = asyncExecutor.postExecute();
                        if (continueExecution) {
                            final ProcessorTask processorTask =
                                    asyncExecutor.getProcessorTask();
                    
                            if (processorTask.hasNextRequest()
                                    && isKeepAlive(processorTask)) {
                                
                                if (LOGGER.isLoggable(LOG_LEVEL)) {
                                    final DefaultProcessorTask dpt = (DefaultProcessorTask) processorTask;
                                    LOGGER.log(LOG_LEVEL, "doTask next request is ready. Available content: {0}", dpt.inputBuffer.toStringAvailable());
                                }

                                asyncExecutor.reset();
                                asyncExecutor.getProcessorTask().prepareForNextRequest();

                                stage = AsyncTask.PRE_EXECUTE;
                            } else {
                                stage = AsyncTask.FINISH;
                            }
                        }
                       break;                
                    case AsyncTask.FINISH:
                        asyncExecutor.finishExecute();
                        asyncExecutor.getAsyncHandler().returnTask(this);
                        return;
                }
            } catch (Throwable t) {
                SelectorThread.logger().log(Level.SEVERE, t.getMessage(), t);
                if (stage <= AsyncTask.INTERRUPTED) {
                    // We must close the connection.
                    stage = AsyncTask.POST_EXECUTE;
                } else {
                    stage = AsyncTask.PRE_EXECUTE;
                    throw new RuntimeException(t);
                }
            }
        }
    }

    
    /**
     * Not used.
     */
    @Override
    public void taskEvent(TaskEvent event) {
    }
    
    /**
     * Return the <code>stage</code> of the current execution.
     */
    public int getStage() {
        return stage;
    }
    
    
    /**
     * Reset the object.
     */
    @Override
    public void recycle() {
        stage = AsyncTask.PRE_EXECUTE;
        asyncExecutor.reset();
    }


    /**
     * Set the <code>AsyncExecutor</code> used by this <code>Task</code>
     * to delegate the execution of a <code>ProcessorTask</code>.
     */
    public void setAsyncExecutor(AsyncExecutor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }
    
    
    /**
     * Get the <code>AsyncExecutor</code>.
     */
    public AsyncExecutor getAsyncExecutor() {
        return asyncExecutor;
    }
    
    
    /**
     * Set the <code>ProcessorTask</code> that needs to be executed
     * asynchronously.
     */
    public void setProcessorTask(ProcessorTask processorTask) {
        if (asyncExecutor != null) {
            asyncExecutor.setProcessorTask(processorTask);
        }

        if (pipeline == null && processorTask != null) {
            setPipeline(processorTask.getPipeline());
        }
    }
    
    
    /**
     * The {@link ProcessorTask} used to execute the request processing.
     * @return {@link ProcessorTask} used to execute the request processing.
     * @deprecated - Use {@link AsyncExecutor#getProcessorTask}
     */        
    public ProcessorTask getProcessorTask() {
        return asyncExecutor == null ? null : asyncExecutor.getProcessorTask();
        
    }

    
    /**
     * 
     */
    public void setStage(int stage) {
        this.stage = stage;
    }
    
    private boolean isKeepAlive(final ProcessorTask processorTask) {
        return processorTask.isKeepAlive() && !processorTask.isError()
                && !processorTask.getDropConnection();
    }
}
