/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */
package com.sun.grizzly.http;

import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.util.http.HtmlHelper;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.logging.Level;

import com.sun.grizzly.tcp.RequestGroupInfo;
import com.sun.grizzly.util.OutputWriter;
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
     * Recycle this task
     */
    protected boolean recycle = true;
    
    
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
     * Gets the {@link RequestGroupInfo} from this task.
     */
    public RequestGroupInfo getRequestGroupInfo() {
        return (selectorThread != null?
            selectorThread.getRequestGroupInfo() : null);
    }
    
    
    /**
     * Returns <tt>true</tt> if monitoring has been enabled, false
     * otherwise.
     */
    public boolean isMonitoringEnabled(){
        return (selectorThread != null ?
            selectorThread.isMonitoringEnabled() : false);
    }
    
    
    /**
     * Gets the <code>KeepAliveStats</code> associated with this task.
     */
    public KeepAliveStats getKeepAliveStats() {
        return (selectorThread != null?
            selectorThread.getKeepAliveStats() : null);
    }
    
    
    /**
     * Execute the task based on its {@link ExecutorService}. If the
     * {@link ExecutorService} is null, then execute the task on using the
     * calling thread.
     */
    public void execute(){
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
     * Declare whether this {@link Task} is recyclable. If so, this
     * {@link Task} will be recycled after every invocation of
     * <code>doTask()</code>.
     */
    public void setRecycle(boolean recycle){
        this.recycle = recycle;
    }
    
    
    /**
     * Return <tt>true</tt> if this {@link Task} is recyclable.
     */
    public boolean getRecycle(){
        return recycle;
    }
    
    
    /**
     * Return the current {@link Socket} used by this instance
     * @return socket the current {@link Socket} used by this instance
     */
    public Socket getSocket(){
        return null;
    }
    
    
    /**
     * Return the underlying {@link Channel}, independent of the NIO
     * mode we are using.
     */
    private SocketChannel getChannel(){
        if ( key == null ) {
            return getSocket().getChannel();
        } else {
            return (SocketChannel)key.channel();
        }
    }
    
    
    /**
     * Cancel the task.
     * @param message the HTTP message to included within the html page
     * @param code The http code to use. If null, automatically close the
     *             connection without sending an error page.
     */
    public void cancelTask(String message, String code){
        SocketChannel channel = getChannel();
        
        if (code != null) {
            SelectorThread.logger().log(Level.WARNING,message);
            try {
                ByteBuffer byteBuffer = HtmlHelper.getErrorPage(message, code, SelectorThread.SERVER_NAME);
                OutputWriter.flushChannel(channel,byteBuffer);
            } catch (IOException ex){
                SelectorThread.logger().log(Level.FINE,"CancelTask failed", ex);
            }
        }
        
        if ( SelectorThread.isEnableNioLogging() ){
            SelectorThread.logger().log(Level.INFO, "Cancelling SocketChannel "
                    + getChannel());
        }
        
        if ( key != null){
            selectorThread.cancelKey(key);
        } else if ( getSocket() != null ){
            try{
                getSocket().close();
            } catch (IOException ex){
            }
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
