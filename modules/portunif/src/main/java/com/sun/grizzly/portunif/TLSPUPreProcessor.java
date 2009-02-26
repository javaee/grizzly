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
import com.sun.grizzly.Controller;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.util.SSLUtils;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

/**
 * <code>PUPreProcessor</code> that will first try to execute an handshake.
 * If the handshake is succesfull - it means data is encoded
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class TLSPUPreProcessor implements PUPreProcessor {
    public static final String ID = "TLS";
    
    private static final String TMP_DECODED_BUFFER ="TMP_DECODED_BUFFER";
    
    /**
     * Decrypted ByteBuffer default size.
     */
    private final static int appBBSize = 5 * 4096;

    
    /**
     * The <code>SSLContext</code> associated with the SSL implementation
     * we are running on.
     */
    private SSLContext sslContext;


    /**
     * Require client Authentication.
     */
    private boolean needClientAuth = false;
    
    
    /** 
     * True when requesting authentication.
     */
    private boolean wantClientAuth = false; 
    
    // ---------------------------------------------------------------------- //
    
    
    public TLSPUPreProcessor() {
    }

    public TLSPUPreProcessor(SSLConfig sslConfig) {
        configure(sslConfig);
    }
    
    public TLSPUPreProcessor(SSLContext sslContext) {
        this.sslContext = sslContext;
    }
    
    public String getId() {
        return ID;
    }

    /**
     * Try to initialize an SSL|TLS handshake to determine 
     * if secured connection is used
     */
    public boolean process(Context context, 
            PUProtocolRequest protocolRequest) throws IOException {
        
        if (sslContext == null){
            return false;
        }

        SelectionKey key = context.getSelectionKey();
        SelectableChannel channel = key.channel();

        
        SSLEngine sslEngine = null;
        Object attachment = key.attachment();
        if (attachment == null || !(attachment instanceof SSLEngine)) {
            sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            sslEngine.setNeedClientAuth(needClientAuth);
            sslEngine.setWantClientAuth(wantClientAuth);
        } else {
            sslEngine = (SSLEngine) attachment;
        }
        
        ByteBuffer inputBB = protocolRequest.getSecuredInputByteBuffer();
        ByteBuffer outputBB =  protocolRequest.getSecuredOutputByteBuffer();
        ByteBuffer byteBuffer =  protocolRequest.getByteBuffer();  
        int inputBBSize = sslEngine.getSession().getPacketBufferSize();        
        if (inputBB == null 
                || (inputBB != null && inputBBSize > inputBB.capacity())){
            inputBB = ByteBuffer.allocate(inputBBSize * 2);
            outputBB = ByteBuffer.allocate(inputBBSize * 2);

            inputBBSize = sslEngine.getSession().getApplicationBufferSize();
            if (byteBuffer == null || inputBBSize > byteBuffer.capacity()) {
                ByteBuffer newBB = ByteBuffer.allocate(inputBBSize);
                byteBuffer.flip();
                newBB.put(byteBuffer);
                byteBuffer = newBB;
                protocolRequest.setByteBuffer(byteBuffer);
            }   

            protocolRequest.setSecuredInputByteBuffer(inputBB);
            protocolRequest.setSecuredOutputByteBuffer(outputBB);
        }
        inputBB.clear();
        outputBB.position(0);
        outputBB.limit(0); 
        
        inputBB.put((ByteBuffer) byteBuffer.flip());
        byteBuffer.clear();

        boolean OK = Boolean.TRUE.equals(
                sslEngine.getSession().getValue(SSLReadFilter.HANDSHAKE));
        
        if (!OK) {  // Handshake wasn't completed on prev step
            HandshakeStatus handshakeStatus = HandshakeStatus.NEED_UNWRAP;

            try {
                byteBuffer = SSLUtils.doHandshake(channel, byteBuffer, 
                        inputBB, outputBB, sslEngine, handshakeStatus, 
                        SSLUtils.getReadTimeout(), inputBB.position() > 0);
                sslEngine.getSession().putValue(SSLReadFilter.HANDSHAKE, true);
                key.attach(sslEngine);
                protocolRequest.setSSLEngine(sslEngine);
                // set "no available data" for secured output buffer
                outputBB.limit(outputBB.position());
                OK = true;
            } catch (EOFException ex) {
                // DO nothing, as the client closed the connection
            } catch (Exception ex) {
                // An exception means the handshake failed.
                if (Controller.logger().isLoggable(Level.FINE)) {
                    Controller.logger().log(Level.FINE, 
                            "Exception during handshake attempt", ex);
                }
                
                byteBuffer.put(inputBB);
            }
        } else { // Handshake was completed on prev step
            // Check if there is remaining decoded data from prev call
            ByteBuffer tmpBuffer = 
                    (ByteBuffer) context.removeAttribute(TMP_DECODED_BUFFER);
            
            if (tmpBuffer != null) {
                // if there is remaining decoded data - add it
                byteBuffer.put(tmpBuffer);
            }
        }
          
        if (OK) {
            int byteRead = -1;
            if (inputBB.position() == 0) {
                byteRead = SSLUtils.doRead(channel, inputBB, sslEngine, 
                        SSLUtils.getReadTimeout());
            } else {
                byteRead = inputBB.position();
            }
            
            if (byteRead > -1) {
                byteBuffer = SSLUtils.unwrapAll(byteBuffer, inputBB, sslEngine);
                protocolRequest.setByteBuffer(byteBuffer);
                sslEngine.getSession().putValue(SSLReadFilter.DATA_DECODED, 
                        Boolean.TRUE);
            } else {
                throw new EOFException();
            }
        }
        return OK;
    }

    public void postProcess(Context context, PUProtocolRequest protocolRequest) {
        // 1) Copy decoded data to a temporary buffer
        ByteBuffer srcBuffer = protocolRequest.getByteBuffer();
        srcBuffer.flip();
        if (srcBuffer.hasRemaining()) {
            ByteBuffer tmpBuffer = ByteBuffer.allocate(srcBuffer.remaining());
            tmpBuffer.put(srcBuffer);
            tmpBuffer.flip();
            context.setAttribute(TMP_DECODED_BUFFER, tmpBuffer);
        }
        
        // 2) Copy remaining secured input bytes to the main buffer
        ByteBuffer inputBB = protocolRequest.getSecuredInputByteBuffer();
        inputBB.flip();
        srcBuffer.clear();
        srcBuffer.put(inputBB);
        inputBB.clear();
    }
    
    /**
     * Set the SSLContext required to support SSL over NIO.
     * @param sslContext <code>SSLContext</code>
     */
    public void setSSLContext(SSLContext sslContext){
        this.sslContext = sslContext;
    }
    
    
    /**
     * Configures SSL settings. <code>SSLConfig</code> contains all the parameters
     * required to build <code>SSLEngine</code>. There will be no need to call
     * three methods: setSSLContext, setWantClientAuth, 
     * setNeedClientAuth.
     * @param sslConfig <code>SSLConfig</code> configuration
     */
    public void configure(SSLConfig sslConfig) {
        sslContext = sslConfig.createSSLContext();
        wantClientAuth = sslConfig.isWantClientAuth();
        needClientAuth = sslConfig.isNeedClientAuth();
    }

    /**
     * Return the SSLContext required to support SSL over NIO.
     * @return <code>SSLContext</code>
     */    
    public SSLContext getSSLContext(){
        return sslContext;
    }

    public boolean isNeedClientAuth() {
        return needClientAuth;
    }
    
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }
    
    public boolean isWantClientAuth() {
        return wantClientAuth;
    }
    
    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }
    
}
