
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

package com.sun.grizzly.filterchain;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Context;
import com.sun.grizzly.GrizzlyFuture;
import java.io.IOException;
import com.sun.grizzly.ProcessorResult;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.Appendable;
import com.sun.grizzly.Appender;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import com.sun.grizzly.asyncqueue.AsyncQueueWriter;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.ReadyFutureImpl;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.utils.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Default {@link FilterChain} implementation
 *
 * @see FilterChain
 * @see Filter
 * 
 * @author Alexey Stashok
 */
public final class DefaultFilterChain extends ListFacadeFilterChain {
    public enum FILTER_STATE_TYPE {INCOMPLETE, REMAINDER};
    
    protected final static Attribute<FiltersState> FILTERS_STATE_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("DefaultFilterChain-StoredData");

    protected final static Attribute<Queue<Pair>> STANDALONE_READ_LISTENERS_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("DefaultFilterChain-ReadListener");

    protected final static Attribute<Queue<ReadResult>> STORED_READ_RESULTS_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("DefaultFilterChain-StoredReadResults");

    /**
     * NONE,
     * SERVER_ACCEPT,
     * ACCEPTED,
     * CONNECTED,
     * READ,
     * WRITE,
     * CLOSED
     *
     * Filter executors array
     */
    private static final FilterExecutor[] filterExecutors = {
        null, null,
        new FilterExecutorUp() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleAccept(context, nextAction);
            }
        },
        new FilterExecutorUp() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleConnect(context, nextAction);
            }
        },
        new FilterExecutorUp() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleRead(context, nextAction);
            }
        },
        new FilterExecutorDown() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleWrite(context, nextAction);
            }
        },
        new FilterExecutorUp() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleClose(context, nextAction);
            }
        },
    };


    /**
     * Logger
     */
    private Logger logger = Grizzly.logger(DefaultFilterChain.class);

    public DefaultFilterChain() {
        this(new ArrayList<Filter>());
    }
    
    public DefaultFilterChain(Collection<Filter> initialFilters) {
        super(new ArrayList<Filter>(initialFilters));
    }

    @Override
    public ProcessorResult process(Context context) throws IOException {
        final IOEvent ioEvent = context.getIoEvent();
        if (ioEvent == IOEvent.WRITE) {
            final Connection connection = context.getConnection();
            final AsyncQueueEnabledTransport transport =
                    (AsyncQueueEnabledTransport) connection.getTransport();
            final AsyncQueueWriter writer = transport.getAsyncQueueIO().getWriter();

            writer.processAsync(connection);

            return ProcessorResult.createCompletedLeave();
        }

        return execute((FilterChainContext) context);
    }

    @Override
    public GrizzlyFuture read(Connection connection,
            CompletionHandler completionHandler) throws IOException {
        return registerStandaloneReadListener(connection, completionHandler);
    }

    @Override
    public GrizzlyFuture write(Connection connection, Object dstAddress,
            Object message, CompletionHandler completionHandler)
            throws IOException {
        final FutureImpl future = FutureImpl.create();
        final FilterChainContext context = (FilterChainContext) Context.create(
                this, connection, IOEvent.WRITE, future, completionHandler);
        context.setAddress(dstAddress);
        context.setMessage(message);

        final ProcessorResult result = execute(context);
        if (result.getStatus() != ProcessorResult.Status.TERMINATE) {
            context.recycle();
        }

        return future;
    }


    /**
     * Execute this FilterChain.
     * @param ctx {@link FilterChainContext} processing context
     * @throws java.lang.Exception
     */
    @Override
    public ProcessorResult execute(FilterChainContext ctx) {
        final int ioEventIndex = ctx.getIoEvent().ordinal();
        final FilterExecutor executor = filterExecutors[ioEventIndex];

        if (isEmpty()) ProcessorResult.createCompleted();

        if (ctx.getFilterIdx() == FilterChainContext.NO_FILTER_INDEX) {
            final int startIdx = executor.defaultStartIdx(ctx);
            ctx.setFilterIdx(startIdx);
            ctx.setStartIdx(startIdx);
        }

        final int end = executor.defaultEndIdx(ctx);

        try {
            do {
                if (!executeChainPart(ctx, executor, ctx.getFilterIdx(), end)) {
                    return ProcessorResult.createTerminate();
                }
            } while (checkRemainder(ctx, executor, ctx.getStartIdx(), end));
        } catch (IOException e) {
            try {
                logger.log(Level.FINE, "Exception during FilterChain execution", e);
                throwChain(ctx, executor, e);
                ctx.getConnection().close();
            } catch (IOException ioe) {
                logger.log(Level.FINE, "Exception during reporting the failuer", ioe);
            }
        } catch (Exception e) {
            try {
                logger.log(Level.WARNING, "Exception during FilterChain execution", e);
                throwChain(ctx, executor, e);
                ctx.getConnection().close();
            } catch (IOException ioe) {
                logger.log(Level.FINE, "Exception during reporting the failuer", ioe);
            }
        }

        return ProcessorResult.createCompleted();
    }
    
    /**
     * Sequentially lets each {@link Filter} in chain to process {@link IOEvent}.
     * 
     * @param ctx {@link FilterChainContext} processing context
     * @param executor {@link FilterExecutor}, which will call appropriate
     *          filter operation to process {@link IOEvent}.
     * @return <tt>false</tt> to terminate exectution, or <tt>true</tt> for
     *         normal exection process
     */
    protected boolean executeChainPart(FilterChainContext ctx,
            FilterExecutor executor, int start, int end) throws Exception {
        final IOEvent ioEvent = ctx.getIoEvent();
        
        final Connection connection = ctx.getConnection();
        
        final NextAction invokeAction = ctx.getInvokeAction();
        
        FiltersState filtersState = FILTERS_STATE_ATTR.get(connection);
        
        int i = start;

        while (i != end) {
            // current Filter to be executed
            final Filter currentFilter = get(i);

            // Check if there is any data stored for the current Filter
            final FilterStateElement filterState;
            if (filtersState != null && 
                    (filterState = filtersState.clearState(ioEvent, i)) != null) {
                Object storedMessage = filterState.getState();
                final Object currentMessage = ctx.getMessage();
                if (currentMessage != null) {
                    final Appender appender = filterState.getAppender();
                    if (appender != null) {
                        storedMessage =
                                appender.append(storedMessage, currentMessage);
                    } else {
                        ((Appendable) storedMessage).append(currentMessage);
                    }
                }
                
                ctx.setMessage(storedMessage);
            }
            
            if (logger.isLoggable(Level.FINEST)) {
                logger.fine("Execute filter. filter=" + currentFilter +
                        " context=" + ctx);
            }
            // execute the task
            final NextAction nextNextAction = executor.execute(currentFilter,
                    ctx, invokeAction);

            if (logger.isLoggable(Level.FINEST)) {
                logger.fine("after execute filter. filter=" + currentFilter +
                        " context=" + ctx + " nextAction=" + nextNextAction);
            }

            final int nextNextActionType = nextNextAction.type();
            
            if (nextNextActionType == SuspendAction.TYPE) { // on suspend - return immediatelly
                return false;
            }

            if (nextNextActionType == InvokeAction.TYPE) {
                final Object remainder = ((InvokeAction) nextNextAction).getRemainder();
                if (remainder != null) {
                    if (filtersState == null) {
                        filtersState = new FiltersState(size());
                        FILTERS_STATE_ATTR.set(connection, filtersState);
                    }

                    filtersState.setState(ioEvent, i,
                            new FilterStateElement(FILTER_STATE_TYPE.REMAINDER,
                            remainder, null));
                }
            } else {
                // If the next action is StopAction and there is some data to store for the processed Filter - store it
                Object messageToStore;
                if (nextNextActionType == StopAction.TYPE &&
                        (messageToStore =
                        ((StopAction) nextNextAction).getRemainder()) != null) {
                    if (filtersState == null) {
                        filtersState = new FiltersState(size());
                        FILTERS_STATE_ATTR.set(connection, filtersState);
                    }

                    final Appender appender = ((StopAction) nextNextAction).getAppender();
                    filtersState.setState(ioEvent, i,
                            new FilterStateElement(FILTER_STATE_TYPE.INCOMPLETE,
                            messageToStore, appender));
                    return true;
                }
                
                return true;
            }

            i = executor.getNextFilter(ctx);
            ctx.setFilterIdx(i);
        }

        if (i == end && ioEvent == IOEvent.READ) {
            notifyStandaloneReadListener(ctx);
        }

        return true;
    }

    /**
     * Sequentially lets each executed {@link Filter} to post process
     * {@link IOEvent}. The {@link Filter}s will be called in opposite order
     * they were called on processing phase.
     *
     * @param ctx {@link FilterChainContext} processing context
     * @param executor {@link FilterExecutor}, which will call appropriate
     *          filter operation to post process {@link IOEvent}.
     * @return <tt>false</tt> to terminate exectution, or <tt>true</tt> for
     *         normal exection process
     */
    protected boolean checkRemainder(FilterChainContext ctx,
            FilterExecutor executor, int start, int end) throws Exception {

        final Connection connection = ctx.getConnection();

        // Take the last added remaining data info
        final FiltersState filtersState = FILTERS_STATE_ATTR.get(connection);

        if (filtersState == null) return false;
        final int add = (end - start > 0) ? 1 : -1;
        final IOEvent ioEvent = ctx.getIoEvent();

        for(int i = start; i != end; i += add) {
            final FilterStateElement element = filtersState.getState(ioEvent, i);
            if (element != null &&
                    element.getType() == FILTER_STATE_TYPE.REMAINDER) {
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
    protected void throwChain(FilterChainContext ctx, FilterExecutor executor,
            Throwable exception) {

        final int endIdx = ctx.getStartIdx();

        if (ctx.getFilterIdx() == endIdx) return;

        int i;
        while(true) {
            i = executor.getPreviousFilter(ctx);
            ctx.setFilterIdx(i);
            get(i).exceptionOccurred(ctx, exception);

            if (i == endIdx) return;
        }
    }

    @Override
    public DefaultFilterChain subList(int fromIndex, int toIndex) {
        return new DefaultFilterChain(filters.subList(fromIndex, toIndex));
    }


    private GrizzlyFuture registerStandaloneReadListener(
            Connection connection, CompletionHandler completionHandler) {
        
        synchronized(connection) {
            final Queue<ReadResult> standaloneResults =
                    STORED_READ_RESULTS_ATTR.get(connection);

            if (standaloneResults != null && !standaloneResults.isEmpty()) {
                final ReadResult result = standaloneResults.remove();
                if (completionHandler != null) {
                    completionHandler.completed(result);
                }

                return ReadyFutureImpl.create(result);
            }

            final FutureImpl future = FutureImpl.create();

            final Pair<FutureImpl, CompletionHandler> pair =
                    new Pair<FutureImpl, CompletionHandler>(
                    future, completionHandler);

            Queue readQueue = STANDALONE_READ_LISTENERS_ATTR.get(connection);
            if (readQueue == null) {
                readQueue = new LinkedList<Pair>();
                STANDALONE_READ_LISTENERS_ATTR.set(connection, readQueue);
            }

            readQueue.add(pair);

            return future;
        }
    }

    private void notifyStandaloneReadListener(FilterChainContext ctx) {
        final Connection connection = ctx.getConnection();
        synchronized (connection) {
            final ReadResult readResult = ReadResult.create(connection,
                    ctx.getMessage(), ctx.getAddress(), 0);

            final Queue<Pair> listeners =
                    STANDALONE_READ_LISTENERS_ATTR.get(connection);
            if (listeners != null && !listeners.isEmpty()) {
                final Pair<FutureImpl, CompletionHandler> listener =
                        listeners.remove();
                if (listener.getSecond() != null) {
                    listener.getSecond().completed(readResult);
                }

                listener.getFirst().result(readResult);
            } else {
                Queue<ReadResult> results =
                        STORED_READ_RESULTS_ATTR.get(connection);
                if (results == null) {
                    results = new LinkedList<ReadResult>();
                    STORED_READ_RESULTS_ATTR.set(connection, results);
                }

                results.add(readResult);
            }
        }
    }
    
    /**
     * Executes appropriate {@link Filter} processing method to process occured
     * {@link IOEvent}.
     */
    public interface FilterExecutor {
        public NextAction execute(Filter filter, FilterChainContext context,
                NextAction nextAction) throws IOException;

        public int defaultStartIdx(FilterChainContext context);

        public int defaultEndIdx(FilterChainContext context);
        
        public int getNextFilter(FilterChainContext context);

        public int getPreviousFilter(FilterChainContext context);

        public boolean hasNextFilter(FilterChainContext context, int idx);

        public boolean hasPreviousFilter(FilterChainContext context, int idx);
    }

    /**
     * Executes appropriate {@link Filter} processing method to process occured
     * {@link IOEvent}.
     */
    public static abstract class FilterExecutorUp implements FilterExecutor {

        @Override
        public final int defaultStartIdx(FilterChainContext context) {
            if (context.getFilterIdx() != FilterChainContext.NO_FILTER_INDEX) {
                return context.getFilterIdx();
            }
            
            context.setFilterIdx(0);
            return 0;
        }

        @Override
        public final int defaultEndIdx(FilterChainContext context) {
            return context.getFilterChain().size();
        }

        @Override
        public final int getNextFilter(FilterChainContext context) {
            return context.getFilterIdx() + 1;
        }

        @Override
        public final int getPreviousFilter(FilterChainContext context) {
            return context.getFilterIdx() - 1;
        }

        @Override
        public final boolean hasNextFilter(FilterChainContext context, int idx) {
            return idx < context.getFilterChain().size() - 1;
        }

        @Override
        public final boolean hasPreviousFilter(FilterChainContext context, int idx) {
            return idx > 0;
        }
    }
    
    /**
     * Executes appropriate {@link Filter} processing method to process occured
     * {@link IOEvent}.
     */
    public static abstract class FilterExecutorDown implements FilterExecutor {
        @Override
        public final int defaultStartIdx(FilterChainContext context) {
            if (context.getFilterIdx() != FilterChainContext.NO_FILTER_INDEX) {
                return context.getFilterIdx();
            }

            final int idx = context.getFilterChain().size() - 1;
            context.setFilterIdx(idx);
            return idx;
        }

        @Override
        public final int defaultEndIdx(FilterChainContext context) {
            return -1;
        }

        @Override
        public final int getNextFilter(FilterChainContext context) {
            return context.getFilterIdx() - 1;
        }

        @Override
        public final int getPreviousFilter(FilterChainContext context) {
            return context.getFilterIdx() + 1;
        }

        @Override
        public final boolean hasNextFilter(FilterChainContext context, int idx) {
            return idx > 0;
        }

        @Override
        public final boolean hasPreviousFilter(FilterChainContext context, int idx) {
            return idx < context.getFilterChain().size() - 1;
        }
    }

    public static final class FiltersState {
        private final FilterStateElement[][] state;

        public FiltersState(int filtersNum) {
            this.state = new FilterStateElement[IOEvent.values().length][filtersNum];
        }

        public FilterStateElement getState(final IOEvent event,
                final int filterIndex) {
            return state[event.ordinal()][filterIndex];
        }

        public void setState(final IOEvent event, final int filterIndex,
                final FilterStateElement stateElement) {
            state[event.ordinal()][filterIndex] = stateElement;
        }

        public FilterStateElement clearState(final IOEvent event,
                final int filterIndex) {
            
            final int eventIdx = event.ordinal();
            final FilterStateElement oldState = state[eventIdx][filterIndex];
            state[eventIdx][filterIndex] = null;
            return oldState;
        }

        public int indexOf(final IOEvent event,
                final FILTER_STATE_TYPE type) {
            return indexOf(event, type, 0);
        }

        public int indexOf(final IOEvent event,
                final FILTER_STATE_TYPE type, final int start) {
            final int eventIdx = event.ordinal();
            final int length = state[eventIdx].length;

            for (int i = start; i < length; i++) {
                final FilterStateElement filterState;
                if ((filterState = state[eventIdx][i]) != null &&
                        filterState.getType() == type) {
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
                if ((filterState = state[eventIdx][i]) != null &&
                        filterState.getType() == type) {
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
        public static final FilterStateElement create(
                final FILTER_STATE_TYPE type,
                final Object remainder) {
            if (remainder instanceof Buffer) {
                return create(type, (Buffer) remainder,
                        BufferUtils.BUFFER_APPENDER);
            } else {
                return create(type, (Appendable) remainder);
            }
        }

        public static final FilterStateElement create(
                final FILTER_STATE_TYPE type, final Appendable state) {
            return new FilterStateElement(type, state);
        }

        public static final <E> FilterStateElement create(
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
