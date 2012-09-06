/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * The Filter is responsible for tracking {@link Connection} activity and closing
 * {@link Connection} ones it becomes idle for certain amount of time.
 * Unlike {@link IdleTimeoutFilter}, this Filter assumes {@link Connection}
 * is idle, even if some event is being executed on it, so it really requires
 * some action to be executed on {@link Connection} to reset the timeout.
 * 
 * @see IdleTimeoutFilter
 * 
 * @author Alexey Stashok
 */
public class ActivityCheckFilter extends BaseFilter {
    private static final Logger LOGGER = Grizzly.logger(ActivityCheckFilter.class);
    
    public static final String ACTIVE_ATTRIBUTE_NAME = "connection-active-attribute";
    private static final Attribute<ActiveRecord> IDLE_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            ACTIVE_ATTRIBUTE_NAME, new NullaryFunction<ActiveRecord>() {

        @Override
        public ActiveRecord evaluate() {
            return new ActiveRecord();
        }
    });
    
    private final long timeoutMillis;
    private final DelayedExecutor.DelayQueue<Connection> queue;

    // ------------------------------------------------------------ Constructors


    public ActivityCheckFilter(final DelayedExecutor executor,
                             final long timeout,
                             final TimeUnit timeoutUnit) {

        this(executor, timeout, timeoutUnit, null);

    }


    public ActivityCheckFilter(final DelayedExecutor executor,
                             final long timeout,
                             final TimeUnit timeoutUnit,
                             final TimeoutHandler handler) {

        this(executor, new DefaultWorker(handler), timeout,  timeoutUnit);

    }


    protected ActivityCheckFilter(final DelayedExecutor executor,
                                final DelayedExecutor.Worker<Connection> worker,
                                final long timeout,
                                final TimeUnit timeoutUnit) {

        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }

        this.timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeoutUnit);

        queue = executor.createDelayQueue(worker, new Resolver());

    }


    // ----------------------------------------------------- Methods from Filter



    @Override
    public NextAction handleAccept(final FilterChainContext ctx) throws IOException {
        queue.add(ctx.getConnection(), timeoutMillis, TimeUnit.MILLISECONDS);

//        queueAction(ctx);
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleConnect(final FilterChainContext ctx) throws IOException {
        queue.add(ctx.getConnection(), timeoutMillis, TimeUnit.MILLISECONDS);

//        queueAction(ctx);
        return ctx.getInvokeAction();
    }
    
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        IDLE_ATTR.get(ctx.getConnection()).timeoutMillis = System.currentTimeMillis() + timeoutMillis;
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        IDLE_ATTR.get(ctx.getConnection()).timeoutMillis = System.currentTimeMillis() + timeoutMillis;
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleClose(final FilterChainContext ctx) throws IOException {
        queue.remove(ctx.getConnection());
        return ctx.getInvokeAction();
    }


    // ---------------------------------------------------------- Public Methods

    @SuppressWarnings({"UnusedDeclaration"})
    public static DelayedExecutor createDefaultIdleDelayedExecutor() {

        return createDefaultIdleDelayedExecutor(1000, TimeUnit.MILLISECONDS);

    }

    @SuppressWarnings({"UnusedDeclaration"})
    public static DelayedExecutor createDefaultIdleDelayedExecutor(final long checkInterval,
                                                                   final TimeUnit checkIntervalUnit) {

        final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread newThread = new Thread(r);
                newThread.setName("Grizzly-ActiveTimeoutFilter-IdleCheck");
                newThread.setDaemon(true);
                return newThread;
            }
        });
        return new DelayedExecutor(executor,
                                   ((checkInterval > 0)
                                       ? checkInterval
                                       : 1000L),
                                   ((checkIntervalUnit != null)
                                       ? checkIntervalUnit
                                       : TimeUnit.MILLISECONDS));

    }

    @SuppressWarnings({"UnusedDeclaration"})
    public long getTimeout(TimeUnit timeunit) {
        return timeunit.convert(timeoutMillis, TimeUnit.MILLISECONDS);
    }


    // ----------------------------------------------------------- Inner Classes


    public interface TimeoutHandler {

        void onTimeout(final Connection c);

    }


    // ---------------------------------------------------------- Nested Classes


    private static final class Resolver implements DelayedExecutor.Resolver<Connection> {

        @Override
        public boolean removeTimeout(final Connection connection) {
            IDLE_ATTR.get(connection).timeoutMillis = 0;
            return true;
        }

        @Override
        public long getTimeoutMillis(final Connection connection) {
            return IDLE_ATTR.get(connection).timeoutMillis;
        }

        @Override
        public void setTimeoutMillis(final Connection connection,
                final long timeoutMillis) {
            IDLE_ATTR.get(connection).timeoutMillis = timeoutMillis;
        }

    } // END Resolver

    private static final class ActiveRecord {

        private volatile long timeoutMillis;

    } // END IdleRecord

    private static final class DefaultWorker implements DelayedExecutor.Worker<Connection> {

        private final TimeoutHandler handler;


        // -------------------------------------------------------- Constructors


        DefaultWorker(final TimeoutHandler handler) {

            this.handler = handler;

        }


        // --------------------------------- Methods from DelayedExecutor.Worker

        @Override
        public boolean doWork(final Connection connection) {
            if (handler != null) {
                handler.onTimeout(connection);
            }

            connection.closeSilently();

            return true;
        }

    } // END DefaultWorker


}
