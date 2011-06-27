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

package org.glassfish.grizzly.ssl;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.TransformerStreamWriter;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.utils.CompletionHandlerAdapter;
import org.glassfish.grizzly.utils.conditions.Condition;
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
            logger.log(Level.FINEST, "connection={0} engine={1} handshakeStatus={2}",
                    new Object[]{connection, sslEngine, sslEngine.getHandshakeStatus()});
        }

        HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();

        if (handshakeStatus == HandshakeStatus.NOT_HANDSHAKING) {
            sslEngine.beginHandshake();
        }

        final FutureImpl<SSLEngine> future = SafeFutureImpl.create();

        final HandshakeCompletionHandler hsCompletionHandler =
                new HandshakeCompletionHandler(future, completionHandler, sslEngine);

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

    protected static class SSLHandshakeCondition implements Condition {

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
                    logger.log(Level.FINEST, "Loop Engine: {0} handshakeStatus={1}",
                            new Object[]{sslEngine, sslEngine.getHandshakeStatus()});
                }

                switch (handshakeStatus) {
                    case NEED_UNWRAP: {

                        if (isLoggingFinest) {
                            logger.log(Level.FINEST, "NEED_UNWRAP Engine: {0}",
                                    sslEngine);
                        }

                        return false;
                    }

                    case NEED_WRAP: {
                        if (isLoggingFinest) {
                            logger.log(Level.FINEST, "NEED_WRAP Engine: {0}",
                                    sslEngine);
                        }

                        streamWriter.writeBuffer(Buffers.EMPTY_BUFFER);
                        streamWriter.flush();
                        handshakeStatus = sslEngine.getHandshakeStatus();

                        break;
                    }

                    case NEED_TASK: {
                        if (isLoggingFinest) {
                            logger.log(Level.FINEST, "NEED_TASK Engine: {0}",
                                    sslEngine);
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

    protected static final class HandshakeCompletionHandler extends
            CompletionHandlerAdapter<SSLEngine, Integer> {

        final SSLEngine sslEngine;

        public HandshakeCompletionHandler(FutureImpl<SSLEngine> future,
                CompletionHandler<SSLEngine> completionHandler, SSLEngine sslEngine) {
            super(future, completionHandler);
            this.sslEngine = sslEngine;
        }

        @Override
        protected SSLEngine adapt(Integer result) {
            return sslEngine;
        }
    }
}
