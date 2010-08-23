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

package com.sun.grizzly.aio.util;

import com.sun.grizzly.Controller;
import com.sun.grizzly.util.DataStructures;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * AIO utility to flush {@link ByteBuffer}
 *
 * @author Jeanfrancois Arcand
 */
public class AIOOutputWriter {

    /**
     * The default rime out before closing the connection
     */
    private static int defaultWriteTimeout = 30;
    public static boolean ASYNC_WRITE = Boolean.valueOf(
            System.getProperty("com.sun.grizzly.asyncWrite", "true")).booleanValue();



    /**
     * Write the {@link ByteBuffer} asynchronously.
     * @param channel {@link AsynchronousSocketChannel}
     * @param bb the ByteBuffer to write.
     * @return the number of bytes written.
     * @throws java.io.IOException
     */
    public static long flushChannel(AsynchronousSocketChannel channel, ByteBuffer bb)
            throws IOException{
        return flushChannel(channel, bb, defaultWriteTimeout);
    }

    /**
     * Write the {@link ByteBuffer} asynchronously.
     * 
     * @param conn The {@link Connection} object
     * @param bb the ByteBuffer to write.
     * @param bb The {@link ByteBuffer}. This instance cannot be re-used until the
     * write operation has fully completed. To get notified when the write completed,
     * see {@link AIOOutputWriter#ByteBufferHandler}
     * @return the number of bytes written.
     * @throws java.io.IOException
     */
    public static long flushChannel(Connection conn, ByteBuffer bb)
            throws IOException {
        return flushChannel(conn, bb, defaultWriteTimeout);
    }


    /**
     * Write the {@link ByteBuffer} asynchronously.
     * @param conn The {@link Connection} object
     * @param bb The {@link ByteBuffer}. This instance cannot be re-used until the
     * write operation has fully completed. To get notified when the write completed,
     * see {@link AIOOutputWriter#ByteBufferHandler}
     * @param writeTimeout how long should we wait for the client to read the written bytes
     * @return the number of bytes written.
     * @throws java.io.IOException
     */
    public static long flushChannel(final Connection conn,
            final ByteBuffer bb, final long writeTimeout) throws IOException {

        if (ASYNC_WRITE){
            int nWrite = bb.limit();
            // Write Async and ordered to avoid WritePendingException.
            offer(conn,bb);
            return nWrite;
        } else {
            return flushChannel(conn.channel,bb,writeTimeout);
        }
    }

    /**
     * Write the {@link ByteBuffer} asynchronously.
     * @param channel The {@link AsynchronousSocketChannel}
     * @param bb The {@link ByteBuffer}. This instance cannot be re-used until the
     * write operation has fully completed.
     * @param writeTimeout how long should we wait for the client to read the written bytes
     * @return the number of bytes written.
     * @throws java.io.IOException
     */
    public static long flushChannel(final AsynchronousSocketChannel channel,
            final ByteBuffer bb, final long writeTimeout) throws IOException {

        if (!bb.hasRemaining()) {
            return 0;
        }

        if (ASYNC_WRITE){
            Connection connection = new Connection(channel, null);
            return flushChannel(connection, bb);
        }
              
        int nWrite = bb.limit();
        try {
            while (bb.hasRemaining()) {
                channel.write(bb).get(writeTimeout, TimeUnit.SECONDS);
            }
        } catch (Exception ie) {
            throw new IOException(ie);
        }
        return nWrite;
    }


    /**
     * Simple object for wrapping a {@link AsynchronousSocketChannel} with an
     * ordered {@link Queue} of {@link ByteBuffer} to write.
     */
    public static class Connection {

        private final Queue<ByteBuffer> queue = DataStructures.getCLQinstance(ByteBuffer.class);
        private final AsynchronousSocketChannel channel;
        private final ByteBufferHandler bbh;

        /**
         * Create a Connection based on {@link AsynchronousSocketChannel} and
         * a callback handler {@link ByteBufferHandler} for recycling {@link ByteBuffer}
         * @param channel
         * @param bbh
         */
        public Connection(AsynchronousSocketChannel channel, ByteBufferHandler bbh) {
            this.channel = channel;
            this.bbh = bbh;
        }

        /**
         * Return the associated {@link AsynchronousSocketChannel}
         * @return the associated {@link AsynchronousSocketChannel}
         */
        AsynchronousByteChannel channel() {
            return channel;
        }

        
        /**
         * Return the associated {@link Queue}
         * @return the associated {@link Queue}
         */
        Queue<ByteBuffer> queue() {
            return queue;
        }


        /**
         * Return the associated {@link Queue}
         * @return the associated {@link Queue}
         */
        ByteBufferHandler byteBufferHandler(){
            return bbh;
        }
    }


    /**
     * {@link CompletionHandler} that takes care of writting {@link ByteBuffer}
     * asynchronously and ordered..
     */
    static CompletionHandler<Integer, Connection> handler =
            new CompletionHandler<Integer, Connection>() {

        /**
         * Invoked when some I/O operations happended.
         */
        public void completed(Integer bytesWritten, Connection conn) {
            Queue<ByteBuffer> queue = conn.queue();
            ByteBufferHandler bbh = conn.byteBufferHandler();
            ByteBuffer buffer;
            synchronized (queue) {
                buffer = queue.peek();
                assert buffer != null;
                if (!buffer.hasRemaining()) {
                    queue.remove();
                    if (bbh != null){
                        bbh.completed(buffer);
                    }
                    buffer = queue.peek();
                }
            }

            if (buffer != null) {
                conn.channel().write(buffer, conn, this);
            }
        }

        
        public void failed(Throwable exc, Connection conn) {
            try{
                conn.channel().close();
            } catch (IOException ex){
                //;
            } finally {
                // Remote client closed the connection.
                if (Controller.logger().isLoggable(Level.FINE)) {
                    Controller.logger().log(Level.FINE, "failed", exc);
                }
                finishConnection(conn);
            }
        }


        private void finishConnection(Connection conn){
            Queue<ByteBuffer> queue = conn.queue();
            ByteBufferHandler bbh = conn.byteBufferHandler();
            ByteBuffer buffer;
            synchronized (queue) {
                buffer = queue.peek();
                assert buffer != null;
                if (!buffer.hasRemaining()) {
                    queue.remove();
                    if (bbh != null){
                        bbh.completed(buffer);
                    }
                }
            }
        }
        
        public void cancelled(Connection conn) {
            Controller.logger().log(Level.WARNING, "Cancelled");
            finishConnection(conn);
        }      
    };

    
    /**
     * Write a {@link ByteBuffer} asynchronously.
     * 
     * @param conn A {@link Connection}
     * @param buffer A {@link ByteBuffer}
     */
    static void offer(Connection conn, ByteBuffer buffer) {
        Queue<ByteBuffer> queue = conn.queue();
        boolean needToWrite;
        synchronized (queue) {
            needToWrite = queue.isEmpty();
            queue.offer(buffer);
        }

        if (needToWrite) {
            conn.channel().write(buffer, conn, handler);
        }
    }

    
    /**
     * Object that implement this interface will be notified when a {@link Connection}
     * has completed the asynchronous writing of the {@link ByteBuffer}
     */
    public interface ByteBufferHandler{
        public void completed(ByteBuffer bb);
    }


    
    public static int getDefaultWriteTimeout() {
        return defaultWriteTimeout;
    }

    public static void setDefaultWriteTimeout(int aDefaultWriteTimeout) {
        defaultWriteTimeout = aDefaultWriteTimeout;
    }
}
