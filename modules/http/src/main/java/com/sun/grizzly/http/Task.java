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

import com.sun.grizzly.Pipeline;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import com.sun.grizzly.tcp.RequestGroupInfo;

/**
 * Wrapper object used by the WorkerThread
 *
 * @author Jean-Francois Arcand
 */
public interface Task extends Runnable, Callable{
      
    // Simple flag to avoid calling instanceof
    public static int ACCEPT_TASK = 0;
    public static int READ_TASK = 1;
    public static int PROCESSOR_TASK = 2;

    /**
     * Return this <code>Tash</code> type.
     */
    public int getType();
    
    /**
     * Execute the task.
     */
    public void doTask() throws IOException;


    /**
     * Cancel the task.
     */
    public void cancelTask(String message, String httpCode);


    /**
     * Set the <code>SelectionKey</code>
     */
    public void setSelectionKey(SelectionKey key);
    
    
    /**
     * Return the <code>SelectionKey</code> associated with this tasks.
     */
    public SelectionKey getSelectionKey();

    
    /**
     * Set the <code>SelectorThread</code> used by this task.
     */
    public void setSelectorThread(SelectorThread selectorThread);
    
    
    /**
     * Returns the <code>SelectorThread</code> used by this task.
     */
    public SelectorThread getSelectorThread();


    /**
     * Gets the <code>RequestGroupInfo</code> from this task.
     */    
    public RequestGroupInfo getRequestGroupInfo();


    /**
     * Returns <code>true</code> if monitoring has been enabled, false
     * otherwise.
     */
    public boolean isMonitoringEnabled();


    /**
     * Gets the <code>KeepAliveStats</code> associated with this task.
     */
    public KeepAliveStats getKeepAliveStats();

    
    /**
     * Add a <code>Task</code> to this class.
     */
    public void addTaskListener(TaskListener task);

    
    /**
     * Remove a <code>Task</code> to this class.
     */
    public void removeTaskListener(TaskListener task);
    
    
    /**
     * Execute this task by using the associated <code>Pipeline</code>.
     * If the <code>Pipeline</code> is null, the task's <code>doTask()</code>
     * method will be invoked.
     */   
    public void execute();
    
    
    /**
     * Recycle this task.
     */
    public void recycle();
    
    
    /**
     * Return the <code>ArrauList</code> containing the listeners.
     */
    public ArrayList getTaskListeners(); 


    /**
     * Remove all listeners
     */
    public void clearTaskListeners();


    /**
     * Recycle the Task after every doTask invokation.
     */
    public void setRecycle(boolean recycle);


    /**
     * Return <code>true</code> if this <code>Task</code> will be recycled.
     */
    public boolean getRecycle();

    
    /**
     * Set the pipeline on which Worker Threads will synchronize.
     */
    public void setPipeline(Pipeline pipeline);
    
    
    /**
     * Return the pipeline used by this object.
     */
    public Pipeline getPipeline();  
}
