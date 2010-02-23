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

import com.sun.grizzly.Transport.IOEventReg;
import com.sun.grizzly.attributes.AttributeHolder;
import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.attributes.IndexedAttributeHolder;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.nio.NIOConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Object, which is responsible for holding context during I/O event processing.
 *
 * @author Alexey Stashok
 */
public class Context implements AttributeStorage, Cacheable {
    private static final Logger logger = Grizzly.logger(Context.class);

    private static final ThreadCache.CachedTypeIndex<Context> CACHE_IDX =
            ThreadCache.obtainIndex(Context.class, 4);
    
    public static Context create() {
        final Context context = ThreadCache.takeFromCache(CACHE_IDX);
        if (context != null) {
            return context;
        }

        return new Context();
    }

    public static Context create(Processor processor) {
        return processor.context();
    }

    public static Context create(Processor processor, Connection connection,
            IOEvent ioEvent, FutureImpl future,
            CompletionHandler completionHandler) {
        final Context context = create(processor);
        context.setConnection(connection);
        context.setIoEvent(ioEvent);
        context.setCompletionFuture(future);
        context.setCompletionHandler(completionHandler);

        return context;
    }

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
     * Attributes, associated with the processing Context
     */
    private final AttributeHolder attributes;
    
    /**
     * Context task state
     */
    private volatile State state;

    /**
     * {@link Future}, which will be notified, when {@link Processor} will
     * complete processing of a task.
     */
    private FutureImpl completionFuture;

    /**
     * {@link CompletionHandler}, which will be notified, when {@link Processor}
     * will complete processing of a task.
     */
    private CompletionHandler completionHandler;

    private final Runnable contextRunnable;
    
    public Context() {
        attributes = new IndexedAttributeHolder(Grizzly.DEFAULT_ATTRIBUTE_BUILDER);

        contextRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    final Connection connection = Context.this.connection;
                    final IOEvent ioEvent = Context.this.ioEvent;

                    final Transport transport = connection.getTransport();
                    final IOEventReg ioEventReg =
                            transport.fireIOEvent(Context.this);
                    if (ioEventReg == IOEventReg.REGISTER) {
                        ((NIOConnection) connection).enableIOEvent(ioEvent);
                    }
                } catch (Exception e) {
                    logger.log(Level.FINE, "Exception during running Processor", e);
                }
            }
        };
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

    public FutureImpl getCompletionFuture() {
        return completionFuture;
    }

    public void setCompletionFuture(FutureImpl completionFuture) {
        this.completionFuture = completionFuture;
    }

    public CompletionHandler getCompletionHandler() {
        return completionHandler;
    }

    public void setCompletionHandler(CompletionHandler completionHandler) {
        this.completionHandler = completionHandler;
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

    public final Runnable getRunnable() {
        return contextRunnable;
    }

    /**
     * If implementation uses {@link ObjectPool} to store and reuse
     * {@link Context} instances - this method will be called before
     * {@link Context} will be offered to pool.
     */
    public void reset() {
        attributes.clear();
        
        state = State.RUNNING;
        processor = null;
        connection = null;
        completionFuture = null;
        completionHandler = null;
        ioEvent = IOEvent.NONE;
    }

    /**
     * Recycle this {@link Context}
     */
    @Override
    public void recycle() {
        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }
}
