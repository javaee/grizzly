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

import com.sun.grizzly.Controller;
import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.NIOContext;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.http.TaskBase;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

/**
 * A {@link Task} implementation that allow Grizzly ARP to invokeCometHandler
 * {@link CometHandler} when new data (bytes) are available from the 
 * {@link CometSelector}.
 *
 * @author Jeanfrancois Arcand
 */
public class CometTask extends TaskBase{
    
    public enum OP_EVENT { READ, WRITE }
    
   
    /**
     * The current non blocking operation.
     */
    protected OP_EVENT upcoming_op = OP_EVENT.READ;
    
    
    /**
     * The {@link CometContext} associated with this instance.
     */
    private CometContext cometContext;
    
    
    /**
     * The {@link CometSelector} .
     */
    private CometSelector cometSelector;
    
    
    /**
     * The time in milliseconds before this object was registered the 
     * {@link SelectionKey} on the {@link CometSelector}
     */
    private long expireTime ;


    /**
     * used by cometselector to optmize:
     * dont give simple read == -1 operations to thread pool
     */
    private volatile boolean comethandlerisAsyncregistered;
    
    /**
     * The InputStream used to read bytes from the {@link CometSelector}
     */
    private InputReader cometInputStream;
    
    
    /**
     * The CometSelector registered key.
     */
    private SelectionKey cometKey;

    /**
     * The {@link AsyncProcessorTask}
     */
    private AsyncProcessorTask asyncProcessorTask;

    /**
     * The {@link CometEvent} associated with this task.
     */
    private CometEvent event;
        
    /**
     * The {@link CometHandler} associated with this task.
     */
    private CometHandler cometHandler;

    /**
     * The CometWriter associated with this task.
     */
    private CometWriter writer;
    
    
    /**
     * The CometReader associated with this task.
     */
    private CometReader reader;
    
    
    /**
     * <tt>true</tt> if the CometHandler has been registered for OP_READ 
     * events.
     *  false by default. java lang specification states that.
     */
    private boolean asyncReadSupported ;
    
    
    /**
     * New {@link CometTask}.
     */
    public CometTask() {  
    }

    
    /**
     * Notify the {@link CometHandler} that bytes are available for read.
     * The notification will invoke all {@link CometContext}
     */
    public void doTask() throws IOException{     
        // The CometHandler has been resumed.
        if (!cometContext.isActive(cometHandler) ){
            return;
        }

        /**
         * The CometHandler in that case is **always** invoked using this
         * thread so we can re-use the Thread attribute safely.
         */
        ByteBuffer byteBuffer = null;
        boolean connectionClosed = false;
        boolean clearBuffer = true;
        try{

            if (cometInputStream == null){
                cometInputStream = new  InputReader();
            }            
            
            cometInputStream.setSelectionKey(cometKey);
            byteBuffer = ((WorkerThread)Thread.currentThread()).getByteBuffer();
            if (byteBuffer == null){
                byteBuffer = ByteBuffer.allocate(selectorThread.getBufferSize());
                ((WorkerThread)Thread.currentThread()).setByteBuffer(byteBuffer);
            } else {
                byteBuffer.clear();
            }

            cometInputStream.setByteBuffer(byteBuffer); 
            SocketChannel socketChannel = (SocketChannel)cometKey.channel();
            if (upcoming_op == OP_EVENT.READ){                   
                /*
                 * We must execute the first read to prevent client abort.
                 */               
                int nRead = socketChannel.read(byteBuffer);   
                if (nRead == -1 ){
                    connectionClosed = true;
                } else {        
                    /* 
                     * This is an HTTP pipelined request. We need to resume
                     * the continuation and invoke the http parsing 
                     * request code.
                     */
                    if (!asyncReadSupported){
                        // Don't let the main Selector (SelectorThread) starts
                        // handling the pipelined request.
                        key.attach(Long.MIN_VALUE);

                        /**
                         * Something when wrong, most probably the CometHandler
                         * has been resumed or removed by the Comet implementation.
                         */
                        if (!cometContext.isActive(cometHandler)){
                            return;
                        }
                        
                        // Before executing, make sure the connection is still
                        // alive. This situation happens with SSL and there 
                        // is not a cleaner way fo handling the browser closing
                        // the connection.
                        nRead = socketChannel.read(byteBuffer);                         
                        if (nRead == -1){
                           connectionClosed = true;
                           return;
                        }
                        
                        cometContext.resumeCometHandler(cometHandler, false);
                        clearBuffer = false;
                                               
                        Controller controller = getSelectorThread().getController();
                        ProtocolChain protocolChain = 
                                controller.getProtocolChainInstanceHandler().poll();
                        NIOContext ctx = (NIOContext)controller.pollContext(key);                       
                        ctx.setController(controller);
                        ctx.setSelectionKey(key);
                        ctx.setProtocolChain(protocolChain);
                        ctx.setProtocol(Protocol.TCP);
                        protocolChain.execute(ctx);                                         
                    } else {
                        byteBuffer.flip(); 
                        reader = new CometReader();
                        reader.setNRead(nRead);
                        reader.setByteBuffer(byteBuffer);
                        if (event == null)
                            event = new CometEvent();
                        event.type = CometEvent.READ;
                        event.attach(reader);
                        cometContext.invokeCometHandler(event,cometHandler);
                        reader.setByteBuffer(null);
                        
                        // This Reader is now invalid. Any attempt to use
                        // it will results in an IllegalStateException.
                        reader.setReady(false);
                    }
                }
            } else if (upcoming_op == OP_EVENT.WRITE){
                if (event == null)
                    event = new CometEvent();
                event.type = CometEvent.WRITE;
                writer = new CometWriter();
                writer.setChannel(socketChannel);
                event.attach(writer);
                cometContext.invokeCometHandler(event,cometHandler);  
                        
                // This Writer is now invalid. Any attempt to use
                // it will results in an IllegalStateException.                
                writer.setReady(false);
           }
        } catch (IOException ex){
            connectionClosed = true;
            // Bug 6403933 & GlassFish 2013
            if (SelectorThread.logger().isLoggable(Level.FINEST)){
                SelectorThread.logger().log(Level.FINEST,"Comet exception",ex);
            }
        } catch (Throwable t){
            connectionClosed = true;
            SelectorThread.logger().log(Level.SEVERE,"Comet exception",t);            
        } finally {   
            // Bug 6403933
            if (connectionClosed){
                cometSelector.cancelKey(cometKey,true,true, true);
            }
            
            if (clearBuffer && byteBuffer != null){
                byteBuffer.clear();
            }
            asyncReadSupported = false;
        }
    }

