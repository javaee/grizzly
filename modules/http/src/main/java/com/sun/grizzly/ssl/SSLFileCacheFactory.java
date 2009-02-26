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

package com.sun.grizzly.ssl;

import com.sun.grizzly.http.FileCache;
import com.sun.grizzly.http.FileCacheFactory;
import com.sun.grizzly.http.FileCache.FileCacheEntry;
import com.sun.grizzly.util.SSLOutputWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * File cache extension used to support SSL.
 *
 * @author Jeanfrancois Arcand
 */
public class SSLFileCacheFactory extends FileCacheFactory{
    
    public SSLFileCacheFactory() {
    }
    
    /**
     * Configure the factory.
     */
    public static FileCacheFactory newInstance(int currentPort){
        FileCacheFactory fileCacheFactory= new SSLFileCacheFactory();

        fileCacheFactory.port = currentPort;
        cache.put(currentPort, fileCacheFactory);

        ConcurrentLinkedQueue<FileCacheEntry> cacheManager =
            new  ConcurrentLinkedQueue<FileCacheEntry>();
        fileCacheFactory.setCacheManager(cacheManager);  

        return fileCacheFactory;
    }    
     
    
    /**
     * Return an instance of this Factory.
     */
    public static FileCacheFactory getFactory(int currentPort){
                
        FileCacheFactory fileCacheFactory = cache.get(currentPort);
        if ( fileCacheFactory == null ){
            fileCacheFactory = newInstance(currentPort); 
        }

        return fileCacheFactory;
    }
    
    
    /**
     * Return an instance of a <code>FileCache</code>
     */
    @Override
    public FileCache getFileCache(){
        if (fileCache == null){
            fileCache = new FileCache(){
                
                @Override
                protected void sendCache(SocketChannel socketChannel,  
                        FileCacheEntry entry,
                    boolean keepAlive) throws IOException{

                    SSLOutputWriter.flushChannel(socketChannel, 
                            entry.headerBuffer.slice());
                    ByteBuffer keepAliveBuf = keepAlive ? connectionKaBB.slice():
                    connectionCloseBB.slice();
                    SSLOutputWriter.flushChannel(socketChannel, keepAliveBuf);        
                    SSLOutputWriter.flushChannel(socketChannel, entry.bb.slice());
                }  
            };
            fileCache.setIsEnabled(isEnabled);
            fileCache.setLargeFileCacheEnabled(isLargeFileCacheEnabled);
            fileCache.setSecondsMaxAge(secondsMaxAge);
            fileCache.setMaxCacheEntries(maxCacheEntries);
            fileCache.setMinEntrySize(minEntrySize);
            fileCache.setMaxEntrySize(maxEntrySize);
            fileCache.setMaxLargeCacheSize(maxLargeFileCacheSize);
            fileCache.setMaxSmallCacheSize(maxSmallFileCacheSize);         
            fileCache.setCacheManager(cacheManager);
            FileCache.setIsMonitoringEnabled(isMonitoringEnabled);
        }
        
        return fileCache;
    }     
 
}
