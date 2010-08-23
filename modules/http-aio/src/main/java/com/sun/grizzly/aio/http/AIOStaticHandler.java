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

import com.sun.grizzly.http.Constants;
import com.sun.grizzly.http.FileCacheFactory;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.http.FileCache;
import com.sun.grizzly.http.SelectorThread;
import java.io.IOException;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.Ascii;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;


/**
 * This {@link Interceptor} is invoked after the request line has been parsed. 
 * 
 * @author Jeanfrancois Arcand
 */
public class AIOStaticHandler implements Interceptor<Request,AsynchronousSocketChannel> {
      
    /**
     * The {@link AsynchronousSocketChannel} used to send a static resources.
     */
    private AsynchronousSocketChannel channel;
 
    
    /**
     * The FileCache mechanism used to cache static resources.
     */
    protected AIOFileCache fileCache;
    
    
    // ----------------------------------------------------- Constructor ----//
    
    
    public AIOStaticHandler(){
    }
    
      
    /**
     * Attach a {@link AsynchronousSocketChannel} to this object.
     */
    public void attachChannel(AsynchronousSocketChannel channel){
        this.channel = channel;
        if ( fileCache == null && channel != null){
            try{
                fileCache = (AIOFileCache)FileCacheFactory.getFactory(
                        ((InetSocketAddress)channel.getLocalAddress()).getPort())
                            .getFileCache();
            } catch (IOException ex){
                ex.printStackTrace();
            }
        }
    }    
    
    
    /**
     * Intercept the request and decide if we cache the static resource. If the
     * static resource is already cached, return it.
     */
    public int handle(Request req, int handlerCode) throws IOException{
        if (fileCache == null) return Interceptor.CONTINUE;
        
        if (handlerCode == Interceptor.RESPONSE_PROCEEDED && fileCache.isEnabled()){
            String docroot = SelectorThread.getWebAppRootPath();
            MessageBytes mb = req.requestURI();
            String uri = req.requestURI().toString();                
            fileCache.add(FileCache.DEFAULT_SERVLET_NAME,docroot,uri,
                          req.getResponse().getMimeHeaders(),false);        
        } else if (handlerCode == Interceptor.REQUEST_LINE_PARSED) {
            ByteChunk requestURI = req.requestURI().getByteChunk(); 
            if (fileCache.sendCache(requestURI.getBytes(), requestURI.getStart(),
                                    requestURI.getLength(), channel,
                                    keepAlive(req))){
                return Interceptor.BREAK;   
            }
        }     
        return Interceptor.CONTINUE;
    }
       
    
    /**
     * Get the keep-alive header.
     */
    private boolean keepAlive(Request request){
        MimeHeaders headers = request.getMimeHeaders();

        // Check connection header
        MessageBytes connectionValueMB = headers.getValue("connection");
        if (connectionValueMB != null) {
            ByteChunk connectionValueBC = connectionValueMB.getByteChunk();
            if (findBytes(connectionValueBC, Constants.CLOSE_BYTES) != -1) {
                return false;
            } else if (findBytes(connectionValueBC, 
                                 Constants.KEEPALIVE_BYTES) != -1) {
                return true;
            }
        }
        return false;
    }
    
    
    /**
     * Specialized utility method: find a sequence of lower case bytes inside
     * a ByteChunk.
     */
    protected int findBytes(ByteChunk bc, byte[] b) {

        byte first = b[0];
        byte[] buff = bc.getBuffer();
        int start = bc.getStart();
        int end = bc.getEnd();

        // Look for first char 
        int srcEnd = b.length;

        for (int i = start; i <= (end - srcEnd); i++) {
            if (Ascii.toLower(buff[i]) != first) continue;
            // found first char, now look for a match
            int myPos = i+1;
            for (int srcPos = 1; srcPos < srcEnd; ) {
                    if (Ascii.toLower(buff[myPos++]) != b[srcPos++])
                break;
                    if (srcPos == srcEnd) return i - start; // found it
            }
        }
        return -1;
    }
}
