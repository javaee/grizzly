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

package com.sun.grizzly.utils;

import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.utils.LinkedTransferQueue;
import java.io.IOException;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
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
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            ATTR_NAME, UNLIMITED_TIMEOUT);

    private volatile ScheduledFuture scheduledFuture;
    private final long timeoutMillis;
    private final ScheduledExecutorService scheduledThreadPool;
    private final Queue<Connection> connections;
    private volatile TimeoutChecker checker;

    public SilentConnectionFilter(ScheduledExecutorService scheduledThreadPool,
            long timeout, TimeUnit timeunit) {
        this.timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
        this.scheduledThreadPool = scheduledThreadPool;
        connections = new LinkedTransferQueue<Connection>();
    }

    public ScheduledExecutorService getScheduledThreadPool() {
        return scheduledThreadPool;
    }

    public long getTimeout(TimeUnit timeunit) {
        return timeunit.convert(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        setConnectionTimeout(connection, System.currentTimeMillis() + timeoutMillis);
        connections.add(connection);

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        if (getConnectionTimeout(connection) != UNSET_TIMEOUT) {
            setConnectionTimeout(connection, UNSET_TIMEOUT);
        }
        
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        if (getConnectionTimeout(connection) != UNSET_TIMEOUT) {
            setConnectionTimeout(connection, UNSET_TIMEOUT);
        }

        return ctx.getInvokeAction();
    }

    @Override
    public synchronized void onAdded(FilterChain filterChain) {
        super.onAdded(filterChain);

        if (scheduledFuture != null) {
            throw new IllegalStateException(
                    "SilentConnectionFilter was already initialized!");
        }
        registerChecker();
    }

    @Override
    public synchronized void onRemoved(FilterChain filterChain) {
        release();
        super.onRemoved(filterChain);
    }


    protected synchronized void release() {
        checker = null;
        scheduledFuture.cancel(false);
        scheduledFuture = null;
        connections.clear();
    }

    protected void registerChecker() {
        if (timeoutMillis > 0) {
            checker = new TimeoutChecker();
            scheduledFuture = scheduledThreadPool.schedule(checker,
                    timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private static long getConnectionTimeout(Connection connection) {
        return silentConnectionAttr.get(connection);
    }

    private static void setConnectionTimeout(Connection connection, long timeout) {
        silentConnectionAttr.set(connection, timeout);
    }

    public class TimeoutChecker implements Runnable {

        @Override
        public void run() {
            long currentTimeMillis = System.currentTimeMillis();
            long nextTimeout = timeoutMillis;
            try {
                for (Iterator<Connection> it = connections.iterator(); it.hasNext();) {
                    Connection connection = it.next();

                    if (!connection.isOpen()) {
                        it.remove();
                        continue;
                    }

                    final Long expirationTime = getConnectionTimeout(connection);

                    if (expirationTime == 0) {
                        continue;
                    }

                    long diff = currentTimeMillis - expirationTime;
                    if (diff >= 0) {
                        try {
                            connection.close().markForRecycle(true);
                        } catch (IOException e) {
                            LOGGER.log(Level.FINE, "SilentConnectionFilter:" +
                                    "unexpected exception, when trying " +
                                    "to close connection", e);
                        } finally {
                            it.remove();
                        }
                    } else {
                        diff = -diff;
                        if (nextTimeout > diff) {
                            nextTimeout = diff;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "SilentConnectionFilter: unexpected exception", e);
            } finally {
                if (this == checker) {
                    scheduledFuture = scheduledThreadPool.schedule(checker,
                            nextTimeout, TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
