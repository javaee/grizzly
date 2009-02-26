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

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;

/**
 * Simple <code>ProcessorTask</code> that configure the <code>outputBuffer</code>
 * using an instance of <code>SSLOutputBuffer</code>. All the request/response
 * operations are delegated to the <code>ProcessorTask</code>
 *
 * @author Jeanfrancois Arcand
 */
public class SSLAsyncProcessorTask extends SSLDefaultProcessorTask {
    
    // ----------------------------------------------------- Constructor ---- //

    public SSLAsyncProcessorTask() {
        this(true,true);
    }
        
    public SSLAsyncProcessorTask(boolean init, boolean bufferResponse) {    
        super(init,bufferResponse);
    }

    
    /**
     * Initialize the stream and the buffer used to parse the request.
     */
    @Override
    public void initialize() {
        started = true;   
        request = new Request();

        response = new Response();
        response.setHook(this);
        
        inputBuffer = new InternalInputBuffer(request,requestBufferSize); 
        outputBuffer = new SSLAsyncOutputBuffer(response,maxHttpHeaderSize,
                                                bufferResponse);
        request.setInputBuffer(inputBuffer);
       
        response.setOutputBuffer(outputBuffer);
        request.setResponse(response);

        initializeFilters();
    }
    
    
    /**
     * Retunr the <code>SSLAsyncOutputBuffer</code>
     */
    public SSLAsyncOutputBuffer getSSLAsyncOutputBuffer() {
        return (SSLAsyncOutputBuffer)outputBuffer;
    }
    
}
