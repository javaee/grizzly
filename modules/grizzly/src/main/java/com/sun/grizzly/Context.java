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

package com.sun.grizzly;

import com.sun.grizzly.attributes.AttributeHolder;
import com.sun.grizzly.attributes.IndexedAttributeHolder;
import com.sun.grizzly.utils.ObjectPool;
import com.sun.grizzly.utils.PoolableObject;

/**
 * Object, which is responsible for holding context during I/O event processing.
 *
 * @author Alexey Stashok
 */
public class Context implements PoolableObject {
    public enum State {
        RUNNING, SUSPEND;
    };
    
    /**
     * Processing Connection
     */
    private Connection connection;
    
    /**
     * Processing IOEvent
     */
    private IOEvent ioEvent;

    /**
     * Processor, responsible for I/O event processing
     */
    private Processor processor;

    /**
     * PostProcessor to be called, on processing completion
     */
    private PostProcessor postProcessor;

    /**
     * Attributes, associated with the processing Context
     */
    private AttributeHolder attributes;
    
    /**
     * Processing task, executed on processorExecutor
     */
    private ProcessorRunnable processorRunnable;
    
    private final ObjectPool<Context> parentPool;

    /**
     * Context task state
     */
    private volatile State state;

    public Context() {
        this(null);
    }

    public Context(ObjectPool parentPool) {
        this.parentPool = parentPool;
    }
    
    /**
     * Get the current processing task state.
     * @return the current processing task state.
     */
    public State state() {
        return state;
    }

    /**
     * Suspend processing of the current task
     */
    public void suspend() {
        this.state = State.SUSPEND;
    }

    /**
     * Resume processing of the current task
     */
    public void resume() {
        this.state = State.RUNNING;
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
    public void setIoEvent(IOEvent ioEvent) {
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
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    /**
     * Get the {@link ProcessorRunnable} task instance.
     * 
     * @return the {@link ProcessorRunnable} task instance.
     */
    public ProcessorRunnable getProcessorRunnable() {
        return processorRunnable;
    }

    /**
     * Set the {@link ProcessorRunnable} task instance.
     *
     * @param processorRunnable the {@link ProcessorRunnable} task instance.
     */
    public void setProcessorRunnable(ProcessorRunnable processorRunnable) {
        this.processorRunnable = processorRunnable;
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
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    /**
     * Get the {@link PostProcessor}, which will be called after
     * {@link Processor} will finish its execution to finish IOEvent processing.
     *
     * @return the {@link PostProcessor}, which will be called after
     * {@link Processor} will finish its execution to finish IOEvent processing.
     */
    public PostProcessor getPostProcessor() {
        return postProcessor;
    }

    /**
     * Set the {@link PostProcessor}, which will be called after
     * {@link Processor} will finish its execution to finish IOEvent processing.
     *
     * @param ioEventPostProcessor the {@link PostProcessor}, which will be
     * called after {@link Processor} will finish its execution to
     * finish IOEvent processing.
     */
    public void setPostProcessor(PostProcessor ioEventPostProcessor) {
        this.postProcessor = ioEventPostProcessor;
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
    public AttributeHolder getAttributes() {
        return attributes;
    }

    /**
     * Get attributes ({@link AttributeHolder}), associated with the processing
     * {@link Context}. {@link AttributeHolder} is cleared after each I/O event
     * processing.
     * Method will always returns <tt>non-null</tt> {@link AttributeHolder}.
     *
     * @return attributes ({@link AttributeHolder}), associated with the processing
     * {@link Context}.
     */
    public AttributeHolder obtainAttributes() {
        if (attributes == null) {
            if (connection == null) {
                throw new IllegalStateException(
                        "Can not obtain an attributes. " +
                        "Connection is not set for this context object!");
            }

            attributes = initializeAttributeHolder();
        }


        return attributes;
    }

    protected AttributeHolder initializeAttributeHolder() {
        return new IndexedAttributeHolder(
                connection.getTransport().getAttributeBuilder());
    }

    protected void setAttributes(AttributeHolder attributes) {
        this.attributes = attributes;
    }

    /**
     * If implementation uses {@link ObjectPool} to store and reuse
     * {@link Context} instances - this method will be called before
     * {@link Context} will be polled from pool.
     */
    @Override
    public void prepare() {
        if (attributes == null) {
            attributes = 
                    new IndexedAttributeHolder(Grizzly.DEFAULT_ATTRIBUTE_BUILDER);
        }
    }

    /**
     * If implementation uses {@link ObjectPool} to store and reuse
     * {@link Context} instances - this method will be called before
     * {@link Context} will be offered to pool.
     */
    @Override
    public void release() {
        if (attributes != null) {
            attributes.clear();
        }
        
        state = State.RUNNING;
        processor = null;
        postProcessor = null;
        connection = null;
        ioEvent = IOEvent.NONE;
    }

    /**
     * Return this {@link Context} to the {@link ObjectPool} it was
     * taken from.
     */
    public void offerToPool() {
        if (parentPool != null) {
            parentPool.offer(this);
        }
    }
}
