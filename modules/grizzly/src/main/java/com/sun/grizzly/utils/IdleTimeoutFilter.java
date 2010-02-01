/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */
package com.sun.grizzly.utils;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class IdleTimeoutFilter extends FilterAdapter {
    private static Logger logger = Grizzly.logger(IdleTimeoutFilter.class);
    
    public static final long UNLIMITED_TIMEOUT = -1;
    public static final long UNSET_TIMEOUT = 0;
    
    public static final String IDLE_ATTRIBUTE_NAME = "connection-idle-attribute";
    public static Attribute<Long> idleAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            IDLE_ATTRIBUTE_NAME, UNLIMITED_TIMEOUT);
    
    private boolean isHandleAccepted;
    private boolean isHandleConnected;
    private volatile ScheduledFuture scheduledFuture;
    private long timeoutMillis;
    private ScheduledExecutorService scheduledThreadPool;
    private LinkedTransferQueue<Connection> connections;
    private volatile TimeoutChecker checker;

    public IdleTimeoutFilter(long timeout, TimeUnit timeunit) {
        this(timeout, timeunit,
                TransportFactory.getInstance().getDefaultScheduledThreadPool());
    }

    public IdleTimeoutFilter(long timeout, TimeUnit timeunit,
            ScheduledExecutorService scheduledThreadPool) {
        this.timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
        this.scheduledThreadPool = scheduledThreadPool;
        connections = new LinkedTransferQueue<Connection>();
        isHandleAccepted = true;
        isHandleConnected = false;
    }

    public ScheduledExecutorService getScheduledThreadPool() {
        return scheduledThreadPool;
    }

    public void setScheduledThreadPool(
            ScheduledExecutorService scheduledThreadPool) {
        this.scheduledThreadPool = scheduledThreadPool;
    }

    public long getTimeout(TimeUnit timeunit) {
        return timeunit.convert(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void setTimeout(long timeout, TimeUnit timeunit) {
        timeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
        if (scheduledFuture == null) {
            scheduledFuture.cancel(false);
            registerChecker();
        }
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
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction) throws IOException {
        clearTimeout(ctx.getConnection());
        return nextAction;
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx, NextAction nextAction) throws IOException {
        clearTimeout(ctx.getConnection());
        return nextAction;
    }

    @Override
    public NextAction postRead(FilterChainContext ctx, NextAction nextAction) throws IOException {
        resetTimeout(ctx.getConnection());
        return nextAction;
    }

    @Override
    public NextAction postWrite(FilterChainContext ctx, NextAction nextAction) throws IOException {
        resetTimeout(ctx.getConnection());
        return nextAction;
    }

    @Override
    public NextAction postAccept(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        if (isHandleAccepted) {
            Connection connection = ctx.getConnection();
            resetTimeout(connection);
            addConnection(connection);
        }

        return nextAction;
    }

    @Override
    public NextAction postConnect(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        if (isHandleConnected()) {
            Connection connection = ctx.getConnection();
            resetTimeout(connection);
            addConnection(connection);
        }

        return nextAction;
    }

    public synchronized void initialize() {
        if (scheduledFuture != null) {
            throw new IllegalStateException(
                    "IdleTimeoutFilter was already initialized!");
        }
        registerChecker();
    }

    public synchronized void release() {
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
                            connection.close();
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
