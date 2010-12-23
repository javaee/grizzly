/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class IdleTimeoutFilter extends BaseFilter {
    private static final Logger LOGGER = Grizzly.logger(IdleTimeoutFilter.class);
    
    public static final long UNLIMITED_TIMEOUT = -1;
    public static final long UNSET_TIMEOUT = 0;
    
    public static final String IDLE_ATTRIBUTE_NAME = "connection-idle-attribute";
    public static final Attribute<Long> idleAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(IDLE_ATTRIBUTE_NAME);
    
    private volatile boolean isHandleAccepted;
    private volatile boolean isHandleConnected;

    private final long timeoutMillis;
    private final DelayedExecutor executor;
    private final DelayedExecutor.DelayQueue<Connection> queue;

    private final boolean wasExecutorStarted;

    public IdleTimeoutFilter(long timeout, TimeUnit timeunit) {
        this(new DelayedExecutor(Executors.newSingleThreadExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread newThread = new Thread(r);
                newThread.setDaemon(true);
                return newThread;
            }
        })), true, timeout, timeunit);
    }

    public IdleTimeoutFilter(DelayedExecutor executor, long timeout, TimeUnit timeunit) {
        this(executor, false, timeout, timeunit);
    }

    protected IdleTimeoutFilter(DelayedExecutor executor, boolean needStartExecutor,
            long timeout, TimeUnit timeunit) {
        this.timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);

        wasExecutorStarted = needStartExecutor;
        if (needStartExecutor) {
            executor.start();
        }

        this.executor = executor;
        queue = executor.createDelayQueue(
                new DelayedExecutor.Worker<Connection>() {

            @Override
            public boolean doWork(final Connection connection) {
                try {
                    connection.close().markForRecycle(true);
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "SilentConnectionFilter:" +
                            "unexpected exception, when trying " +
                            "to close connection", e);
                }

                return true;
            }
        }, new Resolver());

        isHandleAccepted = true;
        isHandleConnected = true;
    }

    public long getTimeout(TimeUnit timeunit) {
        return timeunit.convert(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isHandleAccepted() {
        return isHandleAccepted;
    }

    public void setHandleAccepted(boolean isHandleAccepted) {
        this.isHandleAccepted = isHandleAccepted;
    }

    public boolean isHandleConnected() {
        return isHandleConnected;
    }

    public void setHandleConnected(boolean isHandleConnected) {
        this.isHandleConnected = isHandleConnected;
    }

    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        if (isHandleAccepted) {
            queue.add(ctx.getConnection(), timeoutMillis, TimeUnit.MILLISECONDS);
        }

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        if (isHandleConnected()) {
            queue.add(ctx.getConnection(), timeoutMillis, TimeUnit.MILLISECONDS);
        }

        return ctx.getInvokeAction();
    }
    
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        resetTimeout(ctx.getConnection());
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        resetTimeout(ctx.getConnection());
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        queue.remove(ctx.getConnection());
        return ctx.getInvokeAction();
    }


    protected void resetTimeout(Connection connection) {
        idleAttribute.set(connection, System.currentTimeMillis() + timeoutMillis);
    }

    @Override
    protected void finalize() throws Throwable {
        if (wasExecutorStarted) {
            executor.stop();
        }
        super.finalize();
    }


    private static final class Resolver implements DelayedExecutor.Resolver<Connection> {

        @Override
        public boolean removeTimeout(Connection connection) {
            return idleAttribute.remove(connection) != null;
        }

        @Override
        public Long getTimeoutMillis(Connection connection) {
            return idleAttribute.get(connection);
        }

        @Override
        public void setTimeoutMillis(Connection connection, long timeoutMillis) {
            idleAttribute.set(connection, timeoutMillis);
        }
    }
}
