/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.samples.lifecycle;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Sample {@link org.glassfish.grizzly.filterchain.Filter}, which tracks the connections
 * lifecycle.  The new connections could be either accepted if we have server,
 * or connected, if we establish client connection.
 *
 * @author Alexey Stashok
 */
public class LifeCycleFilter extends BaseFilter {
    private Attribute<Integer> connectionIdAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("connection-id");

    private AtomicInteger totalConnectionNumber;
    private Map<Connection, Integer> activeConnectionsMap;

    public LifeCycleFilter() {
        totalConnectionNumber = new AtomicInteger();
        activeConnectionsMap = DataStructures.<Connection, Integer>getConcurrentMap();
    }

    /**
     * Method is called, when new {@link Connection} was
     * accepted by a {@link org.glassfish.grizzly.Transport}
     *
     * @param ctx the filter chain context
     * @return the next action to be executed by chain
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        newConnection(ctx.getConnection());

        return ctx.getInvokeAction();
    }


    /**
     * Method is called, when new client {@link Connection} was
     * connected to some endpoint
     *
     * @param ctx the filter chain context
     * @return the next action to be executed by chain
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        newConnection(ctx.getConnection());

        return ctx.getInvokeAction();
    }

    /**
     * Method is called, when the {@link Connection} is getting closed
     *
     * @param ctx the filter chain context
     * @return the next action to be executed by chain
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        activeConnectionsMap.remove(ctx.getConnection());
        return super.handleClose(ctx);
    }

    /**
     * Add connection to the {@link Map>
     *
     * @param connection new {@link Connection}
     */
    private void newConnection(Connection connection) {
        final Integer id = totalConnectionNumber.incrementAndGet();
        connectionIdAttribute.set(connection, id);
        activeConnectionsMap.put(connection, id);
    }

    /**
     * Returns the total number of connections ever
     * created by the {@link org.glassfish.grizzly.Transport}
     *
     * @return the total number of connections ever
     * created by the {@link org.glassfish.grizzly.Transport}
     */
    public int getTotalConnections() {
        return totalConnectionNumber.get();
    }

    /**
     * Returns the {@link Set} of currently active {@link Connection}s.
     *
     * @return the {@link Set} of currently active {@link Connection}s
     */
    public Set<Connection> getActiveConnections() {
        return activeConnectionsMap.keySet();
    }
}
