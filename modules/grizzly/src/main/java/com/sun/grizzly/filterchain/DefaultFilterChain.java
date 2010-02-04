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
import com.sun.grizzly.Transformer;
import java.io.IOException;
import com.sun.grizzly.ProcessorResult;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.Appendable;
import com.sun.grizzly.Appender;
import com.sun.grizzly.Codec;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.ProcessorResult.Status;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.memory.BufferUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

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

    private static final Codec NO_CODEC = new Codec() {
        @Override
        public Transformer getDecoder() {
            return null;
        }

        @Override
        public Transformer getEncoder() {
            return null;
        }
    };

    private Codec[] filterChainCodecLibrary;

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
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleAccept(context, nextAction);
            }
        },
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleConnect(context, nextAction);
            }
        },
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleRead(context, nextAction);
            }
        },
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleWrite(context, nextAction);
            }
        },
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleClose(context, nextAction);
            }
        },
    };

    /**
     * NONE,
     * SERVER_ACCEPT,
     * ACCEPTED,
     * CONNECTED,
     * READ,
     * WRITE,
     * CLOSED
     *
     * Filter post executors array
     */
    private static final FilterExecutor[] filterPostExecutors = {
        null, null,
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postAccept(context, nextAction);
            }
        },
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postConnect(context, nextAction);
            }
        },
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postRead(context, nextAction);
            }
        },
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postWrite(context, nextAction);
            }
        },
        new FilterExecutor() {
        @Override
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postClose(context, nextAction);
            }
        },
    };

    /**
     * Logger
     */
    private Logger logger = Grizzly.logger(DefaultFilterChain.class);

    private volatile boolean hasCodecFilter;

    public DefaultFilterChain() {
        this(new ArrayList<Filter>());
    }
    
    public DefaultFilterChain(Collection<Filter> initialFilters) {
        super(new ArrayList<Filter>(initialFilters));

        filterChainCodecLibrary = new DefaultFilterChainCodec[0];
        ensureCodecLibraryCapacity(8);
    }

    /**
     * Execute this FilterChain.
     * @param ctx {@link FilterChainContext} processing context
     * @throws java.lang.Exception 
     */
    @Override
    public ProcessorResult execute(FilterChainContext ctx) {
        if (isEmpty()) return null;
        
        try {
            int ioEventIndex = ctx.getIoEvent().ordinal();
            do {
                if (!executeChain(ctx, filterExecutors[ioEventIndex])
                        || !postExecuteChain(ctx, filterPostExecutors[ioEventIndex])) {
                    return new ProcessorResult(Status.TERMINATE);
                }
            } while (ctx.getLastExecutedFilterIdx() > FilterChainContext.NO_FILTER_INDEX);
        } catch (IOException e) {
            try {
                logger.log(Level.FINE, "Exception during FilterChain execution", e);
                throwChain(ctx, e);
                ctx.getConnection().close();
            } catch (IOException ioe) {
            }
        } catch (Exception e) {
            try {
                logger.log(Level.WARNING, "Exception during FilterChain execution", e);
                throwChain(ctx, e);
                ctx.getConnection().close();
            } catch (IOException ioe) {
            }
        }

        return null;
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
    protected boolean executeChain(FilterChainContext ctx,
            FilterExecutor executor) throws Exception {
        final IOEvent ioEvent = ctx.getIoEvent();
        
        final Connection connection = ctx.getConnection();
        
        final NextAction invokeAction = ctx.getInvokeAction();
        
        final int lastExecutedFilterIdx = ctx.getLastExecutedFilterIdx();

        FiltersState filtersState = FILTERS_STATE_ATTR.get(connection);
        
        final int size = size();
        for (int i = lastExecutedFilterIdx + 1; i < size; i++) {
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

            ctx.setLastExecutedFilterIdx(i);

            if (nextNextActionType == InvokeAction.TYPE) {
                final Object remainder = ((InvokeAction) nextNextAction).getRemainder();
                if (remainder != null) {
                    if (filtersState == null) {
                        filtersState = new FiltersState(size);
                        FILTERS_STATE_ATTR.set(connection, filtersState);
                    }

                    final Appender appender = ((InvokeAction) nextNextAction).getAppender();
                    filtersState.setState(ioEvent, i,
                            new FilterStateElement(FILTER_STATE_TYPE.REMAINDER,
                            remainder, appender));
                }
            } else {
                // If the next action is StopAction and there is some data to store for the processed Filter - store it
                Object messageToStore;
                if (nextNextActionType == StopAction.TYPE &&
                        (messageToStore =
                        ((StopAction) nextNextAction).getRemainder()) != null) {
                    if (filtersState == null) {
                        filtersState = new FiltersState(size);
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
    protected boolean postExecuteChain(FilterChainContext ctx,
            FilterExecutor executor) throws Exception {

        final Connection connection = ctx.getConnection();

        // Take the last added remaining data info
        final FiltersState filtersState = FILTERS_STATE_ATTR.get(connection);

        final NextAction invokeAction = ctx.getInvokeAction();
        final int lastExecutedFilterIdx = ctx.getLastExecutedFilterIdx();

        for(int i = lastExecutedFilterIdx; i >= 0; i--) {
            // current Filter to be executed
            final Filter currentFilter = get(i);
            
            if (logger.isLoggable(Level.FINEST)) {
                logger.fine("PostExecute filter. filter=" + currentFilter +
                        " context=" + ctx);
            }
            // execute the task
            final NextAction nextNextAction = executor.execute(currentFilter,
                    ctx, invokeAction);

            if (logger.isLoggable(Level.FINEST)) {
                logger.fine("after PostExecute filter. filter=" + currentFilter +
                        " context=" + ctx + " nextAction=" + nextNextAction);
            }

            if (nextNextAction.type() == RerunChainAction.TYPE ||
                    checkStoredDataRemaining(filtersState, ctx.getIoEvent(), i)) {
                ctx.setLastExecutedFilterIdx(i - 1);
                ctx.setMessage(null);
                return true;
            }
        }
        
        ctx.setLastExecutedFilterIdx(FilterChainContext.NO_FILTER_INDEX);
        
        return true;
    }

    private boolean checkStoredDataRemaining(final FiltersState filtersState,
            final IOEvent ioEvent, final int index) {

        final FilterStateElement filterState;
        
        if (filtersState != null &&
                (filterState = filtersState.getState(ioEvent, index)) != null &&
                filterState.getType() == FILTER_STATE_TYPE.REMAINDER) {
            
            return true;
        }

        return false;
    }

    /**
     * Notify the filters about error.
     * @param ctx {@link FilterChainContext}
     * @return position of the last executed {@link Filter}
     */
    protected void throwChain(FilterChainContext ctx, Throwable exception) {
        int lastExecutedFilterIdx = ctx.getLastExecutedFilterIdx();

        for(int i = 0; i <= lastExecutedFilterIdx; i++) {
            get(i).exceptionOccurred(ctx, exception);
        }
    }

    public boolean hasCodecFilter() {
        return hasCodecFilter;
    }

    /**
     * Get filter chain codec
     * @return filter chain codec
     */
    @Override
    public Codec getCodec() {
        return getCodec(size());
    }

    @Override
    public Codec<Buffer, Object> getCodec(int limit) {
        if (!hasCodecFilter) return NO_CODEC;
        
        return filterChainCodecLibrary[limit];
    }

    @Override
    public DefaultFilterChain subList(int fromIndex, int toIndex) {
        return new DefaultFilterChain(filters.subList(fromIndex, toIndex));
    }

    @Override
    protected void notifyChangedExcept(Filter filter) {
        super.notifyChangedExcept(filter);
        ensureCodecLibraryCapacity(size());

        boolean hasCodecFilterLocal = false;
        for (final Filter currentFilter : filters) {
            if (currentFilter instanceof CodecFilter) {
                hasCodecFilterLocal = true;
                break;
            }
        }
        
        hasCodecFilter = hasCodecFilterLocal;
    }

    private final void ensureCodecLibraryCapacity(int capacity) {
        final int codecLibrarySize = filterChainCodecLibrary.length;
        if (codecLibrarySize < capacity) {
            final Codec[] newLibrary =
                    Arrays.copyOf(filterChainCodecLibrary, capacity);
            for (int i = 0; i < newLibrary.length; i++) {
                if (newLibrary[i] == null) {
                    newLibrary[i] = new DefaultFilterChainCodec(this, i);
                }
            }

            filterChainCodecLibrary = newLibrary;
        }
    }
    
    /**
     * Executes appropriate {@link Filter} processing method to process occured
     * {@link IOEvent}.
     */
    public interface FilterExecutor {
        public NextAction execute(Filter filter, FilterChainContext context,
                NextAction nextAction) throws IOException;
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
