/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.Appendable;
import org.glassfish.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.filterchain.FilterChainContext.Operation;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * Default {@link FilterChain} implementation
 *
 * @see FilterChain
 * @see Filter
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("deprecation")
public final class DefaultFilterChain extends ListFacadeFilterChain {

    public enum FILTER_STATE_TYPE {
        INCOMPLETE, UNPARSED
    }
    
    private final FiltersStateFactory filtersStateFactory =
            new FiltersStateFactory();
    
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
    public ProcessorResult process(final Context context) {
        if (isEmpty()) return ProcessorResult.createComplete();
        
        final InternalContextImpl internalContext = (InternalContextImpl) context;
        final FilterChainContext filterChainContext = internalContext.filterChainContext;

        if (filterChainContext.getOperation() == Operation.NONE) {
            final IOEvent ioEvent = internalContext.getIoEvent();

            if (ioEvent != IOEvent.WRITE) {
                filterChainContext.setOperation(FilterChainContext.ioEvent2Operation(ioEvent));
            } else {
                // On OP_WRITE - call the async write queue
                final Connection connection = context.getConnection();
                final AsyncQueueEnabledTransport transport =
                        (AsyncQueueEnabledTransport) connection.getTransport();
                final AsyncQueueWriter writer = transport.getAsyncQueueIO().getWriter();

                return writer.processAsync(context).toProcessorResult();
            }
        }

        return execute(filterChainContext);
    }
    
