/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.memory.jmx;

import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.memory.MemoryProbe;
import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;
import org.glassfish.gmbal.NameValue;
import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;

/**
 * {@link org.glassfish.grizzly.memory.MemoryManager} JMX object.
 *
 * @author Alexey Stashok
 */
@ManagedObject
@Description("Grizzly Memory Manager")
public class MemoryManager extends JmxObject {

    protected final org.glassfish.grizzly.memory.MemoryManager memoryManager;
    private final MemoryProbe probe;

    private final AtomicLong totalAllocatedBytes = new AtomicLong();
    private final AtomicLong realAllocatedBytes = new AtomicLong();
    private final AtomicLong poolAllocatedBytes = new AtomicLong();
    private final AtomicLong poolReleasedBytes = new AtomicLong();
    
    public MemoryManager(org.glassfish.grizzly.memory.MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        probe = new JmxMemoryProbe();
    }

    @Override
    public String getJmxName() {
        return "MemoryManager";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        memoryManager.getMonitoringConfig().addProbes(probe);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onDeregister(GrizzlyJmxManager mom) {
        memoryManager.getMonitoringConfig().removeProbes(probe);
    }

    @NameValue
    public String getMemoryManagerType() {
        return memoryManager.getClass().getName();
    }

    @ManagedAttribute(id="total-allocated-bytes")
    @Description("Total number of allocated bytes (real + pool)")
    public long getTotalAllocatedBytes() {
        return totalAllocatedBytes.get();
    }

    @ManagedAttribute(id="real-allocated-bytes")
    @Description("Total number of bytes allocated using ByteBuffer.allocate(...) operation")
    public long getRealAllocatedBytes() {
        return realAllocatedBytes.get();
    }

    @ManagedAttribute(id="pool-allocated-bytes")
    @Description("Total number of bytes allocated from memory pool")
    public long getPoolAllocatedBytes() {
        return poolAllocatedBytes.get();
    }

    @ManagedAttribute(id="pool-released-bytes")
    @Description("Total number of bytes released to memory pool")
    public long getPoolReleasedBytes() {
        return poolReleasedBytes.get();
    }

    private class JmxMemoryProbe implements MemoryProbe {

        @Override
        public void onBufferAllocateEvent(int size) {
            totalAllocatedBytes.addAndGet(size);
            realAllocatedBytes.addAndGet(size);
        }

        @Override
        public void onBufferAllocateFromPoolEvent(int size) {
            totalAllocatedBytes.addAndGet(size);
            poolAllocatedBytes.addAndGet(size);
        }

        @Override
        public void onBufferReleaseToPoolEvent(int size) {
            poolReleasedBytes.addAndGet(size);
        }

    }
}
