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

package com.sun.grizzly.nio.transport.jmx;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.ConnectionProbe;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.Transport;
import com.sun.grizzly.TransportProbe;
import com.sun.grizzly.monitoring.jmx.GrizzlyJmxManager;
import com.sun.grizzly.monitoring.jmx.JmxObject;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.nio.AbstractNIOTransport;
import com.sun.grizzly.utils.LinkedTransferQueue;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;
import org.glassfish.gmbal.NameValue;

/**
 * NIO Transport JMX object.
 *
 * @author Alexey Stashok
 */
@ManagedObject
@Description("Grizzly NIO Transport")
public class NIOTransport extends JmxObject {
    protected final AbstractNIOTransport transport;
    private final JmxTransportProbe probe;
    private final JmxConnectionProbe connectionProbe;

    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicLong bytesWritten = new AtomicLong();
    
    private volatile EventDate stateEvent;
    private volatile EventDate lastErrorEvent;

    private final ConcurrentHashMap<Connection, String> boundConnections =
            new ConcurrentHashMap<Connection, String>();

    private final Queue<String> boundAddresses = new LinkedTransferQueue<String>();

    private final AtomicInteger openConnectionsNum = new AtomicInteger();
    private final AtomicLong totalConnectionsNum = new AtomicLong();

    private GrizzlyJmxManager mom;
    
    private MemoryManager currentMemoryManager;
    private JmxObject memoryManagerJmx;

    private ExecutorService currentThreadPool;
    private JmxObject threadPoolJmx;


    private final Object subtreeLock = new Object();

    public NIOTransport(AbstractNIOTransport transport) {
        this.transport = transport;
        probe = new JmxTransportProbe();
        connectionProbe = new JmxConnectionProbe();
    }

    @NameValue
    public String getName() {
        return transport.getName();
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
    protected void onUnregister(GrizzlyJmxManager mom) {
        synchronized(subtreeLock) {
            transport.getMonitoringConfig().removeProbes(probe);
            transport.getConnectionMonitoringConfig().removeProbes(connectionProbe);

            mom = null;
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

    @ManagedAttribute(id="strategy")
    public String getStrategy() {
        return getType(transport.getStrategy());
    }

    @ManagedAttribute(id="channel-distributor")
    public String getChannelDistributor() {
        return getType(transport.getNioChannelDistributor());
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
        return getType(transport.getThreadPool());
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
                mom.unregister(memoryManagerJmx);
                
                currentMemoryManager = null;
                memoryManagerJmx = null;
            }

            if (memoryManager != null) {
                final JmxObject mmJmx = memoryManager.getMonitoringConfig().createManagmentObject();
                mom.register(this, mmJmx, "MemoryManager");
                currentMemoryManager = memoryManager;
                memoryManagerJmx = mmJmx;
            }
        }
    }

    private static class EventDate {
        private final String event;
        private Date date;

        public EventDate(String event) {
            this.event = event;
            date = new Date();
        }

        @Override
        public String toString() {
            return event + " (" + date + ")";
        }
    }

    private class JmxTransportProbe implements TransportProbe {

        @Override
        public void onStartEvent(Transport transport) {
            stateEvent = new EventDate("START");
        }

        @Override
        public void onStopEvent(Transport transport) {
            stateEvent = new EventDate("STOP");
        }

        @Override
        public void onPauseEvent(Transport transport) {
            stateEvent = new EventDate("PAUSE");
        }

        @Override
        public void onResumeEvent(Transport transport) {
            stateEvent = new EventDate("RESUME");
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
        public void onAcceptEvent(Connection connection) {
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
        public void onWriteEvent(Connection connection, Buffer data, int size) {
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

            openConnectionsNum.decrementAndGet();
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
