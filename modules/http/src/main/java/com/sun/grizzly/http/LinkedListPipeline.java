/*
 * The contents of this file are subject to the terms 
 * of the Common Development and Distribution License 
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at 
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing 
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL 
 * Header Notice in each file and include the License file 
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.http;

import com.sun.grizzly.DefaultPipeline;
import com.sun.grizzly.util.WorkerThreadImpl;

/**
 * Internal FIFO used by the Worker Threads to pass information
 * between <code>Task</code> objects.
 *
 * @author Jean-Francois Arcand
 */
public class LinkedListPipeline extends DefaultPipeline{
    
    /**
     * The <code>PipelineStatistic</code> objects used when gathering statistics.
     */
    protected transient PipelineStatistic pipelineStat;


    /**
     * Create new <code>WorkerThreadImpl</code>. This method must be invoked
     * from a synchronized block.
     * @param increment - how many additional <code>WorkerThreadImpl</code> 
     * objects to add
     * @param startThread - should newly added <code>WorkerThreadImpl</code> 
     * objects be started after creation?
     */
    protected void increaseWorkerThread(int increment, boolean startThread){        
        HttpWorkerThread workerThread;
        int currentCount = threadCount;
        int increaseCount = threadCount + increment; 
        for (int i=currentCount; i < increaseCount; i++){
            workerThread = new HttpWorkerThread(this, 
                    name + "HttpWorkerThread-"  + port + "-" + i);
            workerThread.setPriority(priority);
            
            if (startThread)
                workerThread.start();
            
            workerThreads[i] = workerThread;
            threadCount++; 
        }
    }
    
    
    /**
     * Set the <code>PipelineStatistic</code> object used
     * to gather statistic;
     */
    public void setPipelineStatistic(PipelineStatistic pipelineStatistic){
        this.pipelineStat = pipelineStatistic;
    }
    
    
    /**
     * Return the <code>PipelineStatistic</code> object used
     * to gather statistic;
     */
    public PipelineStatistic getPipelineStatistic(){
        return pipelineStat;
    }
    
}
