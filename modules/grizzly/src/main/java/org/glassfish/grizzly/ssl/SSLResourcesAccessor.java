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

package org.glassfish.grizzly.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.threadpool.WorkerThread;
import org.glassfish.grizzly.util.AttributeStorage;

/**
 * Utility class, which simplifies the access to the SSL related resources
 * stored in {@link AttributeHolder}.
 * 
 * @author Alexey Stashok
 */
public class SSLResourcesAccessor {
    public static final String SSL_ENGINE_ATTR_NAME = "SSLEngineAttr";
    public static final String SSL_SECURED_IN_BUFFER_NAME =
            TransportFilter.WORKER_THREAD_BUFFER_NAME;
    public static final String SSL_SECURED_OUT_BUFFER_NAME =
            "SSLOutputBufferAttr";
    public static final String SSL_APP_BUFFER_NAME = "SSLPlainBufferAttr";
    
    private static volatile SSLResourcesAccessor instance =
            new SSLResourcesAccessor().initializeAttributes();

    public static SSLResourcesAccessor getInstance() {
        return instance;
    }

    public static void setInstance(SSLResourcesAccessor instance) {
        SSLResourcesAccessor.instance = instance;
    }

    public Attribute<SSLEngine> sslEngineAttribute;
    public Attribute<Buffer> plainBufferAttribute;
    public Attribute<Buffer> sslSecuredInBufferAttribute;
    public Attribute<Buffer> sslSecuredOutBufferAttribute;

    protected SSLResourcesAccessor initializeAttributes() {
        AttributeBuilder attrBuilder = getAttributeBuilder();
        sslEngineAttribute =
                attrBuilder.<SSLEngine>createAttribute(SSL_ENGINE_ATTR_NAME);
        sslSecuredInBufferAttribute =
                attrBuilder.<Buffer>createAttribute(SSL_SECURED_IN_BUFFER_NAME);
        sslSecuredOutBufferAttribute =
                attrBuilder.<Buffer>createAttribute(SSL_SECURED_OUT_BUFFER_NAME);
        plainBufferAttribute =
                attrBuilder.<Buffer>createAttribute(SSL_APP_BUFFER_NAME);
        return this;
    }

    protected AttributeBuilder getAttributeBuilder() {
        return Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
    }
    
    public SSLEngine getSSLEngine(AttributeStorage state) {
        return getAttribute(state, sslEngineAttribute);
    }

    public SSLEngine obtainSSLEngine(SSLContext sslContext,
            AttributeStorage state) {
        SSLResourcesAccessor stateController =
                SSLResourcesAccessor.getInstance();
        SSLEngine engine = stateController.getSSLEngine(state);
        
        if (engine == null) {
            engine = sslContext.createSSLEngine();
            stateController.setSSLEngine(state, engine);
        }

        return engine;
    }


    public void setSSLEngine(AttributeStorage state, SSLEngine sslEngine) {
        setAttribute(state, sslEngineAttribute, sslEngine);
    }

    public Buffer getAppBuffer(AttributeStorage state) {
        return getAttribute(state, plainBufferAttribute);
    }
    
    /**
     * Obtaining secured input buffer
     *
     * @param state State storage
     * @param sslEngine SSL engine
     * @return Secured input buffer, or null if buffer could not be allocated
     * automatically.
     */
    public Buffer obtainAppBuffer(AttributeStorage state) {
        // #1 - Try to get buffer from the WorkerThread (if possible)

        Buffer buffer = getAppBuffer(state);
        if (buffer != null) return buffer;

        Thread currentThread = Thread.currentThread();
        boolean isWorkingThread = (currentThread instanceof WorkerThread);
        if (isWorkingThread) {
            WorkerThread workerThread = (WorkerThread) currentThread;
            buffer = getAppBuffer(workerThread);
        }

        SSLEngine sslEngine = getSSLEngine(state);
        assert sslEngine != null;
        
        // #2 - Allocate new buffer
        if (buffer == null) {
            if (state instanceof Connection) {
                MemoryManager memoryManager =
                        ((Connection) state).getTransport().getMemoryManager();
                buffer = memoryManager.allocate(sslEngine.getSession().
                        getApplicationBufferSize());
                return buffer;
            } else {
                return null;
            }
        }

        // #3 - Check if buffer size matches SSLEngine's requirements
        if (buffer.capacity() < sslEngine.getSession().getApplicationBufferSize()) {
            if (state instanceof Connection) {
                MemoryManager memoryManager =
                        ((Connection) state).getTransport().getMemoryManager();
                buffer.dispose();
                buffer = memoryManager.allocate(
                        sslEngine.getSession().getApplicationBufferSize());
            } else {
                return null;
            }
        }

        if (isWorkingThread) {
            WorkerThread workerThread = (WorkerThread) currentThread;
            setAppBuffer(workerThread, buffer);
        } else {
            setAppBuffer(state, buffer);
        }

        return buffer;
    }

    public void setAppBuffer(AttributeStorage state, Buffer buffer) {
        setAttribute(state, plainBufferAttribute, buffer);
    }

    public Buffer getSecuredInBuffer(AttributeStorage state) {
        return getAttribute(state, sslSecuredInBufferAttribute);
    }

