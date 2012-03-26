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
import java.util.logging.Logger;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;

/**
 * Object, which is responsible for holding context during I/O event processing.
 *
 * @author Alexey Stashok
 */
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
            final Processor processor, final Event event) {
        final Context context;

        if (processor != null) {
            context = processor.obtainContext(connection);
        } else {
            context = NULL_PROCESSOR.obtainContext(connection);
        }

        context.setEvent(event);

        return context;
    }

    public static Context create(final Connection connection,
            final Processor processor, final ServiceEvent event,
            final ServiceEventProcessingHandler processingHandler) {
        
        final Context context = create(connection, processor, event);
        context.setProcessingHandler(processingHandler);
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
     * ServiceEventProcessingHandler is called to notify about ServiceEvent processing
     * life-cycle events like suspend, resume, complete.
     */
    protected ServiceEventProcessingHandler serviceEventProcessingHandler;
    /**
     * <tt>true</tt> if this ServiceEvent processing was suspended during its processing,
     * or <tt>false</tt> otherwise.
     */
    protected boolean wasSuspended;

    protected boolean isManualServiceEventControl;

    public Context() {
        attributes = new IndexedAttributeHolder(Grizzly.DEFAULT_ATTRIBUTE_BUILDER);
    }

    /**
     * Notify Context its processing will be suspended in the current thread.
     */
    public void suspend() {
        wasSuspended = true;
        final ServiceEventProcessingHandler processingHandlerLocal = this.serviceEventProcessingHandler;
        if (processingHandlerLocal != null) {
            try {
                processingHandlerLocal.onContextSuspend(this);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
    
    /**
     * Notify Context its processing will be resumed in the current thread.
     */
    public void resume() {
        final ServiceEventProcessingHandler processingHandlerLocal = this.serviceEventProcessingHandler;
        if (processingHandlerLocal != null) {
            try {
                processingHandlerLocal.onContextResume(this);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
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
     * Switches processing to the manual ServiceEvent control.
     * {@link Connection#enableServiceEvent(org.glassfish.grizzly.ServiceEvent)} or
     * {@link Connection#disableServiceEvent(org.glassfish.grizzly.ServiceEvent)} might be
     * explicitly called.
     */
    public void setManualServiceEventControl() {
        isManualServiceEventControl = true;
        final ServiceEventProcessingHandler processingHandlerLocal = this.serviceEventProcessingHandler;
        if (processingHandlerLocal != null) {
            try {
                processingHandlerLocal.onContextManualServiceEventControl(this);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * @return <tt>true</tt>, if processing was switched to the manual ServiceEvent
     * control, or <tt>false</tt> otherwise.
     */
    public boolean isManualServiceEventControl() {
        return isManualServiceEventControl;
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
    public void setEvent(Event event) {
        this.event = event;
        this.serviceEventProcessingHandler = null;
    }

    /**
     * Set the processing {@link ServiceEvent}.
     *
     * @param event the processing {@link ServiceEvent}.
     */
    public void setEvent(final ServiceEvent event,
            final ServiceEventProcessingHandler handler) {
        this.event = event;
        this.serviceEventProcessingHandler = handler;
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
    protected void setConnection(Connection connection) {
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
    public void setProcessor(final Processor processor) {
        this.processor = processor;
    }

    protected ServiceEventProcessingHandler getProcessingHandler() {
        return serviceEventProcessingHandler;
    }

    protected void setProcessingHandler(final ServiceEventProcessingHandler processingHandler) {
        this.serviceEventProcessingHandler = processingHandler;
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
        serviceEventProcessingHandler = null;
        connection = null;
        event = Event.NULL;
        wasSuspended = false;
        isManualServiceEventControl = false;
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
                Object message, CompletionHandler completionHandler,
                LifeCycleHandler lifeCycleHandler) {
            throw new UnsupportedOperationException("Not supported.");
        }
    }
}
