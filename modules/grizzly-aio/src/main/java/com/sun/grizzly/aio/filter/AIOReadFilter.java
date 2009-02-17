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
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.aio.filter;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ProtocolFilter;
import java.io.IOException;
import java.util.logging.Level;

import com.sun.grizzly.aio.AIOContext;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.ByteBufferFactory.ByteBufferType;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.WorkerThread;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.util.concurrent.TimeUnit;

/**
 * Simple AIO based {@link ProtocolFilter} used to read bytes.
 * 
 * @author Jeanfrancois Arcand
 */
public class AIOReadFilter implements ProtocolFilter{
    
    public AIOReadFilter(){
    }
    
    
    /**
     * Read available bytes and delegate the processing of them to the next
     * ProtocolFilter in the ProtocolChain.
     * @return <tt>true</tt> if the next ProtocolFilter on the ProtocolChain
     *                       need to bve invoked.
     */
    public boolean execute(Context ctx) throws IOException {
        return true;
    }

    
    /**
     *
     * @return <tt>true</tt> if the previous ProtocolFilter postExecute method
     *         needs to be invoked.
     */
    public boolean postExecute(Context ctx) throws IOException {
        AIOContext context = (AIOContext)ctx;
        if (!context.isKeepAlive()){
            try{
                context.getChannel().close();
            } catch (AsynchronousCloseException ex){
                if (Controller.logger().isLoggable(Level.FINE)){
                    Controller.logger().log(Level.FINE,"postExecute()", ex);
                }
            } finally{
                context.getController().returnContext(context);
            }
            return false;
        } else {
            ByteBuffer buffer = ((WorkerThread)Thread.currentThread()).getByteBuffer();
            ByteBuffer bb = context.getByteBuffer();
            if (bb == null && buffer != null){
                bb = buffer;
                ((WorkerThread)Thread.currentThread()).setByteBuffer(null);
            } else if (bb == null && buffer == null){
                int size = 8192;
                ByteBufferType bbt = ByteBufferType.DIRECT;
                if (ctx.getThreadPool() instanceof DefaultThreadPool){
                    size = ((DefaultThreadPool)ctx.getThreadPool())
                            .getInitialByteBufferSize();
                    bbt = ((DefaultThreadPool)ctx.getThreadPool())
                            .getByteBufferType();
                }
               bb = ByteBufferFactory.allocate(bbt, size);
            }    
            bb.clear();
            context.setByteBuffer(bb);
            long timeOut = (Long)ctx.getAttribute("timeout");
            context.getChannel().read(bb,timeOut, TimeUnit.SECONDS, null,context);
            return true;
        }
    }

    
    /**
     * Log a message/exception.
     * @param msg <code>String</code>
     * @param t <code>Throwable</code>
     */
    protected void log(String msg,Throwable t){
        if (Controller.logger().isLoggable(Level.FINE)){
            Controller.logger().log(Level.FINE, msg, t);
        }
    }    
}
