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

package com.sun.grizzly.http;

import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.http.algorithms.NoParsingAlgorithm;
import com.sun.grizzly.rcm.ResourceAllocationFilter;
import com.sun.grizzly.util.ByteBufferInputStream;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.util.StreamAlgorithm;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default ProtocolFilter implementation, that allows http request processing.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultProtocolFilter implements ProtocolFilter {
    
    /**
     * The <code>StreamAlgorithm</code> classes.
     */
    private Class algorithmClass;
    
    /**
     * The current TCP port.
     */
    private int port;
    
    /**
     * Logger
     */
    protected final static Logger logger = SelectorThread.logger();
    
    
    public DefaultProtocolFilter(Class algorithmClass, int port) {
        this.algorithmClass = algorithmClass;
        this.port = port;
    }
    
    public boolean execute(Context ctx) throws IOException {
        HttpWorkerThread workerThread =
                ((HttpWorkerThread)Thread.currentThread());
        
        SelectorThread selectorThread = SelectorThread.getSelector(port);
        
        // (1) Get the ByteBuffer from the Thread. Never null
        ByteBuffer byteBuffer = workerThread.getByteBuffer();
        
        // (2) Get the ByteBufferInputStream from the Thread.
        ByteBufferInputStream inputStream =
                workerThread.getInputStream();
        
        if (inputStream == null){
            inputStream = new ByteBufferInputStream();
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
        
        SelectionKey key = ctx.getSelectionKey();
        configureByteBufferInputStream(inputStream, ctx, workerThread);
        SocketChannel socketChannel =
                (SocketChannel)key.channel();
        streamAlgorithm.setSocketChannel(socketChannel);
        
        byteBuffer = streamAlgorithm.preParse(byteBuffer);
        boolean keepAlive = false;
        
        if (streamAlgorithm.parse(byteBuffer)){
            ProcessorTask processorTask
                    = workerThread.getProcessorTask();
            if (processorTask == null){
                processorTask = selectorThread.getProcessorTask();
                workerThread.setProcessorTask(processorTask);
            }
            
            configureProcessorTask(processorTask, ctx, workerThread, 
                    streamAlgorithm.getHandler());
            
            try{
                keepAlive = processorTask.process(inputStream,null);
            } catch (Throwable ex){
                logger.log(Level.INFO,"ProcessorTask exception", ex);
                keepAlive = false;
            }
            processorTask.recycle();
        } else {
            keepAlive = true;
        }
        
        if (keepAlive){
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.REGISTER);
        } else {
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.CANCEL);
        }
        streamAlgorithm.postParse(byteBuffer);
        byteBuffer.clear();
        if (selectorThread.isRcmSupported()){
            ctx.removeAttribute(ResourceAllocationFilter.BYTEBUFFER_INPUTSTREAM);
            ctx.removeAttribute(ResourceAllocationFilter.BYTE_BUFFER);
            ctx.removeAttribute(ResourceAllocationFilter.INVOKE_NEXT);
        }
        // Last filter.
        return true;
    }
    
    public boolean postExecute(Context ctx) throws IOException {
        return true;
    }
    
    /**
     * Configure <code>ProcessorTask</code>.
     */
    protected void configureProcessorTask(ProcessorTask processorTask, 
            Context context, HttpWorkerThread workerThread, 
            Interceptor handler) {
        SelectionKey key = context.getSelectionKey();
        
        processorTask.setSelectionKey(key);
        processorTask.setSocket(((SocketChannel) key.channel()).socket());
        
        if (processorTask.getHandler() == null){
            processorTask.setHandler(handler);
        }
    }

    /**
     * Configure <code>ByteBufferInputStream</code>.
     */
    protected void configureByteBufferInputStream(
            ByteBufferInputStream inputStream, Context context, 
            HttpWorkerThread workerThread) {
        inputStream.setSelectionKey(context.getSelectionKey());
        inputStream.setByteBuffer(workerThread.getByteBuffer());
        inputStream.setSecure(isSecure());
    }
    
    /**
     * Is <code>ProtocolFilter</code> secured
     * @return is <code>ProtocolFilter</code> secured
     */
    protected boolean isSecure() {
        return false;
    }
}
