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

import com.sun.grizzly.Context;
import com.sun.grizzly.arp.AsyncProtocolFilter;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.util.ByteBufferInputStream;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.util.net.SSLImplementation;
import com.sun.grizzly.util.net.SSLSupport;
import java.io.InputStream;

/**
 * Asynchronous SSL support over NIO. This <code>Task</code> handles the SSL
 * requests using a non blocking socket. The SSL handshake is done using this
 * class. Once the handshake is successful, the <code>SSLProcessorTask</code> is
 * executed.
 *
 * @author Jean-Francois Arcand
 */
public class SSLAsyncProtocolFilter extends AsyncProtocolFilter {
    /**
     * The Coyote SSLImplementation used to retrive the <code>SSLContext</code>
     */
    protected SSLImplementation sslImplementation;
    
    public SSLAsyncProtocolFilter(Class algorithmClass, int port,
            SSLImplementation sslImplementation) {
        super(algorithmClass, port);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureProcessorTask(ProcessorTask processorTask,
            Context context, HttpWorkerThread workerThread,
            Interceptor handler, InputStream inputStream) {
        super.configureProcessorTask(processorTask, context, workerThread,
                handler, inputStream);
        
        SSLSupport sslSupport = sslImplementation.
                getSSLSupport(workerThread.getSSLEngine());
        ((SSLDefaultProcessorTask)processorTask).setSSLSupport(sslSupport);

        SSLAsyncOutputBuffer outputBuffer =
                ((SSLAsyncProcessorTask)processorTask).getSSLAsyncOutputBuffer();
        
        outputBuffer.setSSLEngine(workerThread.getSSLEngine());
        outputBuffer.setOutputBB(workerThread.getOutputBB());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected ByteBufferInputStream createByteBufferInputStream() {
        return new SSLAsyncStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureByteBufferInputStream(
            ByteBufferInputStream inputStream, Context context, 
            HttpWorkerThread workerThread) {
        ((SSLAsyncStream) inputStream).setSslEngine(workerThread.getSSLEngine());
        ((SSLAsyncStream) inputStream).setInputBB(workerThread.getInputBB());        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isSecure() {
        return true;
    }
}