    public void setComethandlerisAsyncregistered(boolean comethandlerisAsyncregistered) {
        this.comethandlerisAsyncregistered = comethandlerisAsyncregistered;
    }

    public boolean isComethandlerisAsyncregistered() {
        return comethandlerisAsyncregistered;
    }

    /**
     * returns true if the CometHandler has not been resumed / removed.
     * allows cometSelector to do a fast check before leting threadpool execute the comettask
     * @return
     */
    public boolean cometHandlerNotResumed(){
        return cometContext.isActive(cometHandler);
    }
    
    /**
     * Return the {@link CometContext} associated with this instance.
     * @return CometContext the {@link CometContext} associated with this 
     *         instance.
     */
    public CometContext getCometContext() {
        return cometContext;
    }
    
    
    /**
     * Set the {@link CometContext} used to invokeCometHandler {@link CometHandler}.
     * @param cometContext the {@link CometContext} used to invokeCometHandler {@link CometHandler}
     */
    public void setCometContext(CometContext cometContext) {
        this.cometContext = cometContext;
    }

    
    /**
     * Recycle this object.
     */
    @Override
    public void recycle(){
        super.recycle();
        key = null;
        cometContext = null;
        asyncReadSupported = false;
        if(cometInputStream != null) {
            cometInputStream.recycle();
        }
    }

    
    /**
     * Return the {@link CometSelector}
     * @return CometSelector the {@link CometSelector}
     */
    public CometSelector getCometSelector() {
        return cometSelector;
    }

    
    /**
     * Set the {@link CometSelector}
     * @param cometSelector the {@link CometSelector}
     */   
    public void setCometSelector(CometSelector cometSelector) {
        this.cometSelector = cometSelector;
    }
    
    
    /**
     * Return the time in milliseconds before this object was registered the 
     * {@link SelectionKey} on the {@link CometSelector}
     * @return long Return the time in milliseconds before this object was
     *         registered the {@link SelectionKey} on the
     *         {@link CometSelector}
     */
    public long getExpireTime() {
        return expireTime;
    }

    
    /**
     * Set the time in milliseconds before this object was registered the 
     * {@link SelectionKey} on the {@link CometSelector}
     * @param expireTime Return the time in milliseconds before this object was
     *                   registered the {@link SelectionKey} on the
     *                   {@link CometSelector}
     */   
    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }
    
    
    /**
     * Return the {@link CometSelector}'s {@link SelectionKey}.
     */
    public SelectionKey getCometKey() {
        return cometKey;
    }

    
    /**
     * Set the {@link CometSelector}'s {@link SelectionKey}.
     */
    public void setCometKey(SelectionKey cometKey) {
        this.cometKey = cometKey;
    }

    
    public boolean isAsyncReadSupported() {
        return asyncReadSupported;
    }

    
    public void setAsyncReadSupported(boolean asyncReadSupported) {
        this.asyncReadSupported = asyncReadSupported;
    }

    /**
     * Return true if cometContext.getExpirationDelay() != -1 
     * && timestamp - expireTime >= cometContext.getExpirationDelay();
     * @param timestamp
     * @return
     */
    protected boolean hasExpired(long timestamp){
        long expdelay = cometContext.getExpirationDelay();
        return expdelay != -1 && timestamp - expireTime >= expdelay;
    }

    public boolean isRecycle() {
        return recycle;
    }

    public AsyncProcessorTask getAsyncProcessorTask() {
        return asyncProcessorTask;
    }

    public void setAsyncProcessorTask(AsyncProcessorTask asyncProcessorTask) {
        this.asyncProcessorTask = asyncProcessorTask;
    }

    public CometHandler getCometHandler() {
        return cometHandler;
    }

    public void setCometHandler(CometHandler cometHandler) {
        this.cometHandler = cometHandler;
    }    
}
