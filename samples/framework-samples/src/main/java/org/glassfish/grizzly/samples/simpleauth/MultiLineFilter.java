/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.simpleauth;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * The {@link org.glassfish.grizzly.filterchain.Filter} is responsible for a
 * {@link String} <-> {@link MultiLinePacket} transformations.
 *
 * When reading - filter is gathering single {@link String} lines into a MultiLinePacket,
 * when writing - filter breaks {@link MultiLinePacket} into a single {@link String}s.
 * 
 * @author Alexey Stashok
 */
public class MultiLineFilter extends BaseFilter {
    private static final Logger logger = Grizzly.logger(MultiLineFilter.class);

    // MultiLinePacket terminating line (the String line value, which indicates last line in a MultiLinePacket}.
    private final String terminatingLine;

    // Attribute to store the {@link MultiLinePacket} decoding state.
    private static final Attribute<MultiLinePacket> decoderPacketAttr =
            Attribute.create("Multiline-decoder-packet");

    // Attribute to store the {@link MultiLinePacket} encoding state.
    private static final Attribute<Integer> encoderPacketAttr =
            Attribute.create("Multiline-encoder-packet");
    
    public MultiLineFilter(String terminatingLine) {
        this.terminatingLine = terminatingLine;
    }

    /**
     * The method is called once we have received a single {@link String} line.
     * 
     * Filter check if it's {@link MultiLinePacket} terminating line, if yes -
     * we assume {@link MultiLinePacket} completed and pass control to a next
     * {@link org.glassfish.grizzly.filterchain.Filter} in a chain. If it's not a
     * terminating line - we add another string line to a {@link MultiLinePacket}
     * and stop the request processing until more strings will get available.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        // Get the Connection
        final Connection connection = ctx.getConnection();
        // Check if some MultiLinePacket is being parsed
        MultiLinePacket packet = decoderPacketAttr.get(connection);
        if (packet == null) {
            // If not - create a new one
            packet = MultiLinePacket.create();
            // store it as connection state
            decoderPacketAttr.set(connection, packet);
        }

        // Get parsed String
        final String input = (String) ctx.getMessage();

        // Check if it's not MultiLinePacket terminating string
        if (input.equals(terminatingLine)) {
            // If yes - clear connection associated MultiLinePacket
            decoderPacketAttr.remove(connection);
            // Set MultiLinePacket packet as a context message
            ctx.setMessage(packet);

            logger.log(Level.INFO, "--------Received from network: \n" + packet);

            // Pass control to a next filter in a chain
            return ctx.getInvokeAction();
        }

        // If this is not terminating line - add this line to a MultiLinePacket
        packet.getLines().add(input);
        // Stop the request processing
        return ctx.getStopAction();
    }

    /**
     * The method is called whem we send MultiLinePacket.
     *
     * Filter is responsible to split MultiLinePacket into a single Strings and
     * pass each String to a next Filter in chain.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {

        // Get a connection
        final Connection connection = ctx.getConnection();
        // Get a processing MultiLinePacket
        final MultiLinePacket input = (MultiLinePacket) ctx.getMessage();

        // Get the MultiLinePacket encoding state (the current encoding line number)
        Integer encodingLine = encoderPacketAttr.get(connection);
        if (encodingLine == null) {
            // if not state is associated - it means we're just starting MultiLinePacket encoding
            encodingLine = 0;
            logger.log(Level.INFO, "-------Sending to network: \n" + input);
        }

        // Check if the current encoding line is the last
        if (encodingLine == input.getLines().size()) {
            // if yes - finishing MultiLinePacket encoding
            // remove associated encoding state
            encoderPacketAttr.remove(connection);
            // set the terminatingLine to be passed to a next filter in a chain
            ctx.setMessage(terminatingLine);
            // pass control to a next filter in a chain
            return ctx.getInvokeAction();
        }

        // if it's not the last line
        // extract the single line from MultiLinePacket
        ctx.setMessage(input.getLines().get(encodingLine));
        
        // increment the current encoding line counter
        encodingLine++;
        // save the state
        encoderPacketAttr.set(connection, encodingLine);

        // pass control to a next filter in chain and pass input (MultiLinePacket) as
        // a remainder notifying the filter chain, that we didn't process
        // entire message yet.
        return ctx.getInvokeAction(input);
    }
}
