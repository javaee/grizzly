/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Appendable;
import org.glassfish.grizzly.Appender;
import org.glassfish.grizzly.CloseReason;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.ProcessorResult;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext.Operation;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.utils.Exceptions;
import org.glassfish.grizzly.utils.Futures;

/**
 * Default {@link FilterChain} implementation
 *
 * @see FilterChain
 * @see Filter
 * 
 *
 */
public final class DefaultFilterChain implements FilterChain {

    /**
     * Logger
     */
    private static final Logger LOGGER = Grizzly.logger(DefaultFilterChain.class);
    
    // Normal termination type
    private static final ProcessorResult NORMAL_TERMINATE =
            ProcessorResult.createTerminate(NORMAL_TERMINATE_TYPE);
    // Connect termination type
    private static final ProcessorResult CONNECT_TERMINATE =
            ProcessorResult.createTerminate(CONNECT_TERMINATE_TYPE);

    private static final String TAIL_NAME = "TAIL";
    private static final String HEAD_NAME = "HEAD";
    private static final String AUTO_GENERATED_NAME_MARKER = "*";

    private final FilterReg head, tail;
    private final Map<String, FilterReg> idToRegMap =
            new ConcurrentHashMap<String, FilterReg>(8);
    
    public DefaultFilterChain() {
        head = new FilterReg(this, new BaseFilter(), HEAD_NAME, true); // dummy head filter
        tail = new FilterReg(this, new BaseFilter(), TAIL_NAME, true); // dummy tail filter
        head.next = tail;
        tail.prev = head;
    }
    
    public DefaultFilterChain(final Collection<Filter> initialFilters) {
        this();
        
        if (initialFilters == null || initialFilters.isEmpty()) {
            return;
        }
        
        final Iterator<Filter> it = initialFilters.iterator();

        FilterReg current = head;
        
        while (it.hasNext()) {
            final FilterReg next = newFilterReg(it.next(), false);
            current.next = next;
            next.prev = current;
            next.next = tail;
            
            current = next;
            tail.prev = current;
            
            current.filter.onAdded(current);
        }
    }

    private DefaultFilterChain(final FilterChain filterChain) {
        this();
        
        if (filterChain.isEmpty()) {
            return;
        }
        
        FilterReg cElement;
        try {
            cElement = filterChain.firstFilterReg();
        } catch (NoSuchElementException nsee) {
            // source FilterChain was cleaned asynchronously
            return;
        }

        assert cElement != null;
        FilterReg current = head;
        
        do {
            final FilterReg next = newFilterReg(cElement);
            current.next = next;
            next.prev = current;
            next.next = tail;
            
            current = next;
            tail.prev = current;
            
            current.filter.onAdded(current);
            
            cElement = cElement.next();
        } while (cElement != null);
    }
    
    @Override
    public ProcessorResult process(final Context context) {
        if (isEmpty()) return ProcessorResult.createComplete();
        
        final InternalContextImpl internalContext = (InternalContextImpl) context;
        final FilterChainContext filterChainContext = internalContext.filterChainContext;

        if (filterChainContext.getOperation() == Operation.NONE) {
            final Event event = internalContext.getEvent();

            final Operation operation =
                    FilterChainContext.event2Operation(event);

            if (operation != null) {
                filterChainContext.setOperation(operation);
            } else {
                filterChainContext.setOperation(Operation.UPSTREAM_EVENT);
            }
        }

        return execute(filterChainContext);
    }

    @Override
    public final FilterChainContext obtainFilterChainContext(
            final Connection connection) {
        return obtainFilterChainContext(connection, connection);
    }
    
    @Override
    public final FilterChainContext obtainFilterChainContext(
            final Connection connection, final Closeable closeable) {

        final FilterChainContext context =
                FilterChainContext.create(connection, closeable);
        context.internalContext.setFilterChain(this);
        return context;
    }

    @Override
    public FilterChainContext obtainFilterChainContext(
            final Connection connection,
            final FilterReg startFilterReg,
            final FilterReg endFilterReg,
            final FilterReg currentFilterReg) {
        return obtainFilterChainContext(connection, startFilterReg,
                endFilterReg, currentFilterReg, connection);
    }
    
