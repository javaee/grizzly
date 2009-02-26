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
package com.sun.grizzly.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.ByteBuffer;


/**
 * This class encapsulates the logic required to synchronized
 * unblocking socket request with the blocked stream architecture of Tomcat.
 *
 * @author Jean-Francois Arcand
 */
public class TaskContext{

    
    /**
     * Use a PipeInputStream since we are using a non blocking byteChannel.
     */
    private InputStream inputStream;
    
    
    /**
     * Use a PipeInputStream since we are using a non blocking byteChannel.
     */
    private ByteBufferStream outputStream;   
    

    // ------------------------------------------------------Constructor -----//
    
    /**
     * Create a instance of this object. 
     */
    public TaskContext(){
    }    


    //------------------------------------------------------------------------//
     
        
    /**
     * Return the input stream used by this request. The default stream is an 
     * instance of <code>NonBlockinginputStream</code>
     */
    public InputStream getInputStream(){
        return inputStream;
    }
    
    public OutputStream getOutputStream(){
        return (OutputStream)outputStream;
    }
    
    public void setInputStream(InputStream inputStream){
        this.inputStream = inputStream;
    }
    
    public void setOutputStream(ByteBufferStream outputStream){
        this.outputStream = outputStream;
    }
    
    // ---------------------------------------------------------------------//

        
    /**
     * Fill the current output stream with the available bytes
     */
    public void write(ByteBuffer byteBuffer) throws IOException {  
        outputStream.write(byteBuffer);
    }
        
    
    /**
     * Flush bytes to the <code>NonBlockinginputStream</code>
     */
    public void flush() throws IOException {   
        if (outputStream != null) {
            outputStream.flush();
        }
    }
    
    
    /**
     * Recycle all streams used by this object.
     */
    public void recycle() throws IOException{
        if (inputStream != null){
            flush();
            inputStream.close();
        }
    }
    
}





