/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.web.connector.grizzly;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

/**
 * NIO utility to flush <code>ByteBuffer</code>
 *
 * @author Scott Oaks
 */
public class OutputWriter {
    
    /**
     * The default rime out before closing the connection
     */
    private static int defaultWriteTimeout = 30000;
    
    
    /**
     * Flush the buffer by looping until the <code>ByteBuffer</code> is empty
     * @param bb the ByteBuffer to write.
     */   
    public static long flushChannel(SocketChannel socketChannel, ByteBuffer bb)
            throws IOException{
        return flushChannel(socketChannel,bb,defaultWriteTimeout);
    }
       
    
    /**
     * Flush the buffer by looping until the <code>ByteBuffer</code> is empty
     * @param bb the ByteBuffer to write.
     */   
    public static long flushChannel(SocketChannel socketChannel,
            ByteBuffer bb, long writeTimeout) throws IOException{    
        
        if (bb == null || socketChannel == null){
            if (SelectorThread.logger().isLoggable(Level.FINE)){
                SelectorThread.logger().log(Level.FINE,"Invalid Response State " + bb
                       + "SocketChannel cannot be null." + socketChannel);
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
            while ( bb.hasRemaining() ) {
                len = socketChannel.write(bb);
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
                        key = socketChannel.register(writeSelector,
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
    
    
    /**
     * Flush the buffer by looping until the <code>ByteBuffer</code> is empty
     * @param bb the ByteBuffer to write.
     */   
    public static long flushChannel(SocketChannel socketChannel, ByteBuffer[] bb)
            throws IOException{
        return flushChannel(socketChannel,bb,defaultWriteTimeout);
    }    
     
    
    /**
     * Flush the buffer by looping until the <code>ByteBuffer</code> is empty
     * @param bb the ByteBuffer to write.
     */   
    public static long flushChannel(SocketChannel socketChannel,
            ByteBuffer[] bb, long writeTimeout) throws IOException{
      
        if (bb == null || socketChannel == null){
            if (SelectorThread.logger().isLoggable(Level.FINE)){
                SelectorThread.logger().log(Level.FINE,"Invalid Response State " + bb
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
}
