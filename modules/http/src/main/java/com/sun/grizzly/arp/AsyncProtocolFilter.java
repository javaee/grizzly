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

package com.sun.grizzly.arp;

import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.util.StreamAlgorithm;
import com.sun.grizzly.http.TaskEvent;
import com.sun.grizzly.http.TaskListener;
import com.sun.grizzly.http.algorithms.NoParsingAlgorithm;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.ByteBufferInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ProtocolFilter that allow asynchronous http request processing.
 *
 * @author Jeanfrancois Arcand
 */
public class AsyncProtocolFilter implements ProtocolFilter,TaskListener{
    
    /**
     * The <code>StreamAlgorithm</code> classes.
     */
    private Class algorithmClass;
    
    
    /**
     * The current TCP port.
     */
    private int port;
    
    
    private final static Logger logger = SelectorThread.logger();
    
    
    /**
     * When Asynchronous Request Processing is enabled, the byteBuffer
     * per thread mechanism cannot be used as the execution will
     * free the thread hence the ByteBuffer will be re-used.
     */
    private ConcurrentLinkedQueue<ByteBufferInputStream> byteBufferStreams
            = new ConcurrentLinkedQueue<ByteBufferInputStream>();
    
    
    /**
     * Default size for ByteBuffer.
     */
    protected int bbSize = 4096;
    
    
    public AsyncProtocolFilter(Class algorithmClass,int port) {
        this.algorithmClass = algorithmClass;
        this.port = port;
    }
    
    /**
     * Execute a unit of processing work to be performed. This ProtocolFilter
     * may either complete the required processing and return false,
     * or delegate remaining processing to the next ProtocolFilter in a
     * ProtocolChain containing this ProtocolFilter by returning true.
     */
    public boolean execute(Context ctx) throws IOException{
        HttpWorkerThread workerThread = ((HttpWorkerThread)Thread.currentThread());
        
        StreamAlgorithm streamAlgorithm =
                workerThread.getStreamAlgorithm();
        if (streamAlgorithm == null){
            try{
                streamAlgorithm = (StreamAlgorithm)algorithmClass
                        .newInstance();
            } catch (InstantiationException ex){
                logger.log(Level.WARNING,
                        "Unable to instantiate Algorithm: "+ algorithmClass.getName());
            } catch (IllegalAccessException ex){
                logger.log(Level.WARNING,
                        "Unable to instantiate Algorithm: " + algorithmClass.getName());
            } finally {
                if ( streamAlgorithm == null){
                    streamAlgorithm = new NoParsingAlgorithm();
                }
            }
            streamAlgorithm.setPort(port);
            workerThread.setStreamAlgorithm(streamAlgorithm);
        }
        
        SelectorThread selectorThread = SelectorThread.getSelector(port);
        bbSize = SelectorThread.getSelector(port).getMaxHttpHeaderSize();
        
        ByteBufferInputStream inputStream = byteBufferStreams.poll();
        if (inputStream == null) {
            inputStream = createByteBufferInputStream();
        }
        configureByteBufferInputStream(inputStream, ctx, workerThread);
        
        SelectionKey key = ctx.getSelectionKey();
        SocketChannel socketChannel = (SocketChannel) key.channel();
        streamAlgorithm.setSocketChannel(socketChannel);
        
        /**
         * Switch ByteBuffer since we are asynchronous.
         */
        ByteBuffer nextBuffer = inputStream.getByteBuffer();
        nextBuffer.clear();
        ByteBuffer byteBuffer = workerThread.getByteBuffer();
        workerThread.setByteBuffer(nextBuffer);
        inputStream.setByteBuffer(byteBuffer);
        
        byteBuffer = streamAlgorithm.preParse(byteBuffer);
        if (streamAlgorithm.parse(byteBuffer)){
            ProcessorTask processor =
                    selectorThread.getProcessorTask();
            configureProcessorTask(processor, ctx, workerThread, 
                    streamAlgorithm.getHandler(), inputStream);
            
            try{
                selectorThread.getAsyncHandler().handle(processor);
            } catch (Throwable ex){
                logger.log(Level.INFO,"Processor exception",ex);
                ctx.setKeyRegistrationState(
                        Context.KeyRegistrationState.CANCEL);
                return false;
            }
        }
        ctx.setKeyRegistrationState(Context.KeyRegistrationState.NONE);
        
        // Last filter.
        return true;
    }
    
    
    /**
     * Called when the Asynchronous Request Processing is resuming.
     */
    public void taskEvent(TaskEvent event){
        if (event.getStatus() == TaskEvent.COMPLETED
                || event.getStatus() == TaskEvent.ERROR){
            ProcessorTask processor = (ProcessorTask) event.attachement();
            ByteBufferInputStream is = (ByteBufferInputStream) processor.getInputStream();
            is.getByteBuffer().clear();
            byteBufferStreams.offer(is);
            
            SelectorThread selectorThread = processor.getSelectorThread();
            if (processor.isKeepAlive() && !processor.isError()){
                selectorThread.registerKey(processor.getSelectionKey());
            } else {
                selectorThread.cancelKey(processor.getSelectionKey());
            }
            processor.recycle();
            selectorThread.returnTask(processor);
        }
    }
    
    /**
     * Execute any cleanup activities, such as releasing resources that were
     * acquired during the execute() method of this ProtocolFilter instance.
     */
    public boolean postExecute(Context ctx) throws IOException{
        return true;
    }
    
    /**
     * Configure <code>SSLProcessorTask</code>.
     */
    protected void configureProcessorTask(ProcessorTask processorTask,
            Context context, HttpWorkerThread workerThread,
            Interceptor handler, InputStream inputStream) {
        SelectionKey key = context.getSelectionKey();
        
        processorTask.setSelectionKey(key);
        processorTask.setSocket(((SocketChannel) key.channel()).socket());
        processorTask.addTaskListener(this);
        processorTask.setInputStream(inputStream);
        if (processorTask.getHandler() == null){
            processorTask.setHandler(handler);
        }
        
    }
    
    /**
     * Configure <code>ByteBufferInputStream</code>
     * @param <code>ByteBufferInputStream</code>
     */
    protected void configureByteBufferInputStream(
            ByteBufferInputStream inputStream, Context context, 
            HttpWorkerThread workerThread) {
        inputStream.setSelectionKey(context.getSelectionKey());
        inputStream.setSecure(false);
    }
    
    /**
     * Is <code>ProtocolFilter</code> secured
     * @return is <code>ProtocolFilter</code> secured
     */
    protected boolean isSecure() {
        return false;
    }

    /**
     * Creates <code>ByteBufferInputStream</code>
     */
    protected ByteBufferInputStream createByteBufferInputStream() {
        return new ByteBufferInputStream(
                    ByteBufferFactory.allocateView(bbSize,false));
    }
}
