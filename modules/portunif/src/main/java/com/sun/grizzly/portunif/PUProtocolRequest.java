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

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import javax.net.ssl.SSLEngine;

/**
 *
 * @author Alexey Stashok
 */
public interface PUProtocolRequest {
    public SelectionKey getSelectionKey();
    
    public SelectableChannel getChannel();
    
    public ByteBuffer getByteBuffer();
    
    public void setByteBuffer(ByteBuffer byteBuffer);
    
    public SSLEngine getSSLEngine();
    
    public void setSSLEngine(SSLEngine sslEngine);
    
    public ByteBuffer getSecuredInputByteBuffer();
    
    public void setSecuredInputByteBuffer(ByteBuffer securedInputByteBuffer);
    
    public ByteBuffer getSecuredOutputByteBuffer();

    public void setSecuredOutputByteBuffer(ByteBuffer securedOutputByteBuffer);
    
    public Collection<String> getPassedPreProcessors();
    
    public boolean isPreProcessorPassed(String id);
    
    public void addPassedPreProcessor(String preProcessor);
    
    public String getProtocolName();
    
    public void setProtocolName(String protocolName);
    
    public void setExecuteFilterChain(boolean isExecuteFilterChain);
    
    public boolean isExecuteFilterChain();
}
