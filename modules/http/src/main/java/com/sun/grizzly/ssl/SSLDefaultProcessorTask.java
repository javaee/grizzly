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

import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.http.DefaultProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.tcp.http11.InputFilter;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.tcp.http11.filters.BufferedInputFilter;
import com.sun.grizzly.util.net.SSLSupport;
import java.util.logging.Level;

/**
 * Simple <code>ProcessorTask</code> that configure the <code>outputBuffer</code>
 * using an instance of <code>SSLOutputBuffer</code>. All the request/response
 * operations are delegated to the <code>ProcessorTask</code>
 *
 * @author Jeanfrancois Arcand
 */
public class SSLDefaultProcessorTask extends DefaultProcessorTask {
    // ----------------------------------------------------- Constructor ---- //

    public SSLDefaultProcessorTask(){
        this(true,true);
    }
        
    public SSLDefaultProcessorTask(boolean init, boolean bufferResponse){    
        super(init,bufferResponse);
    }

    
    /**
     * Initialize the stream and the buffer used to parse the request.
     */
    @Override
    public void initialize(){
        started = true;   
        request = new Request();

        response = new Response();
        response.setHook(this);
        
        inputBuffer = new InternalInputBuffer(request,requestBufferSize); 
        outputBuffer = new SSLOutputBuffer(response,maxHttpHeaderSize,
                                           bufferResponse);
        request.setInputBuffer(inputBuffer);
       
        response.setOutputBuffer(outputBuffer);
        request.setResponse(response);

        initializeFilters();
    }
    
   
    /**
     * Send an action to the connector.
     * 
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @Override
    public void action(ActionCode actionCode, Object param) {
 
        if (actionCode == ActionCode.ACTION_REQ_SSL_ATTRIBUTE ) {
            try {
                if (sslSupport != null) {
                    Object sslO = sslSupport.getCipherSuite();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.CIPHER_SUITE_KEY, sslO);
                    sslO = SSLReadFilter.doPeerCertificateChain(key, false);
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    sslO = sslSupport.getKeySize();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.KEY_SIZE_KEY, sslO);
                    sslO = sslSupport.getSessionId();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.SESSION_ID_KEY, sslO);
                }
            } catch (Exception e) {
                SelectorThread.logger().log(Level.WARNING,
                        "processorTask.errorSSL" ,e);
            }
        } else if (actionCode == ActionCode.ACTION_REQ_SSL_CERTIFICATE) {
            if(sslSupport != null) {
                /*
                 * Consume and buffer the request body, so that it does not
                 * interfere with the client's handshake messages
                 */
                InputFilter[] inputFilters = inputBuffer.getFilters();
                ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER])
                    .setLimit(maxPostSize);
                inputBuffer.addActiveFilter
                    (inputFilters[Constants.BUFFERED_FILTER]);
                try {
                    Object sslO = SSLReadFilter.doPeerCertificateChain(key, true);
                    if(sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                } catch (Exception e) {
                    SelectorThread.logger().log(Level.WARNING,
                            "processorTask.exceptionSSLcert",e);
                }
            }
        } else {
            super.action(actionCode,param);
        }
    } 
}
