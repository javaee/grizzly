/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.ajp;

import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * Filter, responsible for parsing Ajp requests and making sure the request
 * packets are complete and properly constructed.
 * 
 * @author Alexey Stashok
 */
public class AjpMessageFilter extends BaseFilter {
    private final Attribute<ParsingState> parsingStateAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            AjpMessageFilter.class + ".parsingStateAttribute",
            new NullaryFunction<ParsingState>() {

                @Override
                public ParsingState evaluate() {
                    return new ParsingState();
                }
            });

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Buffer buffer = ctx.getMessage();
        final Connection connection = ctx.getConnection();

        final ParsingState parsingState = parsingStateAttribute.get(connection);

        // Have we read the AJP message header?
        if (!parsingState.isHeaderParsed) {
            if (buffer.remaining() < AjpConstants.H_SIZE) {
                return ctx.getStopAction(buffer);
            }

            final int start = buffer.position();

            final int mark = buffer.getShort(start);

            if (mark != 0x1234 && mark != 0x4142) {
                throw new IllegalStateException("Unexpected mark=" + mark);
            }

            parsingState.length = buffer.getShort(start + 2);
            parsingState.isHeaderParsed = true;

            if (parsingState.length + AjpConstants.H_SIZE >
                    AjpConstants.MAX_PACKET_SIZE) {
                throw new IllegalStateException("The message is too large. " +
                        (parsingState.length + AjpConstants.H_SIZE) + ">" +
                        AjpConstants.MAX_PACKET_SIZE);
            }
        }

        // Do we have the entire content?
        if (buffer.remaining() < AjpConstants.H_SIZE + parsingState.length) {
            return ctx.getStopAction(buffer);
        }

        // Message is ready

        final int start = buffer.position();

        // Split off the remainder
        final Buffer remainder = buffer.split(start + parsingState.length +
                AjpConstants.H_SIZE);

        // Skip the Ajp message header
        buffer.position(start + 4);
        
        parsingState.parsed();

        // Invoke the next filter
        return ctx.getInvokeAction(remainder.hasRemaining() ? remainder : null);
    }

    static final class ParsingState {
        boolean isHeaderParsed;
        int length;

        void parsed() {
            isHeaderParsed = false;
            length = 0;
        }

        void reset() {
            isHeaderParsed = false;
            length = 0;
        }
    }
}