    /**
     * Obtaining secured input buffer
     *
     * @param state State storage
     * @param sslEngine SSL engine
     * @return Secured input buffer, or null if buffer could not be allocated
     * automatically.
     */
    public Buffer obtainSecuredInBuffer(AttributeStorage state) {

        // #1 - Try to get buffer from the attribute storage (connection)
        Buffer buffer = getSecuredInBuffer(state);

        if (buffer != null) return buffer;

        // #2 - Try to get buffer from the WorkerThread (if possible)
        Thread currentThread = Thread.currentThread();
        boolean isWorkingThread = (currentThread instanceof WorkerThread);

        if (isWorkingThread) {
            WorkerThread workerThread = (WorkerThread) currentThread;
            buffer = getSecuredInBuffer(workerThread);
        }

        SSLEngine sslEngine = getSSLEngine(state);
        assert sslEngine != null;

        // #3 - Allocate new buffer
        if (buffer == null) {
            if (state instanceof Connection) {
                MemoryManager memoryManager =
                        ((Connection) state).getTransport().getMemoryManager();
                buffer = memoryManager.allocate(
                        sslEngine.getSession().getPacketBufferSize());
                buffer.flip();
            } else {
                return null;
            }
        }

        // #4 - Check if buffer size matches SSLEngine's requirements
        if (buffer.capacity() < sslEngine.getSession().getPacketBufferSize()) {
            if (state instanceof Connection) {
                MemoryManager memoryManager =
                        ((Connection) state).getTransport().getMemoryManager();
                Buffer newBuffer = memoryManager.allocate(
                        sslEngine.getSession().getPacketBufferSize());
                newBuffer.put(buffer);
                buffer.dispose();
                buffer = newBuffer.flip();
            } else {
                return null;
            }
        }
        
        if (isWorkingThread) {
            WorkerThread workerThread = (WorkerThread) currentThread;
            setSecuredInBuffer(workerThread, buffer);
        } else {
            setSecuredInBuffer(state, buffer);
        }

        return buffer;
    }

    public void setSecuredInBuffer(AttributeStorage state, Buffer buffer) {
        setAttribute(state, sslSecuredInBufferAttribute, buffer);
    }

    public Buffer getSecuredOutBuffer(AttributeStorage state) {
        return getAttribute(state, sslSecuredOutBufferAttribute);
    }

    /**
     * Obtaining secured output buffer
     *
     * @param state State storage
     * @param sslEngine SSL engine
     * @return Secured output buffer, or null if buffer could not be allocated
     * automatically.
     */
    public Buffer obtainSecuredOutBuffer(AttributeStorage state) {

        // #1 - Try to get buffer from the attribute storage (connection)
        Buffer buffer = getSecuredOutBuffer(state);

        if (buffer != null) return buffer;

        // #2 - Try to get buffer from the WorkerThread (if possible)
        Thread currentThread = Thread.currentThread();
        boolean isWorkingThread = (currentThread instanceof WorkerThread);

        if (isWorkingThread) {
            WorkerThread workerThread = (WorkerThread) currentThread;
            buffer = getSecuredOutBuffer(workerThread);
        }

        SSLEngine sslEngine = getSSLEngine(state);
        assert sslEngine != null;

        // #3 - Allocate new buffer
        if (buffer == null) {
            if (state instanceof Connection) {
                MemoryManager memoryManager =
                        ((Connection) state).getTransport().getMemoryManager();
                buffer = memoryManager.allocate(
                        sslEngine.getSession().getPacketBufferSize());
                return buffer;
            } else {
                return null;
            }
        }

        // #4 - Check if buffer size matches SSLEngine's requirements
        if (buffer.capacity() < sslEngine.getSession().getPacketBufferSize()) {
            if (state instanceof Connection) {
                MemoryManager memoryManager =
                        ((Connection) state).getTransport().getMemoryManager();
                buffer.dispose();
                buffer = memoryManager.allocate(
                        sslEngine.getSession().getPacketBufferSize());
            } else {
                return null;
            }
        }

        if (isWorkingThread) {
            WorkerThread workerThread = (WorkerThread) currentThread;
            setSecuredOutBuffer(workerThread, buffer);
        } else {
            setSecuredOutBuffer(state, buffer);
        }

        return buffer;
    }

    public void setSecuredOutBuffer(AttributeStorage state, Buffer buffer) {
        setAttribute(state, sslSecuredOutBufferAttribute, buffer);
    }

    public void clear(AttributeStorage state) {
        AttributeHolder attributeHolder = state.getAttributes();
        if (attributeHolder != null) {
            sslEngineAttribute.remove(attributeHolder);
            plainBufferAttribute.remove(attributeHolder);
            sslSecuredInBufferAttribute.remove(attributeHolder);
            sslSecuredOutBufferAttribute.remove(attributeHolder);
        }
    }

    protected <T> T getAttribute(AttributeStorage state,
            Attribute<T> attribute) {
         AttributeHolder holder = state.getAttributes();
         if (holder != null) {
             return attribute.get(holder);
         }

         return null;
    }

    protected <T> void setAttribute(AttributeStorage state,
            Attribute<T> attribute, T value) {
         AttributeHolder holder = state.obtainAttributes();
         attribute.set(holder, value);
    }
}
