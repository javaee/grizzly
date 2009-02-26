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

package com.sun.grizzly.portunif;

import com.sun.grizzly.Context;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLEngine;

/**
 *
 * @author Alexey Stashok
 */
public class ProtocolRequestWorkerThreadAdapter implements PUProtocolRequest {

    private Context context;
    
    private String protocolName;
    
    private Set<String> passedPreProcessors;
    
    private boolean isExecuteFilterChain;
    
    public void setContext(Context context) {
        this.context = context;
    }
    
    public SelectionKey getSelectionKey() {
        return context.getSelectionKey();
    }

    public SelectableChannel getChannel() {
        return getSelectionKey().channel();
    }

    public ByteBuffer getByteBuffer() {
        return workerThread().getByteBuffer();
    }

    public void setByteBuffer(ByteBuffer byteBuffer) {
        workerThread().setByteBuffer(byteBuffer);
    }

    public SSLEngine getSSLEngine() {
        return workerThread().getSSLEngine();
    }

    public void setSSLEngine(SSLEngine sslEngine) {
        workerThread().setSSLEngine(sslEngine);
    }

    public ByteBuffer getSecuredInputByteBuffer() {
        return workerThread().getInputBB();
    }

    public void setSecuredInputByteBuffer(ByteBuffer securedInputByteBuffer) {
        workerThread().setInputBB(securedInputByteBuffer);
    }

    public ByteBuffer getSecuredOutputByteBuffer() {
        return workerThread().getOutputBB();
    }

    public void setSecuredOutputByteBuffer(ByteBuffer securedOutputByteBuffer) {
        workerThread().setOutputBB(securedOutputByteBuffer);
    }

    private WorkerThreadImpl workerThread() {
        return (WorkerThreadImpl) Thread.currentThread();
    }

    public Collection<String> getPassedPreProcessors() {
        return passedPreProcessors;
    }

    public void addPassedPreProcessor(String preProcessorId) {
        if (passedPreProcessors == null) {
            passedPreProcessors = new HashSet<String>(2);
        }
        
        passedPreProcessors.add(preProcessorId);
    }

    public boolean isPreProcessorPassed(String preProcessorId) {
        if (passedPreProcessors != null) {
            return passedPreProcessors.contains(preProcessorId);
        }
        
        return false;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public void setProtocolName(String protocolName) {
        this.protocolName = protocolName;
    }

    public void setExecuteFilterChain(boolean isExecuteFilterChain) {
        this.isExecuteFilterChain = isExecuteFilterChain;
    }

    public boolean isExecuteFilterChain() {
        return isExecuteFilterChain;
    }
}
