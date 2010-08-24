/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.web.connector.grizzly.async;

import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.SocketChannelOutputBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import org.apache.coyote.Response;

/**
 * Thread-Safe {@link SocketChannelOutputBuffer} used when Comet is used.
 */
public class AsynchronousOutputBuffer extends SocketChannelOutputBuffer{    

    private ReentrantLock byteBufferWriteLock;
    
    /**
     * Alternate constructor.
     */
    public AsynchronousOutputBuffer(Response response, 
            int headerBufferSize, boolean useSocketBuffer) {
        super(response,headerBufferSize, useSocketBuffer); 
        byteBufferWriteLock = new ReentrantLock();
    }

    
    @Override
    public void realWriteBytes(byte cbuf[], int off, int len) throws IOException {
        try{
            byteBufferWriteLock.lock();
            super.realWriteBytes(cbuf, off, len);
        } finally {
            byteBufferWriteLock.unlock();
        }
    }
    
     
    @Override
    public void flushChannel(ByteBuffer bb) throws IOException{
        try{
            byteBufferWriteLock.lock();
            super.flushChannel(bb);
        } catch (IOException ex){
            // Swallow that exception as it just means the Response
            // object has been recycled and another asynchronous
            // operations was still occuring.
            final String message = ex.getMessage();
            if (message == null || !message.startsWith("Illegal")){
                throw ex;
            } else {
                if (SelectorThread.logger().isLoggable(Level.FINEST)){
                    SelectorThread.logger().log(Level.FINEST,"",ex);
                }
            }
            
        } finally {
            byteBufferWriteLock.unlock();
        }            
    }

    @Override
    public void flush() throws IOException{
        try{
            byteBufferWriteLock.lock();        
            super.flush();
        } finally {
            byteBufferWriteLock.unlock();
        }        
    }

    @Override
    public void flushBuffer() throws IOException{
        try{
            byteBufferWriteLock.lock();
            super.flushBuffer();
        } finally {
            byteBufferWriteLock.unlock();
        }        
    }

 
}
