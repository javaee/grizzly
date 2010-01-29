/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */
package com.sun.grizzly.ssl;

import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Connection;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.TransformerStreamWriter;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.CompletionHandlerWrapper;
import com.sun.grizzly.utils.conditions.Condition;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

/**
 * SSL aware {@link StreamWriter} implementation, which work like a wrapper over
 * existing {@link StreamWriter}.
 *
 * @see SSLStreamReader
 *
 * @author Alexey Stashok
 */
public class SSLStreamWriter extends TransformerStreamWriter {

    public SSLStreamWriter(StreamWriter underlyingWriter) {
        super(underlyingWriter, new SSLEncoderTransformer());
    }

    public Future<SSLEngine> handshake(final SSLStreamReader sslStreamReader,
            final SSLEngineConfigurator configurator)
            throws IOException {
        return handshake(sslStreamReader, configurator, null);
    }

    public Future<SSLEngine> handshake(final SSLStreamReader sslStreamReader,
            final SSLEngineConfigurator configurator,
            final CompletionHandler<SSLEngine> completionHandler)
            throws IOException {

        final Connection connection = getConnection();

        SSLEngine sslEngine = SSLUtils.getSSLEngine(getConnection());

        if (sslEngine == null) {
            sslEngine = configurator.createSSLEngine();
            SSLUtils.setSSLEngine(connection, sslEngine);
            checkBuffers(connection, sslEngine);
        }

        final boolean isLoggingFinest = logger.isLoggable(Level.FINEST);

        if (isLoggingFinest) {
            logger.finest("connection=" + connection + " engine=" + sslEngine
                    + " handshakeStatus=" + sslEngine.getHandshakeStatus());
        }

        HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();

        if (handshakeStatus == HandshakeStatus.NOT_HANDSHAKING) {
            sslEngine.beginHandshake();
            handshakeStatus = sslEngine.getHandshakeStatus();
        }

        final FutureImpl<SSLEngine> future = new FutureImpl();

        final HandshakeCompletionHandler hsCompletionHandler =
                new HandshakeCompletionHandler(future, completionHandler);

        hsCompletionHandler.setResult(sslEngine);
        
        sslStreamReader.notifyCondition(new SSLHandshakeCondition(sslStreamReader,
                this, configurator, sslEngine, hsCompletionHandler),
                hsCompletionHandler);

        return future;
    }

    private static void checkBuffers(Connection connection, SSLEngine sslEngine) {
        final int packetBufferSize = sslEngine.getSession().getPacketBufferSize();
        if (connection.getReadBufferSize() < packetBufferSize) {
            connection.setReadBufferSize(packetBufferSize);
        }

        if (connection.getWriteBufferSize() < packetBufferSize) {
            connection.setWriteBufferSize(packetBufferSize);
        }
    }

    protected class SSLHandshakeCondition implements Condition {

        private final SSLEngineConfigurator configurator;
        private final Connection connection;
        private final SSLEngine sslEngine;
        private final StreamReader streamReader;
        private final StreamWriter streamWriter;
        private final HandshakeCompletionHandler completionHandler;

        public SSLHandshakeCondition(StreamReader streamReader,
                StreamWriter streamWriter,
                SSLEngineConfigurator configurator, SSLEngine sslEngine,
                HandshakeCompletionHandler completionHandler) {

            this.connection = streamReader.getConnection();
            this.configurator = configurator;
            this.sslEngine = sslEngine;
            this.completionHandler = completionHandler;

            this.streamReader = streamReader;
            this.streamWriter = streamWriter;
        }

        @Override
        public boolean check() {
            try {
                return doHandshakeStep();
            } catch (IOException e) {
                completionHandler.failed(e);
                throw new RuntimeException("Unexpected handshake exception");
            }
        }

        public boolean doHandshakeStep() throws IOException {

            final boolean isLoggingFinest = logger.isLoggable(Level.FINEST);

            HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();

            if (handshakeStatus == HandshakeStatus.FINISHED
                    || handshakeStatus == HandshakeStatus.NOT_HANDSHAKING) {
                return true;
            }

            while (true) {

                if (isLoggingFinest) {
                    logger.finest("Loop Engine: " + sslEngine
                            + " handshakeStatus=" + sslEngine.getHandshakeStatus());
                }

                switch (handshakeStatus) {
                    case NEED_UNWRAP: {

                        if (isLoggingFinest) {
                            logger.finest("NEED_UNWRAP Engine: " + sslEngine);
                        }

                        return false;
                    }

                    case NEED_WRAP: {
                        if (isLoggingFinest) {
                            logger.finest("NEED_WRAP Engine: " + sslEngine);
                        }

                        streamWriter.writeBuffer(BufferUtils.EMPTY_BUFFER);
                        streamWriter.flush();
                        handshakeStatus = sslEngine.getHandshakeStatus();

                        break;
                    }

                    case NEED_TASK: {
                        if (isLoggingFinest) {
                            logger.finest("NEED_TASK Engine: " + sslEngine);
                        }
                        SSLUtils.executeDelegatedTask(sslEngine);
                        handshakeStatus = sslEngine.getHandshakeStatus();
                        break;
                    }
                    default: {
                        throw new RuntimeException("Invalid Handshaking State"
                                + handshakeStatus);
                    }
                }

                if (handshakeStatus == HandshakeStatus.FINISHED) {
                    return true;
                }
            }
        }
    }

    protected final class HandshakeCompletionHandler extends
            CompletionHandlerWrapper<SSLEngine, Integer> {

        public HandshakeCompletionHandler(FutureImpl<SSLEngine> future,
                CompletionHandler<SSLEngine> completionHandler) {
            super(future, completionHandler);
        }
    }
}
