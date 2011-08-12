/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Level;

/**
 * NIO utility to flush {@link ByteBuffer}
 *
 * @author Scott Oaks
 */
public class OutputWriter {
    
    /**
     * The default rime out before closing the connection
     */
    private static int defaultWriteTimeout = 30000;
    
    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param channel {@link SelectableChannel}
     * @param bb the ByteBuffer to write.
     * @return 
     * @throws java.io.IOException 
     */   
    public static long flushChannel(SelectableChannel channel, ByteBuffer bb)
            throws IOException{
        return flushChannel(channel,bb,defaultWriteTimeout);
    }
       
    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param channel {@link SelectableChannel}
     * @param bb the ByteBuffer to write.
     * @param writeTimeout 
     * @return 
     * @throws java.io.IOException 
     */   
    public static long flushChannel(SelectableChannel channel, 
            ByteBuffer bb, long writeTimeout) throws IOException{    
        
        if (bb == null || channel == null){
            if (LoggerUtils.getLogger().isLoggable(Level.FINE)){
                LoggerUtils.getLogger().log(Level.FINE,"Invalid Response State " + bb
                       + "SocketChannel cannot be null." + channel);
            }
            return -1;
        }       
        
        SelectionKey key = null;
        Selector writeSelector = null;
        int attempts = 0;
        int nWrite = 0;
        int len = -1;
        long elapsedTime = 0;
        try {
            WritableByteChannel writableChannel = (WritableByteChannel) channel;
            while ( bb.hasRemaining() ) {
                len = writableChannel.write(bb);
                if (len > 0){
                    attempts = 0;
                    elapsedTime = 0;
                    nWrite += len;
                } else {
                    attempts++;
                    if ( writeSelector == null ){
                        writeSelector = SelectorFactory.getSelector();
                        if ( writeSelector == null){
                            // Continue using the main one.
                            continue;
                        }
                        key = channel.register(writeSelector, 
                             SelectionKey.OP_WRITE);
                    }
                    
                    long startTime = System.currentTimeMillis();
                    if (writeSelector.select(writeTimeout) == 0) {
                        elapsedTime += (System.currentTimeMillis() - startTime);
                        if (attempts > 2 && ( writeTimeout > 0 && elapsedTime >= writeTimeout ) )
                            throw new IOException("Client is busy or timed out");
                    }
                }
            }   
        } catch (IOException ex){
            len = -1;
            throw ex;
        } finally {
            if (len == -1) {
                if (key == null) {
                    // create dummy key to not break notifyRemotelyClosed contract
                    writeSelector = SelectorFactory.getSelector();
                    key = channel.register(writeSelector, 0);
                }

                notifyRemotelyClosed(key);
            }
            
            if (key != null) {
                key.cancel();
                key = null;
            }
            
            if ( writeSelector != null ) {
                // Cancel the key.
                SelectorFactory.selectNowAndReturnSelector(writeSelector);
            }            
        }
        return nWrite;
    }  
    
    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param socketChannel {@link SocketChannel}
     * @param bb the ByteBuffer to write.
     * @return 
     * @throws java.io.IOException 
     */   
    public static long flushChannel(SocketChannel socketChannel, ByteBuffer[] bb)
            throws IOException{
        return flushChannel(socketChannel,bb,defaultWriteTimeout);
    }    
     
    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param socketChannel {@link SocketChannel}
     * @param bb the ByteBuffer to write.
     * @param writeTimeout 
     * @return 
     * @throws java.io.IOException 
     */   
    public static long flushChannel(SocketChannel socketChannel,
            ByteBuffer[] bb, long writeTimeout) throws IOException{
      
        if (bb == null || socketChannel == null){
            if (LoggerUtils.getLogger().isLoggable(Level.FINE)){
                LoggerUtils.getLogger().log(Level.FINE,"Invalid Response State " + bb
                       + "SocketChannel cannot be null." + socketChannel);
            }
            return -1;
        }
        
        SelectionKey key = null;
        Selector writeSelector = null;
        int attempts = 0;
        long totalBytes = 0;
        for (int i=0;i<bb.length;i++) {
            totalBytes += bb[i].remaining();
        }
        
        long nWrite = 0;
        long len = -1;
        long elapsedTime = 0;
        try {
            while (nWrite < totalBytes ) {
                len = socketChannel.write(bb);
                if (len > 0){
                    attempts = 0;
                    elapsedTime = 0;
                    nWrite += len;
                } else {
                    if ( writeSelector == null ){
                        writeSelector = SelectorFactory.getSelector();
                        if ( writeSelector == null){
                            // Continue using the main one.
                            continue;
                        }
                    }
                    
                    key = socketChannel.register(writeSelector,  
                                                 SelectionKey.OP_WRITE);

                    long startTime = System.currentTimeMillis();
                    if (writeSelector.select(writeTimeout) == 0) {
                        elapsedTime += (System.currentTimeMillis() - startTime);
                        if (attempts > 2 && ( writeTimeout > 0 && elapsedTime >= writeTimeout ) )
                            throw new IOException("Client is busy or timed out");
                    } 
                } 
            }   
        } catch (IOException ex){
            len = -1;
            throw ex;
        } finally {
            if (len == -1) {
                if (key == null) {
                    // create dummy key to not break notifyRemotelyClosed contract
                    writeSelector = SelectorFactory.getSelector();
                    key = socketChannel.register(writeSelector, 0);
                }

                notifyRemotelyClosed(key);
            }
            
            if (key != null) {
                key.cancel();
                key = null;
            }
            
            if ( writeSelector != null ) {
                // Cancel the key.
                SelectorFactory.selectNowAndReturnSelector(writeSelector);
            }
        }
        return nWrite;
    }  

    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param datagramChannel 
     * @param socketAddress 
     * @param bb the ByteBuffer to write.
     * @return 
     * @throws java.io.IOException 
     */   
    public static long flushChannel(DatagramChannel datagramChannel,
            SocketAddress socketAddress, ByteBuffer bb) 
                throws IOException{   
        return flushChannel(datagramChannel,socketAddress,bb,defaultWriteTimeout);
    }
    
    
    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param datagramChannel 
     * @param socketAddress 
     * @param bb the ByteBuffer to write.
     * @param writeTimeout 
     * @return 
     * @throws java.io.IOException 
     */   
    public static long flushChannel(DatagramChannel datagramChannel,
            SocketAddress socketAddress, ByteBuffer bb, long writeTimeout) 
                throws IOException{    
        
        if (bb == null){
            throw new IllegalStateException("Invalid Response State. ByteBuffer" 
                    + " cannot be null.");
        }
        
        if (datagramChannel == null){
            throw new IllegalStateException("Invalid Response State. " +
                    "DatagramChannel cannot be null.");
        }       
        
        if (socketAddress == null){
            throw new IllegalStateException("Invalid Response State. " +
                    "SocketAddress cannot be null.");
        }
        
        SelectionKey key = null;
        Selector writeSelector = null;
        int attempts = 0;
        int nWrite = 0;
        long elapsedTime = 0;
        try {
            while ( bb.hasRemaining() ) {
                int len = datagramChannel.send(bb,socketAddress);
                if (len > 0){
                    attempts = 0;
                    elapsedTime = 0;
                    nWrite += len;
                } else {
                    if ( writeSelector == null ){
                        writeSelector = SelectorFactory.getSelector();
                        if ( writeSelector == null){
                            // Continue using the main one.
                            continue;
                        }
                    }
                    
                    key = datagramChannel.register(writeSelector, 
                                                   SelectionKey.OP_WRITE);

                    long startTime = System.currentTimeMillis();
                    if (writeSelector.select(writeTimeout) == 0) {
                        elapsedTime += (System.currentTimeMillis() - startTime);
                        if (attempts > 2 && ( writeTimeout > 0 && elapsedTime >= writeTimeout ) )
                            throw new IOException("Client is busy or timed out");
                    } else {
                        attempts--;
                    }
                } 
            }   
        } finally {
            if (key != null) {
                key.cancel();
                key = null;
            }
            
            if ( writeSelector != null ) {
                // Cancel the key.
                SelectorFactory.selectNowAndReturnSelector(writeSelector);
            }
        }
        return nWrite;
    }  
    
    
    public static int getDefaultWriteTimeout() {
        return defaultWriteTimeout;
    }

    
    public static void setDefaultWriteTimeout(int aDefaultWriteTimeout) {
        defaultWriteTimeout = aDefaultWriteTimeout;
    }

    private static void notifyRemotelyClosed(SelectionKey key) {
        if (Thread.currentThread() instanceof WorkerThread) {
            ConnectionCloseHandlerNotifier notifier =
                    (ConnectionCloseHandlerNotifier) ((WorkerThread) Thread.currentThread()).getAttachment().getAttribute("ConnectionCloseHandlerNotifier");
            if (notifier != null) {
                notifier.notifyRemotlyClose(key);
            }
        }
    }
}
