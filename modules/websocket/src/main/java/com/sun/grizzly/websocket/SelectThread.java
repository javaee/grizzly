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
 * getstatistics . make it threadsafe by using a runnable task that gets the
 * info from inside selthread and then uses a barrier to return it to caller.
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
            new ThreadPoolConfig("websocketpool", -1,
            Math.max(16, 2*Runtime.getRuntime().availableProcessors()),
            null, -1,
            1, TimeUnit.MILLISECONDS,
            null, Thread.NORM_PRIORITY, null));

    private final AtomicLong socketsServedLifeTimeCounter = new AtomicLong();

    private final AtomicInteger wakenup = new AtomicInteger();

    private long nextIdleCheck;

    private long currentSelectTimeStamp;

    private final Selector selector;

    private final Queue<SelectorLogicHandler> newConnections;

    protected final Queue<Runnable> tasksforSelectThread;

    private SelectThread next;

    private static SelectThread current;

    static{
        int i = Runtime.getRuntime().availableProcessors();
        if (i>2){
            i = Math.min(8,i/2);
        }
        SelectThread first = dostart();
        SelectThread a = first;
        while(--i>0){
            a = a.next = dostart();
        }
        a.next = first;
        current = first;
    }

  //todo make more rubust design.
    private static SelectThread dostart() {
        try {
            SelectThread ws = new SelectThread();
            ws.start();
            return ws;
        } catch (Throwable ex) {
            logger.log(Level.SEVERE,"" , ex);
        }
        return null;
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
        return "SelectorLoadBalance stats: threads:"+tc+" sockets[max:"+max+" min:"+min+
                " total:"+total+"]";
    }


    /**
     * TODO supposed to be called from one thread only,
     * currently its not.
     * @param slh
     */
    protected final static void moveToSelectThread(SelectorLogicHandler slh){
        SelectThread current_  = current;
        current = current_.next;
        current_.newConnections.offer(slh);
        current_.wakeUpSelector();
    }
    

    /**
     * 
     * @throws IOException
     */
    public SelectThread() throws IOException {
        super(SelectThread.class.getSimpleName()+
                "("+threadcount.incrementAndGet()+")");
        this.selector = Selector.open();
        this.newConnections = DataStructures.getCLQinstance(SelectorLogicHandler.class);
        this.tasksforSelectThread = DataStructures.getCLQinstance(Runnable.class);
        //this.workers  = initThreadPool();
        this.setPriority(Thread.NORM_PRIORITY + 1);
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
        long t1 = System.nanoTime();
        currentSelectTimeStamp = t1;
        SelectorLogicHandler wsl;
        Queue<SelectorLogicHandler> queue = newConnections;
        int added = 0;
        while((wsl=queue.poll())!=null){
            wsl.enteredSelector(this,t1);
            added++;
        }
        if (added > 0){
            socketsServedLifeTimeCounter.addAndGet(added);
        }
    }

    private void idlecheck(){
        long t = currentSelectTimeStamp;
        if (t-nextIdleCheck>0){
            nextIdleCheck = t + idlecheckdelta;
            for (SelectionKey key:selector.keys()){
                ((SelectorLogicHandler)key.attachment()).idleCheck(t);
            }
        }
    }

    private void handleSelectedKeys(){
        long t1 = currentSelectTimeStamp;
        Set<SelectionKey> selectedkeys = selector.selectedKeys();
        for (SelectionKey key:selectedkeys){
            ((SelectorLogicHandler)key.attachment()).handleSelectedKey(key,t1);
        }
        selectedkeys.clear();
    }
    
    private void handleTasksforSelectThread(){
        Runnable task;
        Queue<Runnable> queue = tasksforSelectThread;
        while((task=queue.poll())!=null){
            task.run();
        }
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
           //internaly synchronized on same lock as select....(sun jdk7)
            selector.wakeup();
        }
    }

    public long getSocketsServedLifeTimeCount() {
        return socketsServedLifeTimeCounter.get();
    }
    
    @Override
    public String toString() {
        return getName() +
                //" sockets:"+socketCount+
                " socketsServedLifeTime:"+socketsServedLifeTimeCounter+" ";
    }    

}
