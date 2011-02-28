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

package org.glassfish.grizzly.utils;

import org.glassfish.grizzly.attributes.AttributeStorage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.attributes.Attribute;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * String decoder, which decodes {@link Buffer} to {@link String}
 * 
 * @author Alexey Stashok
 */
public class StringDecoder extends AbstractTransformer<Buffer, String> {
    private static final Logger logger = Grizzly.logger(StringDecoder.class);
    
    protected Charset charset;
    
    protected final Attribute<Integer> lengthAttribute;

    protected byte[] stringTerminateBytes = null;

    public StringDecoder() {
        this(null, null);
    }

    public StringDecoder(String stringTerminator) {
        this(Charset.forName("UTF-8"), stringTerminator);
    }

    public StringDecoder(Charset charset) {
        this(charset, null);
    }

    public StringDecoder(Charset charset, String stringTerminator) {
        if (charset != null) {
            this.charset = charset;
        } else {
            this.charset = Charset.defaultCharset();
        }
        
        if (stringTerminator != null) {
            try {
                this.stringTerminateBytes = stringTerminator.getBytes(
                        this.charset.name());
            } catch (UnsupportedEncodingException e) {
                // should never happen as we are getting charset name from Charset
            }
        }

        lengthAttribute = attributeBuilder.createAttribute(
                "StringDecoder.StringSize");
    }

    @Override
    public String getName() {
        return "StringDecoder";
    }

    @Override
    protected TransformationResult<Buffer, String> transformImpl(
            AttributeStorage storage, Buffer input)
            throws TransformationException {

        if (input == null) {
            throw new TransformationException("Input could not be null");
        }

        final TransformationResult<Buffer, String> result;

        if (stringTerminateBytes == null) {
            result = parseWithLengthPrefix(storage, input);
        } else {
            result = parseWithTerminatingSeq(storage, input);
        }

        return result;
    }

    protected TransformationResult<Buffer, String> parseWithLengthPrefix(
            AttributeStorage storage, Buffer input) {
        Integer stringSize = lengthAttribute.get(storage);

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "StringDecoder decode stringSize={0} buffer={1} content={2}",
                    new Object[]{stringSize, input, input.toStringContent()});
        }

        if (stringSize == null) {
            if (input.remaining() < 4) {
                return TransformationResult.createIncompletedResult(input);
            }

            stringSize = (int) input.getInt();
            lengthAttribute.set(storage, stringSize);
        }
        
        if (input.remaining() < stringSize) {
            return TransformationResult.createIncompletedResult(input);
        }

        int tmpLimit = input.limit();
        input.limit(input.position() + stringSize);
        String stringMessage = input.toStringContent(charset);
        input.position(input.limit());
        input.limit(tmpLimit);

        return TransformationResult.createCompletedResult(
                stringMessage, input);
    }

    protected TransformationResult<Buffer, String> parseWithTerminatingSeq(
            AttributeStorage storage, Buffer input) {
        final int terminationBytesLength = stringTerminateBytes.length;
        int checkIndex = 0;
        
        int termIndex = -1;

        Integer offsetInt = lengthAttribute.get(storage);
        int offset = 0;
        if (offsetInt != null) {
            offset = offsetInt;
        }

        for(int i = input.position() + offset; i < input.limit(); i++) {
            if (input.get(i) == stringTerminateBytes[checkIndex]) {
                checkIndex++;
                if (checkIndex >= terminationBytesLength) {
                    termIndex = i - terminationBytesLength + 1;
                    break;
                }
            }
        }

        if (termIndex >= 0) {
            // Terminating sequence was found
            int tmpLimit = input.limit();
            input.limit(termIndex);
            String stringMessage = input.toStringContent(charset);
            input.limit(tmpLimit);
            input.position(termIndex + terminationBytesLength);
            return TransformationResult.createCompletedResult(
                    stringMessage, input);
        } else {
            offset = input.remaining() - terminationBytesLength;
            if (offset < 0) {
                offset = 0;
            }

            lengthAttribute.set(storage, offset);            
            return TransformationResult.createIncompletedResult(
                    input);
        }
    }

    @Override
    public void release(AttributeStorage storage) {
        lengthAttribute.remove(storage);
        super.release(storage);
    }

    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
        return input != null && input.hasRemaining();
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
