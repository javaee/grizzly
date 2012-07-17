/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.monitoring.MonitoringAware;
import org.glassfish.grizzly.monitoring.MonitoringConfig;
import org.glassfish.grizzly.monitoring.MonitoringConfigImpl;
import org.glassfish.grizzly.monitoring.jmx.AbstractJmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.threadpool.ThreadPoolProbe;
import org.glassfish.grizzly.utils.StateHolder;

/**
 * Abstract {@link Transport}.
 * Implements common transport functionality.
 *
 * @author Alexey Stashok
 */
public abstract class AbstractTransport implements Transport {
    /**
     * Transport name
     */
    protected String name;

    /**
     * Transport mode
     */
    protected volatile boolean isBlocking;

    /**
     * Transport state controller
     */
    protected final StateHolder<State> state;

    /**
     * Transport default Processor
     */
    protected Processor processor;

    /**
     * Transport strategy
     */
    protected IOStrategy strategy;

    /**
     * Transport MemoryManager
     */
    protected MemoryManager memoryManager;

    /**
     * Worker thread pool
     */
    protected ExecutorService workerThreadPool;

    /**
     * Kernel thread pool.
     */
    protected ExecutorService kernelPool;

    /**
     * Transport AttributeBuilder, which will be used to create Attributes
     */
    protected AttributeBuilder attributeBuilder;

    /**
     * Transport default buffer size for read operations
     */
    protected int readBufferSize;

    /**
     * Transport default buffer size for write operations
     */
    protected int writeBufferSize;

    protected ThreadPoolConfig workerPoolConfig;

    protected ThreadPoolConfig kernelPoolConfig;

    protected boolean managedWorkerPool = true;

    /**
     * Transport probes
     */
    protected final AbstractJmxMonitoringConfig<TransportProbe> transportMonitoringConfig =
            new AbstractJmxMonitoringConfig<TransportProbe>(TransportProbe.class) {

        @Override
        public JmxObject createManagementObject() {
            return createJmxManagementObject();
        }
    };

    /**
     * Connection probes
     */
    protected final MonitoringConfigImpl<ConnectionProbe> connectionMonitoringConfig =
            new MonitoringConfigImpl<ConnectionProbe>(ConnectionProbe.class);
    
    /**
     * Thread pool probes
     */
    protected final MonitoringConfigImpl<ThreadPoolProbe> threadPoolMonitoringConfig =
            new MonitoringConfigImpl<ThreadPoolProbe>(ThreadPoolProbe.class);

    public AbstractTransport(String name) {
        this.name = name;
        state = new StateHolder<State>(State.STOP);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.name = name;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBlocking() {
        return isBlocking;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StateHolder<State> getState() {
        return state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getReadBufferSize() {
        return readBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isStopped() {
        final State currentState = state.getState();
        return currentState == State.STOP || currentState == State.STOPPING;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPaused() {
        return (state.getState() == State.PAUSE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Processor getProcessor() {
        return processor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProcessor(Processor processor) {
        this.processor = processor;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IOStrategy getIOStrategy() {
        return strategy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIOStrategy(IOStrategy IOStrategy) {
        this.strategy = IOStrategy;
        final ThreadPoolConfig strategyConfig = IOStrategy.createDefaultWorkerPoolConfig(this);
        if (strategyConfig == null) {
            workerPoolConfig = null;
        } else {
            if (workerPoolConfig == null) {
                setWorkerThreadPoolConfig(strategyConfig);
            }
        }
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMemoryManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutorService getWorkerThreadPool() {
        return workerThreadPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutorService getKernelThreadPool() {
        return kernelPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKernelThreadPool(ExecutorService kernelPool) {
        this.kernelPool = kernelPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKernelThreadPoolConfig(ThreadPoolConfig kernelPoolConfig) {
        if (isStopped()) {
            this.kernelPoolConfig = kernelPoolConfig;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkerThreadPoolConfig(ThreadPoolConfig workerPoolConfig) {
        if (isStopped()) {
            this.workerPoolConfig = workerPoolConfig;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThreadPoolConfig getKernelThreadPoolConfig() {
        return ((isStopped()) ? kernelPoolConfig : kernelPoolConfig.copy());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThreadPoolConfig getWorkerThreadPoolConfig() {
        return ((isStopped()) ? workerPoolConfig : workerPoolConfig.copy());
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void setWorkerThreadPool(final ExecutorService threadPool) {
        managedWorkerPool = false;
        if (threadPool instanceof MonitoringAware) {
            ((MonitoringAware<ThreadPoolProbe>) threadPool).getMonitoringConfig()
                    .addProbes(threadPoolMonitoringConfig.getProbes());
        }

        setWorkerThreadPool0(threadPool);
    }

    protected void setWorkerThreadPool0(final ExecutorService threadPool) {
        this.workerThreadPool = threadPool;
        notifyProbesConfigChanged(this);
    }

    protected void setKernelPool0(final ExecutorService kernelPool) {
        this.kernelPool = kernelPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeBuilder getAttributeBuilder() {
        return attributeBuilder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttributeBuilder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = attributeBuilder;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<ConnectionProbe> getConnectionMonitoringConfig() {
        return connectionMonitoringConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JmxMonitoringConfig<TransportProbe> getMonitoringConfig() {
        return transportMonitoringConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonitoringConfig<ThreadPoolProbe> getThreadPoolMonitoringConfig() {
        return threadPoolMonitoringConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fireEvent(final Event event,
            final Connection connection) {

        final Processor conProcessor = connection.getProcessor();

        ProcessorExecutor.execute(Context.create(connection,
                conProcessor, event));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fireEvent(final Event event,
            final Connection connection,
            final EventProcessingHandler processingHandler) {

        final Processor conProcessor = connection.getProcessor();

        ProcessorExecutor.execute(Context.create(connection,
                conProcessor, event, processingHandler));
    }
    
    @Override
    public void notifyTransportError(final Throwable error) {
        notifyProbesError(this, error);
    }

    /**
     * Notify registered {@link TransportProbe}s about the config changed event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesConfigChanged(final AbstractTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onConfigChangeEvent(transport);
            }
        }
    }
    
    /**
     * Notify registered {@link TransportProbe}s about the error.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesError(final AbstractTransport transport,
            final Throwable error) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onErrorEvent(transport, error);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the start event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesStart(final AbstractTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onStartEvent(transport);
            }
        }
    }
    
    /**
     * Notify registered {@link TransportProbe}s about the stop event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesStop(final AbstractTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onStopEvent(transport);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the pause event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesPause(final AbstractTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onPauseEvent(transport);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the resume event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesResume(final AbstractTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onResumeEvent(transport);
            }
        }
    }
    /**
     * Starts the transport
     * 
     * @throws IOException
     */
    @Override
    public abstract void start() throws IOException;
    
    /**
     * Stops the transport and closes all the connections
     * 
     * @throws IOException
     */
    @Override
    public abstract void stop() throws IOException;
    
    /**
     * Pauses the transport
     * 
     * @throws IOException
     */
    @Override
    public abstract void pause() throws IOException;
    
    /**
     * Resumes the transport after a pause
     * 
     * @throws IOException
     */
    @Override
    public abstract void resume() throws IOException;

    /**
     * Create the Transport JMX managment object.
     *
     * @return the Transport JMX managment object.
     */
    protected abstract JmxObject createJmxManagementObject();
}
