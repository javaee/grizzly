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
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.standalone;

import com.sun.grizzly.http.Constants;
import com.sun.grizzly.http.FileCacheFactory;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.http.FileCache;
import com.sun.grizzly.http.SelectorThread;
import java.io.IOException;
import java.nio.channels.SocketChannel;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.Ascii;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
/**
 * This <code>Interceptor</code> is invoked after the request line has been parsed. 
 * 
 * @author Jeanfrancois Arcand
 */
public class StaticHandler implements Interceptor<Request> {
      
    /**
     * The <code>SocketChannel</code> used to send a static resources.
     */
    private SocketChannel socketChannel;
 
    
    /**
     * The FileCache mechanism used to cache static resources.
     */
    protected FileCache fileCache;     
    
    
    // ----------------------------------------------------- Constructor ----//
    
    
    public StaticHandler(){
    }
    
      
    /**
     * Attach a <code>SocketChannel</code> to this object.
     */
    public void attachChannel(SocketChannel socketChannel){
        this.socketChannel = socketChannel;
        if ( fileCache == null && socketChannel != null){
            fileCache = FileCacheFactory.getFactory(
                    socketChannel.socket().getLocalPort()).getFileCache();
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
            ByteChunk requestURI = mb.getByteChunk();       
            String uri = req.requestURI().toString();                
            fileCache.add(FileCache.DEFAULT_SERVLET_NAME,docroot,uri,
                          req.getResponse().getMimeHeaders(),false);        
        } else if (handlerCode == Interceptor.REQUEST_LINE_PARSED) {
            ByteChunk requestURI = req.requestURI().getByteChunk(); 
            if (fileCache.sendCache(requestURI.getBytes(), requestURI.getStart(),
                                requestURI.getLength(), socketChannel,
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
        return true;
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