    /**
     * Execute this FilterChain.
     * @param ctx {@link FilterChainContext} processing context
     * @throws java.lang.Exception
     */
    @Override
    public ProcessorResult execute(FilterChainContext ctx) {
        final FilterExecutor executor = ExecutorResolver.resolve(ctx);

        if (ctx.getFilterIdx() == FilterChainContext.NO_FILTER_INDEX) {
            executor.initIndexes(ctx);
        }

        final Connection connection = ctx.getConnection();
        final FiltersState filtersState = obtainFiltersState(connection);
        final int end = ctx.getEndIdx();

        try {
            do {
                final FilterExecution execution = executeChainPart(ctx,
                        executor, ctx.getFilterIdx(), end, filtersState);
                switch (execution.type) {
                    case FilterExecution.TERMINATE_TYPE:
                        return ProcessorResult.createTerminate();
                    case FilterExecution.REEXECUTE_TYPE:
                        ctx = execution.getContext();
                        
                        final int idx = indexOfRemainder(
                                filtersState,
                                ctx.getOperation(), ctx.getStartIdx(), end);
                        if (idx != -1) {
                            // if there is a remainder associated with the connection
                            // rerun the filter chain with the new context right away
                            ctx.setMessage(null);
                            ctx.setFilterIdx(idx);
                            return ProcessorResult.createRerun(ctx.internalContext);
                        }

                        // reregister to listen for next operation,
                        // keeping the current Context
                        return ProcessorResult.createReregister(ctx.internalContext);
                }
            } while (prepareRemainder(ctx, filtersState,
                    ctx.getStartIdx(), end));
        } catch (Throwable e) {
            LOGGER.log(e instanceof IOException ? Level.FINE : Level.WARNING,
                    "Exception during FilterChain execution", e);
            throwChain(ctx, executor, e);
            ctx.getConnection().closeSilently();

            return ProcessorResult.createError(e);
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
    @SuppressWarnings("unchecked")
    protected final FilterExecution executeChainPart(FilterChainContext ctx,
            final FilterExecutor executor,
            final int start,
            final int end,
            final FiltersState filtersState)
            throws IOException {

        int i = start;
        Filter currentFilter = null;

        int lastNextActionType = InvokeAction.TYPE;
        NextAction lastNextAction = null;

        while (i != end) {

            // current Filter to be executed
            currentFilter = get(i);

            if (ctx.predefinedNextAction == null) {

                // Checks if there was a remainder message stored from the last filter execution
                checkStoredMessage(ctx, filtersState, i);

                // execute the task
                lastNextAction = executeFilter(executor, currentFilter, ctx);
            } else {
                lastNextAction = ctx.predefinedNextAction;
                ctx.predefinedNextAction = null;
            }

            lastNextActionType = lastNextAction.type();
            if (lastNextActionType != InvokeAction.TYPE) { // if we don't need to execute next filter
                break;
            }

            final InvokeAction invokeAction = (InvokeAction) lastNextAction;
            final Object chunk = invokeAction.getChunk();

            if (chunk != null) {
                // Store the remainder
                final FILTER_STATE_TYPE type = invokeAction.isIncomplete() ?
                        FILTER_STATE_TYPE.INCOMPLETE :
                        FILTER_STATE_TYPE.UNPARSED;
                storeMessage(ctx,
                        filtersState,
                        type,
                        i,
                        chunk,
                        invokeAction.getAppender());
            }

            i = executor.getNextFilter(ctx);
            ctx.setFilterIdx(i);
        }

        switch (lastNextActionType) {
            case InvokeAction.TYPE:
                notifyComplete(ctx);
                break;
            case StopAction.TYPE:
                assert currentFilter != null;

                // If the next action is StopAction and there is some data to store for the processed Filter - store it
                final StopAction stopAction = (StopAction) lastNextAction;
                storeMessage(ctx,
                        filtersState,
                        FILTER_STATE_TYPE.INCOMPLETE,
                        i,
                        stopAction.getIncompleteChunk(),
                        stopAction.getAppender());
                break;
            case ForkAction.TYPE:
                final ForkAction forkAction =
                        (ForkAction) lastNextAction;
                return FilterExecution.createReExecute(
                                        forkAction.getContext());
            case SuspendAction.TYPE: // on suspend - return immediatelly
                return FilterExecution.createTerminate();
        }

        return FilterExecution.createContinue();
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
     * Locates a message remainder in the {@link FilterChain}, associated with the
     * {@link Connection} and prepares the {@link Context} for remainder processing.
     */
    protected static boolean prepareRemainder(final FilterChainContext ctx,
            final FiltersState filtersState, final int start, final int end) {

        final int idx = indexOfRemainder(filtersState, ctx.getOperation(),
                start, end);
        
        if (idx != -1) {
            ctx.setFilterIdx(idx);
            ctx.setMessage(null);
            return true;
        }
        
        return false;
    }
    
    /**
     * Locates a message remainder in the {@link FilterChain}, associated with the
     * {@link Connection}.
     */
    protected static int indexOfRemainder(final FiltersState filtersState,
            final Operation operation, final int start, final int end) {

        final int add = (end - start > 0) ? 1 : -1;
        
        for (int i = end - add; i != start - add; i -= add) {
            final FilterStateElement element = filtersState.get(operation, i);
            if (element != null
                    && element.getType() == FILTER_STATE_TYPE.UNPARSED) {
                return i;
            }
        }
        
        return -1;
    }
    
    @Override
    public void read(final Connection connection,
            final CompletionHandler<ReadResult> completionHandler) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.setOperation(FilterChainContext.Operation.READ);
        context.getTransportContext().configureBlocking(true);
        
        try {
            final ReadResult readResult = read(context);
            Futures.notifyResult(null, completionHandler, readResult);
        } catch (IOException e) {
            Futures.notifyFailure(null, completionHandler, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ReadResult read(final FilterChainContext context) throws IOException {
        final Connection connection = context.getConnection();
        if (!context.getTransportContext().isBlocking()) {
            throw new IllegalStateException("FilterChain doesn't support standalone non blocking read. Please use Filter instead.");
        } else {
            final FutureImpl<FilterChainContext> future =
                    Futures.<FilterChainContext>createUnsafeFuture();
            context.operationCompletionHandler =
                    Futures.toCompletionHandler(future);

            final FilterExecutor executor = ExecutorResolver.resolve(context);
            final FiltersState filtersState = obtainFiltersState(connection);

            do {
                if (!prepareRemainder(context, filtersState,
                        0, context.getEndIdx())) {
                    context.setFilterIdx(0);
                    context.setMessage(null);
                }
                
                executeChainPart(context, executor, context.getFilterIdx(),
                        context.getEndIdx(), filtersState);
            } while (!future.isDone());

            try {
                final FilterChainContext retContext = future.get();
                ReadResult rr = ReadResult.create(connection);
                rr.setMessage(retContext.getMessage());
                rr.setSrcAddressHolder(retContext.getAddressHolder());

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
    public void write(final Connection connection,
            final Object dstAddress, final Object message,
            final CompletionHandler<WriteResult> completionHandler) {
        write(connection, dstAddress, message, completionHandler,
                (MessageCloner) null);
    }

    @Override
    public void write(final Connection connection,
            final Object dstAddress, final Object message,
            final CompletionHandler<WriteResult> completionHandler,
            final MessageCloner messageCloner) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.transportFilterContext.completionHandler = completionHandler;
        context.transportFilterContext.cloner = messageCloner;
        context.setAddress(dstAddress);
        context.setMessage(message);
        context.setOperation(Operation.WRITE);
        ProcessorExecutor.execute(context.internalContext);
    }

    
    @Override
    @Deprecated
    public void write(final Connection connection,
            final Object dstAddress, final Object message,
            final CompletionHandler completionHandler,
            final org.glassfish.grizzly.asyncqueue.PushBackHandler pushBackHandler) {

        final FilterChainContext context = obtainFilterChainContext(connection);
        context.transportFilterContext.completionHandler = completionHandler;
        context.transportFilterContext.pushBackHandler = pushBackHandler;
        context.setAddress(dstAddress);
        context.setMessage(message);
        context.setOperation(Operation.WRITE);
        ProcessorExecutor.execute(context.internalContext);
    }

    @Override
    public void flush(final Connection connection,
            final CompletionHandler<WriteResult> completionHandler) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.setOperation(Operation.EVENT);
        context.event = TransportFilter.createFlushEvent(completionHandler);
        ExecutorResolver.DOWNSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);
    }

    @Override
    public void fireEventDownstream(final Connection connection,
            final FilterChainEvent event,
            final CompletionHandler<FilterChainContext> completionHandler) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.operationCompletionHandler = completionHandler;
        context.setOperation(Operation.EVENT);
        context.event = event;
        ExecutorResolver.DOWNSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);
    }

    @Override
    public void fireEventUpstream(final Connection connection,
            final FilterChainEvent event,
            final CompletionHandler<FilterChainContext> completionHandler) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.operationCompletionHandler = completionHandler;
        context.setOperation(Operation.EVENT);
        context.event = event;
        ExecutorResolver.UPSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);
    }

    @Override
    public void fail(FilterChainContext context, Throwable failure) {
        throwChain(context, ExecutorResolver.resolve(context), failure);
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

    @SuppressWarnings("unchecked")
    private FiltersState obtainFiltersState(final Connection connection) {
        return (FiltersState) connection.obtainProcessorState(this, filtersStateFactory);
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
    @SuppressWarnings("unchecked")
    private void checkStoredMessage(final FilterChainContext ctx,
            final FiltersState filtersState, final int filterIdx) {

        final Operation operation = ctx.getOperation();
        final FilterStateElement filterState;

        // Check if there is any data stored for the current Filter
        if (filtersState != null && (filterState = filtersState.get(operation, filterIdx)) != null &&
                filterState.isValid) {
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
            
            filterState.reset();
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
    private <M> void storeMessage(final FilterChainContext ctx,
            final FiltersState filtersState, final FILTER_STATE_TYPE type,
            final int filterIdx, final M messageToStore,
            final Appender<M> appender) {

        if (messageToStore != null) {
            final Operation operation = ctx.getOperation();

            FilterStateElement elem = filtersState.get(operation, filterIdx);
            if (elem != null) {
                elem.set(type, messageToStore, appender);
            } else {
                elem = FilterStateElement.create(type, messageToStore, appender);
                filtersState.set(operation, filterIdx, elem);
            }
        }
    }

    private void notifyComplete(final FilterChainContext context) {
        final CompletionHandler<FilterChainContext> completionHandler =
                context.operationCompletionHandler;
        if (completionHandler != null) {
            completionHandler.completed(context);
        }


        // If TransportFilter was invoked on the way - the following CompletionHandler and Future
        // will be null.
        final CompletionHandler<?> transportCompletionHandler =
                context.transportFilterContext.completionHandler;

        if (transportCompletionHandler != null) {
            transportCompletionHandler.completed(null);
        }
    }

    private void notifyFailure(final FilterChainContext context, final Throwable e) {

        final CompletionHandler completionHandler = context.operationCompletionHandler;

        if (completionHandler != null) {
            completionHandler.failed(e);
        }

        final CompletionHandler transportCompletionHandler = context.transportFilterContext.completionHandler;

        if (transportCompletionHandler != null) {
            transportCompletionHandler.failed(e);
        }
    }

    static final class FiltersState {
        private static final int OPERATIONS_NUM = Operation.values().length;

        private final FilterStateElement[][] state;

        public FiltersState(int filtersNum) {
            this.state = new FilterStateElement[OPERATIONS_NUM][filtersNum];
        }

        public FilterStateElement get(final Operation operation,
                final int filterIndex) {
            return state[operation.ordinal()][filterIndex];
        }

        public void set(final Operation operation, final int filterIndex,
                final FilterStateElement stateElement) {
            state[operation.ordinal()][filterIndex] = stateElement;
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

    static final class FilterStateElement {

        public static FilterStateElement create(
                final FILTER_STATE_TYPE type,
                final Object remainder) {
            if (remainder instanceof Buffer) {
                return create(type, (Buffer) remainder,
                        Buffers.getBufferAppender(true));
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
        
        private FILTER_STATE_TYPE type;
        private Object state;
        private Appender appender;
        private boolean isValid = true;
        
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

        public void reset() {
            type = null;
            state = null;
            appender = null;
            isValid = false;
        }
        
        public <E> void set(final FILTER_STATE_TYPE type, final E state,
                final Appender<E> appender) {
            this.type = type;
            this.state = state;
            this.appender = appender;
            
            isValid = true;
        }
        
        public Object getState() {
            return state;
        }

        public Appender getAppender() {
            return appender;
        }
    }
    
    private final class FiltersStateFactory implements
            NullaryFunction<FiltersState> {

        @Override
        public FiltersState evaluate() {
            return new FiltersState(size());
        }        
    }
    
    private static final class FilterExecution {
        private static final int CONTINUE_TYPE = 0;
        private static final int TERMINATE_TYPE = 1;
        private static final int REEXECUTE_TYPE = 2;
        
        private static final FilterExecution CONTINUE =
                new FilterExecution(CONTINUE_TYPE, null);
        
        private static final FilterExecution TERMINATE =
                new FilterExecution(TERMINATE_TYPE, null);
        
        private final int type;
        private final FilterChainContext context;
        
        public static FilterExecution createContinue() {
            return CONTINUE;
        }

        public static FilterExecution createTerminate() {
            return TERMINATE;
        }
        
        public static FilterExecution createReExecute(final FilterChainContext context) {
            return new FilterExecution(REEXECUTE_TYPE, context);
        }

        public FilterExecution(final int type, final FilterChainContext context) {
            this.type = type;
            this.context = context;
        }

        public int getType() {
            return type;
        }
        
        public FilterChainContext getContext() {
            return context;
        }
    }
}
