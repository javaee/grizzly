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
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.util.SSLUtils;
import com.sun.grizzly.util.SelectionKeyAttachment;
import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.ThreadAttachment.Mode;
import com.sun.grizzly.util.WorkerThread;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;
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


    private int sslInactivityTimeout = SSLUtils.DEFAULT_SSL_INACTIVITY_TIMEOUT;
    
    
    /**
     * Logger
     */
    private static Logger logger = Controller.logger();

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
        
        if (sslContext == null) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING,
                           LogMessages.WARNING_GRIZZLY_PU_TLS_PROCESSOR_SKIPPED());
            }
            
            return false;
        }

        SelectionKey key = context.getSelectionKey();
        SelectableChannel channel = key.channel();

        
        SSLEngine sslEngine = null;
        Object attachment = SelectionKeyAttachment.getAttachment(key);
        final boolean isloglevelfine = logger.isLoggable(Level.FINE);
        if (isloglevelfine) {
            logger.log(Level.FINE, "SelectionKeyAttachment: " + key);
        }
        
        if (attachment != null && attachment instanceof ThreadAttachment) {
            sslEngine = ((ThreadAttachment) attachment).getSSLEngine();
        }
        
        if (sslEngine == null) {
            sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(false);
            if (needClientAuth) {
                sslEngine.setNeedClientAuth(needClientAuth);
            }
            if (wantClientAuth) {
                sslEngine.setWantClientAuth(wantClientAuth);
            }
        }
        
        if (isloglevelfine) {
            logger.log(Level.FINE, "sslEngine: " + sslEngine);
        }
        
        ByteBuffer inputBB = protocolRequest.getSecuredInputByteBuffer();
        ByteBuffer outputBB =  protocolRequest.getSecuredOutputByteBuffer();
        ByteBuffer byteBuffer =  protocolRequest.getByteBuffer();  
        int securedBBSize = sslEngine.getSession().getPacketBufferSize();        
        if (inputBB == null 
                || (inputBB != null && securedBBSize > inputBB.capacity())) {
            inputBB = ByteBuffer.allocate(securedBBSize * 2);
            protocolRequest.setSecuredInputByteBuffer(inputBB);
        }
        
        if (outputBB == null 
                || (outputBB != null && securedBBSize > outputBB.capacity())) {
            outputBB = ByteBuffer.allocate(securedBBSize * 2);
            protocolRequest.setSecuredOutputByteBuffer(outputBB);
        }

        int applicationBBSize = sslEngine.getSession().getApplicationBufferSize();
        if (byteBuffer == null || applicationBBSize > byteBuffer.capacity()) {
            ByteBuffer newBB = ByteBuffer.allocate(securedBBSize);
            byteBuffer.flip();
            newBB.put(byteBuffer);
            byteBuffer = newBB;
            protocolRequest.setByteBuffer(byteBuffer);
        }

        inputBB.clear();
        outputBB.position(0);
        outputBB.limit(0); 
        
        inputBB.put((ByteBuffer) byteBuffer.flip());
        byteBuffer.clear();

        boolean OK = sslEngine.getSession().isValid();

        if (isloglevelfine) {
            logger.log(Level.FINE, "Is session valid: " + OK);
        }
        
        if (!OK) {  // Handshake wasn't completed on prev step
            HandshakeStatus handshakeStatus = HandshakeStatus.NEED_UNWRAP;

            try {
                byteBuffer = SSLUtils.doHandshake(channel, byteBuffer, 
                        inputBB, outputBB, sslEngine, handshakeStatus,
                        sslInactivityTimeout, inputBB.position() > 0);
                if (isloglevelfine) {
                    logger.log(Level.FINE, "handshake is done");
                }
                
                WorkerThread workerThread = (WorkerThread) Thread.currentThread();
                attachment = workerThread.updateAttachment(Mode.SSL_ENGINE);
                key.attach(attachment);
                
                protocolRequest.setSSLEngine(sslEngine);
                // set "no available data" for secured output buffer
                outputBB.limit(outputBB.position());
                OK = true;
            } catch (EOFException ex) {
                if (isloglevelfine) {
                    logger.log(Level.FINE, "handshake failed", ex);
                }
                // DO nothing, as the client closed the connection
            } catch (Exception ex) {
                // An exception means the handshake failed.
                if (isloglevelfine) {
                    logger.log(Level.FINE, "handshake failed", ex);
                }
                
                inputBB.flip();
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
          
        if (isloglevelfine) {
            logger.log(Level.FINE, "after handshake. isComplete: " + OK);
        }
        
        if (OK) {
            int byteRead = -1;
            if (isloglevelfine) {
                logger.log(Level.FINE, "secured bytebuffer: " + inputBB);
            }
            
            if (inputBB.position() == 0) {
                byteRead = SSLUtils.doRead(channel, inputBB, sslEngine,
                        sslInactivityTimeout).bytesRead;
            } else {
                byteRead = inputBB.position();
            }
            
            if (isloglevelfine) {
                logger.log(Level.FINE, "secured bytebuffer additional read: " + byteRead);
            }
            if (byteRead > -1) {
                byteBuffer = SSLUtils.unwrapAll(byteBuffer, inputBB, sslEngine);
                protocolRequest.setByteBuffer(byteBuffer);
            } else {
                throw new EOFException();
            }
        }

        if (OK) {
            context.setAttribute(SSLReadFilter.SSL_PREREAD_DATA, Boolean.TRUE);
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
        sslInactivityTimeout = sslConfig.getSslInactivityTimeout();
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

    public int getSslInactivityTimeout() {
        return sslInactivityTimeout;
    }

    public void setSslInactivityTimeout(int sslInactivityTimeout) {
        this.sslInactivityTimeout = sslInactivityTimeout;
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
