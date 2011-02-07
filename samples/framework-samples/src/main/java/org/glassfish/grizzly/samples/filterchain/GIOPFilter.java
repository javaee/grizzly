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

package org.glassfish.grizzly.samples.filterchain;

import java.io.IOException;
import java.util.logging.Filter;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * Example of parser {@link Filter},
 * which is response for Buffer <-> GIOPMessage transformation.
 *
 * @author Alexey Stashok
 */
public final class GIOPFilter extends BaseFilter {

    private static final int HEADER_SIZE = 12;

    /**
     * Method is called, when new data was read from the Connection and ready
     * to be processed.
     *
     * We override this method to perform Buffer -> GIOPMessage transformation.
     * 
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        // Get the source buffer from the context
        final Buffer sourceBuffer = ctx.getMessage();

        final int sourceBufferLength = sourceBuffer.remaining();

        // If source buffer doesn't contain header
        if (sourceBufferLength < HEADER_SIZE) {
            // stop the filterchain processing and store sourceBuffer to be
            // used next time
            return ctx.getStopAction(sourceBuffer);
        }

        // Get the body length
        final int bodyLength = sourceBuffer.getInt(HEADER_SIZE - 4);
        // The complete message length
        final int completeMessageLength = HEADER_SIZE + bodyLength;
        
        // If the source message doesn't contain entire body
        if (sourceBufferLength < completeMessageLength) {
            // stop the filterchain processing and store sourceBuffer to be
            // used next time
            return ctx.getStopAction(sourceBuffer);
        }

        // Check if the source buffer has more than 1 complete GIOP message
        // If yes - split up the first message and the remainder
        final Buffer remainder = sourceBufferLength > completeMessageLength ? 
            sourceBuffer.split(completeMessageLength) : null;

        // Construct a GIOP message
        final GIOPMessage giopMessage = new GIOPMessage();

        // Set GIOP header bytes
        giopMessage.setGIOPHeader(sourceBuffer.get(), sourceBuffer.get(),
                sourceBuffer.get(), sourceBuffer.get());

        // Set major version
        giopMessage.setMajor(sourceBuffer.get());

        // Set minor version
        giopMessage.setMinor(sourceBuffer.get());

        // Set flags
        giopMessage.setFlags(sourceBuffer.get());

        // Set value
        giopMessage.setValue(sourceBuffer.get());

        // Set body length
        giopMessage.setBodyLength(sourceBuffer.getInt());

        // Read body
        final byte[] body = new byte[bodyLength];
        sourceBuffer.get(body);
        // Set body
        giopMessage.setBody(body);

        ctx.setMessage(giopMessage);

        // We can try to dispose the buffer
        sourceBuffer.tryDispose();

        // Instruct FilterChain to store the remainder (if any) and continue execution
        return ctx.getInvokeAction(remainder);
    }

    /**
     * Method is called, when we write a data to the Connection.
     *
     * We override this method to perform GIOPMessage -> Buffer transformation.
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        // Get the source GIOP message to be written
        final GIOPMessage giopMessage = ctx.getMessage();

        final int size = HEADER_SIZE + giopMessage.getBodyLength();

        // Retrieve the memory manager
        final MemoryManager memoryManager =
                ctx.getConnection().getTransport().getMemoryManager();

        // allocate the buffer of required size
        final Buffer output = memoryManager.allocate(size);

        // Allow Grizzly core to dispose the buffer, once it's written
        output.allowBufferDispose(true);
        
        // GIOP header
        output.put(giopMessage.getGIOPHeader());

        // Major version
        output.put(giopMessage.getMajor());

        // Minor version
        output.put(giopMessage.getMinor());

        // Flags
        output.put(giopMessage.getFlags());

        // Value
        output.put(giopMessage.getValue());

        // Body length
        output.putInt(giopMessage.getBodyLength());

        // Body
        output.put(giopMessage.getBody());

        // Set the Buffer as a context message
        ctx.setMessage(output.flip());

        // Instruct the FilterChain to call the next filter
        return ctx.getInvokeAction();
    }
}
