/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.logging.Logger;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;

/**
 * Object, which is responsible for holding context during I/O event processing.
 *
 * @author Alexey Stashok
 */
public class Context implements AttributeStorage, Cacheable {

    private static final Logger LOGGER = Grizzly.logger(Context.class);
    private static final ThreadCache.CachedTypeIndex<Context> CACHE_IDX =
            ThreadCache.obtainIndex(Context.class, 4);

    public static Context create(final Connection connection) {
        Context context = ThreadCache.takeFromCache(CACHE_IDX);
        if (context == null) {
            context = new Context();
        }

        context.setConnection(connection);
        return context;
    }
    
   public static Context create(final Connection connection,
            final Processor processor, final Event event) {
        assert processor != null;
        
        final Context context = processor.obtainContext(connection);
        context.setEvent(event);

        return context;
    }    

    public static Context create(final Connection connection,
            final Processor processor, final Event event,
            final EventLifeCycleListener lifeCycleListener) {
        
        final Context context = create(connection, processor, event);
        
        if (lifeCycleListener != null) {
            context.addLifeCycleListener(lifeCycleListener);
        }
        
        return context;
    }

    /**
     * Processing Connection
     */
    private Connection connection;
    /**
     * Processing Event
     */
    protected Event event = Event.NULL;
    /**
     * Processor, responsible for I/O event processing
     */
    private Processor processor;
    /**
     * Attributes, associated with the processing Context
     */
    private final AttributeHolder attributes;
    /**
     * EventProcessingHandler is called to notify about Event processing
     * life-cycle events like suspend, resume, complete.
     */
    protected final MinimalisticArrayList<EventLifeCycleListener> lifeCycleListeners =
            new MinimalisticArrayList<EventLifeCycleListener>(EventLifeCycleListener.class, 2);
    /**
     * <tt>true</tt> if this Event processing was suspended during its processing,
     * or <tt>false</tt> otherwise.
     */
    protected boolean wasSuspended;

   public Context() {
        attributes = AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createUnsafeAttributeHolder();
    }

    /**
     * Notify Context its processing will be suspended in the current thread.
     */
    public void suspend() {
        try {
            final int sz = lifeCycleListeners.size;
            final EventLifeCycleListener[] array = lifeCycleListeners.array;
            for (int i = 0; i < sz; i++) {
                array[i].onSuspend(this);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        
        wasSuspended = true;
    }
    
    /**
     * Notify Context its processing will be resumed in the current thread.
     */
    public void resume() {
        try {
            final int sz = lifeCycleListeners.size;
            final EventLifeCycleListener[] array = lifeCycleListeners.array;
            for (int i = 0; i < sz; i++) {
                array[i].onResume(this);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return  <tt>true</tt> if this ServiceEvent processing was suspended during
     * its processing, or <tt>false</tt> otherwise.
     */
    public boolean wasSuspended() {
        return wasSuspended;
    }

    /**
     * Get the processing {@link Event}.
     *
     * @return the processing {@link Event}.
     */
    public Event getEvent() {
        return event;
    }

    /**
     * Set the processing {@link Event}.
     *
     * @param event the processing {@link Event}.
     */
    public void setEvent(final Event event) {
        this.event = event;
    }

    /**
     * Get the processing {@link Connection}.
     *
     * @return the processing {@link Connection}.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Set the processing {@link Connection}.
     *
     * @param connection the processing {@link Connection}.
     */
    protected void setConnection(final Connection connection) {
        this.connection = connection;
    }

    /**
     * Get the {@link Processor}, which is responsible to process
     * the {@link Event}.
     * 
     * @return the {@link Processor}, which is responsible to process
     * the {@link Event}.
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the {@link Processor}, which is responsible to process
     * the {@link Event}.
     *
     * @param processor the {@link Processor}, which is responsible to process
     * the {@link Event}.
     */
    public void setFilterChain(final Processor processor) {
        this.processor = processor;
    }

    public boolean hasLifeCycleListener(final EventLifeCycleListener listener) {
        return lifeCycleListeners.contains(listener);
    }
    
    public void addLifeCycleListener(final EventLifeCycleListener listener) {
        lifeCycleListeners.add(listener);
    }
    
    public boolean removeLifeCycleListener(final EventLifeCycleListener listener) {
        return lifeCycleListeners.remove(listener);
    }
    
    public void removeAllLifeCycleListeners() {
        lifeCycleListeners.clear();
    }

    /**
     * Get attributes ({@link AttributeHolder}), associated with the processing
     * {@link Context}. {@link AttributeHolder} is cleared after each I/O event
     * processing.
     * Method may return <tt>null</tt>, if there were no attributes added before.
     *
     * @return attributes ({@link AttributeHolder}), associated with the processing
     * {@link Context}. 
     */
    @Override
    public AttributeHolder getAttributes() {
        return attributes;
    }

    /**
     * If implementation uses {@link org.glassfish.grizzly.utils.ObjectPool}
     * to store and reuse {@link Context} instances - this method will be
     * called before {@link Context} will be offered to pool.
     */
    public void reset() {
        attributes.recycle();

        processor = null;
        lifeCycleListeners.clear();
        connection = null;
        event = Event.NULL;
        wasSuspended = false;
    }

    /**
     * Recycle this {@link Context}
     */
    @Override
    public void recycle() {
        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }
    
    protected final class MinimalisticArrayList<E> {

        private E[] array;
        private int size;

        @SuppressWarnings("unchecked")
        private MinimalisticArrayList(final Class<E> clazz,
                final int initialCapacity) {
            array = (E[]) Array.newInstance(clazz, initialCapacity);
        }

        public void add(final E listener) {
            ensureCapacity();
            array[size++] = listener;
        }

        public boolean contains(final E listener) {
            return indexOf(listener) != -1;
        }

        private boolean remove(final E listener) {
            final int idx = indexOf(listener);
            if (idx == -1) {
                return false;
            }
            
            if (idx < size - 1) {
                System.arraycopy(array, idx + 1, array, idx, size - idx - 1);
            }
            
            array[--size] = null;
            return true;
        }
        

        public void copyFrom(final MinimalisticArrayList<E> list) {
            if (list.size > array.length) {
                array = Arrays.copyOf(list.array, list.size);
                size = list.size;
            } else {
                System.arraycopy(list.array, 0, array, 0, list.size);
                for (int i = list.size; i < size; i++) {
                    array[i] = null;
                }
                
                size = list.size;
            }
        }
        
        public int size() {
            return size;
        }
        
        public E[] array() {
            return array;
        }
        
        public void clear() {
            for (int i = 0; i < size; i++) {
                array[i] = null;
            }
            
            size = 0;
        }

        private int indexOf(final E listener) {
            for (int i = 0; i < size; i++) {
                if (array[i].equals(listener)) {
                    return i;
                }
            }
            return -1;
        }

        private void ensureCapacity() {
            if (size == array.length) {
                array = Arrays.copyOf(array, size + 2);
            }
        }
    }
}
