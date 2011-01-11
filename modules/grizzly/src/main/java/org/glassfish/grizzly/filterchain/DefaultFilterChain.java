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
package org.glassfish.grizzly.filterchain;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.GrizzlyFuture;
import java.io.IOException;

import org.glassfish.grizzly.ProcessorResult;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Appendable;
import org.glassfish.grizzly.Appender;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext.Operation;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.impl.UnsafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

/**
 * Default {@link FilterChain} implementation
 *
 * @see FilterChain
 * @see Filter
 * 
 * @author Alexey Stashok
 */
public final class DefaultFilterChain extends ListFacadeFilterChain {

//    Comment the mapping array, because "if"s work faster
//    private static final Operation[] IOEVENT_2_OPERATION = new Operation[] {
//        Operation.NONE, Operation.NONE, Operation.ACCEPT,
//        Operation.NONE, Operation.CONNECT,
//        Operation.READ, Operation.NONE, Operation.CLOSE
//    };

    public enum FILTER_STATE_TYPE {
        INCOMPLETE, REMAINDER
    }

    public enum FilterExecution {
        CONTINUE,
        REEXECUTE,
        TERMINATE
    }
    protected final Attribute<FiltersState> FILTERS_STATE_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            DefaultFilterChain.class.getName() + "-" +
            System.identityHashCode(this) + ".connection-state");
    
    /**
     * Logger
     */
    private static final Logger LOGGER = Grizzly.logger(DefaultFilterChain.class);

    public DefaultFilterChain() {
        this(new ArrayList<Filter>());
    }

    public DefaultFilterChain(Collection<Filter> initialFilters) {
        super(new ArrayList<Filter>(initialFilters));
    }

    @Override
    public ProcessorResult process(Context context) throws IOException {
        final InternalContextImpl internalContext = (InternalContextImpl) context;
        final FilterChainContext filterChainContext = internalContext.filterChainContext;

        if (filterChainContext.getOperation() == Operation.NONE) {
            final IOEvent ioEvent = internalContext.getIoEvent();

            if (ioEvent != IOEvent.WRITE) {
                filterChainContext.setOperation(FilterChainContext.ioEvent2Operation(ioEvent));
//                filterChainContext.setOperation(IOEVENT_2_OPERATION[ioEvent.ordinal()]);
            } else {
                // On OP_WRITE - call the async write queue
                final Connection connection = context.getConnection();
                final AsyncQueueEnabledTransport transport =
                        (AsyncQueueEnabledTransport) connection.getTransport();
                final AsyncQueueWriter writer = transport.getAsyncQueueIO().getWriter();

                writer.processAsync(connection);

                return ProcessorResult.createCompleteLeave();
            }
        }

        return execute(filterChainContext);
    }

    @Override
    public GrizzlyFuture read(Connection connection,
            CompletionHandler completionHandler) throws IOException {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.setOperation(FilterChainContext.Operation.READ);
        context.getTransportContext().configureBlocking(true);

        return ReadyFutureImpl.create(read(context));
    }

    @Override
    public ReadResult read(final FilterChainContext context) throws IOException {
        final Connection connection = context.getConnection();
        if (!context.getTransportContext().isBlocking()) {
            throw new IllegalStateException("FilterChain doesn't support standalone non blocking read. Please use Filter instead.");
        } else {
            final UnsafeFutureImpl<FilterChainContext> future =
                    UnsafeFutureImpl.<FilterChainContext>create();
            context.operationCompletionFuture = future;

            final FilterExecutor executor = ExecutorResolver.resolve(context);

            do {
                if (!checkRemainder(context, executor, 0, context.getEndIdx())) {
                    context.setFilterIdx(0);
                    context.setMessage(null);
                }
                
                executeChainPart(context, executor, context.getFilterIdx(), context.getEndIdx());
            } while (!future.isDone());

            try {
                final FilterChainContext retContext = future.get();
                ReadResult rr = ReadResult.create(connection);
                rr.setMessage(retContext.getMessage());
                rr.setSrcAddress(retContext.getAddress());

                future.recycle(false);

                return rr;
            } catch (ExecutionException e) {
                Throwable t = e.getCause();
                if (t instanceof IOException) {
                    throw (IOException) t;
                }

                throw new IOException(t);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public GrizzlyFuture write(Connection connection,
            Object dstAddress, Object message,
            CompletionHandler completionHandler)
            throws IOException {
        final FutureImpl<WriteResult> future = SafeFutureImpl.create();

        final FilterChainContext context = obtainFilterChainContext(connection);
        context.transportFilterContext.future = future;
        context.transportFilterContext.completionHandler = completionHandler;
        context.setAddress(dstAddress);
        context.setMessage(message);
        context.setOperation(Operation.WRITE);
        ProcessorExecutor.execute(context.internalContext);

        return future;
    }

    @Override
    public <M> GrizzlyFuture<WriteResult> flush(final Connection connection,
            final CompletionHandler<WriteResult> completionHandler)
            throws IOException {
        final FutureImpl<WriteResult> future = SafeFutureImpl.create();

        final FilterChainContext context = obtainFilterChainContext(connection);
        context.setOperation(Operation.EVENT);
        context.event = TransportFilter.createFlushEvent(future, completionHandler);
        ExecutorResolver.DOWNSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);

        return future;
    }

    @Override
    public GrizzlyFuture<FilterChainContext> fireEventDownstream(final Connection connection,
            final FilterChainEvent event,
            final CompletionHandler<FilterChainContext> completionHandler) throws IOException {
        final FutureImpl<FilterChainContext> future =
                SafeFutureImpl.<FilterChainContext>create();

        final FilterChainContext context = obtainFilterChainContext(connection);
        context.operationCompletionFuture = future;
        context.operationCompletionHandler = completionHandler;
        context.setOperation(Operation.EVENT);
        context.event = event;
        ExecutorResolver.DOWNSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);

        return future;
    }

    @Override
    public GrizzlyFuture<FilterChainContext> fireEventUpstream(final Connection connection,
            final FilterChainEvent event,
            final CompletionHandler<FilterChainContext> completionHandler) throws IOException {
        final FutureImpl<FilterChainContext> future =
                SafeFutureImpl.<FilterChainContext>create();

        final FilterChainContext context = obtainFilterChainContext(connection);
        context.operationCompletionFuture = future;
        context.operationCompletionHandler = completionHandler;
        context.setOperation(Operation.EVENT);
        context.event = event;
        ExecutorResolver.UPSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);

        return future;
    }

    @Override
    public void fail(FilterChainContext context, Throwable failure) {
        throwChain(context, ExecutorResolver.resolve(context), failure);
    }

    /**
     * Execute this FilterChain.
     * @param ctx {@link FilterChainContext} processing context
     * @throws java.lang.Exception
     */
    @Override
    public ProcessorResult execute(FilterChainContext ctx) {
        final FilterExecutor executor = ExecutorResolver.resolve(ctx);

        if (isEmpty()) {
            ProcessorResult.createComplete();
        }

        if (ctx.getFilterIdx() == FilterChainContext.NO_FILTER_INDEX) {
            executor.initIndexes(ctx);
        }

        final int end = ctx.getEndIdx();

        try {

            FilterExecution status;
            do {
                status = executeChainPart(ctx, executor, ctx.getFilterIdx(), end);
                if (status == FilterExecution.TERMINATE) {
                    return ProcessorResult.createTerminate();
                } else if (status == FilterExecution.REEXECUTE) {
                    ctx = cloneContext(ctx);
                    if (checkRemainder(ctx, executor, ctx.getStartIdx(), end)) {
                        // if there is a remainder associated with the connection
                        // rerun the filter chain with the new context right away
                        return ProcessorResult.createRerun(ctx.internalContext);
                    }

                    // recycle cloned context
                    ctx.recycle();
                    // reregister to listen for next operation
                    return ProcessorResult.createReregister();
                }
            } while (checkRemainder(ctx, executor, ctx.getStartIdx(), end));
        } catch (IOException e) {
            try {
                LOGGER.log(Level.FINE, "Exception during FilterChain execution", e);
                throwChain(ctx, executor, e);
                ctx.getConnection().close().markForRecycle(true);
            } catch (IOException ioe) {
                LOGGER.log(Level.FINE, "Exception during reporting the failure", ioe);
            }

            return ProcessorResult.createCompleteLeave();
        } catch (Exception e) {
            try {
                LOGGER.log(Level.WARNING, "Exception during FilterChain execution", e);
                throwChain(ctx, executor, e);
                ctx.getConnection().close().markForRecycle(true);
            } catch (IOException ioe) {
                LOGGER.log(Level.FINE, "Exception during reporting the failure", ioe);
            }

            return ProcessorResult.createCompleteLeave();
        }

        return ProcessorResult.createComplete();
    }

    /**
     * Sequentially lets each {@link Filter} in chain to process {@link IOEvent}.
     * 
     * @param ctx {@link FilterChainContext} processing context
     * @param executor {@link FilterExecutor}, which will call appropriate
     *          filter operation to process {@link IOEvent}.
     * @return TODO: Update
     */
    protected final FilterExecution executeChainPart(FilterChainContext ctx,
            FilterExecutor executor,
            int start,
            int end)
            throws IOException {

        final Connection connection = ctx.getConnection();

        FiltersState filtersState = FILTERS_STATE_ATTR.get(connection);

        int i = start;

        while (i != end) {
            // current Filter to be executed
            final Filter currentFilter = get(i);

            // Checks if there was a remainder message stored from the last filter execution
            checkStoredMessage(ctx, filtersState, i);

            // Save initial inputMessage
            final Object inputMessage = ctx.getMessage();

            // execute the task
            final NextAction nextNextAction = executeFilter(executor,
                    currentFilter, ctx);

            final int nextNextActionType = nextNextAction.type();

            if (nextNextActionType == SuspendAction.TYPE) { // on suspend - return immediatelly
                return FilterExecution.TERMINATE;
            }

            if (nextNextActionType == InvokeAction.TYPE) { // if we need to execute next filter
                // Take the remainder, if any?
                final Object remainder = ((InvokeAction) nextNextAction).getRemainder();
                if (remainder != null) {
                    boolean isStoreRemainder = true;

                    if (remainder == inputMessage && remainder instanceof Buffer) {
                        final Buffer bufferToStore = (Buffer) remainder;
                        bufferToStore.shrink();
                        isStoreRemainder = bufferToStore.hasRemaining();
                    }

                    if (isStoreRemainder) {
                        filtersState = storeMessage(ctx,
                                filtersState, FILTER_STATE_TYPE.REMAINDER, i,
                                remainder, null);
                    } else {
                        ((Buffer) remainder).tryDispose();
                    }
                }
            } else if (nextNextActionType == SuspendingStopAction.TYPE) {
                ctx.suspend();
                return FilterExecution.REEXECUTE;
            } else {
                // If the next action is StopAction and there is some data to store for the processed Filter - store it
                Object messageToStore;
                if (nextNextActionType == StopAction.TYPE
                        && (messageToStore =
                        ((StopAction) nextNextAction).getRemainder()) != null) {

                    storeMessage(ctx,
                            filtersState, FILTER_STATE_TYPE.INCOMPLETE, i,
                            messageToStore, ((StopAction) nextNextAction).getAppender());
                    return FilterExecution.CONTINUE;
                }

                return FilterExecution.CONTINUE;
            }

            i = executor.getNextFilter(ctx);
            ctx.setFilterIdx(i);
        }

        if (i == end) {
            notifyComplete(ctx);
        }

        return FilterExecution.CONTINUE;
    }

    /**
     * Execute the {@link Filter}, using specific {@link FilterExecutor} and
     * {@link FilterChainContext}.
     * 
     * @param executor
     * @param currentFilter
     * @param ctx
     *
     * @return {@link NextAction}.
     * 
     * @throws IOException
     */
    protected NextAction executeFilter(final FilterExecutor executor,
            final Filter currentFilter, final FilterChainContext ctx)
            throws IOException {

        NextAction nextNextAction;
        do {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINE, "Execute filter. filter={0} context={1}",
                        new Object[]{currentFilter, ctx});
            }
            // execute the task
            nextNextAction = executor.execute(currentFilter, ctx);

            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINE, "after execute filter. filter={0} context={1} nextAction={2}",
                        new Object[]{currentFilter, ctx, nextNextAction});
            }
        } while (nextNextAction.type() == RerunFilterAction.TYPE);

        return nextNextAction;
    }

    /**
     * Sequentially lets each executed {@link Filter} to post process
     * {@link IOEvent}. The {@link Filter}s will be called in opposite order
     * they were called on processing phase.
     *
     * @param ctx {@link FilterChainContext} processing context
     * @param executor {@link FilterExecutor}, which will call appropriate
     *          filter operation to post process {@link IOEvent}.
     * @return <tt>false</tt> to terminate execution, or <tt>true</tt> for
     *         normal execution process
     */
    protected boolean checkRemainder(FilterChainContext ctx,
            FilterExecutor executor, int start, int end) {

        final Connection connection = ctx.getConnection();

        // Take the last added remaining data info
        final FiltersState filtersState = FILTERS_STATE_ATTR.get(connection);

        if (filtersState == null) {
            return false;
        }
        final int add = (end - start > 0) ? 1 : -1;
        final Operation operation = ctx.getOperation();

        for (int i = end - add; i != start - add; i -= add) {
            final FilterStateElement element = filtersState.getState(operation, i);
            if (element != null
                    && element.getType() == FILTER_STATE_TYPE.REMAINDER) {
                ctx.setFilterIdx(i);
                ctx.setMessage(null);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Notify the filters about error.
     * @param ctx {@link FilterChainContext}
     * @return position of the last executed {@link Filter}
     */
    private void throwChain(final FilterChainContext ctx,
            final FilterExecutor executor, final Throwable exception) {

        notifyFailure(ctx, exception);

        final int endIdx = ctx.getStartIdx();

        if (ctx.getFilterIdx() == endIdx) {
            return;
        }

        int i;
        while (true) {
            i = executor.getPreviousFilter(ctx);
            ctx.setFilterIdx(i);
            get(i).exceptionOccurred(ctx, exception);

            if (i == endIdx) {
                return;
            }
        }
    }

    @Override
    public DefaultFilterChain subList(int fromIndex, int toIndex) {
        return new DefaultFilterChain(filters.subList(fromIndex, toIndex));
    }

    /**
     * Checks if {@link Connection} has some stored data related to the processing
     * {@link Filter}. If yes - appends new context data to the stored one and
     * set the result as context message.
     * 
     * @param ctx {@link FilterChainContext}
     * @param filtersState {@link FiltersState} associated with the Connection
     * @param filterIdx the current filter index
     */
    private void checkStoredMessage(final FilterChainContext ctx,
            final FiltersState filtersState, final int filterIdx) {

        final Operation operation = ctx.getOperation();
        final FilterStateElement filterState;

        // Check if there is any data stored for the current Filter
        if (filtersState != null && (filterState = filtersState.clearState(operation, filterIdx)) != null) {
            Object storedMessage = filterState.getState();
            final Object currentMessage = ctx.getMessage();
            if (currentMessage != null) {
                final Appender appender = filterState.getAppender();
                if (appender != null) {
                    storedMessage = appender.append(storedMessage, currentMessage);
                } else {
                    storedMessage = ((Appendable) storedMessage).append(currentMessage);
                }
            }
            ctx.setMessage(storedMessage);
        }
    }

    /**
     * Stores the Filter associated remainder. This remainder will be reused next
     * time the same filter will be invoked on this Connection.
     * 
     * @param ctx
     * @param filtersState
     * @param type
     * @param filterIdx
     * @param messageToStore
     * @param appender
     * @return
     */
    private FiltersState storeMessage(final FilterChainContext ctx,
            FiltersState filtersState, FILTER_STATE_TYPE type, final int filterIdx,
            final Object messageToStore, final Appender appender) {

        if (filtersState == null) {
            final Connection connection = ctx.getConnection();
            filtersState = new FiltersState(size());
            FILTERS_STATE_ATTR.set(connection, filtersState);
        }

        final Operation operation = ctx.getOperation();

        filtersState.setState(operation, filterIdx,
                new FilterStateElement(type, messageToStore, appender));

        return filtersState;
    }

    private void notifyComplete(final FilterChainContext context) {
        final CompletionHandler completionHandler = context.operationCompletionHandler;
        final FutureImpl future = context.operationCompletionFuture;

        if (completionHandler != null) {
            completionHandler.completed(context);
        }

        if (future != null) {
            future.result(context);
        }


        // If TransportFilter was invoked on the way - the following CompletionHandler and Future
        // will be null.
        final CompletionHandler transportCompletionHandler = context.transportFilterContext.completionHandler;
        final FutureImpl transportFuture = context.transportFilterContext.future;

        if (transportCompletionHandler != null) {
            transportCompletionHandler.completed(null);
        }

        if (transportFuture != null) {
            transportFuture.result(null);
        }
    }

    private FilterChainContext cloneContext(FilterChainContext ctx) {

        final FilterChain p = ctx.getFilterChain();
        final FilterChainContext newContext =
                p.obtainFilterChainContext(ctx.getConnection());
        newContext.setOperation(ctx.getOperation());
        newContext.internalContext.setPostProcessor(
                ctx.internalContext.getPostProcessor());
        newContext.internalContext.setIoEvent(
                ctx.internalContext.getIoEvent());
        newContext.setStartIdx(ctx.getStartIdx());
        newContext.setEndIdx(ctx.getEndIdx());
        newContext.setFilterIdx(ctx.getFilterIdx());

        return newContext;
    }

    private void notifyFailure(FilterChainContext context, Throwable e) {

        final CompletionHandler completionHandler = context.operationCompletionHandler;
        final FutureImpl future = context.operationCompletionFuture;

        if (completionHandler != null) {
            completionHandler.failed(e);
        }

        if (future != null) {
            future.failure(e);
        }

        final CompletionHandler transportCompletionHandler = context.transportFilterContext.completionHandler;
        final FutureImpl transportFuture = context.transportFilterContext.future;

        if (transportCompletionHandler != null) {
            transportCompletionHandler.failed(e);
        }

        if (transportFuture != null) {
            transportFuture.failure(e);
        }
    }

    public static final class FiltersState {

        private final FilterStateElement[][] state;

        public FiltersState(int filtersNum) {
            this.state = new FilterStateElement[Operation.values().length][filtersNum];
        }

        public FilterStateElement getState(final Operation operation,
                final int filterIndex) {
            return state[operation.ordinal()][filterIndex];
        }

        public void setState(final Operation operation, final int filterIndex,
                final FilterStateElement stateElement) {
            state[operation.ordinal()][filterIndex] = stateElement;
        }

        public FilterStateElement clearState(final Operation operation,
                final int filterIndex) {

            final int operationIdx = operation.ordinal();
            final FilterStateElement oldState = state[operationIdx][filterIndex];
            state[operationIdx][filterIndex] = null;
            return oldState;
        }

        public int indexOf(final Operation operation,
                final FILTER_STATE_TYPE type) {
            return indexOf(operation, type, 0);
        }

        public int indexOf(final Operation operation,
                final FILTER_STATE_TYPE type, final int start) {
            final int eventIdx = operation.ordinal();
            final int length = state[eventIdx].length;

            for (int i = start; i < length; i++) {
                final FilterStateElement filterState;
                if ((filterState = state[eventIdx][i]) != null
                        && filterState.getType() == type) {
                    return i;
                }
            }

            return -1;
        }

        public int lastIndexOf(final IOEvent event,
                final FILTER_STATE_TYPE type, final int end) {
            final int eventIdx = event.ordinal();

            for (int i = end - 1; i >= 0; i--) {
                final FilterStateElement filterState;
                if ((filterState = state[eventIdx][i]) != null
                        && filterState.getType() == type) {
                    return i;
                }
            }

            return -1;
        }

        public int lastIndexOf(final IOEvent event,
                final FILTER_STATE_TYPE type) {
            return lastIndexOf(event, type, state[event.ordinal()].length);
        }
    }

    public static final class FilterStateElement {

        public static FilterStateElement create(
                final FILTER_STATE_TYPE type,
                final Object remainder) {
            if (remainder instanceof Buffer) {
                return create(type, remainder,
                        Buffers.BUFFER_APPENDER);
            } else {
                return create(type, (Appendable) remainder);
            }
        }

        public static FilterStateElement create(
                final FILTER_STATE_TYPE type, final Appendable state) {
            return new FilterStateElement(type, state);
        }

        public static <E> FilterStateElement create(
                final FILTER_STATE_TYPE type,
                final E state, final Appender<E> appender) {
            return new FilterStateElement(type, state, appender);
        }
        
        private final FILTER_STATE_TYPE type;
        private final Object state;
        private final Appender appender;

        private FilterStateElement(FILTER_STATE_TYPE type, Appendable state) {
            this.type = type;
            this.state = state;
            appender = null;
        }

        private <E> FilterStateElement(FILTER_STATE_TYPE type, E state, Appender<E> appender) {
            this.type = type;
            this.state = state;
            this.appender = appender;
        }

        private FILTER_STATE_TYPE getType() {
            return type;
        }

        public Object getState() {
            return state;
        }

        public Appender getAppender() {
            return appender;
        }
    }
}
