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

package com.sun.grizzly.filter;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.SSLUtils;
import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.ThreadAttachment.Mode;
import com.sun.grizzly.util.WorkerThread;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;

/**
 * Simple ProtocolFilter implementation which execute an SSL handshake and
 * decrypt the bytes, the pass the control to the next filter.
 *
 * @author Jeanfrancois Arcand
 */
public class SSLReadFilter implements ProtocolFilter{
    /**
     * Attribute is used to instruct SSLReadFilter to continue processing,
     * if there is some data availabe in decoded ByteBuffer, even, if
     * SSLReaderFilter wasn't able to read any additional data
     */
    public static final String SSL_PREREAD_DATA = "SSLReadFilter.preread";
    
    /**
     * The {@link SSLContext} associated with the SSL implementation
     * we are running on.
     */
    protected SSLContext sslContext;
    
    
    /**
     * The list of cipher suite
     */
    private String[] enabledCipherSuites = null;
    
    
    /**
     * the list of protocols
     */
    private String[] enabledProtocols = null;
    
    
    /**
     * Client mode when handshaking.
     */
    private boolean clientMode = false;
    
    
    /**
     * Require client Authentication.
     */
    private boolean needClientAuth = false;
    
    
    /**
     * True when requesting authentication.
     */
    private boolean wantClientAuth = false;
    
    
    /**
     * Has the enabled protocol configured.
     */
    private boolean isProtocolConfigured = false;
    
    
    /**
     * Has the enabled Cipher configured.
     */
    private boolean isCipherConfigured = false;


