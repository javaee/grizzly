/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.utils;

import com.sun.grizzly.attributes.AttributeStorage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import com.sun.grizzly.AbstractTransformer;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.attributes.Attribute;

/**
 * String decoder, which decodes {@link Buffer} to {@link String}
 * 
 * @author Alexey Stashok
 */
public class StringDecoder extends AbstractTransformer<Buffer, String> {

    protected Charset charset;
    
    protected Attribute<Integer> lengthAttribute;

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
                        charset.name());
            } catch (UnsupportedEncodingException e) {
                // should never happen as we are getting charset name from Charset
            }
        }

        lengthAttribute = attributeBuilder.<Integer>createAttribute(
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

        if (stringSize == null) {
            if (input.remaining() < 2) {
                return TransformationResult.<Buffer, String>createIncompletedResult(input, false);
            }

            stringSize = (int) input.getShort();
            lengthAttribute.set(storage, stringSize);
        }

        if (input.remaining() < stringSize) {
            return TransformationResult.<Buffer, String>createIncompletedResult(input, false);
        }

        int tmpLimit = input.limit();
        input.limit(input.position() + stringSize);
        String stringMessage = input.toStringContent(charset);
        input.position(input.limit());
        input.limit(tmpLimit);

        return TransformationResult.<Buffer, String>createCompletedResult(
                stringMessage, input, false);
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

        TransformationResult<Buffer, String> result;
        if (termIndex >= 0) {
            // Terminating sequence was found
            int tmpLimit = input.limit();
            input.limit(termIndex);
            String stringMessage = input.toStringContent(charset);
            input.limit(tmpLimit);
            input.position(termIndex + terminationBytesLength);
            return TransformationResult.<Buffer, String>createCompletedResult(
                    stringMessage, input, false);
        } else {
            offset = input.remaining() - terminationBytesLength;
            if (offset < 0) {
                offset = 0;
            }

            return TransformationResult.<Buffer, String>createIncompletedResult(
                    input, false);
        }
    }

    @Override
    public void release(AttributeStorage storage) {
        lengthAttribute.remove(storage);
        super.release(storage);
    }

    @Override
    public boolean hasInputRemaining(Buffer input) {
        return input != null && input.hasRemaining();
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
