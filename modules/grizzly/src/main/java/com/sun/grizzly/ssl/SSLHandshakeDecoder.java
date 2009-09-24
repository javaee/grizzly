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

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.attributes.AttributeHolder;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 * {@link Transformer}, which works as the decoder on the SSL handshaking phase.
 *
 * @author Alexey Stashok
 */
public class SSLHandshakeDecoder implements Transformer<Buffer, Buffer> {
    private Logger logger = Grizzly.logger;
    
    private TransformationResult<Buffer> lastResult;
    private Buffer input;
    private Buffer output;
    
    @Override
    public TransformationResult transform(AttributeStorage state)
            throws TransformationException {
        return transform(state, getInput(state), getOutput(state));
    }

    @Override
    public TransformationResult transform(AttributeStorage state,
            Buffer input, Buffer output) throws TransformationException {

        SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
        SSLEngine sslEngine = accessor.getSSLEngine(state);
        assert sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP;

        if (input == null) {
            input = getInput(state);
        }

        if (output == null) {
            output = getOutput(state);
        }
        
        SSLEngineResult result = null;

        do {
            try {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("SSLHandshakeDecoder engine: " + sslEngine +
                            " input: " + input + " output: " + output);
                }

                result = sslEngine.unwrap((ByteBuffer) input.underlying(),
                        (ByteBuffer) output.underlying());
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("SSLHandshakeDecoder result: " + result +
                            " engine: " + sslEngine + " input: " + input +
                            " output: " + output);
                }
            } catch (SSLException e) {
                throw new TransformationException(e);
            }
        } while(input.hasRemaining() &&
                result.getStatus() == SSLEngineResult.Status.OK &&
                result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP);

        lastResult = new TransformationResult<Buffer>(Status.COMPLETED,
                output.duplicate().flip());

//        ((Connection) state).obtainProcessorLock(IOEvent.READ).lock();
        return lastResult;
    }

    @Override
    public Buffer getInput(AttributeStorage state) {
        if (input == null) {
            SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
            return accessor.obtainSecuredInBuffer(state);
        }
        
        return input;
    }

    @Override
    public void setInput(AttributeStorage state, Buffer input) {
        this.input = input;
    }

    @Override
    public Buffer getOutput(AttributeStorage state) {
        if (output == null) {
            SSLResourcesAccessor accessor = SSLResourcesAccessor.getInstance();
            return accessor.obtainAppBuffer(state);
        }
        
        return output;
    }

    @Override
    public void setOutput(AttributeStorage state, Buffer output) {
        this.output = output;
    }

    @Override
    public TransformationResult<Buffer> getLastResult(AttributeStorage state) {
        return lastResult;
    }

    @Override
    public AttributeHolder getProperties(AttributeStorage state) {
        return state.getAttributes();
    }

    @Override
    public void hibernate(AttributeStorage state) {
    }

    @Override
    public void release(AttributeStorage state) {
        lastResult = null;
        input = null;
        output = null;
    }
}
