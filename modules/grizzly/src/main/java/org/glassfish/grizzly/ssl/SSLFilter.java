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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.filterchain.CodecFilter;
import org.glassfish.grizzly.filterchain.FilterAdapter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.StopAction;
import org.glassfish.grizzly.threadpool.WorkerThread;

/**
 * SSL {@link Filter} to operate with SSL encrypted data.
 * 
 * @author Alexey Stashok
 */
public class SSLFilter extends FilterAdapter implements CodecFilter {
    private Logger logger = Grizzly.logger;
    
    private SSLCodec sslCodec;

    public SSLFilter() {
        this(null);
    }
    
    public SSLFilter(SSLCodec sslCodec) {
        this.sslCodec = sslCodec;
    }

    public SSLCodec getSslCodec() {
        return sslCodec;
    }

    public void setSslCodec(SSLCodec sslCodec) {
        this.sslCodec = sslCodec;
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        Connection connection = ctx.getConnection();

        SSLResourcesAccessor sslResourceAccessor =
                SSLResourcesAccessor.getInstance();
        SSLEngine sslEngine = sslResourceAccessor.getSSLEngine(connection);

        if (sslEngine == null) {
            // Initialize SSLEngine
            sslEngine =
                    sslCodec.getServerSSLEngineConfig().createSSLEngine();
            sslResourceAccessor.setSSLEngine(connection, sslEngine);
        }

        Buffer appBuffer = sslResourceAccessor.obtainAppBuffer(connection);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("connection=" + connection +
                    " engine=" + sslEngine +
                    " handshakeStatus=" + sslEngine.getHandshakeStatus() +
                    " message=" + ctx.getMessage() +
                    " appBuffer=" + appBuffer);
        }

        Buffer sourceMessage = (Buffer) ctx.getMessage();

        /*
         * Assuming sourceMessage is currently associated with this WorkerThread
         */
        sourceMessage = checkSecuredInputBuffer(connection);

        TransformationResult<Buffer> result =
                sslCodec.getDecoder().transform(connection,
                sourceMessage, appBuffer);

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("transformed. connection=" + connection +
                    " result=" + result +
                    " engine=" + sslEngine +
                    " handshakeStatus=" + sslEngine.getHandshakeStatus() +
                    " message=" + ctx.getMessage() +
                    " appBuffer=" + appBuffer);
        }

        switch (result.getStatus()) {
            case COMPLETED:
            case INCOMPLED:
                tryHandshake(connection, sslEngine);
                if (result.getMessage().hasRemaining()) {
                    ctx.setMessage(result.getMessage());
                } else {
                    nextAction = new StopAction();
                }
                break;
            default:
                nextAction = processTransformationError(ctx, nextAction);
        }

        return nextAction;
    }

    @Override
    public NextAction postRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        return nextAction;
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        Connection connection = ctx.getConnection();
        TransformationResult<Buffer> result =
                sslCodec.getEncoder().transform(connection,
                (Buffer) ctx.getMessage(), null);

        TransformationResult.Status status = result.getStatus();
        if (status == TransformationResult.Status.COMPLETED) {
            ctx.setMessage(result.getMessage());
        } else if (status == TransformationResult.Status.INCOMPLED) {
            nextAction = new StopAction();
        } else {
            nextAction = processTransformationError(ctx, nextAction);
        }


        return nextAction;
    }

    @Override
    public NextAction postWrite(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        return nextAction;
    }

    @Override
    public NextAction postClose(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        SSLResourcesAccessor.getInstance().clear(ctx.getConnection());
        return nextAction;
    }

    public Transformer<Buffer, Buffer> getDecoder() {
        return sslCodec.getDecoder();
    }

    public Transformer<Buffer, Buffer> getEncoder() {
        return sslCodec.getEncoder();
    }

    protected NextAction processTransformationError(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        ctx.getConnection().close();
        return new StopAction();
    }

    private Buffer checkSecuredInputBuffer(Connection connection) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        Buffer obtainedBuffer = accessor.obtainSecuredInBuffer(connection);
        
        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        Buffer workerThreadBuffer = accessor.getSecuredInBuffer(workerThread);
        if (workerThreadBuffer != obtainedBuffer) {
            accessor.setSecuredInBuffer(workerThread, obtainedBuffer);
        }

        return obtainedBuffer;
    }

    private void tryHandshake(Connection connection, SSLEngine sslEngine)
            throws IOException {
        
        HandshakeStatus status = sslEngine.getHandshakeStatus();
        if (status != HandshakeStatus.FINISHED &&
                status != HandshakeStatus.NOT_HANDSHAKING) {
            sslCodec.handshake(connection,
                    sslCodec.getServerSSLEngineConfig());
        }
    }
}
