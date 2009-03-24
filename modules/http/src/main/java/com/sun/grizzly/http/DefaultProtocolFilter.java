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

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.http.algorithms.NoParsingAlgorithm;
import com.sun.grizzly.rcm.ResourceAllocationFilter;
import com.sun.grizzly.util.InputReader;
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
     * The {@link StreamAlgorithm} classes.
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
            k.setTimeout(System.currentTimeMillis());
            KeepAliveStats ks = selectorThread.getKeepAliveStats();
            k.setKeepAliveStats(ks);
            
            // Bind the Attachment to the SelectionKey
            ctx.getSelectionKey().attach(k);
                    
            int count = k.increaseKeepAliveCount();
            if (count > selectorThread.getMaxKeepAliveRequests() && ks != null) {
                ks.incrementCountRefusals();
                processorTask.setDropConnection(true);
            } else {
                processorTask.setDropConnection(false);
            }
                       
            configureProcessorTask(processorTask, ctx, workerThread, 
                    streamAlgorithm.getHandler());
            
            try{
                keepAlive = processorTask.process(inputStream,null);
            } catch (Throwable ex){
                logger.log(Level.INFO,"ProcessorTask exception", ex);
                keepAlive = false;
            }
        } else {
            if (ctx.getProtocol() == Controller.Protocol.TCP){
                ctx.getSelectionKey().attach(null);
            } else {
                workerThread.getAttachment().setTimeout(Long.MIN_VALUE);
            }
            keepAlive = true;
        }
        
        Object ra = workerThread.getAttachment().getAttribute("suspend");
        if (ra != null){
            // Detatch anything associated with the Thread.
            workerThread.setInputStream(new InputReader());
            workerThread.setByteBuffer(null);
            workerThread.setProcessorTask(null);
                       
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.REGISTER);
            return true;
        }
        
        if (processorTask != null){
            processorTask.recycle();
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
     * Configure {@link ProcessorTask}.
     */
    protected void configureProcessorTask(ProcessorTask processorTask, 
            Context context, HttpWorkerThread workerThread, 
            Interceptor handler) {
        SelectionKey key = context.getSelectionKey();
        
        processorTask.setSelectorHandler(context.getSelectorHandler());
        processorTask.setSelectionKey(key);
        processorTask.setSocket(((SocketChannel) key.channel()).socket());
        
        if (processorTask.getHandler() == null){
            processorTask.setHandler(handler);
        }
    }

    /**
     * Configure {@link InputReader}.
     */
    protected void configureByteBufferInputStream(
            InputReader inputStream, Context context, 
            HttpWorkerThread workerThread) {
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
}
