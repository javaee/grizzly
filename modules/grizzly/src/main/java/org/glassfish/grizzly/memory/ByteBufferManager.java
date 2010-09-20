/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.memory;

import org.glassfish.grizzly.monitoring.jmx.AbstractJmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * The simple Buffer manager implementation, which works as wrapper above
 * {@link ByteBuffer}s. It's possible to work either with direct or heap
 * {@link ByteBuffer}s.
 *
 * @see MemoryManager
 * @see ByteBuffer
 * 
 * @author Jean-Francois Arcand
 * @author Alexey Stashok
 */
public class ByteBufferManager implements MemoryManager<ByteBufferWrapper>,
        WrapperAware<ByteBufferWrapper>, ByteBufferAware {
    /**
     * Is direct ByteBuffer should be used?
     */
    protected boolean isDirect;

    protected final AbstractJmxMonitoringConfig<MemoryProbe> monitoringConfig =
            new AbstractJmxMonitoringConfig<MemoryProbe>(MemoryProbe.class) {

        @Override
        public JmxObject createManagementObject() {
            return createJmxManagementObject();
        }

    };
    
    public ByteBufferManager() {
        this(false);
    }

    public ByteBufferManager(boolean isDirect) {
        this.isDirect = isDirect;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper allocate(final int size) {
        return wrap(allocateByteBuffer(size));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper reallocate(ByteBufferWrapper oldBuffer,
            int newSize) {
        return wrap(reallocateByteBuffer(oldBuffer.underlying(), newSize));
    }

    /**
     * Lets JVM Garbage collector to release buffer.
     */
    @Override
    public void release(ByteBufferWrapper buffer) {
        releaseByteBuffer(buffer.underlying());
    }

    /**
     * Returns <tt>true</tt>, if <tt>ByteBufferManager</tt> works with direct
     * {@link ByteBuffer}s, or <tt>false</tt> otherwise.
     * 
     * @return <tt>true</tt>, if <tt>ByteBufferManager</tt> works with direct
     * {@link ByteBuffer}s, or <tt>false</tt> otherwise.
     */
    public boolean isDirect() {
        return isDirect;
    }

    /**
     * Set <tt>true</tt>, if <tt>ByteBufferManager</tt> works with direct
     * {@link ByteBuffer}s, or <tt>false</tt> otherwise.
     *
     * @param isDirect <tt>true</tt>, if <tt>ByteBufferManager</tt> works with
     * direct {@link ByteBuffer}s, or <tt>false</tt> otherwise.
     */
    public void setDirect(boolean isDirect) {
        this.isDirect = isDirect;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(byte[] data) {
        return wrap(data, 0, data.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(byte[] data, int offset, int length) {
        return wrap(ByteBuffer.wrap(data, offset, length));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(String s) {
        return wrap(s, Charset.defaultCharset());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(String s, Charset charset) {
        try {
            byte[] byteRepresentation = s.getBytes(charset.name());
            return wrap(byteRepresentation);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBufferWrapper wrap(ByteBuffer byteBuffer) {
        return new ByteBufferWrapper(this, byteBuffer);
    }

    /**
     * Allocates {@link ByteBuffer} of required size.
     *
     * @param size {@link ByteBuffer} size.
     * @return allocated {@link ByteBuffer}.
     */
    @Override
    public ByteBuffer allocateByteBuffer(int size) {
        ProbeNotifier.notifyBufferAllocated(monitoringConfig, size);

        if (isDirect) {
            return ByteBuffer.allocateDirect(size);
        } else {
            return ByteBuffer.allocate(size);
        }
    }

    @Override
    public ByteBuffer reallocateByteBuffer(ByteBuffer oldByteBuffer, int newSize) {
        ByteBuffer newByteBuffer = allocateByteBuffer(newSize);
        oldByteBuffer.flip();
        return newByteBuffer.put(oldByteBuffer);
    }

    @Override
    public void releaseByteBuffer(ByteBuffer byteBuffer) {
    }

    // ------- Monitoring section ----------------------

    @Override
    public JmxMonitoringConfig<MemoryProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    /**
     * Create the Memory Manager JMX management object.
     *
     * @return the Memory Manager JMX management object.
     */
    protected JmxObject createJmxManagementObject() {
        return new org.glassfish.grizzly.memory.jmx.ByteBufferManager(this);
    }
}
