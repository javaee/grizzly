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
package org.glassfish.grizzly.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;

/**
 * Utility class, which implements the set of useful SSL related operations.
 * 
 * @author Alexey Stashok
 */
public class SSLUtils {

    public static SSLEngineResult unwrap(SSLEngine sslEngine,
            Buffer securedInBuffer, Buffer plainBuffer) throws IOException {
        return sslEngine.unwrap((ByteBuffer) securedInBuffer.underlying(),
                (ByteBuffer) plainBuffer.underlying());
    }

    public static SSLEngineResult wrap(SSLEngine sslEngine, Buffer plainBuffer,
            Buffer securedOutBuffer) throws IOException {
        return sslEngine.wrap((ByteBuffer) plainBuffer.underlying(),
                (ByteBuffer) securedOutBuffer.underlying());
    }

    /**
     * Complete hanshakes operations.
     * @param sslEngine The SSLEngine used to manage the SSL operations.
     * @return SSLEngineResult.HandshakeStatus
     */
    public static void executeDelegatedTask(SSLEngine sslEngine) {

        Runnable runnable;
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
    }

    public static Future<SSLEngine> handshake(Connection connection)
            throws IOException {
        SSLResourcesAccessor resourceAccessor =
                SSLResourcesAccessor.getInstance();
        SSLEngine sslEngine = resourceAccessor.getSSLEngine(connection);
        
        SSLHandshakeFutureImpl future = new SSLHandshakeFutureImpl();
        future.setSSLEngine(sslEngine);
        future.setCompletionHandler(
                new SSLHandshakeCompletionHandler(future));
        
        if (sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
            sslEngine.beginHandshake();
        }

        continueHandshake(future, connection);
        
        return future;
    }

    protected static void continueHandshake(SSLHandshakeFutureImpl future,
            Connection connection) throws IOException {
        SSLResourcesAccessor resourceAccessor =
                SSLResourcesAccessor.getInstance();
        SSLEngine sslEngine = resourceAccessor.getSSLEngine(connection);

        Logger logger = Grizzly.logger;
        boolean isLoggingFinest = logger.isLoggable(Level.FINEST);
        
        if (isLoggingFinest) {
            logger.finest("connection=" + connection + " engine=" + sslEngine +
                    " handshakeStatus=" + sslEngine.getHandshakeStatus());
        }
        HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        while (handshakeStatus != HandshakeStatus.FINISHED &&
                handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {

            if (isLoggingFinest) {
                logger.finest("Loop Engine: " + sslEngine +
                        " handshakeStatus=" + sslEngine.getHandshakeStatus());
            }

            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    if (isLoggingFinest) {
                        logger.finest("NEED_UNWRAP Engine: " + sslEngine);
                    }
                    
//                    connection.read(null,
//                            future.getCompletionHandler(),
//                            new SSLHandshakeDecoder(), null);
                    connection.obtainProcessorLock(IOEvent.READ).unlock();
                    return;
                case NEED_WRAP:
                    if (isLoggingFinest) {
                        logger.finest("NEED_WRAP Engine: " + sslEngine);
                    }
//                    connection.write(null,
//                            connection.getTransport().getMemoryManager().allocate(0),
//                            future.getCompletionHandler(),
//                            new SSLHandshakeEncoder());
                    return;
                case NEED_TASK:
                    if (isLoggingFinest) {
                        logger.finest("NEED_TASK Engine: " + sslEngine);
                    }
                    executeDelegatedTask(sslEngine);
                    handshakeStatus = sslEngine.getHandshakeStatus();
                    break;
                default:
                    throw new RuntimeException("Invalid Handshaking State" +
                            handshakeStatus);
            }
        }

        connection.obtainProcessorLock(IOEvent.READ).unlock();
        future.setResult(sslEngine);
    }

    static void clearOrCompact(Buffer buffer) {
        if (buffer == null) {
            return;
        }

        if (!buffer.hasRemaining()) {
            buffer.clear();
        } else if (buffer.position() > 0) {
            buffer.compact();
        }
    }
}