    @Override
    public FilterChainContext obtainFilterChainContext(
            final Connection connection,
            final FilterReg startFilterReg,
            final FilterReg endFilterReg,
            final FilterReg currentFilterReg,
            final Closeable closeable) {
        
        checkFilterReg(startFilterReg);
        checkFilterReg(endFilterReg);
        checkFilterReg(currentFilterReg);
        
        final FilterChainContext context =
                FilterChainContext.create(connection, closeable);
        context.internalContext.setFilterChain(this);
        context.setRegs(startFilterReg != null ? startFilterReg : head.next,
                endFilterReg != null ? endFilterReg : tail,
                currentFilterReg != null ? currentFilterReg : tail);
        return context;
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public final Context obtainContext(final Connection connection) {
        return obtainFilterChainContext(connection).internalContext;
    }
    
    /**
     * Execute this FilterChain.
     * @param initialContext {@link FilterChainContext} processing context
     */
    @Override
    public ProcessorResult execute(final FilterChainContext initialContext) {
        
        FilterChainContext ctx = initialContext;
        
        final FilterExecutor executor = ExecutorResolver.resolve(ctx);

        if (ctx.getFilterReg() == null) {
            executor.initIndexes(ctx);
        }

        final FilterReg endReg = ctx.getEndFilterReg();

        try {
            
            boolean isRerunFilterChain;
            
            do {
                isRerunFilterChain = false;
                final FilterExecution execution = executeChainPart(ctx,
                        executor, ctx.getFilterReg(), endReg);
                switch (execution.type) {
                    case FilterExecution.TERMINATE_TYPE:
                        return NORMAL_TERMINATE;
                    case FilterExecution.CONNECT_TERMINATE_TYPE:
                        return CONNECT_TERMINATE;
                    case FilterExecution.FORK_TYPE:
                        ctx = execution.getContext();
                        isRerunFilterChain = true;
                }
            } while (isRerunFilterChain ||
                    prepareRemainder(ctx, executor,
                    ctx.getStartFilterReg(), endReg));
        } catch (Throwable e) {
            LOGGER.log(e instanceof IOException ? Level.FINE : Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_FILTERCHAIN_EXCEPTION(), e);
            throwChain(ctx, executor, e);
            ctx.getCloseable().closeWithReason(
                    new CloseReason(CloseType.LOCALLY,
                            Exceptions.makeIOException(e)));

            return ProcessorResult.createError(e);
        }

        return ctx == initialContext ?
                ProcessorResult.createComplete() :
                ProcessorResult.createComplete(ctx.internalContext);
    }

    /**
     * Sequentially lets each {@link Filter} in chain to process {@link org.glassfish.grizzly.IOEvent}.
     * 
     * @param ctx {@link FilterChainContext} processing context
     * @param executor {@link FilterExecutor}, which will call appropriate
     *          filter operation to process {@link org.glassfish.grizzly.IOEvent}.
     * @param current the Filter to execute.
     * @param end the last Filter in the chain.
     * @return the execution state from invoking the current filter.
     *
     * @throws java.lang.Exception if an error occurs invoking the filter.
     */
    @SuppressWarnings("unchecked")
    protected final FilterExecution executeChainPart(final FilterChainContext ctx,
            final FilterExecutor executor,
            FilterReg current,
            final FilterReg end)
            throws Exception {

        int lastNextActionType = InvokeAction.TYPE;
        NextAction lastNextAction = null;
        
        while (current != end) {
            
            // current Filter to be executed
            final Filter currentFilter = current.filter;
            
            if (ctx.predefinedThrowable != null) {
                final Throwable error = ctx.predefinedThrowable;
                ctx.predefinedThrowable = null;
                
                Exceptions.throwException(error);
            }
                        
            if (ctx.predefinedNextAction == null) {

                // Checks if there was a remainder message stored from the last filter execution
                checkStoredMessage(ctx, current);

                // execute the task
                lastNextAction = executeFilter(executor, currentFilter, ctx);
            } else {
                lastNextAction = ctx.predefinedNextAction;
                ctx.predefinedNextAction = null;
            }
            
            assert lastNextAction != null;
            
            lastNextActionType = lastNextAction.type();
            if (lastNextActionType != InvokeAction.TYPE) { // if we don't need to execute next filter
                break;
            }
            
            final InvokeAction invokeAction = (InvokeAction) lastNextAction;
            final Object chunk = invokeAction.getChunk();
            
            if (chunk != null) {
                // Store the remainder
                storeMessage(ctx, !invokeAction.isIncomplete(), current,
                        chunk, invokeAction.getAppender());
            }

            current = executor.next(current);
            ctx.setFilterReg(current);
        }

        switch (lastNextActionType) {
            case InvokeAction.TYPE:
                notifyComplete(ctx);
                break;
            case StopAction.TYPE:
                assert current != null;
                assert lastNextAction != null;
                
                // If the next action is StopAction and there is some data to store for the processed Filter - store it
                final StopAction stopAction = (StopAction) lastNextAction;
                
                final Object chunk = stopAction.getIncompleteChunk();
                if (chunk != null) {
                    storeMessage(ctx,
                                 false,
                                 current,
                                 stopAction.getIncompleteChunk(),
                                 stopAction.getAppender());
                }
                break;
            case ForkAction.TYPE:
                assert lastNextAction != null;
                
                final ForkAction forkAction =
                        (ForkAction) lastNextAction;
                return FilterExecution.createFork(
                        forkAction.getContext());
            case SuspendAction.TYPE: // on suspend - return immediatelly
                return FilterExecution.createTerminate();
            case SuspendConnectAction.TYPE: // on suspend connect - return immediatelly
                return FilterExecution.createConnectTerminate();
        }

        return FilterExecution.createContinue();
    }
    
    /**
     * Execute the {@link Filter}, using specific {@link FilterExecutor} and
     * {@link FilterChainContext}.
     * 
     * @param executor the {@link org.glassfish.grizzly.filterchain.FilterExecutor} responsible
     *                 for invoking filter.
     * @param currentFilter the filter to invoke.
     * @param ctx the {@link FilterChainContext} for the current event.
     *
     * @return the {@link org.glassfish.grizzly.filterchain.NextAction} to
     *  be performed.
     * 
     * @throws Exception if an error occurs executing the filter.
     */
    protected NextAction executeFilter(final FilterExecutor executor,
            final Filter currentFilter, final FilterChainContext ctx)
            throws Exception {

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
     * @param ctx the {@link org.glassfish.grizzly.filterchain.FilterChainContext}
     *  for the current event.
     * @param executor the {@link org.glassfish.grizzly.filterchain.FilterExecutor}
     *  responsible for invoking Filters on this chain.
     * @param start the first filter to check for remainders.
     * @param end the last filter to check for remainders.
     * @return <code>true</code> if a remainder was found, otherwise <code>false</code>.
     */
    protected boolean prepareRemainder(final FilterChainContext ctx,
            final FilterExecutor executor,
            final FilterReg start, final FilterReg end) {

        final FilterReg remainderReg = remainderReg(ctx, executor,
                start, end);
        
        if (remainderReg != null) {
            ctx.setFilterReg(remainderReg);
            ctx.setMessage(null);
            return true;
        }
        
        return false;
    }
    
    /**
     * Locates a message remainder in the {@link FilterChain}, associated with the
     * {@link Connection}.
     */
    private FilterReg remainderReg(final FilterChainContext ctx,
            final FilterExecutor executor,
            final FilterReg start, final FilterReg end) {

        final FilterReg termReg = executor.prev(start);
        FilterReg curReg = executor.prev(end);
        
        final Connection connection = ctx.getConnection();
        
        while (curReg != termReg) {
            if (curReg.isEverHadState) {
                final FilterState state = connection.getFilterChainState()
                        .getFilterState(curReg, ctx.getEvent());
                
                if (state != null && state.getRemainder() != null
                        && state.isUnparsed()) {
                    return curReg;
                }
            }
            curReg = executor.prev(curReg);
        }
        
        return null;
    }
    
    @Override
    public void read(final Connection connection,
            final CompletionHandler<ReadResult> completionHandler) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.setOperation(FilterChainContext.Operation.READ);
        context.getTransportContext().configureBlocking(true);
        
        ExecutorResolver.resolve(context).initIndexes(context);
        
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
                    Futures.createUnsafeFuture();
            context.operationCompletionHandler =
                    Futures.toCompletionHandler(future);

            final FilterExecutor executor = ExecutorResolver.resolve(context);

            try {
                do {
                    if (!prepareRemainder(context, executor,
                            head.next, context.getEndFilterReg())) {
                        context.setFilterReg(head.next);
                        context.setMessage(null);
                    }

                    executeChainPart(context, executor, context.getFilterReg(),
                            context.getEndFilterReg());
                } while (!future.isDone());

                final FilterChainContext retContext = future.get();
                ReadResult rr = ReadResult.create(connection);
                rr.setMessage(retContext.getMessage());
                rr.setSrcAddressHolder(retContext.getAddressHolder());

                future.recycle(false);

                return rr;
            } catch (ExecutionException e) {
                throw Exceptions.makeIOException(e.getCause());
            } catch (Exception e) {
                throw Exceptions.makeIOException(e);
            }
        }
    }

