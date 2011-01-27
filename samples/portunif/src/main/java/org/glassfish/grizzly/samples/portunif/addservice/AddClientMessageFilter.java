/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.portunif.addservice;

import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * The ADD-service message parser/serializer, which is responsible for parsing
 * {@link AddResponseMessage} and serializing {@link AddRequestMessage}.
 * 
 * @author Alexey Stashok
 */
public class AddClientMessageFilter extends BaseFilter {
    private final static int MESSAGE_SIZE = 4;  // BODY = RESULT(INT) = 4
    
    /**
     * Handle just read operation, when some message has come and ready to be
     * processed.
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        // Take input buffer
        final Buffer input = ctx.getMessage();

        // If the available data is not enough to parse the message - stop
        if (input.remaining() < MESSAGE_SIZE) {
            return ctx.getStopAction(input);
        }

        // Read result
        final int result = input.getInt(0);

        // Construct AddResponseMessage, based on the result
        final AddResponseMessage addResponseMessage =
                new AddResponseMessage(result);
        // set the AddResponseMessage on context
        ctx.setMessage(addResponseMessage);

        // Split the remainder, if any
        final Buffer remainder = input.remaining() > MESSAGE_SIZE ?
            input.split(MESSAGE_SIZE) : null;

        // Try to dispose the parsed chunk
        input.tryDispose();

        // continue filter chain execution
        return ctx.getInvokeAction(remainder);
    }

    /**
     * Method is called, when we write a data to the Connection.
     *
     * We override this method to perform AddRequestMessage -> Buffer transformation.
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        // Take the source AddRequestMessage
        final AddRequestMessage addRequestMessage = ctx.getMessage();

        final int value1 = addRequestMessage.getValue1();
        final int value2 = addRequestMessage.getValue2();

        // Get MemoryManager
        final MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();
        // Allocate the Buffer
        final Buffer output = mm.allocate(11);
        // Add ADD-service magic
        output.put(AddServiceFilter.magic);
        // Add value1
        output.putInt(value1);
        // Add value2
        output.putInt(value2);
        
        // Allow Grizzly dispose this Buffer
        output.allowBufferDispose();

        // Set the Buffer to the context
        ctx.setMessage(output.flip());

        // continue filterchain execution
        return ctx.getInvokeAction();
    }

}
