/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package org.glassfish.grizzly;

import java.util.concurrent.ExecutorService;
import org.glassfish.grizzly.nio.DefaultNIOTransportFactory;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import org.glassfish.grizzly.memory.DefaultMemoryManager;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.threadpool.DefaultScheduleThreadPool;
import org.glassfish.grizzly.threadpool.DefaultThreadPool;
import org.glassfish.grizzly.util.ConcurrentQueuePool;
import org.glassfish.grizzly.util.ObjectPool;

/**
 *
 * @author oleksiys
 */
public abstract class TransportFactory {

    private static volatile TransportFactory instance;

    public static synchronized TransportFactory getInstance() {
        if (instance == null || instance.isClosed()) {
            instance = new DefaultNIOTransportFactory();
        }

        return instance;
    }

    public static synchronized void setInstance(TransportFactory manager) {
        instance = manager;
    }

    public abstract TCPNIOTransport createTCPTransport();

    public abstract UDPNIOTransport createUDPTransport();

    private boolean isClosed;
    
    protected AttributeBuilder defaultAttributeBuilder;
    protected ObjectPool<Context> defaultIOEventContextPool;
    protected MemoryManager defaultMemoryManager;
    protected ExecutorService defaultWorkerThreadPool;
    protected ExecutorService defaultScheduledThreadPool;

    protected TransportFactory() {
        initialize();
    }

    public AttributeBuilder getDefaultAttributeBuilder() {
        return defaultAttributeBuilder;
    }

    public void setDefaultAttributeBuilder(AttributeBuilder defaultAttributeBuilder) {
        this.defaultAttributeBuilder = defaultAttributeBuilder;
    }

    public ObjectPool<Context> getDefaultIOEventContextPool() {
        return defaultIOEventContextPool;
    }

    public void setDefaultIOEventContextPool(ObjectPool<Context> defaultIOEventContextPool) {
        this.defaultIOEventContextPool = defaultIOEventContextPool;
    }

    public MemoryManager getDefaultMemoryManager() {
        return defaultMemoryManager;
    }

    public void setDefaultMemoryManager(MemoryManager defaultMemoryManager) {
        this.defaultMemoryManager = defaultMemoryManager;
    }

    public ExecutorService getDefaultWorkerThreadPool() {
        return defaultWorkerThreadPool;
    }

    public void setDefaultWorkerThreadPool(ExecutorService defaultThreadPool) {
        this.defaultWorkerThreadPool = defaultThreadPool;
    }

    public ExecutorService getDefaultScheduledThreadPool() {
        return defaultScheduledThreadPool;
    }

    public void setDefaultScheduledThreadPool(ExecutorService defaultScheduledThreadPool) {
        this.defaultScheduledThreadPool = defaultScheduledThreadPool;
    }
    
    public void initialize() {
        defaultAttributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
        
        defaultIOEventContextPool =
                new ConcurrentQueuePool<Context>() {
                    @Override
                    public Context newInstance() {
                        Context context =
                                new Context(defaultIOEventContextPool);
                        context.setAttributes(
                                new IndexedAttributeHolder(
                                defaultAttributeBuilder));

                        return context;
                    }
                };
        defaultMemoryManager = new DefaultMemoryManager();
        defaultWorkerThreadPool = new DefaultThreadPool();
        defaultScheduledThreadPool = new DefaultScheduleThreadPool();
    }

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

    public boolean isClosed() {
        return isClosed;
    }
    
    protected <T extends Transport> T setupTransport(T transport) {
        transport.setAttributeBuilder(defaultAttributeBuilder);
        transport.setDefaultContextPool(defaultIOEventContextPool);
        transport.setMemoryManager(defaultMemoryManager);
        transport.setWorkerThreadPool(defaultWorkerThreadPool);
        return transport;
    }
}
