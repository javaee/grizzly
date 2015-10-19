/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;

/**
 * Object, which is responsible for holding context during I/O event processing.
 *
 * @author Alexey Stashok
 */
@SuppressWarnings("deprecation")
public class Context implements AttributeStorage, Cacheable {

    private static final Logger LOGGER = Grizzly.logger(Context.class);
    private static final Processor NULL_PROCESSOR = new NullProcessor();
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
            final Processor processor, final IOEvent ioEvent,
            final IOEventLifeCycleListener lifeCycleListener) {
        final Context context;

        if (processor != null) {
            context = processor.obtainContext(connection);
        } else {
            context = NULL_PROCESSOR.obtainContext(connection);
        }

        context.setIoEvent(ioEvent);
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
     * Processing IOEvent
     */
    protected IOEvent ioEvent = IOEvent.NONE;
    /**
     * Processor, responsible for I/O event processing
     */
    private Processor processor;
    /**
     * Attributes, associated with the processing Context
     */
    private final AttributeHolder attributes;
    /**
     * IOEventProcessingHandler is called to notify about IOEvent processing
     * life-cycle events like suspend, resume, complete.
     */
    protected final MinimalisticArrayList<IOEventLifeCycleListener> lifeCycleListeners =
            new MinimalisticArrayList<IOEventLifeCycleListener>(IOEventLifeCycleListener.class, 2);
    /**
     * <tt>true</tt> if this IOEvent processing was suspended during its processing,
     * or <tt>false</tt> otherwise.
     */
    protected boolean wasSuspended;

    protected boolean isManualIOEventControl;
    
   public Context() {
        attributes = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createUnsafeAttributeHolder();
    }

    /**
     * Notify Context its processing will be suspended in the current thread.
     */
    public void suspend() {
        try {
            final int sz = lifeCycleListeners.size;
            final IOEventLifeCycleListener[] array = lifeCycleListeners.array;
            for (int i = 0; i < sz; i++) {
                array[i].onContextSuspend(this);
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
            final IOEventLifeCycleListener[] array = lifeCycleListeners.array;
            for (int i = 0; i < sz; i++) {
                array[i].onContextResume(this);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void complete(final ProcessorResult result) {
        ProcessorExecutor.complete(this, result);
    }
    
    /**
     * @return  <tt>true</tt> if this IOEvent processing was suspended during
     * its processing, or <tt>false</tt> otherwise.
     */
    public boolean wasSuspended() {
        return wasSuspended;
    }

    /**
     * Switches processing to the manual IOEvent control.
     * {@link Connection#enableIOEvent(org.glassfish.grizzly.IOEvent)} or
     * {@link Connection#disableIOEvent(org.glassfish.grizzly.IOEvent)} might be
     * explicitly called.
     */
    public void setManualIOEventControl() {
        try {
            final int sz = lifeCycleListeners.size;
            final IOEventLifeCycleListener[] array = lifeCycleListeners.array;
            for (int i = 0; i < sz; i++) {
                array[i].onContextManualIOEventControl(this);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        
        isManualIOEventControl = true;
    }

    /**
     * @return <tt>true</tt>, if processing was switched to the manual IOEvent
     * control, or <tt>false</tt> otherwise.
     */
    public boolean isManualIOEventControl() {
        return isManualIOEventControl;
    }
    /**
     * Get the processing {@link IOEvent}.
     *
     * @return the processing {@link IOEvent}.
     */
    public IOEvent getIoEvent() {
        return ioEvent;
    }

    /**
     * Set the processing {@link IOEvent}.
     *
     * @param ioEvent the processing {@link IOEvent}.
     */
    public void setIoEvent(final IOEvent ioEvent) {
        this.ioEvent = ioEvent;
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
    public void setConnection(final Connection connection) {
        this.connection = connection;
    }

    /**
     * Get the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     * 
     * @return the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     *
     * @param processor the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     */
    public void setProcessor(final Processor processor) {
        this.processor = processor;
    }

    public boolean hasLifeCycleListener(final IOEventLifeCycleListener listener) {
        return lifeCycleListeners.contains(listener);
    }
    
    public void addLifeCycleListener(final IOEventLifeCycleListener listener) {
        lifeCycleListeners.add(listener);
    }
    
    public boolean removeLifeCycleListener(final IOEventLifeCycleListener listener) {
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
        ioEvent = IOEvent.NONE;
        wasSuspended = false;
        isManualIOEventControl = false;
    }

    /**
     * Recycle this {@link Context}
     */
    @Override
    public void recycle() {
        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    private final static class NullProcessor implements Processor {

        @Override
        public Context obtainContext(Connection connection) {
            final Context context = Context.create(connection);
            context.setProcessor(this);

            return context;
        }

        @Override
        public ProcessorResult process(Context context) {
            return ProcessorResult.createNotRun();
        }

        @Override
        public void read(Connection connection,
                CompletionHandler completionHandler) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public void write(Connection connection, Object dstAddress,
                Object message, CompletionHandler completionHandler) {
            throw new UnsupportedOperationException("Not supported.");
        }
        
        @Override
        public void write(Connection connection, Object dstAddress,
                Object message, CompletionHandler completionHandler,
                MessageCloner messageCloner) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        @Deprecated
        public void write(Connection connection, Object dstAddress,
                Object message, CompletionHandler completionHandler,
                org.glassfish.grizzly.asyncqueue.PushBackHandler pushBackHandler) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean isInterested(IOEvent ioEvent) {
            return true;
        }

        @Override
        public void setInterested(IOEvent ioEvent, boolean isInterested) {
        }
    }

    protected static final class MinimalisticArrayList<E> {

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
