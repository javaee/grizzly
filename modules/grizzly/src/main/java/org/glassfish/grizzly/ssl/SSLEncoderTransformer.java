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

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.threadpool.WorkerThread;
import org.glassfish.grizzly.util.AttributeStorage;

/**
 * <tt>Transformer</tt>, which encrypts plain data, contained in the
 * input Buffer, into SSL/TLS data and puts the result to the output Buffer.
 *
 * @author Alexey Stashok
 */
public class SSLEncoderTransformer implements Transformer<Buffer, Buffer> {
    public static final int NEED_HANDSHAKE_ERROR = 1;
    public static final int BUFFER_UNDERFLOW_ERROR = 2;
    public static final int BUFFER_OVERFLOW_ERROR = 3;

    private static Attribute<TransformationResult<Buffer>> lastResultAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "SSLEncoderTransformer.lastResult");
    
    private Logger logger = Grizzly.logger;
    
    public TransformationResult<Buffer> transform(AttributeStorage state)
            throws TransformationException {
        return transform(state, getInput(state), getOutput(state));
    }

    public TransformationResult<Buffer> transform(AttributeStorage state,
            Buffer originalMessage, Buffer targetMessage)
            throws TransformationException {

        SSLResourcesAccessor resourceAccessor =
                SSLResourcesAccessor.getInstance();
        SSLEngine sslEngine =
                resourceAccessor.getSSLEngine(state);

        if (sslEngine == null) {
            // There is no SSLEngine associated with the connection yet
            throw new IllegalStateException("There is no SSLEngine associated!" +
                    " May be handshake phase was not executed?");
        }

        if (originalMessage == null) {
            originalMessage = getInput(state);
        }

        if (targetMessage == null) {
            targetMessage = getOutput(state);
        }
        
        TransformationResult<Buffer> transformationResult = null;
        SSLEngineResult sslEngineResult = null;
        
        do {
            try {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("SSLEncoder engine: " + sslEngine + " input: " +
                            originalMessage + " output: " + targetMessage);
                }

                sslEngineResult =
                        sslEngine.wrap((ByteBuffer) originalMessage.underlying(),
                        (ByteBuffer) targetMessage.underlying());

                SSLEngineResult.Status status = sslEngineResult.getStatus();

                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("SSLEncoder done engine: " + sslEngine +
                            " result: " + sslEngineResult +
                            " input: " + originalMessage +
                            " output: " + targetMessage);
                }
            } catch (SSLException e) {
                throw new TransformationException(e);
            }
        } while (originalMessage.hasRemaining() &&
                sslEngineResult.getStatus() == SSLEngineResult.Status.OK);

        SSLEngineResult.Status sslEngineStatus = sslEngineResult.getStatus();
        switch (sslEngineStatus) {
            case OK:
                if (originalMessage.hasRemaining()) {
                    throw new IllegalStateException(
                            "Status is OK, but still have something to process? Buffer: " +
                            originalMessage);
                }
            case CLOSED:
                transformationResult = new TransformationResult<Buffer>(
                        Status.COMPLETED,
                        targetMessage.duplicate().flip());
                break;
            case BUFFER_UNDERFLOW:
                transformationResult = new TransformationResult<Buffer>(
                        BUFFER_UNDERFLOW_ERROR,
                        "Buffer underflow during wrap operation");
                break;
            case BUFFER_OVERFLOW:
                Buffer resultBuffer = targetMessage.duplicate().flip();
                if (resultBuffer.hasRemaining()) {
                    targetMessage.clear();
                    transformationResult =
                            new TransformationResult<Buffer>(
                            Status.INCOMPLED, resultBuffer);
                } else {
                    transformationResult = new TransformationResult<Buffer>(
                            BUFFER_OVERFLOW_ERROR,
                            "Buffer overflow during wrap operation");
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unexpected SSLEngine state: " + sslEngineStatus);
        }


        lastResultAttr.set(state.obtainAttributes(), transformationResult);
        
        return transformationResult;
    }

    public Buffer getInput(AttributeStorage state) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        return accessor.obtainAppBuffer(state);
    }

    public void setInput(AttributeStorage state, Buffer input) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        accessor.setAppBuffer(state, input);
    }

    public Buffer getOutput(AttributeStorage state) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        return accessor.obtainSecuredOutBuffer(state);
    }

    public void setOutput(AttributeStorage state, Buffer outputTarget) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        accessor.setSecuredOutBuffer(state, outputTarget);
    }

    public TransformationResult<Buffer> getLastResult(AttributeStorage state) {
        return lastResultAttr.get(state);
    }

    public AttributeHolder getProperties(AttributeStorage state) {
        return state.getAttributes();
    }

    public void hibernate(AttributeStorage state) {
        TransformationResult<Buffer> lastResult = getLastResult(state);
        if (lastResult != null) {
            Buffer lastResultMessage = lastResult.getMessage();
            if (lastResultMessage != null && lastResultMessage.hasRemaining()) {
                Buffer outputBuffer = getOutput(state);
                if (detachOutputFromWorkerThread(outputBuffer)) {
                    setOutput(state, outputBuffer);
                }
            }
        }
    }

    public void release(AttributeStorage state) {
        AttributeHolder holder = state.getAttributes();
        if (holder != null) {
            lastResultAttr.remove(holder);
        }

        Buffer output = getOutput(state);
        output.clear();
    }

    private boolean detachOutputFromWorkerThread(Buffer output) {
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkerThread) {
            WorkerThread workerThread = (WorkerThread) thread;
            SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
            if (accessor.getSecuredOutBuffer(workerThread) == output) {
                accessor.setSecuredOutBuffer(workerThread, null);
                return true;
            }
        }
        
        return false;
    }
}
