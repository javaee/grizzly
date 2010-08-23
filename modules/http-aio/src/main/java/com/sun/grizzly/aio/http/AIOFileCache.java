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

