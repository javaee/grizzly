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

package com.sun.grizzly.portunif;

import com.sun.grizzly.Context;
import com.sun.grizzly.util.WorkerThread;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLEngine;

/**
 *
 * @author Alexey Stashok
 */
public class ProtocolRequestWorkerThreadAdapter implements PUProtocolRequest {

    private Context context;
    
    private String protocolName;

    // List is faster then HashMap for small datasets.
    private List<String> passedPreProcessors;
    
    private boolean isExecuteFilterChain;
    
    /** 
     * Shows whether we need to map SelectionKey to the specific ProtocolHandler permanently.
     * So all next requests will directly go the specific ProtocolHandler, without
     * ProtocolFinder to be called.
     */
    private boolean mapSelectionKey = true;
    
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

    /**
     * Returns whether we need to map SelectionKey to the specific ProtocolHandler permanently.
     * So all next requests will directly go to the specific ProtocolHandler, without
     * ProtocolFinder to be called. If Protocol
     * 
     * @return true - SelectionKey should be permanently associated with ProtocolHandler,
     * false - otherwise.
     */
    public boolean isMapSelectionKey() {
        return mapSelectionKey;
    }

    /**
     * Sets whether we need to map SelectionKey to the specific ProtocolHandler permanently.
     * So all next requests will directly go to the specific ProtocolHandler, without
     * ProtocolFinder to be called. If Protocol
     * 
     * @param mapSelectionKey  True if SelectionKey should be permanently 
     * associated with ProtocolHandler, false - otherwise.
     */
    public void setMapSelectionKey(boolean mapSelectionKey) {
        this.mapSelectionKey = mapSelectionKey;
    }
    
    private WorkerThread workerThread() {
        return (WorkerThread) Thread.currentThread();
    }

    public Collection<String> getPassedPreProcessors() {
        return passedPreProcessors;
    }

    public void addPassedPreProcessor(String preProcessorId) {
        if (passedPreProcessors == null) {
            passedPreProcessors = new ArrayList<String>(2);
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