    private int sslActivityTimeout = SSLUtils.DEFAULT_SSL_INACTIVITY_TIMEOUT;
    
    
    /**
     * Encrypted ByteBuffer default size.
     */
    protected int inputBBSize = 5 * 4096;
    
    
    public SSLReadFilter() {
    }

    
    public boolean execute(Context ctx) throws IOException {
        Logger logger = Controller.logger();
        boolean result = true;
        int count = 0;
        Throwable exception = null;
        SelectionKey key = ctx.getSelectionKey();
        WorkerThread workerThread;
        try{
            workerThread = (WorkerThread)Thread.currentThread();   
        } catch (ClassCastException ex){
            throw new IllegalStateException(ex.getMessage());
        }

        SSLEngine sslEngine = workerThread.getSSLEngine();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Thread associated sslEngine: " + sslEngine);
        }
        if (sslEngine == null) {
            sslEngine = obtainSSLEngine(key);
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Obtained sslEngine: " + sslEngine);
            }
            workerThread.setSSLEngine(sslEngine);
            ThreadAttachment attachment = workerThread.updateAttachment(Mode.SSL_ENGINE);
            key.attach(attachment);
        }

        boolean hasHandshake = sslEngine.getSession().isValid();
        try {
            SSLUtils.allocateThreadBuffers(inputBBSize);
            
            if (hasHandshake) {
                count = doRead(key);
                
                if (count == 0 && ctx.removeAttribute(SSL_PREREAD_DATA) == null) {
                    result = false;
                }
            } else if (doHandshake(key, sslActivityTimeout)) {
                hasHandshake = true;
                // set "no available data" for secured output buffer
                ByteBuffer outputBB = workerThread.getOutputBB();
                outputBB.limit(outputBB.position());
            } else {
                count = -1;
            }
        } catch (IOException ex) {
            exception = ex;
            log("SSLReadFilter.execute",ex);
        } catch (Throwable ex) {
            exception = ex;
            log("SSLReadFilter.execute",ex);
        } finally {
            if (exception != null || count == -1){
                ctx.setAttribute(Context.THROWABLE,exception);
                ctx.setKeyRegistrationState(
                        Context.KeyRegistrationState.CANCEL);
                result = false;
            }
        }
        return result;
    }

    
    /**
     * If no bytes were available, close the connection by cancelling the
     * SelectionKey. If bytes were available, register the SelectionKey
     * for new bytes.
     *
     * @return <tt>true</tt> if the previous ProtocolFilter postExecute method
     *         needs to be invoked.
     */
    public boolean postExecute(Context ctx) throws IOException {
        if (ctx.getKeyRegistrationState()
                == Context.KeyRegistrationState.CANCEL){
            ctx.getSelectorHandler().getSelectionKeyHandler().
                    cancel(ctx.getSelectionKey());
        } else if (ctx.getKeyRegistrationState()
                == Context.KeyRegistrationState.REGISTER){            
            saveSecuredBufferRemainders(ctx.getSelectionKey());
            ctx.getSelectorHandler().register(ctx.getSelectionKey(),
                    SelectionKey.OP_READ);
            ctx.setKeyRegistrationState(Context.KeyRegistrationState.NONE);
        }
        return true;
    }
    
    
    /**
     * Execute a non blocking SSL handshake.
     * @param key {@link SelectionKey}
     * @param timeout 
     * @return 
     * @throws java.io.IOException 
     */    
    private static boolean doHandshake(SelectionKey key,int timeout) throws IOException{
        final WorkerThread workerThread = 
                (WorkerThread)Thread.currentThread();
        ByteBuffer byteBuffer = workerThread.getByteBuffer();
        ByteBuffer outputBB = workerThread.getOutputBB();
        ByteBuffer inputBB = workerThread.getInputBB();
        SSLEngine sslEngine = workerThread.getSSLEngine();
        
        HandshakeStatus handshakeStatus = HandshakeStatus.NEED_UNWRAP;
        
        boolean OK = true;    
        try{ 
            byteBuffer = SSLUtils.doHandshake
                         ((SocketChannel) key.channel(), byteBuffer, inputBB,
                    outputBB, sslEngine, handshakeStatus, timeout);
            if (doRead(key) == -1){
                throw new EOFException();
            }
        } catch (IOException ex) {
            log("doHandshake", ex);
            OK = false;
        }
        return OK;
    }    
    
    
    private static int doRead(SelectionKey key) {
        final WorkerThread workerThread =
                (WorkerThread) Thread.currentThread();
        ByteBuffer byteBuffer = workerThread.getByteBuffer();
        ByteBuffer outputBB = workerThread.getOutputBB();
        ByteBuffer inputBB = workerThread.getInputBB();
        SSLEngine sslEngine = workerThread.getSSLEngine();

        int count = -1;
        try {
            // Read first bytes to avoid continuing if the client
            // closed the connection.
            int initialBufferPosition = byteBuffer.position();

            try {
                count = ((SocketChannel) key.channel()).read(inputBB);
            } catch(IOException e) {
                log("Exception during SSL read.", e);
                count = -1;
            }
            
            if (count > -1 || inputBB.position() > 0) {
                // Decrypt the bytes we just read.
                Logger logger = Controller.logger();
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                            "SSLReadFilter. Read: " + count +
                            " Calling unwrapAll. InputBB: " +
                            inputBB + " byteBuffer: " + byteBuffer);
                }

                int initialInputBBPosition = inputBB.position();
                byteBuffer =
                        SSLUtils.unwrapAll(byteBuffer, inputBB, sslEngine);
                workerThread.setInputBB(inputBB);
                workerThread.setOutputBB(outputBB);
                workerThread.setByteBuffer(byteBuffer);
                
                final int byteBufferPosition = byteBuffer.position();
                if (count <= 0 && byteBufferPosition != initialBufferPosition) {
                    return initialInputBBPosition;
                } else if (count > 0 && byteBufferPosition == 0) {
                    return 0;
                } else if (byteBufferPosition == 0 && sslEngine.isInboundDone()) {
                    return -1;
                }
            }
            return count;
        } catch (IOException ex) {
            log("Exception during SSL read.", ex);
            return -1;
        } finally {
            if (count == -1) {
                try {
                    sslEngine.closeInbound();
                } catch (SSLException ex) {
                }
            }
        }
    }
    
    
    /**
     * Get the peer certificate list by initiating a new handshake.
     * @param key {@link SelectionKey}
     * @param needClientAuth 
     * @return Object[] An array of X509Certificate.
     * @throws java.io.IOException 
     */
    public static Object[] doPeerCertificateChain(SelectionKey key,
            boolean needClientAuth) throws IOException {
        
        final WorkerThread workerThread = 
                (WorkerThread)Thread.currentThread();
        ByteBuffer byteBuffer = workerThread.getByteBuffer();
        ByteBuffer inputBB = workerThread.getInputBB();
        ByteBuffer outputBB = workerThread.getOutputBB();
        SSLEngine sslEngine = workerThread.getSSLEngine();
        
        return SSLUtils.doPeerCertificateChain((SocketChannel) key.channel(), 
                byteBuffer, inputBB, outputBB, sslEngine, needClientAuth, 
                InputReader.getDefaultReadTimeout());
    }
    
    
    /**
     * Return a new configured{@link SSLEngine}
     * @return a new configured{@link SSLEngine}
     */
    protected SSLEngine newSSLEngine() {
        Logger logger = Controller.logger();
        SSLEngine sslEngine = sslContext.createSSLEngine();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "newSSLEngine: " + sslEngine);
        }
        
        if (enabledCipherSuites != null){            
            if (!isCipherConfigured){
                enabledCipherSuites = configureEnabledCiphers(sslEngine,
                                                        enabledCipherSuites);
                isCipherConfigured = true;
            }
            sslEngine.setEnabledCipherSuites(enabledCipherSuites);
        }
        
        if (enabledProtocols != null){
            if (!isProtocolConfigured) {
                enabledProtocols = configureEnabledProtocols(sslEngine,
                                                    enabledProtocols);
                isProtocolConfigured = true;
            }
            sslEngine.setEnabledProtocols(enabledProtocols);
        }
        sslEngine.setUseClientMode(clientMode);
        return sslEngine;
    }
    
    
    /**
     * Configure and return an instance of SSLEngine
     * @param key  a {@link SelectionKey}
     * @return  a configured instance of{@link SSLEngine}
     */
    protected SSLEngine obtainSSLEngine(SelectionKey key) {
        Logger logger = Controller.logger();
        SSLEngine sslEngine = null;

        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Obtaining sslEngine. Key attachment: " +
                    key.attachment());
        }

        if (key.attachment() instanceof ThreadAttachment) {
            sslEngine = ((WorkerThread) Thread.currentThread()).getSSLEngine();
        }
        
        if (sslEngine == null) {
           sslEngine = newSSLEngine();
        }

        if (wantClientAuth) {
            sslEngine.setWantClientAuth(wantClientAuth);
        }
        if (needClientAuth) {
            sslEngine.setNeedClientAuth(needClientAuth);
        }
        return sslEngine;
    }
           
    /**
     * Configures SSL settings. <code>SSLConfig</code> contains all the parameters
     * required to build{@link SSLEngine}. There will be no need to call
     * four methods: setSSLContext, setClientMode, setWantClientAuth, 
     * setNeedClientAuth.
     * @param sslConfig <code>SSLConfig</code> configuration
     */
    public void configure(SSLConfig sslConfig) {
        sslContext = sslConfig.createSSLContext();
        wantClientAuth = sslConfig.isWantClientAuth();
        needClientAuth = sslConfig.isNeedClientAuth();
        clientMode = sslConfig.isClientMode();
        sslActivityTimeout = sslConfig.getSslInactivityTimeout();
    }
    
    /**
     * Set the SSLContext required to support SSL over NIO.
     * @param sslContext {@link SSLContext}
     */
    public void setSSLContext(SSLContext sslContext){
        this.sslContext = sslContext;
    }
    
    
    /**
     * Return the SSLContext required to support SSL over NIO.
     * @return {@link SSLContext}
     */    
    public SSLContext getSSLContext(){
        return sslContext;
    }
    
    
    /**
     * Returns the list of cipher suites to be enabled when {@link SSLEngine}
     * is initialized.
     *
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public String[] getEnabledCipherSuites() {
        return enabledCipherSuites;
    }
    
    
    /**
     * Sets the list of cipher suites to be enabled when {@link SSLEngine}
     * is initialized.
     * @param enabledCipherSuites 
     */
    public void setEnabledCipherSuites(String[] enabledCipherSuites) {
        this.enabledCipherSuites = enabledCipherSuites;
    }
    
    
    /**
     * Returns the list of protocols to be enabled when {@link SSLEngine}
     * is initialized.
     *
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }
    
    
    /**
     * Sets the list of protocols to be enabled when {@link SSLEngine}
     * is initialized.
     *
     * @param enabledProtocols <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }
    
    
    /**
     * Returns <tt>true</tt> if the SSlEngine is set to use client mode
     * when handshaking.
     * @return true / false
     */
    public boolean isClientMode() {
        return clientMode;
    }
    
    
    /**
     * Configures the engine to use client (or server) mode when handshaking.
     * @param clientMode 
     */    
    public void setClientMode(boolean clientMode) {
        this.clientMode = clientMode;
    }
    
    
    /**
     * Returns <tt>true</tt> if the SSLEngine will <em>require</em>
     * client authentication.
     * @return 
     */   
    public boolean isNeedClientAuth() {
        return needClientAuth;
    }
    
    
    /**
     * Configures the engine to <em>require</em> client authentication.
     * @param needClientAuth 
     */    
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }
    
    
    /**
     * Returns <tt>true</tt> if the engine will <em>request</em> client
     * authentication.
     * @return 
     */   
    public boolean isWantClientAuth() {
        return wantClientAuth;
    }
    
    
    /**
     * Configures the engine to <em>request</em> client authentication.
     * @param wantClientAuth 
     */    
    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }


    public int getSslActivityTimeout() {
        return sslActivityTimeout;
    }

    public void setSslActivityTimeout(int sslActivityTimeout) {
        this.sslActivityTimeout = sslActivityTimeout;
    }

    /**
     * Return the list of allowed protocol.
     * @return String[] an array of supported protocols.
     */
    private final static String[] configureEnabledProtocols(
            SSLEngine sslEngine, String[] requestedProtocols){
        
        String[] supportedProtocols = sslEngine.getSupportedProtocols();
        String[] protocols = null;
        ArrayList<String> list = null;
        for(String supportedProtocol: supportedProtocols){        
            /*
             * Check to see if the requested protocol is among the
             * supported protocols, i.e., may be enabled
             */
            for(String protocol: requestedProtocols) {
                protocol = protocol.trim();
                if (supportedProtocol.equals(protocol)) {
                    if (list == null) {
                        list = new ArrayList<String>();
                    }
                    list.add(protocol);
                    break;
                }
            }
        } 

        if (list != null) {
            protocols = list.toArray(new String[list.size()]);                
        }
 
        return protocols;
    }
    
    
    /**
     * Determines the SSL cipher suites to be enabled.
     *
     * @return Array of SSL cipher suites to be enabled, or null if none of the
     * requested ciphers are supported
     */
    private final static String[] configureEnabledCiphers(SSLEngine sslEngine,
            String[] requestedCiphers) {

        String[] supportedCiphers = sslEngine.getSupportedCipherSuites();
        String[] ciphers = null;
        ArrayList<String> list = null;
        for(String supportedCipher: supportedCiphers){        
            /*
             * Check to see if the requested protocol is among the
             * supported protocols, i.e., may be enabled
             */
            for(String cipher: requestedCiphers) {
                cipher = cipher.trim();
                if (supportedCipher.equals(cipher)) {
                    if (list == null) {
                        list = new ArrayList<String>();
                    }
                    list.add(cipher);
                    break;
                }
            }
        } 

        if (list != null) {
            ciphers = list.toArray(new String[list.size()]);                
        }
 
        return ciphers;
    }

    private void saveSecuredBufferRemainders(SelectionKey selectionKey) {
        Logger logger = Controller.logger();

        ThreadAttachment attachment = 
                (ThreadAttachment) selectionKey.attachment();
        
        WorkerThread workerThread = (WorkerThread) Thread.currentThread();   
        if (attachment == null || workerThread.getAttachment() != attachment) {
            logger.log(Level.FINE, 
                    "SelectionKey ThreadAttachment is NULL or doesn't " +
                    "correspond to the current thread, when saving buffers");
            return;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "saveSecuredBufferRemainders inputBB: " +
                    workerThread.getInputBB() + " outputBB: " +
                    workerThread.getOutputBB() + " attach: " + attachment);
        }
        
        ByteBuffer inputBB = workerThread.getInputBB();
        if (inputBB != null && inputBB.position() > 0) {
            workerThread.updateAttachment(attachment.getMode() | Mode.INPUT_BB);
        } else {
            workerThread.updateAttachment(attachment.getMode() & 
                    (Integer.MAX_VALUE ^ Mode.INPUT_BB));
        }

        ByteBuffer outputBB = workerThread.getOutputBB();
        if (outputBB != null && outputBB.hasRemaining()) {
            workerThread.updateAttachment(attachment.getMode() | Mode.OUTPUT_BB);
        } else {
            workerThread.updateAttachment(attachment.getMode() & 
                    (Integer.MAX_VALUE ^ Mode.OUTPUT_BB));
        }
    }
    
    /**
     * Log a message/exception.
     * @param msg <code>String</code>
     * @param t <code>Throwable</code>
     */
    protected static void log(String msg, Throwable t) {
        if (Controller.logger().isLoggable(Level.FINE)) {
            Controller.logger().log(Level.FINE, msg, t);
        }
    }   
}
