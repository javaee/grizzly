/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 * @author oleksiys
 */
abstract class ExecutorResolver {
    public static final FilterExecutor UPSTREAM_EXECUTOR_SAMPLE = new UpstreamExecutor() {
        @Override
        public NextAction execute(Filter filter, FilterChainContext context) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };
    
    public static final FilterExecutor DOWNSTREAM_EXECUTOR_SAMPLE = new DownstreamExecutor() {
        @Override
        public NextAction execute(Filter filter, FilterChainContext context) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };

    private static final FilterExecutor CONNECT_EXECUTOR = new UpstreamExecutor() {
        @Override
        public NextAction execute(final Filter filter, final FilterChainContext context)
                throws IOException {
            return filter.handleConnect(context);
        }
    };
    
    private static final FilterExecutor CLOSE_EXECUTOR = new UpstreamExecutor() {
        @Override
        public NextAction execute(final Filter filter, final FilterChainContext context)
                throws IOException {
            return filter.handleClose(context);
        }
    };

    private static final FilterExecutor EVENT_UPSTREAM_EXECUTOR = new UpstreamExecutor() {

        @Override
        public NextAction execute(final Filter filter,
                final FilterChainContext context) throws IOException {
            return filter.handleEvent(context, context.event);
        }
    };
    
    private static final FilterExecutor EVENT_DOWNSTREAM_EXECUTOR = new DownstreamExecutor() {

        @Override
        public NextAction execute(final Filter filter,
                final FilterChainContext context) throws IOException {
            return filter.handleEvent(context, context.event);
        }
    };
    
    private static final FilterExecutor ACCEPT_EXECUTOR = new UpstreamExecutor() {

        @Override
        public NextAction execute(final Filter filter, final FilterChainContext context)
                throws IOException {
            return filter.handleAccept(context);
        }
    };

    private static final FilterExecutor WRITE_EXECUTOR = new DownstreamExecutor() {
        @Override
        public NextAction execute(final Filter filter, final FilterChainContext context)
                throws IOException {
            return filter.handleWrite(context);
        }
    };
    
    private static final FilterExecutor READ_EXECUTOR = new UpstreamExecutor() {
        @Override
        public NextAction execute(final Filter filter, final FilterChainContext context)
                throws IOException {
            return filter.handleRead(context);
        }
    };
    
    
    public static FilterExecutor resolve(final FilterChainContext context) {
        switch(context.getOperation()) {
            case READ: return READ_EXECUTOR;
            case WRITE: return WRITE_EXECUTOR;
            case ACCEPT: return ACCEPT_EXECUTOR;
            case CLOSE: return CLOSE_EXECUTOR;
            case CONNECT: return CONNECT_EXECUTOR;
            case EVENT: return (context.getFilterIdx() == FilterChainContext.NO_FILTER_INDEX ||
                        context.getStartIdx() <= context.getEndIdx()) ?
                    EVENT_UPSTREAM_EXECUTOR:
                    EVENT_DOWNSTREAM_EXECUTOR;
            default: return null;
        }
    }
    
    /**
     * Executes appropriate {@link Filter} processing method to process occurred
     * {@link org.glassfish.grizzly.IOEvent}.
     */
    public static abstract class UpstreamExecutor implements FilterExecutor {

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

        @Override
        public final void initIndexes(FilterChainContext context) {
            final int startIdx = defaultStartIdx(context);
            context.setStartIdx(startIdx);
            context.setFilterIdx(startIdx);
            context.setEndIdx(defaultEndIdx(context));
        }

        @Override
        public final boolean isUpstream() {
            return true;
        }

        @Override
        public final  boolean isDownstream() {
            return false;
        }
    }

    /**
     * Executes appropriate {@link Filter} processing method to process occurred
     * {@link org.glassfish.grizzly.IOEvent}.
     */
    public static abstract class DownstreamExecutor implements FilterExecutor {
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

        @Override
        public final void initIndexes(FilterChainContext context) {
            final int startIdx = defaultStartIdx(context);
            context.setStartIdx(startIdx);
            context.setFilterIdx(startIdx);
            context.setEndIdx(defaultEndIdx(context));
        }

        @Override
        public final boolean isUpstream() {
            return false;
        }

        @Override
        public final  boolean isDownstream() {
            return true;
        }
    }
}
