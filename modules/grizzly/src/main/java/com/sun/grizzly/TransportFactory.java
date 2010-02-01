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

package com.sun.grizzly;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import com.sun.grizzly.nio.DefaultNIOTransportFactory;
import com.sun.grizzly.nio.transport.UDPNIOTransport;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.attributes.AttributeBuilder;
import com.sun.grizzly.memory.DefaultMemoryManager;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.threadpool.DefaultScheduleThreadPool;
import com.sun.grizzly.threadpool.DefaultThreadPool;

/**
 * Factory, responsible for creating and initializing Grizzly {@link Transport}s.
 * 
 * @author Alexey Stashok
 */
public abstract class TransportFactory {

    private static volatile TransportFactory instance;

    /**
     * Get the {@link TransportFactory} instance.
     * 
     * @return the {@link TransportFactory} instance.
     */
    public static synchronized TransportFactory getInstance() {
        if (instance == null || instance.isClosed()) {
            instance = new DefaultNIOTransportFactory();
        }

        return instance;
    }

    /**
     * Set the {@link TransportFactory} instance.
     *
     * @param factory the {@link TransportFactory} instance.
     */
    public static synchronized void setInstance(TransportFactory factory) {
        instance = factory;
    }

    /**
     * Create instance of TCP {@link Transport}.
     * 
     * @return instance of TCP {@link Transport}.
     */
    public abstract TCPNIOTransport createTCPTransport();

    /**
     * Create instance of UDP {@link Transport}.
     *
     * @return instance of UDP {@link Transport}.
     */
    public abstract UDPNIOTransport createUDPTransport();

    /**
     * Is factory closed.
     */
    private boolean isClosed;
    
    /**
     * Default {@link AttributeBuilder} used by all {@link Transport}s.
     */
    protected AttributeBuilder defaultAttributeBuilder;
    /**
     * Default {@link MemoryManager} used by all {@link Transport}s.
     */
    protected MemoryManager defaultMemoryManager;
    /**
     * Default worker thread pool, used by all {@link Transport}s.
     */
    protected ExecutorService defaultWorkerThreadPool;
    /**
     * Default scheduled thread pool, used by all {@link Transport}s.
     */
    protected ScheduledExecutorService defaultScheduledThreadPool;

    protected TransportFactory() {
        initialize();
    }

    /**
     * Get default {@link AttributeBuilder}, used by all {@link Transport}s.
     *
     * @return default {@link AttributeBuilder}, used by all {@link Transport}s.
     */
    public AttributeBuilder getDefaultAttributeBuilder() {
        return defaultAttributeBuilder;
    }

    /**
     * Set default {@link AttributeBuilder}, used by all {@link Transport}s.
     *
     * @param defaultAttributeBuilder default {@link AttributeBuilder},
     * used by all {@link Transport}s.
     */
    public void setDefaultAttributeBuilder(AttributeBuilder defaultAttributeBuilder) {
        this.defaultAttributeBuilder = defaultAttributeBuilder;
    }

    /**
     * Get default {@link MemoryManager}, used by all {@link Transport}s.
     *
     * @return default {@link MemoryManager}, used by all {@link Transport}s.
     */
    public MemoryManager getDefaultMemoryManager() {
        return defaultMemoryManager;
    }

    /**
     * Set default {@link MemoryManager}, used by all {@link Transport}s.
     *
     * @param defaultMemoryManager default {@link MemoryManager},
     * used by all {@link Transport}s.
     */
    public void setDefaultMemoryManager(MemoryManager defaultMemoryManager) {
        this.defaultMemoryManager = defaultMemoryManager;
    }

    /**
     * Get default worker thread pool, used by all {@link Transport}s.
     *
     * @return default worker thread pool, used by all {@link Transport}s.
     */
    public ExecutorService getDefaultWorkerThreadPool() {
        return defaultWorkerThreadPool;
    }

    /**
     * Set default worker thread pool, used by all {@link Transport}s.
     *
     * @param defaultThreadPool default worker thread pool,
     * used by all {@link Transport}s.
     */
    public void setDefaultWorkerThreadPool(ExecutorService defaultThreadPool) {
        this.defaultWorkerThreadPool = defaultThreadPool;
    }

    /**
     * Get default scheduled thread pool, used by all {@link Transport}s.
     *
     * @return default scheduled thread pool, used by all {@link Transport}s.
     */
    public ScheduledExecutorService getDefaultScheduledThreadPool() {
        return defaultScheduledThreadPool;
    }

    /**
     * Set default scheduled thread pool, used by all {@link Transport}s.
     *
     * @param defaultScheduledThreadPool default scheduled thread pool,
     * used by all {@link Transport}s.
     */
    public void setDefaultScheduledThreadPool(
            ScheduledExecutorService defaultScheduledThreadPool) {
        this.defaultScheduledThreadPool = defaultScheduledThreadPool;
    }
    
    /**
     * Initialize default factory settings.
     */
    public void initialize() {
        defaultAttributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
        defaultMemoryManager = new DefaultMemoryManager();
        defaultWorkerThreadPool = new DefaultThreadPool();
        defaultScheduledThreadPool = new DefaultScheduleThreadPool();
    }

    /**
     * Close the {@link TransportFactory} and release all resources.
     */
    public synchronized void close() {
        if (!isClosed()) {
            isClosed = true;
            if (defaultWorkerThreadPool != null) {
                defaultWorkerThreadPool.shutdown();
                defaultWorkerThreadPool = null;
            }

            if (defaultScheduledThreadPool != null) {
                defaultScheduledThreadPool.shutdown();
                defaultScheduledThreadPool = null;
            }
        }
    }

    /**
     * Return <tt>true</tt>, if {@link TransportFactory} has been closed, or
     * <tt>false</tt> otherwise.
     * 
     * @return <tt>true</tt>, if {@link TransportFactory} has been closed, or
     * <tt>false</tt> otherwise.
     */
    public boolean isClosed() {
        return isClosed;
    }
    
    /**
     * Setup {@link Transport} with factory default settings.
     * 
     * @param <T> {@link Transport} type.
     * @param transport {@link Transport}.
     * @return {@link Transport}, initialized with default settings.
     */
    protected <T extends Transport> T setupTransport(T transport) {
        transport.setAttributeBuilder(defaultAttributeBuilder);
        transport.setMemoryManager(defaultMemoryManager);
        return transport;
    }
}
