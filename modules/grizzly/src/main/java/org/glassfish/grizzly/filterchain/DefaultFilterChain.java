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

package org.glassfish.grizzly.filterchain;

import java.io.IOException;
import org.glassfish.grizzly.ProcessorResult;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Codec;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.util.LightArrayList;

/**
 * Default {@link FilterChain} implementation
 *
 * @see FilterChain
 * @see Filter
 * 
 * @author Alexey Stashok
 */
public class DefaultFilterChain extends ListFacadeFilterChain {
    
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
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleAccept(context, nextAction);
            }
        },
        new FilterExecutor() {
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleConnect(context, nextAction);
            }
        },
        new FilterExecutor() {
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleRead(context, nextAction);
            }
        },
        new FilterExecutor() {
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.handleWrite(context, nextAction);
            }
        },
        new FilterExecutor() {
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
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postAccept(context, nextAction);
            }
        },
        new FilterExecutor() {
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postConnect(context, nextAction);
            }
        },
        new FilterExecutor() {
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postRead(context, nextAction);
            }
        },
        new FilterExecutor() {
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postWrite(context, nextAction);
            }
        },
        new FilterExecutor() {
            public NextAction execute(Filter filter, FilterChainContext context,
                    NextAction nextAction) throws IOException {
                return filter.postClose(context, nextAction);
            }
        },
    };

    /*
     * InvokeAction = 0
     * StopAction = 1
     * SuspendAction = 2
     * RerunChainAction = 3
     */
    private static final boolean[] isContinueExecution = new boolean[] {true,
                                                    false, false, true};
    
    /**
     * Logger
     */
    private Logger logger = Grizzly.logger;
    private final DefaultFilterChainCodec filterChainCodec;

    public DefaultFilterChain(FilterChainFactory factory) {
        super(factory, new LightArrayList<Filter>());
        filterChainCodec = new DefaultFilterChainCodec(this);
    }
    
    /**
     * Execute this FilterChain.
     * @param ctx {@link FilterChainContext} processing context
     * @throws java.lang.Exception 
     */
    public ProcessorResult execute(FilterChainContext ctx) {
        try {
            int ioEventIndex = ctx.getIoEvent().ordinal();
            if (!executeChain(ctx, filterExecutors[ioEventIndex]) ||
                    !postExecuteChain(ctx, filterPostExecutors[ioEventIndex])) {
                return new ProcessorResult(Status.TERMINATE);
            }
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

        List<Filter> chain = ctx.getFilters();
        int currentFilterIdx = ctx.getCurrentFilterIdx();

        NextAction nextAction;

        if (chain == null) {
            ctx.setFilters(filters);
            ctx.setCurrentFilterIdx(0);
            chain = filters;
            currentFilterIdx = 0;

            nextAction = getCachedInvokeAction(ctx);
        } else {
            nextAction = new InvokeAction(chain);
        }

        while(currentFilterIdx < chain.size()) {
            ((AbstractNextAction) nextAction).setNextFilterIdx(currentFilterIdx + 1);

            // current Filter to be executed
            Filter currentFilter = chain.get(currentFilterIdx);

            // save current filter to the context
            ctx.setCurrentFilter(currentFilter);

            if (logger.isLoggable(Level.FINEST)) {
                logger.fine("Execute filter. filter=" + currentFilter +
                        " context=" + ctx);
            }
            // execute the task
            nextAction = executor.execute(currentFilter, ctx, nextAction);

            if (logger.isLoggable(Level.FINEST)) {
                logger.fine("after execute filter. filter=" + currentFilter +
                        " context=" + ctx + " nextAction=" + nextAction);
            }

            ctx.getExecutedFilters().add(currentFilter);

            if (!isContinueExecution[nextAction.type()]) {
                return nextAction.type() != SuspendAction.TYPE;
            }

            chain = nextAction.getFilters();
            currentFilterIdx = nextAction.getNextFilterIdx();

            ctx.setFilters(chain);
            ctx.setCurrentFilterIdx(currentFilterIdx);
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
        List<Filter> chain = ctx.getExecutedFilters();
        int offset = chain.size() - 1;
        // check startPosition and chain size
        if (offset < 0) return true;

        NextAction nextAction = new InvokeAction(chain);

        for(int i = chain.size() - 1; i >= 0; i--) {
            // current Filter to be executed
            Filter currentFilter = chain.get(i);

//            nextFiltersList.remove(i);

            // save current filter to the context
            ctx.setCurrentFilter(currentFilter);
            
            if (logger.isLoggable(Level.FINEST)) {
                logger.fine("PostExecute filter. filter=" + currentFilter +
                        " context=" + ctx);
            }
            // execute the task
            nextAction = executor.execute(currentFilter, ctx, nextAction);

            if (logger.isLoggable(Level.FINEST)) {
                logger.fine("after PostExecute filter. filter=" + currentFilter +
                        " context=" + ctx + " nextAction=" + nextAction);
            }

            if (nextAction.type() == RerunChainAction.TYPE) {
                final List<Filter> tmpExecutedFilters = chain;
                final List<Filter> tmpNextFilters = ctx.getFilters();
                final int tmpCurrentFilterIdx = ctx.getCurrentFilterIdx();

                ctx.setExecutedFilters(new LightArrayList<Filter>());
                ctx.setFilters(chain);
                ctx.setCurrentFilterIdx(0);
                try {
                    execute(ctx);
                } finally {
                    ctx.setExecutedFilters(tmpExecutedFilters);
                    ctx.setFilters(tmpNextFilters);
                    ctx.setCurrentFilterIdx(tmpCurrentFilterIdx);
                }
            }
        }

        return true;
    }    

    /**
     * Notify the filters about error.
     * @param ctx {@link FilterChainContext}
     * @return position of the last executed {@link Filter}
     */
    protected void throwChain(FilterChainContext ctx, Throwable exception) {
        List<Filter> chain = ctx.getExecutedFilters();
        // check startPosition and chain size
        if (chain.size() <= 0) return;
        List<Filter> executedFilters = ctx.getExecutedFilters();

        for(Filter filter : executedFilters) {
            ctx.setCurrentFilter(filter);
            filter.exceptionOccurred(ctx, exception);
        }
    }

    /**
     * Get filter chain codec
     * @return filter chain codec
     */
    public Codec getCodec() {
        return filterChainCodec;
    }

    private NextAction getCachedInvokeAction(FilterChainContext ctx) {
        InvokeAction cachedInvokeAction = ctx.getCachedInvokeAction();
        if (cachedInvokeAction == null ||
                cachedInvokeAction.filters != filters) {
            cachedInvokeAction = new InvokeAction(filters);
            ctx.setCachedInvokeAction(cachedInvokeAction);
        }

        return cachedInvokeAction;
    }

    /**
     * Executes appropriate {@link Filter} processing method to process occured
     * {@link IOEvent}.
     */
    public interface FilterExecutor {
        public NextAction execute(Filter filter, FilterChainContext context,
                NextAction nextAction) throws IOException;
    }
}
