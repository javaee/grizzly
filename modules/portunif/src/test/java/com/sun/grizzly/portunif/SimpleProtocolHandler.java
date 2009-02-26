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

package com.sun.grizzly.portunif;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.SSLOutputWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import javax.net.ssl.SSLEngine;

/**
 * @author Alexey Stashok
 */
public class SimpleProtocolHandler implements ProtocolHandler {

    private boolean isLog;
    
    private String protocolId;
    
    private byte[] echoArray;
    
    public SimpleProtocolHandler(String protocolId) {
        this.protocolId = protocolId;
        
        echoArray = protocolId.getBytes();
        for(int i=0; i<echoArray.length / 2; i++) {
            byte tmp = echoArray[i];
            echoArray[i] = echoArray[echoArray.length - 1 - i];
            echoArray[echoArray.length - 1 - i] = tmp;
        }
    }
    
    public String[] getProtocols() {
        return new String[] {protocolId};
    }

    public boolean handle(Context context, PUProtocolRequest protocolRequest) throws IOException {
        ByteBuffer echoBuffer = ByteBuffer.wrap(echoArray);

        ByteBuffer buffer = protocolRequest.getByteBuffer();
        try {
            if (Controller.logger().isLoggable(Level.FINE)) {
                int pos = buffer.position();
                int limit = buffer.limit();
                
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                Controller.logger().log(Level.FINE, 
                        "SimpleProtocolHandler protocolId: " + protocolId + 
                        " request: " + new String(data));
                
                buffer.limit(limit);
                buffer.position(pos);
            }
            
            Object attachment = protocolRequest.getSelectionKey().attachment();
            if (attachment instanceof SSLEngine) {
                protocolRequest.setSSLEngine((SSLEngine) attachment);
                allocateBuffers(protocolRequest);
            } else {
                protocolRequest.setSSLEngine(null);
            }
            
            if (protocolRequest.getSSLEngine() != null ||
                    (protocolRequest.getPassedPreProcessors() != null &&
                    protocolRequest.getPassedPreProcessors().contains(TLSPUPreProcessor.ID))) {
                SSLOutputWriter.flushChannel(protocolRequest.getChannel(), echoBuffer);
            } else {
                long written = OutputWriter.flushChannel(protocolRequest.getChannel(), echoBuffer);
            }
        } finally {
            buffer.clear();
        }
        
        return true;
    }

    public boolean expireKey(SelectionKey key) {
        return true;
    }
    
     /**
     * Allocate themandatory <code>ByteBuffer</code>s. Since the ByteBuffer
     * are maintaned on the <code>WorkerThreadImpl</code> lazily, this method
     * makes sure the ByteBuffers are properly allocated and configured.
     */    
    private static void allocateBuffers(PUProtocolRequest protocolRequest){
        ByteBuffer byteBuffer = protocolRequest.getByteBuffer();
        ByteBuffer outputBB = protocolRequest.getSecuredOutputByteBuffer();
        ByteBuffer inputBB = protocolRequest.getSecuredInputByteBuffer();
            
        int expectedSize = protocolRequest.getSSLEngine().getSession()
            .getPacketBufferSize();

        if (inputBB != null && inputBB.capacity() < expectedSize) {
            ByteBuffer newBB = ByteBuffer.allocate(expectedSize);
            inputBB.flip();
            newBB.put(inputBB);
            inputBB = newBB;                                
        } else if (inputBB == null){
            inputBB = ByteBuffer.allocate(expectedSize);
        }      
        
        if (outputBB == null) {
            outputBB = ByteBuffer.allocate(expectedSize);
        } 
        
        if (byteBuffer == null){
            byteBuffer = ByteBuffer.allocate(expectedSize * 2);
        } 

        expectedSize = protocolRequest.getSSLEngine().getSession()
            .getApplicationBufferSize();
        if ( expectedSize > byteBuffer.capacity() ) {
            ByteBuffer newBB = ByteBuffer.allocate(expectedSize);
            byteBuffer.flip();
            newBB.put(byteBuffer);
            byteBuffer = newBB;
        }   

        protocolRequest.setSecuredInputByteBuffer(inputBB);
        protocolRequest.setSecuredOutputByteBuffer(outputBB);  
        protocolRequest.setByteBuffer(byteBuffer);
   
        outputBB.position(0);
        outputBB.limit(0);
    }
}
