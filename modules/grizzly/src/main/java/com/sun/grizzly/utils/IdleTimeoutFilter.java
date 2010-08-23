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

import com.sun.grizzly.filterchain.FilterChain;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class IdleTimeoutFilter extends BaseFilter {
    private static Logger logger = Grizzly.logger(IdleTimeoutFilter.class);
    
    public static final long UNLIMITED_TIMEOUT = -1;
    public static final long UNSET_TIMEOUT = 0;
    
    public static final String IDLE_ATTRIBUTE_NAME = "connection-idle-attribute";
    public static final Attribute<Long> idleAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            IDLE_ATTRIBUTE_NAME, UNLIMITED_TIMEOUT);
    
    private volatile boolean isHandleAccepted;
    private volatile boolean isHandleConnected;
    private volatile ScheduledFuture scheduledFuture;
    private final long timeoutMillis;
    private final ScheduledExecutorService scheduledThreadPool;
    private final Queue<Connection> connections;
    private volatile TimeoutChecker checker;

    public IdleTimeoutFilter(long timeout, TimeUnit timeunit) {
        this(timeout, timeunit, Executors.newScheduledThreadPool(1,
                new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                final Thread newThread = new Thread(r);
                newThread.setDaemon(true);
                return newThread;
            }
        }));
    }

    public IdleTimeoutFilter(long timeout, TimeUnit timeunit,
            ScheduledExecutorService scheduledThreadPool) {
        this.timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
        this.scheduledThreadPool = scheduledThreadPool;
        connections = new LinkedTransferQueue<Connection>();
        isHandleAccepted = true;
        isHandleConnected = true;
    }

    public ScheduledExecutorService getScheduledThreadPool() {
        return scheduledThreadPool;
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
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        clearTimeout(ctx.getConnection());
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        clearTimeout(ctx.getConnection());
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        if (isHandleAccepted) {
            Connection connection = ctx.getConnection();
            resetTimeout(connection);
            addConnection(connection);
        }

        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        if (isHandleConnected()) {
            Connection connection = ctx.getConnection();
            resetTimeout(connection);
            addConnection(connection);
        }

        return ctx.getInvokeAction();
    }

    @Override
    public synchronized void onAdded(FilterChain filterChain) {
        super.onAdded(filterChain);

        if (scheduledFuture != null) {
            throw new IllegalStateException(
                    "IdleTimeoutFilter was already initialized!");
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

    protected void addConnection(Connection connection) {
        connections.add(connection);
    }

    protected void clearTimeout(Connection connection) {
        long currentTimeout = getConnectionTimeout(connection);
        if (currentTimeout > 0) {
            setConnectionTimeout(connection, UNSET_TIMEOUT);
        }
    }

    protected void resetTimeout(Connection connection) {
        long currentTimeout = getConnectionTimeout(connection);
        if (currentTimeout >= 0) {
            setExpirationTime(connection, timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    protected long getConnectionTimeout(Connection connection) {
        return idleAttribute.get(connection);
    }

    protected void setConnectionTimeout(Connection connection, long timeout) {
        idleAttribute.set(connection, timeout);
    }

    public Long getExpirationTime(Connection connection, TimeUnit timeunit) {
        long expirationTime = getConnectionTimeout(connection);
        if (expirationTime > 0 && timeunit != TimeUnit.MILLISECONDS) {
            return timeunit.convert(expirationTime, TimeUnit.MILLISECONDS);
        }

        return expirationTime;
    }

    public void setExpirationTime(Connection connection, long timeout,
            TimeUnit timeunit) {
        if (timeout > 0 && timeunit != TimeUnit.MILLISECONDS) {
            setConnectionTimeout(connection,
                    TimeUnit.MILLISECONDS.convert(timeout, timeunit));
        } else {
            setConnectionTimeout(connection, timeout);
        }
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

                    Long expirationTime = getExpirationTime(connection,
                            TimeUnit.MILLISECONDS);

                    if (expirationTime == 0) {
                        continue;
                    }

                    long diff = currentTimeMillis - expirationTime;
                    if (diff >= 0) {
                        try {
                            connection.close().markForRecycle(true);
                        } catch (IOException e) {
                            logger.log(Level.FINE, "IdleTimeoutFilter:" +
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
                logger.log(Level.WARNING,
                        "IdleTimeoutFilter: unexpected exception", e);
            } finally {
                if (this == checker) {
                    scheduledFuture = scheduledThreadPool.schedule(checker,
                            nextTimeout, TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
