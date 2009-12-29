/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.websocket;

import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.GrizzlyExecutorService;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.ThreadPoolConfig;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Roundrobin loadbalancing is used.
 * <br>
 * TODO: compare CLQ iterator vs poll() at high loads:
 * The increased concurrency and faster iteration, removal is
 * likely to outweight the GC overad of iterator creation at high loads.
 * <br>
 * TODO: add easy to use configuration of everything.
 * <br>
 * TODO: add jdoc
 * 
 * @author gustav trede
 * @since 2009
 */
class SelectThread extends Thread{

    protected final static Logger logger = LoggerUtils.getLogger();

    private final static long idlecheckdelta = 2*1000*1000*1000;

    private final static AtomicInteger threadcount = new AtomicInteger();    

    protected final static GrizzlyExecutorService workers = 
            GrizzlyExecutorService.createInstance(
            new ThreadPoolConfig("WebsocketThreadPool", -1,
            Math.max(4, 1*Runtime.getRuntime().availableProcessors())
            ,null, -1, 1, TimeUnit.MILLISECONDS,
            new ThreadFactory(){
                //private final AtomicInteger c = new AtomicInteger();
                public Thread newThread(Runnable r) {
                  return new Thread(r);//r,"WebSocketWorker("+c.incrementAndGet()+")");
                }
            }
            ,Thread.NORM_PRIORITY, null));

    public static GrizzlyExecutorService getThreadPool() {
        return workers;
    }


    private final AtomicLong socketsServedLifeTimeCounter = new AtomicLong();

    /**
     * Ensures selector.wakeup() is only called once per select loop.
     * This is needed due to wakeup is internaly synchronized, and its
     * also sharing the lock with selector.select() (least on sun jdk7).
     */
    private final AtomicInteger wakenup = new AtomicInteger();

    private long nextIdleCheck;

    private long currentSelectTimeStamp;

    private final Selector selector;

    private final Queue<SelectorLogicHandler> newConnections;

    protected final Queue<Runnable> tasksforSelectThread;

    private SelectThread next;//TODO: change to volatile and runtime recconfig

    private volatile static SelectThread current;

    static{
        int i = Runtime.getRuntime().availableProcessors();
        if (i>2){
            i = Math.min(16,i/2);
        }
        final SelectThread first = createInstance();
        SelectThread a = first;
        while(--i>0){
            a = a.next = createInstance();
        }
        a.next = first;
        current = first;
        SelectThread c = first;
        do{
            c.start();
        }while((c = c.next) !=  first);
    }

    private static SelectThread createInstance() {
        try {
            return new SelectThread();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }        
    }

    /**
     *
     * @param clearStatistics true if clear stats
     * @return
     */
    public final static String getStatistics(boolean clearStatistics){
        long min = Long.MAX_VALUE,max = 0,total = 0;
        final SelectThread start = current;
        SelectThread c = start;
        int tc = 0;
        do{
            tc++;
            final long v = clearStatistics ?
                c.socketsServedLifeTimeCounter.getAndSet(0) :
                c.socketsServedLifeTimeCounter.get();
            total += v;
            max = Math.max(max, v);
            min = Math.min(min,v);
        }while((c = c.next) !=  start);
        return "SelectorLoadBalance stats: threads:"+tc+" sockets[max:"+max
                +" min:"+min+" total:"+total+"]";
    }

    protected final static SelectThread getNextSelectThread(){
        SelectThread current_  = current;
        current = current_.next;
        return current_;
    }
    

    /**
     * 
     * @throws IOException
     */
    public SelectThread() throws IOException {
        super(SelectThread.class.getSimpleName()+
                "("+threadcount.incrementAndGet()+")");
        this.selector = Selector.open();
        this.newConnections =
                DataStructures.getCLQinstance(SelectorLogicHandler.class);
        this.tasksforSelectThread=DataStructures.getCLQinstance(Runnable.class);
        this.setPriority(Thread.NORM_PRIORITY - 0);
    }

    public void addConnection(SelectorLogicHandler slh){
        newConnections.offer(slh);
        wakeUpSelector();
    }

    @Override
    public void run() {
        while(true){
            doselect();
            handleNewConnections();
            idlecheck();
            handleSelectedKeys();                       
            handleTasksforSelectThread();
        }
    }

    private void doselect(){
        try {
            wakenup.set(0);        
            selector.select(1000);
        } catch (Throwable ex) {
            logger.log(Level.SEVERE,"selector.select() failed", ex);
        }        
    }

    private void handleNewConnections(){
        final long t1 = System.nanoTime();
        currentSelectTimeStamp = t1;
        SelectorLogicHandler wsl;
        int added = 0;
        while((wsl=newConnections.poll()) != null){
            wsl.enteredSelector(this,t1);
            added++;
        }
        if (added > 0){
            socketsServedLifeTimeCounter.addAndGet(added);
        }
    }

    private void idlecheck(){
        final long t = currentSelectTimeStamp;
        if (t-nextIdleCheck>0){
            nextIdleCheck = t + idlecheckdelta;
            for (SelectionKey key:selector.keys()){
                ((SelectorLogicHandler)key.attachment()).idleCheck(t);
            }
        }
    }

    private void handleSelectedKeys(){
        final long t1 = currentSelectTimeStamp;
        final Set<SelectionKey> selectedkeys = selector.selectedKeys();
        for (SelectionKey key:selectedkeys){
            ((SelectorLogicHandler)key.attachment()).handleSelectedKey(key,t1);
        }
        selectedkeys.clear();
    }
    
    private void handleTasksforSelectThread(){
        Runnable task;
        while((task=tasksforSelectThread.poll())!=null){
            task.run();
        }
        /*
        final Iterator<Runnable> iter = tasksforSelectThread.iterator();
        while(iter.hasNext()){
            iter.next().run();
            iter.remove();
        }
        */
    }

    /**
     * Task must not throw exceptions,
     * Throwables should be handled in its run method.
     * @param task
     */
    protected final void offerTask(Runnable task){
        tasksforSelectThread.offer(task);
        wakeUpSelector();
    }

    /**
     * Register channel to this selector.
     * 
     * @param ch
     * @param interests
     * @param attachment 
     * @return
     * @throws IOException
     */
    protected final SelectionKey register(SelectableChannel ch, int interests,
            SelectorLogicHandler attachment)throws IOException{
        return ch.register(selector,interests,attachment);
    }

    protected final void wakeUpSelector(){
        if (wakenup.compareAndSet(0,1)){
            selector.wakeup();
        }
    }

    public long getSocketsServedLifeTimeCount() {
        return socketsServedLifeTimeCounter.get();
    }
    
    @Override
    public String toString() {
        return getName() +
                " socketsServedLifeTime:"+socketsServedLifeTimeCounter+" ";
    }    

}
