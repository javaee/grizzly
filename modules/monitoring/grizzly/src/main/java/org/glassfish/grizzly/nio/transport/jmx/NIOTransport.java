/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio.transport.jmx;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.TransportProbe;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;
import org.glassfish.gmbal.NameValue;
import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * NIO Transport JMX object.
 *
 * @author Alexey Stashok
 */
@ManagedObject
@Description("Grizzly NIO Transport")
public class NIOTransport extends JmxObject {
    protected final org.glassfish.grizzly.nio.NIOTransport transport;
    private final JmxTransportProbe probe;
    private final JmxConnectionProbe connectionProbe;

    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicLong bytesWritten = new AtomicLong();
    
    private volatile EventDate stateEvent;
    private volatile EventDate lastErrorEvent;

    private final ConcurrentMap<Connection, String> boundConnections =
            DataStructures.<Connection, String>getConcurrentMap(4);

    private final Queue<String> boundAddresses = new ConcurrentLinkedQueue<String>();

    private final AtomicInteger openConnectionsNum = new AtomicInteger();
    private final AtomicLong totalConnectionsNum = new AtomicLong();

    private GrizzlyJmxManager mom;
    
    private MemoryManager currentMemoryManager;
    private Object memoryManagerJmx;

    private ExecutorService currentThreadPool;
    private Object threadPoolJmx;


    private final Object subtreeLock = new Object();

    public NIOTransport(org.glassfish.grizzly.nio.NIOTransport transport) {
        this.transport = transport;
        probe = new JmxTransportProbe();
        connectionProbe = new JmxConnectionProbe();
    }

    @NameValue
    public String getName() {
        return transport.getName();
    }

