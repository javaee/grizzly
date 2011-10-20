/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.config;

import com.sun.grizzly.Context;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.ProtocolFinder;
import com.sun.grizzly.config.dom.Ssl;
import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzly.portunif.PUProtocolRequest;
import com.sun.grizzly.util.SSLUtils;
import com.sun.grizzly.util.ThreadAttachment.Mode;
import com.sun.grizzly.util.WorkerThread;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import org.jvnet.hk2.component.Habitat;

/**
 *
 * @author Alexey Stashok
 */
public class HttpProtocolFinder extends com.sun.grizzly.http.portunif.HttpProtocolFinder
        implements ConfigAwareElement<com.sun.grizzly.config.dom.ProtocolFinder> {

    private static final Logger logger = GrizzlyEmbeddedHttp.logger();

    private final Object sync = new Object();
    
    private volatile boolean isSecured;
    private volatile Ssl ssl;
    private volatile SSLConfigHolder sslConfigHolder;

    private volatile boolean isConfigured;

    public void configure(Habitat habitat, ProtocolFinder configuration) {
        Protocol protocol = configuration.findProtocol();
        isSecured = Boolean.parseBoolean(protocol.getSecurityEnabled());

        if (isSecured) {
            ssl = protocol.getSsl();

            try {
                sslConfigHolder = new SSLConfigHolder(habitat, ssl);
            } catch (SSLException e) {
                throw new IllegalStateException(e);
            }

            if (!SSLConfigHolder.isAllowLazyInit(ssl)) {
                configureSSLIfNeeded();
            }
        }
    }

    @Override
    public String find(Context context, PUProtocolRequest protocolRequest)
            throws IOException {

        if (isSecured) {
            configureSSLIfNeeded();

            SelectionKey key = context.getSelectionKey();
            SelectableChannel channel = key.channel();


            final SSLEngine sslEngine = sslConfigHolder.createSSLEngine();

            final boolean isloglevelfine = logger.isLoggable(Level.FINE);
            if (isloglevelfine) {
                logger.log(Level.FINE, "sslEngine: {0}", sslEngine);
            }

            ByteBuffer inputBB = protocolRequest.getSecuredInputByteBuffer();
            ByteBuffer outputBB = protocolRequest.getSecuredOutputByteBuffer();
            ByteBuffer byteBuffer = protocolRequest.getByteBuffer();
            int securedBBSize = sslEngine.getSession().getPacketBufferSize();
            if (inputBB == null || (inputBB != null && securedBBSize > inputBB.capacity())) {
                inputBB = ByteBuffer.allocate(securedBBSize * 2);
                protocolRequest.setSecuredInputByteBuffer(inputBB);
            }

            if (outputBB == null || (outputBB != null && securedBBSize > outputBB.capacity())) {
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

            final WorkerThread workerThread = (WorkerThread) Thread.currentThread();

            boolean isHandshakeDone = false;
            HandshakeStatus handshakeStatus = HandshakeStatus.NEED_UNWRAP;
            try {
                byteBuffer = SSLUtils.doHandshake(channel, byteBuffer,
                        inputBB, outputBB, sslEngine, handshakeStatus,
                        sslConfigHolder.getSslInactivityTimeout(), inputBB.position() > 0);
                if (isloglevelfine) {
                    logger.log(Level.FINE, "handshake is done");
                }

                protocolRequest.setSSLEngine(sslEngine);
                workerThread.setSSLEngine(sslEngine);
                workerThread.setInputBB(inputBB);
                workerThread.setOutputBB(outputBB);
                
                final Object attachment = workerThread.updateAttachment(Mode.SSL_ENGINE);
                key.attach(attachment);

                isHandshakeDone = true;
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
            } finally {
                // set "no available data" for secured output buffer
                outputBB.limit(outputBB.position());                
            }

            if (isloglevelfine) {
                logger.log(Level.FINE, "after handshake. isComplete: " +
                        isHandshakeDone);
            }

            if (isHandshakeDone) {
                int byteRead = -1;
                if (isloglevelfine) {
                    logger.log(Level.FINE, "secured bytebuffer: " + inputBB);
                }

                final long startTime = System.currentTimeMillis();

                String protocol;

                // if we have data available, unwrap it and allow the
                // initial call to super.fine() in the while loop to go through
                // on the existing data.  Otherwise, try to read what we can
                // and continue.
                if (inputBB.position() > 0) {
                    byteBuffer = SSLUtils.unwrapAll(byteBuffer, inputBB, sslEngine);
                    protocolRequest.setByteBuffer(byteBuffer);
                    workerThread.setByteBuffer(byteBuffer);
                }
                final int timeout = sslConfigHolder.getSslInactivityTimeout();
                while((protocol = super.find(context, protocolRequest)) == null &&
                        System.currentTimeMillis() - startTime < timeout) {
                    byteRead = SSLUtils.doRead(channel, inputBB, sslEngine,
                            timeout).bytesRead;
                    if (byteRead == -1) {
                        logger.log(Level.FINE, "EOF");
                        throw new EOFException();
                    }
                    
                    byteBuffer = SSLUtils.unwrapAll(byteBuffer, inputBB, sslEngine);
                    protocolRequest.setByteBuffer(byteBuffer);
                    workerThread.setByteBuffer(byteBuffer);
                }

                context.setAttribute(SSLReadFilter.SSL_PREREAD_DATA, Boolean.TRUE);

                if (isloglevelfine) {
                    logger.log(Level.FINE, "protocol: " + protocol);
                }

                return protocol;
            }

            return null;
        } else {
            return super.find(context, protocolRequest);
        }
    }

    /**
     * Configure SSL
     */
    private void configureSSLIfNeeded() {
        if (!isConfigured) {
            synchronized(sync) {
                if (!isConfigured) {
                    sslConfigHolder.configureSSL();
                    isConfigured = true;
                }
            }
        }
    }
}
