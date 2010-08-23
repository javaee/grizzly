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

package com.sun.grizzly.http;

import com.sun.grizzly.SelectorHandler;
import java.io.IOException;
import java.nio.channels.SelectionKey;

import java.util.concurrent.ExecutorService;

/**
 * Abstract implementation of a {@link Task} object.
 *
 * @author Jean-Francois Arcand
 */
public abstract class TaskBase implements Task{
    
    
    /**
     * This number represent a specific implementation of a {@link Task}
     * instance.
     */
    protected int type;    
    
    /**
     * The {@link ExecutorService} object associated with this
     * {@link Task}
     */
    protected ExecutorService threadPool;
    
    
    /**
     * The {@link SelectionKey} used by this task.
     */
    protected SelectionKey key;
    
    
    /**
     * The {@link SelectorThread} who created this task.
     */
    protected SelectorThread selectorThread;

    /**
     * {@link SelectorHandler}, which handles this {@link SelectionKey} I/O events
     */
    protected SelectorHandler selectorHandler;
    
    /**
     * A {@link TaskListener} associated with this instance.
     */
    private TaskListener taskListener;
    
    // ------------------------------------------------------------------//
    
    public int getType(){
        return type;
    }
    
    
    /**
     * Set the {@link SelectorThread} object.
     */
    public void setSelectorThread(SelectorThread selectorThread){
        this.selectorThread = selectorThread;
    }
    
    
    /**
     * Return the {@link SelectorThread}
     */
    public SelectorThread getSelectorThread(){
        return selectorThread;
    }

    /**
     * {@inheritDoc}
     */
    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }

    /**
     * {@inheritDoc}
     */
    public void setSelectorHandler(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
    }
    
    /**
     * Set the thread pool on which Worker Threads will synchronize.
     */
    public void setThreadPool(ExecutorService threadPool){
        this.threadPool = threadPool;
    }
    
    
    /**
     * Return the thread pool used by this object.
     */
    public ExecutorService getThreadPool(){
        return threadPool;
    }
    
    
    /**
     * Set the {@link SelectionKey}
     */
    public void setSelectionKey(SelectionKey key){
        this.key = key;
    }
    
    
    /**
     * Return the {@link SelectionKey} associated with this task.
     */
    public SelectionKey getSelectionKey(){
        return key;
    }
    
    
    /**
     * Execute the task based on its {@link ExecutorService}. If the
     * {@link ExecutorService} is null, then execute the task on using the
     * calling thread.
     */
    public void execute(){
        execute(threadPool);
    }

    /**
     * {@inheritDoc}
     */
    public void execute(ExecutorService threadPool) {
        if (threadPool != null) {
            threadPool.execute(this);
        } else {
            run();
        }
    }
    
       
    /**
     * Recycle internal state.
     */
    public void recycle(){
    }
       
    
    /**
     * Some {@link ExecutorService} implementation requires a instance of
     * <code>Runnable</code> instance.
     */
    public void run(){
        try{
            doTask();
        } catch (IOException ex){
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * By default, do nothing when a <code>Callable</code> is invoked.
     */
    public Object call() throws Exception{
        doTask();
        return null;
    }

    /**
     * The {@link TaskListener} associated with this instance.
     * @return  {@link TaskListener} associated with this instance.
     */
    public TaskListener getTaskListener() {
        return taskListener;
    }

    /**
     * Set the {@link TaskListener} associated with this class.
     * @param  {@link TaskListener} associated with this instance.
     */ 
    public void setTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
    }
      
}
