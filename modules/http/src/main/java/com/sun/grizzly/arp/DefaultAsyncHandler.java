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

package com.sun.grizzly.arp;

import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.Task;
import com.sun.grizzly.util.DataStructures;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

/**
 * Default implementation of {@link AsyncHandler}. This class handle 
 * the aysnchronous execution of a {@link ProcessorTask}. The request
 * processing is executed by doing:
 *
 * (1) Wrap the {@link ProcessorTask} using an instance of 
 *     {@link AsyncTask}
 * (2) Execute the {@link AsyncTask} using the wrapped
 *     {@link ProcessorTask} {@link ExecutorService}
 * (3) If the {@link AsyncTask} has been interrupted but ready
 *     to be removed from the interrupted queue, remove it and execute the
 *     remaining operations.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultAsyncHandler implements AsyncHandler{
    
    /**
     * Cache instance of {@link AsyncTask}
     */
    private final Queue<AsyncTask> asyncProcessors =
            DataStructures.getCLQinstance(AsyncTask.class);
    
               
    
    /**
     * The {@link AsyncFilter} to execute asynchronous operations on 
     * a {@link ProcessorTask}.
     */
    private final ArrayList<AsyncFilter> asyncFilters =
            new ArrayList<AsyncFilter>();   
    
    
    // ------------------------------------------------- Constructor --------//
    
    
    public DefaultAsyncHandler() {
    }
    
    
    /**
     * Create an instance of {@link AsyncTask}
     */
    private AsyncTask newAsyncProcessorTask(){
        AsyncTask asyncTask = new AsyncProcessorTask();
        asyncTask.setAsyncExecutor(newAsyncExecutor(asyncTask));  
        return asyncTask;
    }
    
    
    /**
     * Create an instance of {@link DefaultAsyncExecutor}
     */    
    protected AsyncExecutor newAsyncExecutor(AsyncTask asyncTask){        
        AsyncExecutor asyncExecutor = new DefaultAsyncExecutor();
        asyncExecutor.setAsyncTask(asyncTask);
        asyncExecutor.setAsyncHandler(this);
            
        for (AsyncFilter l : asyncFilters){
            asyncExecutor.addAsyncFilter(l);
        }
        return asyncExecutor;
    }
    
    
    /**
     * Return an instance of {@link AsyncTask}, which is 
     * configured and ready to be executed.
     */
    private AsyncTask getAsyncProcessorTask(){
        AsyncTask asyncTask = asyncProcessors.poll();
        if ( asyncTask == null) {
            asyncTask = newAsyncProcessorTask();
        } else {
            asyncTask.recycle();
        }
        return asyncTask;
    }
    
    
    // ---------------------------------------------------- Interface -------//
    
    
    /**
     * Handle an instance of a {@link Task}. This method is invoked
     * first by a {@link ProcessorTask}, which delegate its execution to 
     * this handler. This method will uses an {@link AsyncTask} to start 
     * the execution of the defined {@link AsyncFilter}.
     */
    public void handle(Task task){        
        if (task instanceof ProcessorTask){
            AsyncTask apt = getAsyncProcessorTask();
            apt.setThreadPool(task.getThreadPool());
            apt.setSelectorThread(task.getSelectorThread());
            apt.getAsyncExecutor().setProcessorTask((ProcessorTask)task);
            apt.execute(null);
        } else {
            task.execute();
        }
    }
    

    /**
     * Return th {@link Task} to the pool
     */
    public void returnTask(AsyncTask asyncTask){
        asyncProcessors.offer(asyncTask);
    }
    

    /**
     * Add an {@link AsyncFilter}
     */
    public void addAsyncFilter(AsyncFilter asyncFilter) {
        asyncFilters.add(asyncFilter);
    }

    
    /**
     * Remove an {@link AsyncFilter}
     */
    public boolean removeAsyncFilter(AsyncFilter asyncFilter) {
        return asyncFilters.remove(asyncFilter);
    }
}
