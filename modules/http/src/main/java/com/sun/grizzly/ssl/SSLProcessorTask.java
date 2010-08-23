/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.ssl;

import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.tcp.http11.InputFilter;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.tcp.http11.filters.BufferedInputFilter;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.net.SSLSupport;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;

/**
 * Simple {@link ProcessorTask} that configure the {@link OutputBuffer}
 * using an instance of {@link SSLOutputBuffer}. All the request/response
 * operations are delegated to the {@link ProcessorTask}
 *
 * @author Jeanfrancois Arcand
 */
public class SSLProcessorTask extends ProcessorTask {
    // ----------------------------------------------------- Constructor ---- //

    public SSLProcessorTask(){
        this(true,true);
    }
        
    public SSLProcessorTask(boolean init, boolean bufferResponse){    
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

    @Override
    public void preProcess(InputStream input, OutputStream output) throws Exception {
        super.preProcess(input, output);

        ((SecureOutputBuffer) outputBuffer).setSSLEngine(((WorkerThread) Thread.currentThread()).getSSLEngine());
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
