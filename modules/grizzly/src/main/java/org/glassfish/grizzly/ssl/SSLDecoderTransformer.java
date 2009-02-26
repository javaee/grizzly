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
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.util.AttributeStorage;

/**
 * <tt>Transformer</tt>, which decodes SSL encrypted data, contained in the
 * input Buffer, to the output Buffer.
 * 
 * @author Alexey Stashok
 */
public class SSLDecoderTransformer implements Transformer<Buffer, Buffer> {
    public static final int NEED_HANDSHAKE_ERROR = 1;
    public static final int BUFFER_UNDERFLOW_ERROR = 2;
    public static final int BUFFER_OVERFLOW_ERROR = 3;


    private Logger logger = Grizzly.logger;

    private static Attribute<TransformationResult<Buffer>> lastResultAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "SSLDecoderTransformer.lastResult");

    public TransformationResult<Buffer> transform(AttributeStorage state)
            throws TransformationException {
        return transform(state, getInput(state), getOutput(state));
    }

    public TransformationResult<Buffer> transform(AttributeStorage state,
            Buffer originalMessage, Buffer targetMessage)
            throws TransformationException {

        SSLResourcesAccessor resourceAccessor =
                SSLResourcesAccessor.getInstance();

        SSLEngine sslEngine = resourceAccessor.getSSLEngine(state);

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

        int totalConsumed = 0;
        int totalProduced = 0;
        
        do {
            try {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("SSLDecoder engine: " + sslEngine +
                            " input: " + originalMessage +
                            " output: " + targetMessage);
                }

                sslEngineResult = sslEngine.unwrap(
                        (ByteBuffer) originalMessage.underlying(),
                        (ByteBuffer) targetMessage.underlying());

                totalConsumed += sslEngineResult.bytesConsumed();
                totalProduced += sslEngineResult.bytesProduced();
                
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("SSLDecoder done engine: " + sslEngine +
                            " result: " + sslEngineResult +
                            " input: " + originalMessage +
                            " output: " + targetMessage);
                }
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "SSLDecoder done engine: " +
                            sslEngine +
                            " result: " + sslEngineResult +
                            " input: " + originalMessage +
                            " output: " + targetMessage,
                            e);
                }

                throw new TransformationException(e);
            }
        } while(originalMessage.hasRemaining() &&
                sslEngineResult.getStatus() == SSLEngineResult.Status.OK);
            
        SSLEngineResult.Status sslEngineStatus = sslEngineResult.getStatus();
        switch (sslEngineStatus) {
            case OK:
                if (originalMessage.hasRemaining()) {
                    transformationResult = new TransformationResult<Buffer>(
                            Status.INCOMPLED, targetMessage.duplicate().flip());
                    break;
                }
            case CLOSED:
                transformationResult = new TransformationResult<Buffer>(
                        Status.COMPLETED, targetMessage.duplicate().flip());
                break;
            case BUFFER_UNDERFLOW:
                transformationResult =
                        new TransformationResult<Buffer>(Status.INCOMPLED,
                        targetMessage.duplicate().flip());
                break;
            case BUFFER_OVERFLOW:
                Buffer resultBuffer = targetMessage.duplicate().flip();
                if (totalConsumed > 0 || totalProduced > 0) {
                    targetMessage.clear();
                    transformationResult =
                            new TransformationResult<Buffer>(
                            Status.INCOMPLED, resultBuffer);
                } else {
                    transformationResult = new TransformationResult<Buffer>(
                            BUFFER_OVERFLOW_ERROR,
                            "Buffer overflow during unwrap operation");
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unexpected SSLEngine status: " +
                        sslEngineStatus);
        }

        lastResultAttr.set(state.obtainAttributes(), transformationResult);

        return transformationResult;
    }

    public Buffer getInput(AttributeStorage state) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        return accessor.obtainSecuredInBuffer(state);
    }

    public void setInput(AttributeStorage state, Buffer input) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        accessor.setSecuredInBuffer(state, input);
    }

    public Buffer getOutput(AttributeStorage state) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        return accessor.obtainAppBuffer(state);
    }

    public void setOutput(AttributeStorage state, Buffer output) {
        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        accessor.setAppBuffer(state, output);
    }

    public TransformationResult<Buffer> getLastResult(AttributeStorage state) {
        AttributeHolder holder = state.getAttributes();
        if (holder != null) {
            return lastResultAttr.get(holder);
        }

        return null;
    }

    public AttributeHolder getProperties(AttributeStorage state) {
        return state.getAttributes();
    }

    public void hibernate(AttributeStorage state) {
    }

    public void release(AttributeStorage state) {
        AttributeHolder holder = state.getAttributes();
        if (holder != null) {
            SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
            accessor.setSecuredInBuffer(state, null);
            accessor.setAppBuffer(state, null);
            lastResultAttr.remove(holder);
        }
    }
}