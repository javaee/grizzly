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
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.util.LightArrayList;

/**
 * Default {@link FilterChain} implementation
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

    private Logger logger = Grizzly.logger;

    /**
     * Filter chain codec
     */
    protected DefaultFilterChainCodec codec;

    public DefaultFilterChain(FilterChainFactory factory) {
        super(factory);
        filters = new LightArrayList<Filter>();
        codec = new DefaultFilterChainCodec(this);
    }
    
    /**
     * Execute this FilterChain.
     * @param ctx {@link FilterChainContext}
     * @throws java.lang.Exception 
     */
    public ProcessorResult execute(FilterChainContext ctx) {
        try {
            execute(this, getStartingFilterIndex(this, executionDirection),
                executionDirection, ctx);
        } catch (Exception e) {
            try {
                ctx.getConnection().close();
            } catch (IOException ioe) {
            }
        }

        return null;
    }    
    
    /**
     * Execute this FilterChain.
     * @param ctx {@link FilterChainContext}
     * @throws java.lang.Exception
     */
    public ProcessorResult execute(List<Filter> chain, int offset,
            Direction direction, FilterChainContext ctx) throws Exception {
        if (chain.size() > 0) {
            try {
                int ioEventIndex = ctx.getIoEvent().ordinal();
                executeChain(chain, offset,
                        direction, ctx, filterExecutors[ioEventIndex]);

                postExecuteChain(ctx, filterPostExecutors[ioEventIndex]);
            } catch (IOException e) {
                logger.log(Level.FINE, "Exception during FilterChain execution", e);
                throwChain(ctx, e);
                throw e;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception during FilterChain execution", e);
                throwChain(ctx, e);
                throw e;
            }
        }

        return null;
    }

    /**
     * Execute the {@link Filter#execute()} method.
     * @param chain filer chain
     * @param offset position of the first filter in the chain to be executed
     * @param direction direction of execution
     * @param ctx {@link FilterChainContext}
     * @return position of the last executed {@link Filter}
     */
    protected void executeChain(List<Filter> chain, int offset,
            Direction direction, FilterChainContext ctx,
            FilterExecutor executor) throws Exception {

        // check startPosition and chain size
        int size = chain.size();
        if (size <= 0 || offset < 0 || offset >= size) return;

        List<Filter> nextFiltersList = ctx.getNextFiltersList();
        nextFiltersList.clear();
        // fill next filters list
        for(int i=offset; i<size; i++) nextFiltersList.add(chain.get(i));

        NextAction nextAction = new InvokeAction(nextFiltersList);
        
        do {

            int currentPosition = getStartingFilterIndex(nextFiltersList,
                    direction);

            // current Filter to be executed
            Filter currentFilter = nextFiltersList.remove(currentPosition);

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

            if (nextAction.type() == StopAction.TYPE ||
                    nextAction.type() == SuspendAction.TYPE)
                break;


        } while(nextFiltersList.size() > 0);
    }
        
    /**
     * Execute the {@link Filter#postExecute()} method.
     * @param ctx {@link FilterChainContext}
     * @return position of the last executed {@link Filter}
     */
    protected void postExecuteChain(FilterChainContext ctx,
            FilterExecutor executor) throws Exception {
        List<Filter> chain = ctx.getExecutedFilters();
        int offset = chain.size() - 1;
        // check startPosition and chain size
        if (offset < 0) return;

//        List<Filter> nextFiltersList = ctx.getNextFiltersList();
//        nextFiltersList.clear();
//        nextFiltersList.addAll(chain);

        NextAction nextAction = new InvokeAction(null);

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
                List<Filter> tmpExecutedFilters = ctx.getExecutedFilters();
                List<Filter> tmpNextFilters = ctx.getNextFiltersList();

                ctx.setExecutedFilters(new LightArrayList<Filter>());
                ctx.setNextFiltersList(new LightArrayList<Filter>());
                try {
                    execute(chain, i, Direction.FORWARD, ctx);
                } finally {
                    ctx.setExecutedFilters(tmpExecutedFilters);
                    ctx.setNextFiltersList(tmpNextFilters);
                }
            }

        }
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
    public FilterChainCodec getCodec() {
        return codec;
    }

    public interface FilterExecutor {
        public NextAction execute(Filter filter, FilterChainContext context,
                NextAction nextAction) throws IOException;
    }
}
