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
package org.glassfish.grizzly.util;

import org.glassfish.grizzly.attributes.AttributeStorage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.attributes.Attribute;

/**
 * String decoder, which decodes {@link Buffer} to {@link String}
 * 
 * @author Alexey Stashok
 */
public class StringDecoder extends AbstractTransformer<Buffer, String> {

    protected Charset charset;
    
    protected Attribute<Integer> stateAttribute;

    protected byte[] stringTerminateBytes = null;

    public StringDecoder() {
        this((String) null);
    }

    public StringDecoder(String stringTerminator) {
        this(Charset.forName("UTF-8"), stringTerminator);
    }

    public StringDecoder(Charset charset) {
        this(charset, null);
    }

    public StringDecoder(Charset charset, String stringTerminator) {
        this.charset = charset;
        if (stringTerminator != null) {
            try {
                this.stringTerminateBytes = stringTerminator.getBytes(
                        charset.name());
            } catch (UnsupportedEncodingException e) {
                // should never happen as we are getting charset name from Charset
            }
        }

        stateAttribute = attributeBuilder.<Integer>createAttribute(
                "StringDecoder.StringSize");
    }

    public TransformationResult<String> transform(AttributeStorage storage,
            Buffer input, String output) throws TransformationException {

        if (input == null) {
            throw new TransformationException("Input could not be null");
        }

        if (stringTerminateBytes == null) {
            return parseWithLengthPrefix(storage, input);
        } else {
            return parseWithTerminatingSeq(storage, input);
        }
    }

    protected TransformationResult<String> parseWithLengthPrefix(
            AttributeStorage storage, Buffer input) {
        Integer stringSize = stateAttribute.get(storage);

        if (stringSize == null) {
            if (input.remaining() < 2) {
                saveState(storage, incompletedResult, null);
                return incompletedResult;
            }

            stringSize = (int) input.getShort();
        }

        if (input.remaining() < stringSize) {
            saveState(storage, incompletedResult, stringSize);
            return incompletedResult;
        }

        int tmpLimit = input.limit();
        input.limit(input.position() + stringSize);
        String stringMessage = input.contentAsString(charset);
        input.position(input.limit());
        input.limit(tmpLimit);

        TransformationResult<String> result =
                new TransformationResult<String>(Status.COMPLETED, stringMessage);
        saveState(storage, result, stringSize);

        return result;
    }

    protected TransformationResult<String> parseWithTerminatingSeq(
            AttributeStorage storage, Buffer input) {
        final int terminationBytesLength = stringTerminateBytes.length;
        int checkIndex = 0;
        
        int termIndex = -1;

        Integer offsetInt = stateAttribute.get(storage);
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

        TransformationResult<String> result;
        if (termIndex >= 0) {
            // Terminating sequence was found
            int tmpLimit = input.limit();
            input.limit(termIndex);
            String stringMessage = input.contentAsString(charset);
            input.limit(tmpLimit);
            input.position(termIndex + terminationBytesLength);
            result = new TransformationResult<String>(Status.COMPLETED,
                    stringMessage);
        } else {
            offset = input.remaining() - terminationBytesLength;
            if (offset < 0) {
                offset = 0;
            }

            result = incompletedResult;
        }

        saveState(storage, result, offset);
        return result;
    }

    @Override
    public void release(AttributeStorage storage) {
        stateAttribute.remove(storage);
        super.release(storage);
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    protected void saveState(AttributeStorage storage,
            TransformationResult<String> result, Integer state) {
        lastResultAttribute.set(storage, result);
        stateAttribute.set(storage, state);
    }
}
