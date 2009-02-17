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

package com.sun.grizzly.aio.http;

import com.sun.grizzly.aio.util.AIOOutputWriter;
import com.sun.grizzly.http.FileCache;
import com.sun.grizzly.http.SelectorThread;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link AsynchronousSocketChannel} implementation of the {@link FileCache}
 * 
 * @author Jeanfrancois Arcand
 */
public class AIOFileCache extends FileCache{


    private Logger logger = SelectorThread.logger();

    
    /**
     * Send the cache.
     */
    public boolean sendCache(byte[] req, int start, int length,
            AsynchronousSocketChannel asyncChannel, boolean keepAlive){

        try{
            FileCacheEntry entry = map(req,start,length);
            if (entry != null && entry.bb != nullByteBuffer){

                if (SelectorThread.isEnableNioLogging()){
                    SelectorThread.logger().info("Sending cached resource: "
                            + new String(req));
                }

                sendCache(asyncChannel,entry,keepAlive);
                return true;
            }
        } catch (IOException ex){
            if (logger.isLoggable(Level.FINE)){
                logger.fine("File Cache: " + ex.getMessage());
            }
            return true;
        } catch (Throwable t){
            if (logger.isLoggable(Level.FINE)){
                logger.fine("File Cache: " + t.getMessage());
            }
        }
        return false;
    }


    /**
     * Send the cached resource.
     */
    protected void sendCache(AsynchronousSocketChannel channel,  FileCacheEntry entry,
            boolean keepAlive) throws IOException{

        AIOOutputWriter.flushChannel(channel, entry.headerBuffer.slice());
        ByteBuffer keepAliveBuf = keepAlive ? connectionKaBB.slice():
               connectionCloseBB.slice();
        AIOOutputWriter.flushChannel(channel, keepAliveBuf);
        AIOOutputWriter.flushChannel(channel, entry.bb.slice());
    }

}

