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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

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
     * Set the {@link SelectionKey}
     */
    public void setSelectionKey(SelectionKey key);
    
    
    /**
     * Return the {@link SelectionKey} associated with this tasks.
     */
    public SelectionKey getSelectionKey();

    
    /**
     * Set the {@link SelectorThread} used by this task.
     */
    public void setSelectorThread(SelectorThread selectorThread);
    
    
    /**
     * Returns the {@link SelectorThread} used by this task.
     */
    public SelectorThread getSelectorThread();


    /**
     * Returns {@link SelectorHandler}, which handles this taks's {@link SelectionKey}
     * 
     * @return {@link SelectorHandler}, which handles this taks's {@link SelectionKey}
     */
    public SelectorHandler getSelectorHandler();

    /**
     * Sets {@link SelectorHandler}, which handles this taks's {@link SelectionKey}
     *
     * @param  {@link SelectorHandler}, which handles this taks's {@link SelectionKey}
     */
    public void setSelectorHandler(SelectorHandler selectorHandler);

    
    /**
     * Execute this task by using the associated {@link ExecutorService}.
     * If the {@link ExecutorService} is null, the task's <code>doTask()</code>
     * method will be invoked.
     */   
    public void execute();

    /**
     * Execute this task using the passed {@link ExecutorService}.
     * If the {@link ExecutorService} is null, the task's <code>doTask()</code>
     * method will be invoked.
     */
    public void execute(ExecutorService threadPool);
    
    
    /**
     * Recycle this task.
     */
    public void recycle();
        
    
    /**
     * Set the thread pool on which Worker Threads will synchronize.
     */
    public void setThreadPool(ExecutorService threadPool);
    
    
    /**
     * Return the thread pool used by this object.
     */
    public ExecutorService getThreadPool();
}
