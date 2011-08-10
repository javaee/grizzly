/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.Context;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.http.DefaultProtocolFilter;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.util.StreamAlgorithm;
import com.sun.grizzly.http.TaskEvent;
import com.sun.grizzly.http.TaskListener;
import com.sun.grizzly.http.algorithms.NoParsingAlgorithm;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.SelectionKeyAttachment;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.logging.Level;

/**
 * A ProtocolFilter that allow asynchronous http request processing.
 *
 * @author Jeanfrancois Arcand
 */
public class AsyncProtocolFilter extends DefaultProtocolFilter implements TaskListener{
    
    /**
     * When Asynchronous Request Processing is enabled, the byteBuffer
     * per thread mechanism cannot be used as the execution will
     * free the thread hence the ByteBuffer will be re-used.
     */
    private final Queue<InputReader> byteBufferStreams
            = DataStructures.getCLQinstance(InputReader.class);
    
    
    /**
     * Default size for ByteBuffer.
     */
    protected int bbSize = 4096;
    
    
    /**
     * {@link Interceptor} used when determining if a request must be handled
     * directly inside this {@link com.sun.grizzly.ProtocolFilter}.
     */
    protected Interceptor<ByteBuffer, SocketChannel> interceptor;


    /**
     * <p>
     * Invokes {@link com.sun.grizzly.arp.AsyncProtocolFilter#AsyncProtocolFilter(Class, java.net.InetAddress, int)}
     * with a <code>null</code> {@link InetAddress}.
     * </p>
     *
     * @param algorithmClass the {@link StreamAlgorithm}
     * @param port the network port to associate with this filter
     *
     * @deprecated use {@link com.sun.grizzly.arp.AsyncProtocolFilter#AsyncProtocolFilter(Class, java.net.InetAddress, int)}
     */
    @Deprecated
    public AsyncProtocolFilter(Class algorithmClass, int port) {
        super(algorithmClass, null, port);
    }

    /**
     * Constructs a new <code>AsyncProtocolFilter</code>
     * .
     * @param algorithmClass the {@link StreamAlgorithm}
     * @param address the network address to associate with this filter
     * @param port the network port to associate with this filter
     */
    public AsyncProtocolFilter(Class algorithmClass,
                               InetAddress address,
                               int port) {

        super(algorithmClass, address, port);

    }
    
    /**
     * Execute a unit of processing work to be performed. This ProtocolFilter
     * may either complete the required processing and return false,
     * or delegate remaining processing to the next ProtocolFilter in a
     * ProtocolChain containing this ProtocolFilter by returning true.
     */
    @Override
    public boolean execute(Context ctx) throws IOException{
        final SelectorThread selectorThread = SelectorThread.getSelector(address, port);
        final SelectionKey key = ctx.getSelectionKey();
        
        if (Boolean.TRUE.equals(ctx.getAttribute(ReadFilter.DELAYED_CLOSE_NOTIFICATION))) {
            if (selectorThread.isAsyncHttpWriteEnabled()) {
                ctx.setKeyRegistrationState(Context.KeyRegistrationState.NONE);
                flushAsyncWriteQueueAndClose(ctx.getSelectorHandler(), key);
            } else {
                ctx.setKeyRegistrationState(Context.KeyRegistrationState.CANCEL);
            }
            return false;
        }

        HttpWorkerThread workerThread = ((HttpWorkerThread)Thread.currentThread());         
        ByteBuffer byteBuffer = workerThread.getByteBuffer();        

        // Intercept the request and delegate the processing to the parent if
        // true.
        if (interceptor != null
                && interceptor.handle(byteBuffer, Interceptor.REQUEST_BUFFERED)
                    == Interceptor.BREAK){
            return super.execute(ctx);
        }

  
        StreamAlgorithm streamAlgorithm = workerThread.getStreamAlgorithm();
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
        } else {
            workerThread.setStreamAlgorithm(null);
        }
        
                
        bbSize = selectorThread.getMaxHttpHeaderSize();
        
        InputReader inputStream = byteBufferStreams.poll();
        if (inputStream == null) {
            inputStream = createInputReader();
        }
        configureInputBuffer(inputStream, ctx, workerThread);
                
        SocketChannel socketChannel = (SocketChannel) key.channel();
        streamAlgorithm.setChannel(socketChannel);
        
