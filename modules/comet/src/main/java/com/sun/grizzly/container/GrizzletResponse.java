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

package com.sun.grizzly.container;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.ByteChunk;
import java.io.IOException;

/**
 * This class represents an asynchronous response.
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzletResponse {
    private StringBuffer buf = new StringBuffer();
    private ByteChunk chunk = new ByteChunk();
    
    protected Response response;
    
    public GrizzletResponse(Response response) {
        this.response = response;
    }
    
    
    public Response getResponse() {
        return response;
    }
    
    
    protected void setResponse(Response response) {
        this.response = response;
    }
    
    
    /**
     * Add an header to the response.
     */
    public void addHeader(String name, String value){
        response.setHeader(name,value);
    }
    
    
    /**
     * 
     */
    public void setCharacterEncoding(String enc){
        response.setCharacterEncoding(enc);
    }

    
    /**
     *
     */
    public void setStatus(int status){
        response.setStatus(status);
    }
    
    
    public void setMessage(String message){
        response.setMessage("Unprocessable Entity");
    }
    
    
    public void finish() throws IOException{
        response.finish();
    }
    
    
    public void write(String s) throws IOException {
        buf.append(s);
    }
    
    
    public void flush() throws IOException {
        int length = buf.length();
        response.addHeader("Server",
                SelectorThread.SERVER_NAME);
        response.sendHeaders();
        chunk.setBytes(buf.toString().getBytes(),0,length);
        response.doWrite(chunk);
        ((SocketChannelOutputBuffer)response.getOutputBuffer()).flushBuffer();
        
        chunk.recycle();
        buf.delete(0,length);
    }
    
    
    public void setContentType(String s) {
        response.setContentType(s);
    }
}
