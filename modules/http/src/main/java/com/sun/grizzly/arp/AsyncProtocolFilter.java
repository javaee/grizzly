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
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.LinkedTransferQueue;
import com.sun.grizzly.util.SelectionKeyAttachment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ProtocolFilter that allow asynchronous http request processing.
 *
 * @author Jeanfrancois Arcand
 */
public class AsyncProtocolFilter implements ProtocolFilter,TaskListener{
    
    /**
     * The {@link StreamAlgorithm} classes.
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
    private LinkedTransferQueue<InputReader> byteBufferStreams
            = new LinkedTransferQueue<InputReader>();
    
    
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
        
        setSelectionKeyTimeout(ctx.getSelectionKey(), Long.MAX_VALUE);
        
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
        
        InputReader inputStream = byteBufferStreams.poll();
        if (inputStream == null) {
            inputStream = createByteBufferInputStream();
        }
        configureByteBufferInputStream(inputStream, ctx, workerThread);
        
        SelectionKey key = ctx.getSelectionKey();
        SocketChannel socketChannel = (SocketChannel) key.channel();
        streamAlgorithm.setChannel(socketChannel);
        
        /**
         * Switch ByteBuffer since we are asynchronous.
         */
        ByteBuffer nextBuffer = inputStream.getByteBuffer();
        nextBuffer.clear();
        ByteBuffer byteBuffer = workerThread.getByteBuffer();
        workerThread.setByteBuffer(nextBuffer);
        inputStream.setByteBuffer(byteBuffer);
        
        byteBuffer = streamAlgorithm.preParse(byteBuffer);
        ctx.setKeyRegistrationState(Context.KeyRegistrationState.NONE);

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
                                     
            // Should never happens.
            if (processor.getSelectionKey() == null){
                logger.log(Level.WARNING,"AsyncProtocolFilter invalid state.");
                return;
            }          
            
            InputReader is = (InputReader) processor.getInputStream();
            is.getByteBuffer().clear();
            byteBufferStreams.offer(is);
           
            SelectorThread selectorThread = processor.getSelectorThread();
            if (processor.isKeepAlive() && !processor.isError()){
                setSelectionKeyTimeout(processor.getSelectionKey(), Long.MIN_VALUE);

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
     * Configure {@link SSLProcessorTask}.
     */
    protected void configureProcessorTask(ProcessorTask processorTask,
            Context context, HttpWorkerThread workerThread,
            Interceptor handler, InputStream inputStream) {
        SelectionKey key = context.getSelectionKey();
        
        processorTask.setSelectionKey(key);
        processorTask.setSelectorHandler(context.getSelectorHandler());
        processorTask.setSocket(((SocketChannel) key.channel()).socket());
        processorTask.setTaskListener(this);
        processorTask.setInputStream(inputStream);
        processorTask.setHandler(handler);      
    }
    
    /**
     * Configure {@link InputReader}
     * @param {@link InputReader}
     */
    protected void configureByteBufferInputStream(
            InputReader inputStream, Context context, 
            HttpWorkerThread workerThread) {
        inputStream.setSelectionKey(context.getSelectionKey());
        inputStream.setSecure(false);
    }
    
    
    /**
     * Is {@link ProtocolFilter} secured
     * @return is {@link ProtocolFilter} secured
     */
    protected boolean isSecure() {
        return false;
    }

    
    /**
     * Creates {@link InputReader}
     */
    protected InputReader createByteBufferInputStream() {
        return new InputReader(
                    ByteBufferFactory.allocateView(bbSize,false));
    }

    private void setSelectionKeyTimeout(SelectionKey selectionKey,
            long timeout) {
        Object attachment = selectionKey.attachment();
        if (attachment == null){
            selectionKey.attach(timeout);
        } else if (attachment instanceof SelectionKeyAttachment) {
            ((SelectionKeyAttachment) attachment).setTimeout(timeout);
        }
    }
}
