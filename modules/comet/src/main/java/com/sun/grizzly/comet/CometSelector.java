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

package com.sun.grizzly.comet;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.util.LinkedTransferQueue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NIO {@link Selector} allowing {@link CometHandler} to receive
 * non-blocking requests bytes during request polling.
 *
 * TODO: investigate if its possible to move this functionality to grizzly main
 *  selector inorder to lower the extra overhead this 2nd selector is.
 *
 * @author Jeanfrancois Arcand
 * @author Gustav Trede
 */
public class CometSelector {

    /**
     * The {@link CometEngine} singleton
     */
    protected final CometEngine cometEngine;
     
    
    /**
     * The {@link Selector}
     */
    private Selector selector;
       
    /**
     *  timestamp when last expireIdleKeys() performed its check
     */
    private long lastIdleCheck;

    /**
     * Logger.
     */
    private final Logger logger = SelectorThread.logger();

    /**
     * 
     */
    private final ByteBuffer dumybuffer = ByteBuffer.allocate(1);

    /**
     * The list of {@link SelectionKey} to register with the 
     * {@link Selector}
     *  TODO: replace with LinkedTransferQueue 
     */
    private final LinkedTransferQueue<CometTask> keysToRegister
            = new LinkedTransferQueue<CometTask>();

    
    /**
     * New {@link CometSelector}
     * @param cometEngine The {@link CometEngine} singleton 
     */
    public CometSelector(CometEngine cometEngine) {
        this.cometEngine = cometEngine;
    }

    
    /**
     * Start the {@link Selector} running on its 
     * Thread.
     */
    public void start() throws InterruptedException{
        final CountDownLatch isStartedLatch = new CountDownLatch(1);
        new Thread("CometSelector"){{
               setDaemon(true);                
            }
            
            @Override
            public void run(){       
                try{
                    selector = Selector.open();
                } catch(IOException ex){
                    // Most probably a fd leak.
                    logger.log(Level.SEVERE,"CometSelector.open()",ex);
                    return;
                }                
                isStartedLatch.countDown();
                
                doSelection();
            }
        }.start();
        isStartedLatch.await();
    }

    /**
     *  the selection logic
     */
    private void doSelection(){
        while (true){
            int selectorState = 0;
            try{
                try{
                    selectorState = selector.select(1000);
                } catch (CancelledKeyException ex){
                    if (logger.isLoggable(Level.FINEST)){
                        logger.log(Level.FINEST,"CometSelector.open()",ex);
                    }
                }

                handleSelectedKeys();
                expireIdleKeys();
                registerNewKeys();

            } catch (Throwable t){
                handleException(t,null);
            }finally{
                if (selectorState <= 0){ //todo why is this needed ?
                    selector.selectedKeys().clear();
                }
            }
        }
    }

    /**
     * handle the selected keys
     */
    private void handleSelectedKeys(){
        for (SelectionKey cometKey:selector.selectedKeys()) {
            try{
                if (cometKey.isValid()) {
                    CometTask cometTask = (CometTask)cometKey.attachment();
                    boolean asyncExec = cometTask.isComethandlerisAsyncregistered();
                    if (asyncExec){
                        cometTask.setComethandlerisAsyncregistered(false);
                        if (cometKey.isReadable()){
                            cometKey.interestOps(cometKey.interestOps() & (~SelectionKey.OP_READ));
                            cometTask.upcoming_op = CometTask.OP_EVENT.READ;
                        }

                        if (cometKey.isWritable()){
                            cometKey.interestOps(cometKey.interestOps() & (~SelectionKey.OP_WRITE));
                            cometTask.upcoming_op = CometTask.OP_EVENT.WRITE;
                        }
                    }
                    if (cometTask.getSelectionKey().attachment() == null){
                        if (cometTask.cometHandlerNotResumed()){
                            if (asyncExec){
                                cometTask.execute();
                            }else{
                               checkIfclientClosedConnection(cometKey);
                            }
                        }
                    } else {
                       // logger.warning("cometselector comettask.mainkey has an attachment. ");       
                         cancelKey(cometKey,false,true, true);
                    }
                } else {
                    //logger.warning("cometselector select detected invalid cometKey.");
                    cancelKey(cometKey,false,true,true);
                }
            }catch(Throwable t){ 
                handleException(t, cometKey);
            }
        }
        // one shot clear is alot faster then removing each element one by one.
        selector.selectedKeys().clear();
    }

    /**
     *
     * @param cometKey
     */
    private void checkIfclientClosedConnection(SelectionKey cometKey) {
        boolean connectionclosed = true;
        try {
            SocketChannel socketChannel = (SocketChannel)cometKey.channel();
            dumybuffer.clear();
            connectionclosed = socketChannel.read(dumybuffer) == -1;
        } catch (Throwable ex) {
            // null we dont want cancelkey to happen here, cause it does not cancel mainKey
            handleException(ex, null);
        }
        finally{           
           if (connectionclosed)
               cancelKey(cometKey, true, true, true);
        }
    }

