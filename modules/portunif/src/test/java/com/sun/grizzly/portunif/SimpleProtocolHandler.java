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
import com.sun.grizzly.Controller;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.SSLOutputWriter;
import com.sun.grizzly.util.SelectionKeyAttachment;
import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.Utils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;

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
        SelectionKey key = protocolRequest.getSelectionKey();
        
        buffer.flip();
        try {
            if (!buffer.hasRemaining()) {
                buffer.clear();
                Utils.readWithTemporarySelector(protocolRequest.getChannel(), buffer, 5000);
                buffer.flip();
            }
            
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
            
            Object attachment = SelectionKeyAttachment.getAttachment(key);
            if (attachment instanceof ThreadAttachment) {
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
            
            context.getSelectorHandler().register(key, SelectionKey.OP_READ);
        } catch(Exception e) {
            context.getSelectorHandler().getSelectionKeyHandler().cancel(key);
        } finally {
            buffer.clear();
        }
        
        return true;
    }

    public boolean expireKey(SelectionKey key) {
        return true;
    }
    
     /**
     * Allocate themandatory {@link ByteBuffer}s. Since the ByteBuffer
     * are maintaned on the {@link WorkerThreadImpl} lazily, this method
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

    public ByteBuffer getByteBuffer() {
        // Use thread associated byte buffer
        return null;
    }
}