        /**
         * Switch ByteBuffer since we are asynchronous.
         */
        ByteBuffer nextBuffer = inputStream.getByteBuffer();
        nextBuffer.clear();

        workerThread.setByteBuffer(nextBuffer);
        inputStream.setByteBuffer(byteBuffer);
        
        byteBuffer = streamAlgorithm.preParse(byteBuffer);
        ctx.setKeyRegistrationState(Context.KeyRegistrationState.NONE);

        if (streamAlgorithm.parse(byteBuffer)) {
            ProcessorTask processor = selectorThread.getProcessorTask();
            configureProcessorTask(processor, ctx, streamAlgorithm, inputStream);
            try {
                selectorThread.getAsyncHandler().handle(processor);
            } catch (Throwable ex) {
                logger.log(Level.INFO, "Processor exception", ex);
                ctx.setKeyRegistrationState(Context.KeyRegistrationState.CANCEL);
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
            final ProcessorTask processor = (ProcessorTask) event.attachement();
            final SelectionKey processorSelectionKey = processor.getSelectionKey();
            
            // Should never happens.
            if (processorSelectionKey == null){
                logger.log(Level.WARNING,"AsyncProtocolFilter invalid state.");
                return;
            }          
            
            InputReader is = (InputReader) processor.getInputStream();
            is.getByteBuffer().clear();
            byteBufferStreams.offer(is);            

            final SelectorThread selectorThread = processor.getSelectorThread();
            final SelectorHandler selectorHandler = processor.getSelectorHandler();
            
            boolean cancelkey = processor.getAptCancelKey() || processor.isError()
                    || !processor.isKeepAlive();
            try {
                if (!cancelkey) {
                    if (processor.getReRegisterSelectionKey()) {
                        setSelectionKeyTimeout(processorSelectionKey, SelectionKeyAttachment.UNLIMITED_TIMEOUT);
                        selectorHandler.register(processorSelectionKey, SelectionKey.OP_READ);
                    }
                } else {
                    if (selectorThread.isAsyncHttpWriteEnabled()) {
                        flushAsyncWriteQueueAndClose(selectorHandler, processorSelectionKey);
                    } else {
                        selectorHandler.getSelectionKeyHandler().cancel(processorSelectionKey);
                    }
                }
            } finally {
                processor.recycle();
                selectorThread.returnTask(processor);
            }
        }
    }    
    
    /**
     * Configure {@link com.sun.grizzly.ssl.SSLProcessorTask}.
     */
    protected void configureProcessorTask(ProcessorTask processorTask,
            Context context, StreamAlgorithm streamAlgorithm, InputStream inputStream) {
        SelectionKey key = context.getSelectionKey();

        processorTask.setSelectionKey(key);
        processorTask.setSelectorHandler(context.getSelectorHandler());
        processorTask.setSocket(((SocketChannel) key.channel()).socket());
        processorTask.setTaskListener(this);
        processorTask.setInputStream(inputStream);
        processorTask.setStreamAlgorithm(streamAlgorithm);
    }    

    /**
     * Configure {@link InputReader}.
     */
    @Override
    protected void configureInputBuffer(InputReader inputStream, Context context, 
            HttpWorkerThread workerThread) {
        // Save the buffer before recycle
        final ByteBuffer associatedBuffer = inputStream.getByteBuffer();
        inputStream.recycle();

        // Restore the buffer
        inputStream.setByteBuffer(associatedBuffer);
        inputStream.setSelectionKey(context.getSelectionKey());
        inputStream.setSecure(isSecure());
    }
    
    /**
     * Creates {@link InputReader}
     */
    protected InputReader createInputReader() {
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
    
    
    /**
     * Return the current {@link Interceptor}
     * @return  the current {@link Interceptor}
     */
    public Interceptor<ByteBuffer, SocketChannel> getInterceptor() {
        return interceptor;
    }

    /**
     * Set the {@link Interceptor} used to decide if the request must be handled 
     * by this {@link com.sun.grizzly.ProtocolFilter} directly.
     * 
     * @param interceptor the {@link Interceptor}
     */
    public void setInterceptor(Interceptor<ByteBuffer, SocketChannel> interceptor) {
        this.interceptor = interceptor;
    }
}
