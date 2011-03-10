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
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;

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
    protected TransformationResult<String, Buffer> transformImpl(
            AttributeStorage storage, String input)
            throws TransformationException {

        if (input == null) {
            throw new TransformationException("Input could not be null");
        }

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

        final Buffer output =
                obtainMemoryManager(storage).allocate(byteRepresentation.length + 4);

        if (stringTerminator == null) {
            output.putInt(byteRepresentation.length);
        }

        output.put(byteRepresentation);

        output.flip();
        output.allowBufferDispose(true);

        return TransformationResult.createCompletedResult(output, null);
    }

    @Override
    public boolean hasInputRemaining(AttributeStorage storage, String input) {
        return input != null;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
