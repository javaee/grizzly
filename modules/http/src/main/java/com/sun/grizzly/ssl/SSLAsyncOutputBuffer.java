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

import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.SSLOutputWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;

/**
 * Buffer the bytes until the <code>ByteChunk</code> is full or the request
 * is completed, and then delegate the SSL encryption to class 
 * <code>SSLOutputBuffer</code>
 * 
 * @author Jean-Francois Arcand
 */
public class SSLAsyncOutputBuffer extends SocketChannelOutputBuffer{
    
    /**
     * Encrypted Output <code>ByteBuffer</code>
     */
    protected ByteBuffer outputBB;
    
    
    /**
     * The <code>SSLEngine</code> used to write SSL data.
     */
    protected SSLEngine sslEngine;
    
    
    /**
     * Alternate constructor.
     */
    public SSLAsyncOutputBuffer(Response response, int headerBufferSize,
                                boolean useSocketBuffer) {
        super(response,headerBufferSize,useSocketBuffer);     
    }    
        
    
    /**
     * Flush the buffer by looping until the <code>ByteBuffer</code> is empty
     * using <code>SSLOutputBuffer</code>
     * @param bb the ByteBuffer to write.
     */   
    @Override
    public void flushChannel(ByteBuffer bb) throws IOException{
        SSLOutputWriter.flushChannel(socketChannel, bb, outputBB, sslEngine);
    }   
    
    /**
     * Return the encrypted <code>ByteBuffer</code> used to handle response.
     */    
    public ByteBuffer getOutputBB(){
        return outputBB;
    }
    
    
    /**
     * Set the encrypted <code>ByteBuffer</code> used to handle response.
     */   
    public void setOutputBB(ByteBuffer outputBB){
        this.outputBB = outputBB;
    }
    
         
    /**
     * Set the <code>SSLEngine</code>.
     */
    public SSLEngine getSSLEngine() {
        return sslEngine;
    }

        
    /**
     * Get the <code>SSLEngine</code>.
     */
    public void setSSLEngine(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }    
}
