/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import com.sun.grizzly.filter.ReadFilter;
import com.sun.grizzly.http.algorithms.NoParsingAlgorithm;
import com.sun.grizzly.rcm.ResourceAllocationFilter;
import com.sun.grizzly.tcp.SuspendResponseUtils;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.SelectionKeyAttachment;
import com.sun.grizzly.util.StreamAlgorithm;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default ProtocolFilter implementation, that allows HTTP request processing.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultProtocolFilter implements ProtocolFilter {
    private static final PostProcessor POST_PROCESSOR = new PostProcessor();
    
    /**
     * The {@link StreamAlgorithm} class.
     */
    protected Class algorithmClass;
    
    /**
     * The current TCP port.
     */
    protected int port;

    /**
     * The current address.
     */
    protected InetAddress address;
    
    /**
     * Logger
     */
    protected final static Logger logger = SelectorThread.logger();


    /**
     * <p>
     * Invokes {@link com.sun.grizzly.http.DefaultProtocolFilter#DefaultProtocolFilter(Class, java.net.InetAddress, int)}.
     * </p>
     *
     * @param algorithmClass the {@link StreamAlgorithm}
     * @param port the network port to associate with this filter
     *
     * @deprecated call {@link DefaultProtocolFilter#DefaultProtocolFilter(Class, java.net.InetAddress, int)}
     */
    @Deprecated
    public DefaultProtocolFilter(Class algorithmClass, int port) {
        this.algorithmClass = algorithmClass;
        this.port = port;
    }


    /**
     * <p>
     * Constructs a new <code>DefaultProtocolFilter</code>/
     * </p>
     *
     * @param algorithmClass the {@link StreamAlgorithm}
     * @param address the network address to associate with this filter
     * @param port the network port to associate with this filter
     */
    public DefaultProtocolFilter(Class algorithmClass,
                                 InetAddress address,
                                 int port) {

        this.algorithmClass = algorithmClass;
        this.address = address;
        this.port = port;

    }


    
    public boolean execute(Context ctx) throws IOException {
        if (Boolean.TRUE.equals(ctx.getAttribute(ReadFilter.DELAYED_CLOSE_NOTIFICATION))) {
            ctx.setKeyRegistrationState(Context.KeyRegistrationState.CANCEL);
            return false;
        }

        HttpWorkerThread workerThread =
                ((HttpWorkerThread)Thread.currentThread());

        SelectorThread selectorThread =
                ((HttpSelectorHandler) ctx.getSelectorHandler()).getSelectorThread();
        
        // (1) Get the ByteBuffer from the Thread. Never null
        ByteBuffer byteBuffer = workerThread.getByteBuffer();
        
        // (2) Get the InputReader from the Thread.
        InputReader inputStream =
                workerThread.getInputStream();
        
        if (inputStream == null){
            inputStream = new InputReader();
            workerThread.setInputStream(inputStream);
        }
        
        // (3) Get the streamAlgorithm.
        StreamAlgorithm streamAlgorithm =
                workerThread.getStreamAlgorithm();
        
        if (streamAlgorithm == null){
            try{
                streamAlgorithm = (StreamAlgorithm)algorithmClass
                        .newInstance();
            } catch (InstantiationException ex){
                if (logger.isLoggable(Level.WARNING)) {
                    logger.warning(LogMessages.WARNING_GRIZZLY_HTTP_DPF_STREAM_ALGORITHM_INIT_ERROR(algorithmClass.getName()));
                }
            } catch (IllegalAccessException ex){
                if (logger.isLoggable(Level.WARNING)) {
                    logger.warning(LogMessages.WARNING_GRIZZLY_HTTP_DPF_STREAM_ALGORITHM_INIT_ERROR(algorithmClass.getName()));
                }
            } finally {
                if ( streamAlgorithm == null){
                    streamAlgorithm = new NoParsingAlgorithm();
                }
            }
            streamAlgorithm.setPort(port);
        } else {
            workerThread.setStreamAlgorithm(null);
        }

        SelectionKey key = ctx.getSelectionKey();
        configureInputBuffer(inputStream, ctx, workerThread);
        SocketChannel socketChannel =
                (SocketChannel)key.channel();
        streamAlgorithm.setChannel(socketChannel);
        
        byteBuffer = streamAlgorithm.preParse(byteBuffer);
        boolean keepAlive = false;
        
        ProcessorTask processorTask = workerThread.getProcessorTask();   
        if (streamAlgorithm.parse(byteBuffer)){
 
            if (processorTask == null){
                processorTask = selectorThread.getProcessorTask();
                workerThread.setProcessorTask(processorTask);
            }

            KeepAliveThreadAttachment k = (KeepAliveThreadAttachment)
                    workerThread.getAttachment();
            k.setTimeout(System.currentTimeMillis());  // enable transaction timeout
            k.setIdleTimeoutDelay(SelectionKeyAttachment.UNLIMITED_TIMEOUT); // disable idleTimeout
            KeepAliveStats ks = selectorThread.getKeepAliveStats();
            k.setKeepAliveStats(ks);
            
            // Bind the Attachment to the SelectionKey
            ctx.getSelectionKey().attach(k);

            if (selectorThread.getKeepAliveTimeoutInSeconds() == 0){
                processorTask.setDropConnection(true);
            } else if (selectorThread.getMaxKeepAliveRequests() != -1){
                int count = k.getKeepAliveCount();
                // If count == 0 - then we don't know if connection is keep-alive or not
                if (count > 0 && count >= selectorThread.getMaxKeepAliveRequests()) {
                    if (ks.isEnabled()){
                        ks.incrementCountRefusals();
                    }
                    processorTask.setDropConnection(true);
                } else {
                    processorTask.setDropConnection(false);
                }
            }
                       
            configureProcessorTask(processorTask, ctx, streamAlgorithm);

            try {
                processorTask.setAsyncPostProcessor(POST_PROCESSOR);
                keepAlive = processorTask.process(inputStream, null);

                final boolean isSuspended = SuspendResponseUtils.removeSuspendedInCurrentThread();
                if (processorTask != null && !processorTask.isError() && isSuspended) { // Process suspended HTTP request
                    // Detatch anything associated with the Thread.
                    workerThread.setInputStream(new InputReader());
                    workerThread.setByteBuffer(null);
                    workerThread.setProcessorTask(null);
                    workerThread.setInputBB(null);

                    ctx.setKeyRegistrationState(
                            Context.KeyRegistrationState.NONE);
                    return true;
                }
            } catch (Throwable ex) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE,
                            LogMessages.SEVERE_GRIZZLY_HTTP_DPF_PROCESSOR_TASK_ERROR(),
                            ex);
                }
                keepAlive = false;
            }
        } else {
            if (ctx.getProtocol() == Controller.Protocol.TCP){
                ctx.getSelectionKey().attach(null);
            } else {
                workerThread.getAttachment().setTimeout(SelectionKeyAttachment.UNLIMITED_TIMEOUT);
            }
            keepAlive = true;
        }
        
        streamAlgorithm.postParse(byteBuffer);
        
        inputStream.recycle();
        
        if (processorTask != null){
            processorTask.recycle();
        }
        
        if (keepAlive) {
            final Object attachement = ctx.getSelectionKey().attachment();
            if (attachement instanceof SelectionKeyAttachment) {
                final SelectionKeyAttachment selectionKeyAttachment =
                        (SelectionKeyAttachment) attachement;
                selectionKeyAttachment.setTimeout(System.currentTimeMillis());
                selectionKeyAttachment.setIdleTimeoutDelay(
                        SelectionKeyAttachment.UNSET_TIMEOUT);
            }
            
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.REGISTER);
        } else {
            if (selectorThread.isAsyncHttpWriteEnabled()) {
                ctx.setKeyRegistrationState(
                        Context.KeyRegistrationState.NONE);
                flushAsyncWriteQueueAndClose(ctx.getSelectorHandler(), key);
            } else {
                ctx.setKeyRegistrationState(
                        Context.KeyRegistrationState.CANCEL);
            }
        }
        
        byteBuffer.clear();
        if (selectorThread.isRcmSupported()){
            ctx.removeAttribute(ResourceAllocationFilter.BYTEBUFFER_INPUTSTREAM);
            ctx.removeAttribute(ResourceAllocationFilter.BYTE_BUFFER);
            ctx.removeAttribute(ResourceAllocationFilter.INVOKE_NEXT);
        }

        if (processorTask != null && processorTask.isSkipPostExecute()){
            ctx.setKeyRegistrationState(Context.KeyRegistrationState.NONE);
        }
        // Last filter.
        return true;
    }
    
    public boolean postExecute(Context ctx) throws IOException {
        return true;
    }
    
    /**
     * Configure {@link ProcessorTask}.
     */
    protected void configureProcessorTask(ProcessorTask processorTask, 
            Context context, StreamAlgorithm streamAlgorithm) {
        SelectionKey key = context.getSelectionKey();
        
        processorTask.setSelectorHandler(context.getSelectorHandler());
        processorTask.setSelectionKey(key);
        processorTask.setSocket(((SocketChannel) key.channel()).socket());
        
        if (processorTask.getStreamAlgorithm() == null){
            processorTask.setStreamAlgorithm(streamAlgorithm);
        }
    }

    /**
     * Configure {@link InputReader}.
     */
    protected void configureInputBuffer(InputReader inputStream, Context context, 
            HttpWorkerThread workerThread) {
        inputStream.recycle();
        inputStream.setSelectionKey(context.getSelectionKey());
        inputStream.setByteBuffer(workerThread.getByteBuffer());
        inputStream.setSecure(isSecure());
    }
    
    /**
     * Is {@link ProtocolFilter} secured
     * @return is {@link ProtocolFilter} secured
     */
    protected boolean isSecure() {
        return false;
    }

    private static final ByteBuffer NULL_BUFFER = ByteBuffer.allocate(0);

    protected static void flushAsyncWriteQueueAndClose(
            final SelectorHandler selectorHandler, final SelectionKey key) {

        try {
            selectorHandler.getAsyncQueueWriter().write(key, NULL_BUFFER, new AsyncWriteCallbackHandler() {

                public void onWriteCompleted(SelectionKey key, AsyncQueueWriteUnit writtenRecord) {
                    close();
                }

                public void onException(Exception exception, SelectionKey key,
                        ByteBuffer buffer, Queue<AsyncQueueWriteUnit> remainingQueue) {
                    close();
                }

                private void close() {
                    selectorHandler.addPendingKeyCancel(key);
                }
            });
        } catch (Exception e) {
            selectorHandler.addPendingKeyCancel(key);
        }
    }

    private static final class PostProcessor implements ProcessorTask.PostProcessor {

        public boolean postProcess(ProcessorTask processorTask) {
            final boolean keepAlive = processorTask.isKeepAlive();
            final SelectorHandler selectorHandler = processorTask.getSelectorHandler();
            final SelectionKey selectionKey = processorTask.getSelectionKey();
            final SelectorThread selectorThread = processorTask.getSelectorThread();

            if (keepAlive) {                
                selectorHandler.register(selectionKey,
                        SelectionKey.OP_READ);
            } else {
                if (selectorThread.isAsyncHttpWriteEnabled()) {
                    flushAsyncWriteQueueAndClose(selectorHandler, selectionKey);
                } else {
                    selectorHandler.addPendingKeyCancel(selectionKey);
                }
            }

            ((InputReader) processorTask.getInputStream()).recycle();
            processorTask.recycle();
            selectorThread.returnTask(processorTask);
            return true;
        }

    }
}
