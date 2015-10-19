/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Filter, which determines silent connections and closes them.
 * The silent connection is a connection, which didn't send/receive any byte
 * since it was accepted during specified period of time.
 * 
 * @author Alexey Stashok
 */
public final class SilentConnectionFilter extends BaseFilter {
    private static final Logger LOGGER = Grizzly.logger(SilentConnectionFilter.class);

    public static final long UNLIMITED_TIMEOUT = -1;
    public static final long UNSET_TIMEOUT = 0;

    private static final String ATTR_NAME =
            SilentConnectionFilter.class.getName() + ".silent-connection-attr";

    private static final Attribute<Long> silentConnectionAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(ATTR_NAME);

    private final long timeoutMillis;
    private final DelayedExecutor.DelayQueue<Connection> queue;

    public SilentConnectionFilter(DelayedExecutor executor,
            long timeout, TimeUnit timeunit) {
        this.timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
        queue = executor.createDelayQueue(
                new DelayedExecutor.Worker<Connection>() {

            @Override
            public boolean doWork(Connection connection) {
                connection.closeSilently();
                return true;
            }
        }, new Resolver());
    }

    public long getTimeout(TimeUnit timeunit) {
        return timeunit.convert(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        queue.add(connection, timeoutMillis, TimeUnit.MILLISECONDS);

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        queue.remove(connection);
        
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        queue.remove(connection);

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        queue.remove(ctx.getConnection());
        return ctx.getInvokeAction();
    }

    private static final class Resolver implements DelayedExecutor.Resolver<Connection> {

        @Override
        public boolean removeTimeout(Connection connection) {
            return silentConnectionAttr.remove(connection) != null;
        }

        @Override
        public long getTimeoutMillis(Connection connection) {
            final Long timeout = silentConnectionAttr.get(connection);
            return timeout != null ? timeout : DelayedExecutor.UNSET_TIMEOUT;
        }

        @Override
        public void setTimeoutMillis(Connection connection, long timeoutMillis) {
            silentConnectionAttr.set(connection, timeoutMillis);
        }
    }
}