    /**
     * perform the registration of new keys.
     * The mainKey is the SelectionKey returned by the
     * Selector used in the SelectorThread class.
     */
    private void registerNewKeys(){
        SelectionKey cometKey = null;
        CometTask cometTask;
        while ((cometTask = keysToRegister.poll()) != null ){
            try{
                SelectionKey mainKey = cometTask.getSelectionKey();
                SocketChannel channel =  (SocketChannel)mainKey.channel();
                if (mainKey.isValid() && channel.isOpen()) {
                    cometKey = channel.register(selector,SelectionKey.OP_READ);
                    cometTask.setCometKey(cometKey);
                    cometKey.attach(cometTask);
                    cometTask.getCometContext().addActiveCometTask(cometTask);
                    cometTask.getCometContext().
                            addActiveHandler(cometTask.getCometHandler(), cometKey);
                    cometKey = null; 
                }
            }catch(Throwable t){
                handleException(t, cometKey);
            }
        }
    }

    /**
     * Expires registered {@link SelectionKey}. If a 
     * {@link SelectionKey} is expired, the request will be resumed and the 
     * HTTP request will complete,
     */
    private void expireIdleKeys(){
        if (selector.keys().isEmpty()){
            return;
        }
        
        final long current = System.currentTimeMillis();
        if (current - lastIdleCheck < 1000){
            return;
        }
        
        lastIdleCheck = current;
        for (SelectionKey cometKey:selector.keys()){
            try{
                CometTask cometTask = (CometTask)cometKey.attachment();
                if (cometTask == null)
                    continue;
                if (cometTask.hasExpired(current)){
                    cancelKey(cometKey,false,true, true);
                    continue;
                }
                /**
                 * The connection has been resumed since the timeout is
                 * re-attached to the SelectionKey so cancel the Comet key.
                 */
                if (cometTask.getSelectionKey().attachment() instanceof Long){
                    cometKey.attach(null);
                    cometKey.cancel();
                }
            }catch(Throwable t){
                handleException(t, cometKey);
            }
        }
    }  

    /**
     *  handle exceptions for selection logic
     * @param t
     * @param key
     */
     private void handleException(Throwable t, SelectionKey key){
        try{
            cancelKey(key,false,true, true);
        } catch (Throwable t2){
            logger.log(Level.SEVERE,"CometSelector",t2);
        }
        if (logger.isLoggable(Level.FINEST)){
            logger.log(Level.FINEST,"CometSelector",t);
        }
     }
     

    /**
     * Cancel the {@link SelectionKey} associated with a suspended connection.
     */
    protected boolean cancelKey(SelectionKey cometKey, boolean cancelMainKey,
             boolean removeCometHandler, boolean notifyInterrupt){
        if (cometKey == null) //cometcontext.resume can give a null cometkey
            return false;
        boolean status = true;
        CometTask cometTask = null;
        // attach is only atomic since dolphin b06 , hence we must synchronize 
        // until we can require dolphin
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6436220
        synchronized(cometKey){
            cometTask = (CometTask) cometKey.attach(null); 
            if (cometTask != null){
                //synchronizes internally on itself and canceledkeyset,
                //we want hotspot to use lock coarsening.
                cometKey.cancel();
            }
        }
        status = cometTask != null;
        if (status){
            status = cometTask.getCometContext().interrupt(cometTask,
                    removeCometHandler, notifyInterrupt);            
            cometEngine.flushPostExecute(cometTask.getAsyncProcessorTask());            
            
            if (cancelMainKey){                
                cometTask.getSelectorThread().cancelKey(cometTask.getSelectionKey());
            }
        }
        return status;
    }

    /**
     * Register the {@link SelectionKey} to the {@link Selector}. We
     * cannot register the {@link SelectionKey} directy on the
     * {@link Selector} because there is a deadlock in the VM (see bug XXX).
     */
    public void registerKey(CometTask cometTask){
        if (cometTask.getSelectionKey().isValid() && selector != null){
            cometTask.setExpireTime(System.currentTimeMillis());
            keysToRegister.offer(cometTask);
            selector.wakeup();
        }
    }
    
    
    /**
     * Wakes up the {@link Selector} 
     */
    public void wakeup(){
        selector.wakeup();
    }
    
    /**
     * Return the SelectionKey associated with this channel.
     */
    public SelectionKey cometKeyFor(SelectableChannel channel){
        return channel.keyFor(selector);
    }
    
}
