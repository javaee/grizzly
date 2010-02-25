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
package com.sun.grizzly.samples.filterchain;

import com.sun.grizzly.AbstractTransformer;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 * {@link Transformer}, which transforms data, taken from {@link Buffer} to
 * {@link GIOPMessage}.
 * 
 * @author Alexey Stashok
 */
public class GIOPDecoder extends AbstractTransformer<Buffer, GIOPMessage> {

    private static final Attribute<Integer> stateAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("GIOPParseState");
    private static final Attribute<GIOPMessage> preparsedMessageAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("PreparsedGIOPMessage");

    @Override
    protected TransformationResult<Buffer, GIOPMessage> transformImpl(
            AttributeStorage storage,
            Buffer input) throws TransformationException {

        // check if we are in the middle of message parsing
        GIOPMessage message = preparsedMessageAttr.get(storage);
        // get the current parsing state
        Integer parseState = stateAttr.get(storage);

        if (message == null) {
            // if we just started to parse the message - create destination object
            message = new GIOPMessage();
            parseState = new Integer(0);
        }

        boolean isParsing = true;
        while (isParsing) {
            switch (parseState) {
                case 0: // GIOP 4 byte header
                {
                    if (input.remaining() >= 4) {
                        message.setGIOPHeader(input.get(), input.get(),
                                input.get(), input.get());
                        parseState++;
                    } else {
                        isParsing = false;
                        break;
                    }
                }

                case 1: // major, minor
                {
                    if (input.remaining() >= 2) {
                        message.setMajor(input.get());
                        message.setMinor(input.get());
                        parseState++;
                    } else {
                        isParsing = false;
                        break;
                    }
                }

                case 2: // flags
                {
                    if (input.remaining() >= 1) {
                        message.setFlags(input.get());
                        parseState++;
                    } else {
                        isParsing = false;
                        break;
                    }
                }

                case 3: // value
                {
                    if (input.remaining() >= 1) {
                        message.setValue(input.get());
                        parseState++;
                    } else {
                        isParsing = false;
                        break;
                    }
                }

                case 4: // body length
                {
                    if (input.remaining() >= 4) {
                        message.setBodyLength(input.getInt());
                        parseState++;
                    } else {
                        isParsing = false;
                        break;
                    }
                }

                case 5: // body
                {
                    int bodyLength = message.getBodyLength();
                    if (input.remaining() >= bodyLength) {
                        final byte[] body = new byte[bodyLength];
                        input.get(body);
                        message.setBody(body);
                        parseState++;
                    }

                    isParsing = false;
                    break;
                }
            }
        }

        if (parseState < 6) {  // Not enough data to parse whole message
            // Save the parsing state
            preparsedMessageAttr.set(storage, message);
            stateAttr.set(storage, parseState);

            // Stop the filterchain execution until more data available
            return TransformationResult.<Buffer, GIOPMessage>createIncompletedResult(input);
        } else {
            // Remove intermediate parsing state
            preparsedMessageAttr.remove(storage);
            stateAttr.remove(storage);

            return TransformationResult.<Buffer, GIOPMessage>createCompletedResult(message, input, false);
        }
    }

    @Override
    public String getName() {
        return "GIOPDecoder";
    }

    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
        return input != null && input.hasRemaining();
    }
}