    @Override
    public String getJmxName() {
        return "Transport";
    }

    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        synchronized (subtreeLock) {
            this.mom = mom;

            transport.getMonitoringConfig().addProbes(probe);
            transport.getConnectionMonitoringConfig().addProbes(connectionProbe);

            rebuildSubTree();
        }
    }

    @Override
    protected void onDeregister(GrizzlyJmxManager mom) {
        synchronized(subtreeLock) {
            transport.getMonitoringConfig().removeProbes(probe);
            transport.getConnectionMonitoringConfig().removeProbes(connectionProbe);

            this.mom = null;
        }
    }
    
    @ManagedAttribute(id="state")
    public String getState() {
        final EventDate stateEventDate = stateEvent;
        if (stateEventDate == null) {
            return toString(transport.getState().getState());
        }

        return toString(stateEventDate);
    }

    @ManagedAttribute(id="read-buffer-size")
    public int getReadBufferSize() {
        return transport.getReadBufferSize();
    }

    @ManagedAttribute(id="write-buffer-size")
    public int getWriteBufferSize() {
        return transport.getWriteBufferSize();
    }

    @ManagedAttribute(id="processor")
    public String getProcessor() {
        return getType(transport.getProcessor());
    }

    @ManagedAttribute(id="processor-selector")
    public String getProcessorSelector() {
        return getType(transport.getProcessorSelector());
    }

    @ManagedAttribute(id="io-strategy")
    public String getIOStrategy() {
        return getType(transport.getIOStrategy());
    }

    @ManagedAttribute(id="channel-distributor")
    public String getChannelDistributor() {
        return getType(transport.getNIOChannelDistributor());
    }

    @ManagedAttribute(id="selector-handler")
    public String getSelectorHandler() {
        return getType(transport.getSelectorHandler());
    }

    @ManagedAttribute(id="selection-key-handler")
    public String getSelectionKeyHandler() {
        return getType(transport.getSelectionKeyHandler());
    }

    @ManagedAttribute(id="selector-threads-count")
    public int getSelectorHandlerRunners() {
        return transport.getSelectorRunnersCount();
    }

    @ManagedAttribute(id="thread-pool-type")
    public String getThreadPoolType() {
        return getType(transport.getWorkerThreadPool());
    }

    @ManagedAttribute(id="last-error")
    public String getLastError() {
        return toString(lastErrorEvent);
    }

    @ManagedAttribute(id="bytes-read")
    public long getBytesRead() {
        return bytesRead.get();
    }

    @ManagedAttribute(id="bytes-written")
    public long getBytesWritten() {
        return bytesWritten.get();
    }

    @ManagedAttribute(id="bound-addresses")
    public String getBoundAddresses() {
        return boundAddresses.toString();
    }

    @ManagedAttribute(id="open-connections-count")
    public int getOpenConnectionsCount() {
        return openConnectionsNum.get();
    }

    @ManagedAttribute(id="total-connections-count")
    public long getTotalConnectionsCount() {
        return totalConnectionsNum.get();
    }

    private static String getType(Object o) {
        return o != null ? o.getClass().getName() : "N/A";
    }
    
    private static String toString(Object o) {
        return o != null ? o.toString() : "N/A";
    }

    protected void rebuildSubTree() {
        // rebuild memory manager sub element
        final MemoryManager memoryManager = transport.getMemoryManager();
        if (currentMemoryManager != memoryManager) {
            if (currentMemoryManager != null) {
                mom.deregister(memoryManagerJmx);
                
                currentMemoryManager = null;
                memoryManagerJmx = null;
            }

            if (memoryManager != null) {
                final Object mmJmx = memoryManager
                        .getMonitoringConfig().createManagementObject();
                if (mmJmx != null) {
                    mom.register(this, mmJmx);
                    currentMemoryManager = memoryManager;
                    memoryManagerJmx = mmJmx;
                }
            }
        }

        final ExecutorService executorService = transport.getWorkerThreadPool();
        final GrizzlyExecutorService threadPool;
        if (executorService instanceof GrizzlyExecutorService) {
            threadPool = (GrizzlyExecutorService) transport.getWorkerThreadPool();
        } else {
            threadPool = null;
        }
        if (currentThreadPool != threadPool) {
            if (currentThreadPool != null) {
                mom.deregister(threadPoolJmx);

                currentThreadPool = null;
                threadPoolJmx = null;
            }

            if (threadPool != null) {
                final Object jmx = threadPool
                        .getMonitoringConfig().createManagementObject();
                mom.register(this, jmx);
                currentThreadPool = threadPool;
                threadPoolJmx = jmx;
            }
        }
    }

    private static class EventDate {
        private final String event;
        private final Date date;

        public EventDate(String event) {
            this.event = event;
            date = new Date();
        }

        @Override
        public String toString() {
            return event + " (" + date + ')';
        }
    }

    private class JmxTransportProbe implements TransportProbe {

        @Override
        public void onBeforeStartEvent(Transport transport) {
            stateEvent = new EventDate("STARTING");
        }

        @Override
        public void onStartEvent(Transport transport) {
            stateEvent = new EventDate("STARTED");
        }

        @Override
        public void onBeforeStopEvent(Transport transport) {
            stateEvent = new EventDate("STOPPING");
        }

        @Override
        public void onStopEvent(Transport transport) {
            stateEvent = new EventDate("STOPPED");
        }

        @Override
        public void onBeforePauseEvent(Transport transport) {
            stateEvent = new EventDate("PAUSING");
        }

        @Override
        public void onPauseEvent(Transport transport) {
            stateEvent = new EventDate("PAUSED");
        }

        @Override
        public void onBeforeResumeEvent(Transport transport) {
            stateEvent = new EventDate("RESUMING");
        }

        @Override
        public void onResumeEvent(Transport transport) {
            stateEvent = new EventDate("RESUMED");
        }

        @Override
        public void onErrorEvent(Transport transport, Throwable error) {
            lastErrorEvent = new EventDate(error.getClass() + ": " + error.getMessage());
        }

        @Override
        public void onConfigChangeEvent(Transport transport) {
            synchronized (subtreeLock) {
                rebuildSubTree();
            }
        }
    }

    private class JmxConnectionProbe implements ConnectionProbe {

        @Override
        public void onBindEvent(Connection connection) {
            final String bindAddress = connection.getLocalAddress().toString();
            if (boundConnections.putIfAbsent(connection, bindAddress) == null) {
                boundAddresses.add(bindAddress);
            }
        }

        @Override
        public void onAcceptEvent(Connection serverConnection,
                Connection clientConnection) {
            openConnectionsNum.incrementAndGet();
            totalConnectionsNum.incrementAndGet();
        }

        @Override
        public void onConnectEvent(Connection connection) {
            openConnectionsNum.incrementAndGet();
            totalConnectionsNum.incrementAndGet();
        }

        @Override
        public void onReadEvent(Connection connection, Buffer data, int size) {
            bytesRead.addAndGet(size);
        }

        @Override
        public void onWriteEvent(Connection connection, Buffer data, long size) {
            bytesWritten.addAndGet(size);
        }

        @Override
        public void onErrorEvent(Connection connection, Throwable error) {
        }

        @Override
        public void onCloseEvent(Connection connection) {
            final String bindAddress;
            if ((bindAddress = boundConnections.remove(connection)) != null) {
                boundAddresses.remove(bindAddress);
            }
            if (openConnectionsNum.get() > 0) {
                openConnectionsNum.decrementAndGet();
            }
        }

        @Override
        public void onIOEventReadyEvent(Connection connection, IOEvent ioEvent) {
        }

        @Override
        public void onIOEventEnableEvent(Connection connection, IOEvent ioEvent) {
        }

        @Override
        public void onIOEventDisableEvent(Connection connection, IOEvent ioEvent) {
        }
    }
}