    @Override
    public void write(final Connection connection,
            final Object dstAddress, final Object message,
            final CompletionHandler completionHandler,
            final LifeCycleHandler lifeCycleHandler) {

        final FilterChainContext context = obtainFilterChainContext(connection);
        context.transportFilterContext.completionHandler = completionHandler;
        context.transportFilterContext.lifeCycleHandler = lifeCycleHandler;
        context.setAddress(dstAddress);
        context.setMessage(message);
        context.setOperation(Operation.WRITE);
        ProcessorExecutor.execute(context.internalContext);
    }

    @Override
    public void flush(final Connection connection,
            final CompletionHandler<WriteResult> completionHandler) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.setOperation(Operation.DOWNSTREAM_EVENT);
        context.setEvent(TransportFilter.createFlushEvent(completionHandler));
        ExecutorResolver.DOWNSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);
    }

    @Override
    public void fireEventDownstream(final Connection connection,
            final Event event,
            final CompletionHandler<FilterChainContext> completionHandler) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.operationCompletionHandler = completionHandler;
        context.setOperation(Operation.DOWNSTREAM_EVENT);
        context.setEvent(event);
        ExecutorResolver.DOWNSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);
    }

    @Override
    public void fireEventUpstream(final Connection connection,
            final Event event,
            final CompletionHandler<FilterChainContext> completionHandler) {
        final FilterChainContext context = obtainFilterChainContext(connection);
        context.operationCompletionHandler = completionHandler;
        context.setOperation(Operation.UPSTREAM_EVENT);
        context.setEvent(event);
        ExecutorResolver.UPSTREAM_EXECUTOR_SAMPLE.initIndexes(context);

        ProcessorExecutor.execute(context.internalContext);
    }

    @Override
    public void fail(final FilterChainContext context, final Throwable failure) {
        throwChain(context, ExecutorResolver.resolve(context), failure);
    }
    
    /**
     * Notify the filters about error.
     * @param ctx {@link FilterChainContext}
     * @param executor the {@link org.glassfish.grizzly.filterchain.FilterExecutor}
     *  responsible for invoking this filter chain.
     * @param exception the error that triggered this method invocation.
     */
    private void throwChain(final FilterChainContext ctx,
            final FilterExecutor executor, final Throwable exception) {

        notifyFailure(ctx, exception);

        final FilterReg endReg = ctx.getStartFilterReg();
        FilterReg curReg = ctx.getFilterReg();

        while (curReg != endReg) {
            curReg = executor.prev(curReg);
            ctx.setFilterReg(curReg);
            curReg.filter.exceptionOccurred(ctx, exception);
        }
    }

    /**
     * Checks if {@link Connection} has some stored data related to the processing
     * {@link Filter}. If yes - appends new context data to the stored one and
     * set the result as context message.
     * 
     * @param ctx {@link FilterChainContext}
     * @param filterReg the current filter
     */
    @SuppressWarnings("unchecked")
    private void checkStoredMessage(final FilterChainContext ctx,
            final FilterReg filterReg) {

        if (filterReg.isEverHadState) {
            final FilterState state = ctx.getConnection().getFilterChainState()
                    .getFilterState(filterReg, ctx.getEvent());
            
            // Check if there is any data stored for the current Filter
            if (state != null && state.isReady()) {
                Object storedMessage = state.getRemainder();
                final Object currentMessage = ctx.getMessage();
                if (currentMessage != null) {
                    final Appender appender = state.getAppender();
                    if (appender != null) {
                        storedMessage = appender.append(storedMessage, currentMessage);
                    } else {
                        storedMessage = ((Appendable) storedMessage).append(currentMessage);
                    }
                }
                
                state.reset();
                ctx.setMessage(storedMessage);
            }
        }
    }

    /**
     * Stores the Filter associated remainder. This remainder will be reused next
     * time the same filter will be invoked on this Connection.
     * 
     * @param ctx the {@link FilterChainContext}.
     * @param isUnparsed flag indicating whether or not the message to store
     *                   has been parsed or not.
     * @param filterReg the {@link FilterReg} with the message to store.
     * @param messageToStore the message to store.
     * @param appender the {@link Appender} to associate with the state.
     */
    private <M> void storeMessage(final FilterChainContext ctx,
            final boolean isUnparsed,
            final FilterReg filterReg, final M messageToStore,
            final Appender<M> appender) {
        
        assert messageToStore != null;

        filterReg.isEverHadState = true;

        final FilterState state = ctx.getConnection().getFilterChainState()
                .obtainFilterState(filterReg, ctx.getEvent());

        state.setAppender(appender);
        state.setRemainder(messageToStore);
        state.setUnparsed(isUnparsed);
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

    FilterReg newFilterReg(final Filter filter, final String name,
            final boolean isService) {
        
        synchronized (idToRegMap) {
            checkFilterName(name);
            
            final FilterReg filterReg = new FilterReg(this, filter, name,
                    isService);
            idToRegMap.put(name, filterReg);
            
            return filterReg;
        }
    }

    FilterReg newFilterReg(final Filter filter, final boolean isService) {
        synchronized (idToRegMap) {
            final String name = generateUniqueFilterName(filter);
            final FilterReg filterReg = new FilterReg(this, filter, name,
                    isService);
            idToRegMap.put(name, filterReg);
            
            return filterReg;
        }
    }
    
    FilterReg newFilterReg(final FilterReg reg) {
        synchronized (idToRegMap) {
            checkFilterName(reg.name());
            
            final FilterReg filterReg = new FilterReg(this, reg);
            idToRegMap.put(reg.name(), filterReg);
            
            return filterReg;
        }
    }

    private void checkFilterName(final String name) throws IllegalStateException {
        if (name == null) {
            throw new NullPointerException("The name can't be null");
        }
        
        if (idToRegMap.containsKey(name)) {
            throw new IllegalStateException("The Filter name has to be unique within the given FilterChain scope");
        }
        
        if (HEAD_NAME.equalsIgnoreCase(name)) {
            throw new IllegalStateException(HEAD_NAME + " is reserved name");
        }
        
        if (TAIL_NAME.equalsIgnoreCase(name)) {
            throw new IllegalStateException(TAIL_NAME + " is reserved name");
        }
    }
    
    private String generateUniqueFilterName(final Filter filter) {
        String name = filter.getClass().getName();
        final int dotIdx = name.lastIndexOf(".");
        if (dotIdx != -1) {
            name = name.substring(dotIdx + 1);
        }
        
        name = AUTO_GENERATED_NAME_MARKER + name;
        
        if (idToRegMap.containsKey(name)) {
            String uniqueName;
            int counter = 1;
            do {
                uniqueName = new StringBuilder(name.length() + 3).append(name)
                        .append('-').append(counter).toString();
                counter++;
            } while (idToRegMap.containsKey(uniqueName));
            
            name = uniqueName;
        }
        
        return name;
    }
    
    /**
     * Adds Filters after specified afterFilter
     * 
     * @param afterReg the {@link FilterReg} after which the new filters will
     *                 be added.
     * @param filters one or more filters to be added to this chain.
     * @return the {@link FilterReg} of the last filter that has been added as the
     *          result of this invocation.
     * @throws IllegalArgumentException if no filters are provided.
     */
    FilterReg addFilterRegAfter(final FilterReg afterReg, final Filter... filters) {
        if (filters == null || filters.length == 0) {
            throw new IllegalArgumentException("At least one filter has to be passed");
        }
        
        checkFilterReg(afterReg);
        
        final FilterReg beforeReg = afterReg.next;
        FilterReg currentReg = afterReg;
        
        synchronized (idToRegMap) {
            // create a chain based on the passed Filters
                    
            for (Filter filter : filters) {
                final FilterReg newFilterReg = newFilterReg(filter, false);
                
                newFilterReg.prev = currentReg;
                newFilterReg.next = beforeReg;
                
                currentReg.next = newFilterReg;
                beforeReg.prev = newFilterReg;

                filter.onAdded(newFilterReg);
                
                currentReg = newFilterReg;
            }
            
            return currentReg;
        }
    }
    
    FilterReg addFilterRegAfter(final FilterReg afterReg,
            final Filter filter, final String name) {
        checkFilterReg(afterReg);
        
        synchronized (idToRegMap) {
            final FilterReg newFilterReg = newFilterReg(filter, name, false);
            newFilterReg.prev = afterReg;
            newFilterReg.next = afterReg.next;
            
            afterReg.next.prev = newFilterReg;
            afterReg.next = newFilterReg;
            
            filter.onAdded(newFilterReg); // notify the filter
            
            return newFilterReg;
        }
    }

    FilterReg replaceFilterReg(final FilterReg oldFilterReg,
            final Filter newFilter) {
        
        synchronized (idToRegMap) {
            if (idToRegMap.remove(oldFilterReg.name) != null) {
                oldFilterReg.filter.onRemoved(oldFilterReg); // Notify the filter
            }
            
            final FilterReg newFilterReg = newFilterReg(newFilter, false);
            newFilterReg.prev = oldFilterReg.prev;
            newFilterReg.next = oldFilterReg.next;
            
            oldFilterReg.prev.next = newFilterReg;
            oldFilterReg.next.prev = newFilterReg;

            newFilter.onAdded(newFilterReg);
            return newFilterReg;
        }
        
    }

    FilterReg replaceFilterReg(final FilterReg oldFilterReg,
            final Filter newFilter, final String newFilterName) {
        
        synchronized (idToRegMap) {
            if (idToRegMap.remove(oldFilterReg.name) != null) {
                oldFilterReg.filter.onRemoved(oldFilterReg); // Notify the filter
            }
            
            final FilterReg newFilterReg = newFilterReg(newFilter, newFilterName, false);
            newFilterReg.prev = oldFilterReg.prev;
            newFilterReg.next = oldFilterReg.next;
            
            oldFilterReg.prev.next = newFilterReg;
            oldFilterReg.next.prev = newFilterReg;
            
            newFilter.onAdded(newFilterReg);
            return newFilterReg;
        }
    }

    void removeFilterReg(final FilterReg reg) {
        checkFilterReg(reg);
        
        synchronized (idToRegMap) {
            if (idToRegMap.remove(reg.name) != null) {
                reg.prev.next = reg.next;
                reg.next.prev = reg.prev;
                
                reg.filter.onRemoved(reg); // Notify the filter
            }
        }
    }
    
    void checkFilterReg(final FilterReg reg) {
        if (reg != null && reg.filterChain != this) {
            throw new IllegalArgumentException("Invalid FilterReg: doesn't belong to this FilterChain");
        }
    }
    
    @Override
    public FilterReg firstFilterReg() {
        final FilterReg first = head.next;
        if (first == tail) {
            throw new NoSuchElementException("The filter chain is empty");
        }
        
        return first;
    }

    @Override
    public FilterReg lastFilterReg() {
        final FilterReg last = tail.prev;
        if (last == head) {
            throw new NoSuchElementException("The filter chain is empty");
        }
        
        return last;
    }

    @Override
    public Filter first() {
        final FilterReg first = head.next;
        if (first == tail) {
            throw new NoSuchElementException("The filter chain is empty");
        }
        
        return first.filter;
    }

    @Override
    public Filter last() {
        final FilterReg last = tail.prev;
        if (last == head) {
            throw new NoSuchElementException("The filter chain is empty");
        }
        
        return last.filter;
    }
    
    @Override
    public Filter get(final String name) {
        final FilterReg reg = idToRegMap.get(name);
        return reg != null ? reg.filter : null;
    }

    @Override
    public FilterReg getFilterReg(final String name) {
        return idToRegMap.get(name);
    }

    @Override
    public FilterReg getFilterReg(final Filter filter) {
        for (FilterReg reg = head.next; reg != tail; reg = reg.next) {
            if (reg.filter.equals(filter)) {
                return reg;
            }
        }
        
        return null;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <E extends Filter> E getByType(final Class<E> filterType) {
        for (FilterReg reg = head.next; reg != tail; reg = reg.next) {
            if (filterType.isAssignableFrom(reg.filter.getClass())) {
                return (E) reg.filter;
            }
        }
        
        return null;
    }

    private static final Filter[] NO_FILTERS = new Filter[0];
    
    @Override
    public Filter[] getAllByType(final Class<? extends Filter> filterType) {
        Filter[] result = NO_FILTERS;
        
        for (FilterReg reg = head.next; reg != tail; reg = reg.next) {
            if (filterType.isAssignableFrom(reg.filter.getClass())) {
                result = Arrays.copyOf(result, result.length + 1);
                result[result.length - 1] = reg.filter;
            }
        }
        
        return result;
    }
    
    @Override
    public FilterReg getRegByType(final Class<? extends Filter> filterType) {
        for (FilterReg reg = head.next; reg != tail; reg = reg.next) {
            if (filterType.isAssignableFrom(reg.filter.getClass())) {
                return reg;
            }
        }
        
        return null;
    }

    private static final FilterReg[] NO_FILTER_REGS = new FilterReg[0];
    
    @Override
    public FilterReg[] getAllRegsByType(final Class<? extends Filter> filterType) {
        FilterReg[] result = NO_FILTER_REGS;
        
        for (FilterReg reg = head.next; reg != tail; reg = reg.next) {
            if (filterType.isAssignableFrom(reg.filter.getClass())) {
                result = Arrays.copyOf(result, result.length + 1);
                result[result.length - 1] = reg;
            }
        }
        
        return result;
    }
    
    @Override
    public int size() {
        return idToRegMap.size();
    }

    @Override
    public boolean isEmpty() {
        return head.next == tail;
    }
    
    @Override
    public void add(final Filter... filter) {
        addLast(filter);
    }

    @Override
    public void add(final Filter filter, final String filterName) {
        addLast(filter, filterName);
    }

    @Override
    public void addFirst(final Filter... filter) {
        addFilterRegAfter(head, filter);
    }

    @Override
    public void addFirst(Filter filter, String filterName) {
        addFilterRegAfter(head, filter, filterName);
    }

    @Override
    public void addLast(final Filter... filter) {
        addFilterRegAfter(tail.prev, filter);
    }

    @Override
    public void addLast(final Filter filter, final String filterName) {
        addFilterRegAfter(tail.prev, filter, filterName);
    }

    @Override
    public void addAfter(final Filter baseFilter, final Filter... filter) {
        final FilterReg baseFilterReg = getFilterReg(baseFilter);
        if (baseFilterReg == null) {
            throw new NoSuchElementException("The filter " + baseFilter + " is not found");
        }
        
        addFilterRegAfter(baseFilterReg, filter);
    }

    @Override
    public void addAfter(final Filter baseFilter,
            final Filter filter, final String filterName) {
        
        final FilterReg baseFilterReg = getFilterReg(baseFilter);
        if (baseFilterReg == null) {
            throw new NoSuchElementException("The filter " + baseFilter + " is not found");
        }
        
        addFilterRegAfter(baseFilterReg, filter, filterName);
    }

    @Override
    public void addAfter(final String baseFilterName, final Filter... filter) {
        final FilterReg baseFilterReg = getFilterReg(baseFilterName);
        if (baseFilterReg == null) {
            throw new NoSuchElementException("The filter " + baseFilterName + " is not found");
        }
        
        addFilterRegAfter(baseFilterReg, filter);
    }

    @Override
    public void addAfter(String baseFilterName, Filter filter, String filterName) {
        final FilterReg baseFilterReg = getFilterReg(baseFilterName);
        if (baseFilterReg == null) {
            throw new NoSuchElementException("The filter " + baseFilterName + " is not found");
        }
        
        addFilterRegAfter(baseFilterReg, filter, filterName);
    }

    @Override
    public void addBefore(final Filter baseFilter, final Filter... filter) {
        final FilterReg baseFilterReg = getFilterReg(baseFilter);
        if (baseFilterReg == null) {
            throw new NoSuchElementException("The filter " + baseFilter + " is not found");
        }
        
        addFilterRegAfter(baseFilterReg.prev, filter);
    }

    @Override
    public void addBefore(final Filter baseFilter,
            final Filter filter, final String filterName) {
        final FilterReg baseFilterReg = getFilterReg(baseFilter);
        if (baseFilterReg == null) {
            throw new NoSuchElementException("The filter " + baseFilter + " is not found");
        }
        
        addFilterRegAfter(baseFilterReg.prev, filter, filterName);
    }

    @Override
    public void addBefore(final String baseFilterName, final Filter... filter) {
        final FilterReg baseFilterReg = getFilterReg(baseFilterName);
        if (baseFilterReg == null) {
            throw new NoSuchElementException("The filter " + baseFilterName + " is not found");
        }
        
        addFilterRegAfter(baseFilterReg.prev, filter);
    }

    @Override
    public void addBefore(final String baseFilterName,
            final Filter filter, final String filterName) {
        final FilterReg baseFilterReg = getFilterReg(baseFilterName);
        if (baseFilterReg == null) {
            throw new NoSuchElementException("The filter " + baseFilterName + " is not found");
        }
        
        addFilterRegAfter(baseFilterReg.prev, filter, filterName);
    }

    @Override
    public void replace(final Filter oldFilter, final Filter newFilter) {
        final FilterReg oldFilterReg = getFilterReg(oldFilter);
        if (oldFilterReg == null) {
            throw new NoSuchElementException("The filter " + oldFilter + " is not found");
        }
        
        replaceFilterReg(oldFilterReg, newFilter);
    }

    @Override
    public void replace(final Filter oldFilter,
            final Filter newFilter, final String newFilterName) {
        
        final FilterReg oldFilterReg = getFilterReg(oldFilter);
        if (oldFilterReg == null) {
            throw new NoSuchElementException("The filter " + oldFilter + " is not found");
        }
        
        replaceFilterReg(oldFilterReg, newFilter, newFilterName);
    }

    @Override
    public void replace(final String oldFilterName, final Filter newFilter) {
        final FilterReg oldFilterReg = getFilterReg(oldFilterName);
        if (oldFilterReg == null) {
            throw new NoSuchElementException("The filter " + oldFilterName + " is not found");
        }
        
        replaceFilterReg(oldFilterReg, newFilter);
    }

    @Override
    public void replace(final String oldFilterName,
            final Filter newFilter, final String newFilterName) {
        
        final FilterReg oldFilterReg = getFilterReg(oldFilterName);
        if (oldFilterReg == null) {
            throw new NoSuchElementException("The filter " + oldFilterName + " is not found");
        }
        
        replaceFilterReg(oldFilterReg, newFilter, newFilterName);
    }


    @Override
    public boolean remove(final Filter filter) {
        final FilterReg filterReg = getFilterReg(filter);
        if (filterReg == null) {
            return false;
        }
        
        removeFilterReg(filterReg);
        
        return true;
    }

    @Override
    public boolean remove(final String filterName) {
        final FilterReg filterReg = getFilterReg(filterName);
        if (filterReg == null) {
            return false;
        }
        
        removeFilterReg(filterReg);
        
        return true;
    }

    @Override
    public Filter removeFirst() {
        final FilterReg first = head.next;
        if (first == tail) {
            throw new NoSuchElementException("The filter chain is empty");
        }
        
        removeFilterReg(first);
        
        return first.filter;
    }

    @Override
    public Filter removeLast() {
        final FilterReg last = tail.prev;
        if (last == head) {
            throw new NoSuchElementException("The filter chain is empty");
        }
        
        removeFilterReg(last);
        
        return last.filter;
    }
    
    @Override
    public void removeAllBefore(final String filterName) {
        final FilterReg filterReg = getFilterReg(filterName);
        if (filterReg == null) {
            throw new NoSuchElementException("The filter " + filterName + " is not found");
        }
        
        while (head != filterReg.prev) {
            removeFilterReg(filterReg.prev);
        }
    }

    @Override
    public void removeAllAfter(final String filterName) {
        final FilterReg filterReg = getFilterReg(filterName);
        if (filterReg == null) {
            throw new NoSuchElementException("The filter " + filterName + " is not found");
        }
        
        while (tail != filterReg.next) {
            removeFilterReg(filterReg.next);
        }
    }
    
    @Override
    public boolean contains(final Filter filter) {
        return getFilterReg(filter) != null;
    }

    @Override
    public boolean contains(final String filterName) {
        return getFilterReg(filterName) != null;
    }

    @Override
    public List<String> names() {
        final List<String> list = new ArrayList<String>(8);
        for (FilterReg reg = head.next; reg != tail; reg = reg.next) {
            list.add(reg.name);
        }
        
        return list;
    }
    
    @Override
    public void clear() {
        head.next = tail;
        tail.prev = head;

        synchronized (idToRegMap) {
            idToRegMap.clear();
        }
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(256);
        sb.append(getClass().getSimpleName())
                .append('@')
                .append(Integer.toHexString(hashCode()))
                .append(" {");
        
        FilterReg reg = head.next;
        
        if (reg != tail) {
            sb.append(reg.name);
            for (reg = reg.next; reg != tail; reg = reg.next) {
                sb.append(" <-> ");
                sb.append(reg.name);
            }
        }
        
        sb.append('}');
        return sb.toString();
    }
    
    @Override
    public FilterChainIterator iterator() {
        return filterChainIterator(head.next);
    }

    @Override
    public FilterChainIterator iterator(final String name) {
        final FilterReg filterReg = getFilterReg(name);
        
        return filterReg != null ? filterChainIterator(filterReg) : null;
    }

    @Override
    public FilterChain copy() {
        return new DefaultFilterChain(this);
    }

    private FilterChainIterator filterChainIterator(final FilterReg nextFilterReg) {
        checkFilterReg(nextFilterReg);
        
        return new Iter(nextFilterReg);
    }

    private final class Iter implements FilterChainIterator {
        private FilterReg nextReg;
        private FilterReg currentReg;

        private Iter(final FilterReg nextFilterReg) {
            nextReg = nextFilterReg;
        }
        
        @Override
        public boolean hasNext() {
            return nextReg != tail;
        }

        @Override
        public Filter next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            
            currentReg = nextReg;
            nextReg = currentReg.next;
            
            return currentReg.filter;
        }

        @Override
        public boolean hasPrevious() {
            return nextReg.prev != head;
        }

        @Override
        public Filter previous() {
            if (!hasPrevious()) {
                throw new NoSuchElementException();
            }
            
            currentReg = nextReg = nextReg.prev;
            
            return currentReg.filter;
        }

        @Override
        public void add(final Filter e) {
            currentReg = null;
            addFilterRegAfter(nextReg.prev, e);
        }

        @Override
        public void set(final Filter e) {
            if (currentReg == null) {
                throw new IllegalStateException();
            }

            currentReg = replaceFilterReg(currentReg, e);
        }

        @Override
        public void remove() {
            if (currentReg == null) {
                throw new IllegalStateException();
            }
            
            removeFilterReg(currentReg);
            
            if (nextReg == currentReg) {
                nextReg = nextReg.next;
            }
            
            currentReg = null;
        }
    }
    
    private static final class FilterExecution {

        private static final int CONTINUE_TYPE = 0;
        private static final int TERMINATE_TYPE = 1;
        private static final int CONNECT_TERMINATE_TYPE = 2;
        private static final int FORK_TYPE = 3;
        private static final FilterExecution CONTINUE =
                new FilterExecution(CONTINUE_TYPE, null);
        private static final FilterExecution TERMINATE =
                new FilterExecution(TERMINATE_TYPE, null);
        private static final FilterExecution CONNECT_TERMINATE =
                new FilterExecution(CONNECT_TERMINATE_TYPE, null);
        private final int type;
        private final FilterChainContext context;

        public static FilterExecution createContinue() {
            return CONTINUE;
        }

        public static FilterExecution createTerminate() {
            return TERMINATE;
        }

        public static FilterExecution createConnectTerminate() {
            return CONNECT_TERMINATE;
        }

        public static FilterExecution createFork(final FilterChainContext context) {
            return new FilterExecution(FORK_TYPE, context);
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
