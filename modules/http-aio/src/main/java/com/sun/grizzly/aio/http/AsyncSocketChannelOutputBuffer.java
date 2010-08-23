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

import java.io.IOException;
import java.nio.ByteBuffer;
import com.sun.grizzly.aio.util.AIOOutputWriter;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.Response;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channel;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Output buffer.
 * Buffer the bytes until the {@link ByteChunk} is full or the request
 * is completed.
 * 
 * @author Jean-Francois Arcand
 * @author Scott Oaks
 */
public class AsyncSocketChannelOutputBuffer extends SocketChannelOutputBuffer{

    private ByteBufferRecycler bbr  = null;

    
    private static ArrayBlockingQueue<ByteBuffer> cachedByteBuffers;

    
    private AIOOutputWriter.Connection connection;

    // ----------------------------------------------------------- Constructors
    

    /**
     * Alternate constructor.
     */
    public AsyncSocketChannelOutputBuffer(Response response, 
            int headerBufferSize, boolean useSocketBuffer) {
        super(response,headerBufferSize, useSocketBuffer);

        if (AIOOutputWriter.ASYNC_WRITE){
            bbr = new ByteBufferRecycler(); 
        }
    }

    
    /**
     * Guess the number of {@link ByteBuffer} allowed to be cached. When the 
     * limit is reached, {@link ByteBuffer} are not re-used and candidate for
     * garbage collection.
     */
    private void guessCachedByteBufferSize(int headerBufferSize){
        //Allocate half of the free space.
        int size = (int)
                (Runtime.getRuntime().freeMemory()/headerBufferSize)/2;
        
        int maxThreads = ((HttpWorkerThread)Thread.currentThread()).getProcessorTask()
                .getSelectorThread().getMaxThreads();
        size = size/maxThreads;
        cachedByteBuffers = new ArrayBlockingQueue<ByteBuffer>(size);
    }

    
    @Override
    protected ByteBuffer createByteBuffer(int size){
        return ByteBuffer.allocateDirect(size);
    }

    
    /**
     * Set the underlying socket output stream.
     */
    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
        if (channel instanceof AsynchronousSocketChannel){
            connection = new AIOOutputWriter.Connection
                    ((AsynchronousSocketChannel)channel, bbr);
        }
    }

    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param bb the ByteBuffer to write.
     */   
    @Override
    public void flushChannel(ByteBuffer bb) throws IOException{
        if (AIOOutputWriter.ASYNC_WRITE && cachedByteBuffers == null){
            guessCachedByteBufferSize(bb.capacity());
        }
        
        AIOOutputWriter.flushChannel(connection, bb);
        if (AIOOutputWriter.ASYNC_WRITE){
            bb = cachedByteBuffers.poll();
            if (bb == null){
                bb = createByteBuffer(outputByteBuffer.capacity());
            }
            outputByteBuffer = bb;
        } else {
            bb.clear();
        }
    }


    /** 
     * Recycle {@link ByteBuffer}. If the {@link AsyncSocketChannelOutputBuffer#cachedByteBuffers}, 
     * do not cache
     */
    static class ByteBufferRecycler implements AIOOutputWriter.ByteBufferHandler{
        public void completed(ByteBuffer bb) {
            bb.clear();
            cachedByteBuffers.offer(bb);
        }
    }

}
