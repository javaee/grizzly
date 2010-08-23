/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.aio.http;

import com.sun.grizzly.Context;
import com.sun.grizzly.aio.AIOContext;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.rcm.ResourceAllocationFilter;
import com.sun.grizzly.aio.util.AIOInputReader;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.util.StreamAlgorithm;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default ProtocolFilter implementation, that allows http request processing.
 *
 * @author Jeanfrancois Arcand
 */
public class AIOHttpParser implements ProtocolFilter {
    
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
    protected final static Logger logger = AsyncAcceptorThread.logger();
    
    
    public AIOHttpParser(Class algorithmClass, int port) {
        this.algorithmClass = algorithmClass;
        this.port = port;
    }
    
    public boolean execute(Context context) throws IOException {
        AIOContext ctx = (AIOContext)context;
        HttpWorkerThread workerThread =
                ((HttpWorkerThread)Thread.currentThread());

        SelectorThread selectorThread = SelectorThread.getSelector(port);
        ByteBuffer byteBuffer = ctx.getByteBuffer();
        
        if (!(workerThread.getInputStream() instanceof AIOInputReader)){
            workerThread.setInputStream(new AIOInputReader());
        }
        workerThread.getInputStream().setByteBuffer(byteBuffer);
        
        StreamAlgorithm streamAlgorithm =
                workerThread.getStreamAlgorithm();
        
        if (streamAlgorithm == null){
            streamAlgorithm = new AIOStaticStreamAlgorithm();
            streamAlgorithm.setPort(port);
            workerThread.setStreamAlgorithm(streamAlgorithm);
        }
        streamAlgorithm.setChannel(((AIOContext)context).getChannel());
        
        byteBuffer.flip();
        boolean keepAlive = false;

        ProcessorTask processorTask
                = workerThread.getProcessorTask();
        if (processorTask == null){
            processorTask = selectorThread.getProcessorTask();
            workerThread.setProcessorTask(processorTask);
        }

        configureProcessorTask(processorTask, ctx, workerThread, 
                streamAlgorithm.getHandler());

        try{
            keepAlive = processorTask.process(workerThread.getInputStream(),null);
        } catch (Throwable ex){
            logger.log(Level.INFO,"ProcessorTask exception", ex);
            keepAlive = false;
        }
        processorTask.recycle();

        ctx.setKeepAlive(keepAlive);
        
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
        
        if (key == null){
            ((AIOProcessorTask)processorTask).setChannel(
                    ((AIOContext)context).getChannel());
        }
        
        if (processorTask.getHandler() == null){
            processorTask.setHandler(handler);
        }
    }

    /**
     * Configure {@link AIOInputReader}.
     */
    protected void configureByteBufferInputStream(
            AIOInputReader inputReader, Context context, 
            HttpWorkerThread workerThread) {
        inputReader.setSelectionKey(context.getSelectionKey());
        inputReader.setByteBuffer(workerThread.getByteBuffer());
        inputReader.setSecure(isSecure());
    }
    
    /**
     * Is {@link ProtocolFilter} secured
     * @return is {@link ProtocolFilter} secured
     */
    protected boolean isSecure() {
        return false;
    }
}
