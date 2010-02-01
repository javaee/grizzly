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
import com.sun.grizzly.TransportFactory;

/**
 * String decoder, which decodes {@link Buffer} to {@link String}
 *
 * @author Alexey Stashok
 */
public class StringEncoder extends AbstractTransformer<String, Buffer> {

    protected Charset charset;

    protected String stringTerminator = null;

    public StringEncoder() {
        this((String) null);
    }

    public StringEncoder(String stringTerminator) {
        this(null, null);
    }

    public StringEncoder(Charset charset) {
        this(charset, null);
    }

    public StringEncoder(Charset charset, String stringTerminator) {
        if (charset != null) {
            this.charset = charset;
        } else {
            this.charset = Charset.defaultCharset();
        }

        this.stringTerminator = stringTerminator;
    }

    @Override
    public String getName() {
        return "StringEncoder";
    }

    @Override
    public TransformationResult<String, Buffer> transform(AttributeStorage storage,
            String input) throws TransformationException {

        if (input == null) {
            throw new TransformationException("Input could not be null");
        }

        Buffer output = getOutput(storage);

        byte[] byteRepresentation;
        try {
            if (stringTerminator != null) {
                input = input + stringTerminator;
            }
            
            byteRepresentation = input.getBytes(charset.name());
        } catch(UnsupportedEncodingException e) {
            throw new TransformationException("Charset " +
                    charset.name() + " is not supported", e);
        }

        final boolean isAllocated = (output == null);
        if (isAllocated) {
            output = TransportFactory.getInstance().getDefaultMemoryManager().
                    allocate(byteRepresentation.length + 2);
        }

        if (stringTerminator == null) {
            output.putShort((short) byteRepresentation.length);
        }

        output.put(byteRepresentation);

        if (isAllocated) {
            output.flip();
        }

        final TransformationResult<String, Buffer> result =
                TransformationResult.<String, Buffer>createCompletedResult(
                output, null, false);
        lastResultAttribute.set(storage, result);
        return result;
    }

    @Override
    public boolean hasInputRemaining(String input) {
        return input != null;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
