/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
 * {@link List&lt;String&gt;} <-> {@link MultiLinePacket} transformations.
 *
 * When reading - filter is gathering {@link String} lines into a MultiLinePacket,
 * when writing - filter breaks {@link MultiLinePacket} into {@link String} list.
 * 
 * @author Alexey Stashok
 */
public class MultiLineFilter extends BaseFilter {
    private static final Logger LOGGER = Grizzly.logger(MultiLineFilter.class);

    // MultiLinePacket terminating line (the String line value, which indicates last line in a MultiLinePacket}.
    private final String terminatingLine;

    // Attribute to store the {@link String} list decoding state.
    private static final Attribute<MultiLinePacket> incompletePacketAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("Multiline-decoder-packet");

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

        // Get parsed String
        List<String> input = ctx.getMessage();
        
        MultiLinePacket packet = incompletePacketAttr.remove(connection);
        if (packet == null) {
            packet = MultiLinePacket.create();
        }

        boolean foundTerm = false;
        for (Iterator<String> it = input.iterator(); it.hasNext(); ) {
            final String line = it.next();
            it.remove();
            
            if (line.equals(terminatingLine)) {
                foundTerm = true;
                break;
            }
            
            packet.getLines().add(line);
        }
        
        if (!foundTerm) {
            incompletePacketAttr.set(connection, packet);
            return ctx.getStopAction();
        }
        
        // Set MultiLinePacket packet as a context message
        ctx.setMessage(packet);
        LOGGER.log(Level.INFO, "-------- Received from network:\n{0}", packet);
        
        return input.isEmpty()
                ? ctx.getInvokeAction()
                : ctx.getInvokeAction(input);
        
    }

    /**
     * The method is called when we send MultiLinePacket.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {

        // Get a processing MultiLinePacket
        final MultiLinePacket input = ctx.getMessage();

        LOGGER.log(Level.INFO, "------- Sending to network:\n{0}", input);

        // pass MultiLinePacket as List<String>.
        // we could've used input.getLines() as collection to be passed
        // downstream, but we don't want to modify it (adding and removing terminatingLine).
        final List<String> stringList =
                new ArrayList<String>(input.getLines().size() + 1);
        stringList.addAll(input.getLines());
        stringList.add(terminatingLine);
        
        ctx.setMessage(stringList);
        
        return ctx.getInvokeAction();
    }
}
