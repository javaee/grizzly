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

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.impl.ReadyFutureImpl;

/**
 * Blocking {@link SSLHandshaker} implementation.
 *
 * @see SSLHandshaker
 * 
 * @author Alexey Stashok
 */
public class BlockingSSLHandshaker implements SSLHandshaker {
    private Logger logger = Grizzly.logger;

    /**
     * {@inheritDoc}
     */
    public Future<SSLEngine> handshake(
            SSLStreamReader reader,
            SSLStreamWriter writer,
            SSLEngineConfigurator configurator) throws IOException {

        Connection connection = reader.getConnection();
        
        SSLResourcesAccessor resourceAccessor =
                SSLResourcesAccessor.getInstance();
        SSLEngine sslEngine = resourceAccessor.getSSLEngine(connection);

        if (sslEngine == null) {
            sslEngine = configurator.createSSLEngine();
            resourceAccessor.setSSLEngine(connection, sslEngine);
        }

        boolean isLoggingFinest = logger.isLoggable(Level.FINEST);

        if (isLoggingFinest) {
            logger.finest("connection=" + connection + " engine=" + sslEngine +
                    " handshakeStatus=" + sslEngine.getHandshakeStatus());
        }

        HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();

        if (handshakeStatus == HandshakeStatus.NOT_HANDSHAKING) {
            sslEngine.beginHandshake();
            handshakeStatus = sslEngine.getHandshakeStatus();
        }

        boolean readerMode = reader.isBlocking();
        boolean writerMode = writer.isBlocking();

        try {
            reader.setBlocking(true);
            writer.setBlocking(true);

            while (handshakeStatus != HandshakeStatus.FINISHED &&
                    handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {

                if (isLoggingFinest) {
                    logger.finest("Loop Engine: " + sslEngine +
                            " handshakeStatus=" + sslEngine.getHandshakeStatus());
                }

                switch (handshakeStatus) {
                    case NEED_UNWRAP:
                    {
                        if (isLoggingFinest) {
                            logger.finest("NEED_UNWRAP Engine: " + sslEngine);
                        }

                        Future future = reader.handshakeUnwrap(null);
                        future.get(10, TimeUnit.SECONDS);
                        handshakeStatus = sslEngine.getHandshakeStatus();
                        break;
                    }
                    case NEED_WRAP:
                    {
                        if (isLoggingFinest) {
                            logger.finest("NEED_WRAP Engine: " + sslEngine);
                        }

                        Future future = writer.handshakeWrap(null);
                        future.get(10, TimeUnit.SECONDS);
                        handshakeStatus = sslEngine.getHandshakeStatus();
                        break;
                    }
                    case NEED_TASK:
                    {
                        if (isLoggingFinest) {
                            logger.finest("NEED_TASK Engine: " + sslEngine);
                        }
                        SSLUtils.executeDelegatedTask(sslEngine);
                        handshakeStatus = sslEngine.getHandshakeStatus();
                        break;
                    }
                    default:
                    {
                        throw new RuntimeException("Invalid Handshaking State" +
                                handshakeStatus);
                    }
                }
            }
        } catch (Exception e) {
            return new ReadyFutureImpl<SSLEngine>(e);
        } finally {
            reader.setBlocking(readerMode);
            writer.setBlocking(writerMode);
        }

        return new ReadyFutureImpl<SSLEngine>(sslEngine);
    }
}
