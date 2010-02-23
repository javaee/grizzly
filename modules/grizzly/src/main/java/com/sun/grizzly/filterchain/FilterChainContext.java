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

import com.sun.grizzly.Appendable;
import com.sun.grizzly.Appender;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Context;
import com.sun.grizzly.GrizzlyFuture;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.ThreadCache;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.BufferUtils;
import java.io.IOException;

/**
 * {@link FilterChain} {@link Context} implementation.
 *
 * @see Context
 * @see FilterChain
 * 
 * @author Alexey Stashok
 */
public final class FilterChainContext extends Context {
    private static final ThreadCache.CachedTypeIndex<FilterChainContext> CACHE_IDX =
            ThreadCache.obtainIndex(FilterChainContext.class, 4);

    public static FilterChainContext create() {
        final FilterChainContext context = ThreadCache.takeFromCache(CACHE_IDX);
        if (context != null) {
            return context;
        }

        return new FilterChainContext();
    }


    public static final int NO_FILTER_INDEX = Integer.MIN_VALUE;

    /**
     * Cached {@link NextAction} instance for "Invoke action" implementation
     */
    private static final NextAction INVOKE_ACTION = new InvokeAction();
    /**
     * Cached {@link NextAction} instance for "Rerun Chain action" implementation
     */
    private static final NextAction RERUN_CHAIN_ACTION = new RerunChainAction();
    /**
     * Cached {@link NextAction} instance for "Stop action" implementation
     */
    private static final NextAction STOP_ACTION = new StopAction();
    /**
     * Cached {@link NextAction} instance for "Suspend action" implementation
     */
    private static final NextAction SUSPEND_ACTION = new SuspendAction();

    /**
     * Context associated message
     */
    private Object message;

    /**
     * Context associated source address
     */
    private Object address;

    /**
     * Index of the currently executing {@link Filter} in
     * the {@link FilterChainContext#filters} list.
     */
    private int filterIdx;

    private int startIdx;

    private final StopAction cachedStopAction = new StopAction();

    private final InvokeAction cachedInvokeAction = new InvokeAction();

    public FilterChainContext() {
        filterIdx = NO_FILTER_INDEX;
    }

    public int nextFilterIdx() {
        return ++filterIdx;
    }

    public int previousFilterIdx() {
        return --filterIdx;
    }

    public int getFilterIdx() {
        return filterIdx;
    }

    protected void setFilterIdx(int index) {
        this.filterIdx = index;
    }

    public int getStartIdx() {
        return startIdx;
    }

    protected void setStartIdx(int startIdx) {
        this.startIdx = startIdx;
    }

    /**
     * Get {@link FilterChain}, which runs the {@link Filter}.
     *
     * @return {@link FilterChain}, which runs the {@link Filter}.
     */
    public FilterChain getFilterChain() {
        return (FilterChain) getProcessor();
    }

    /**
     * Get message object, associated with the current processing.
     * 
     * Usually {@link FilterChain} represents sequence of parser and process
     * {@link Filter}s. Each parser can change the message representation until
     * it will come to processor {@link Filter}.
     *
     * @return message object, associated with the current processing.
     */
    public Object getMessage() {
        return message;
    }

    /**
     * Set message object, associated with the current processing.
     *
     * Usually {@link FilterChain} represents sequence of parser and process
     * {@link Filter}s. Each parser can change the message representation until
     * it will come to processor {@link Filter}.
     *
     * @param message message object, associated with the current processing.
     */
    public void setMessage(Object message) {
        this.message = message;
    }

    /**
     * Get address, associated with the current {@link IOEvent} processing.
     * When we process {@link IOEvent#READ} event - it represents sender address,
     * or when process {@link IOEvent#WRITE} - address of receiver.
     * 
     * @return address, associated with the current {@link IOEvent} processing.
     */
    public Object getAddress() {
        return address;
    }

    /**
     * Set address, associated with the current {@link IOEvent} processing.
     * When we process {@link IOEvent#READ} event - it represents sender address,
     * or when process {@link IOEvent#WRITE} - address of receiver.
     *
     * @param address address, associated with the current {@link IOEvent} processing.
     */
    public void setAddress(Object address) {
        this.address = address;
    }

    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain. Parameter remaining signals, that
     * there is some data remaining in the source message, so {@link FilterChain}
     * could be rerun.
     *
     * Normally, after receiving this instruction from {@link Filter},
     * {@link FilterChain} executes next filter.
     *
     * @param remainder signals, that there is some data remaining in the source
     * message, so {@link FilterChain} could be rerun.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     */
    public NextAction getInvokeAction(Appendable remainder) {
        cachedInvokeAction.setRemainder(remainder);
        return cachedInvokeAction;
    }
    
    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain. Parameter remaining signals, that
     * there is some data remaining in the source message, so {@link FilterChain}
     * could be rerun.
     *
     * Normally, after receiving this instruction from {@link Filter},
     * {@link FilterChain} executes next filter.
     *
     * @param remaining signals, that there is some data remaining in the source
     * message, so {@link FilterChain} could be rerun.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     */
    public <E> NextAction getInvokeAction(E remainder, Appender<E> appender) {
        cachedInvokeAction.setRemainder(remainder, appender);
        return cachedInvokeAction;
    }

    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain. Parameter remaining signals, that
     * there is some data remaining in the source message, so {@link FilterChain}
     * could be rerun.
     *
     * Normally, after receiving this instruction from {@link Filter},
     * {@link FilterChain} executes next filter.
     *
     * @param remainder signals, that there is some data remaining in the source
     * message, so {@link FilterChain} could be rerun.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     */
    public NextAction getInvokeAction(Object unknownObject) {
        if (unknownObject instanceof Buffer) {
            return getInvokeAction(unknownObject, BufferUtils.BUFFER_APPENDER);
        }

        return getInvokeAction((Appendable) unknownObject);
    }
    
    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     *
     * Normally, after receiving this instruction from {@link Filter},
     * {@link FilterChain} executes next filter.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     */
    public NextAction getInvokeAction() {
        return INVOKE_ACTION;
    }

    /**
     * Get {@link NextAction} implementation, which is expected only on post processing
     * phase. This implementation instructs {@link FilterChain} to re-process the
     * {@link IOEvent} processing again from the beginning.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain}
     * to re-process the {@link IOEvent} processing again from the beginning.
     */
    public NextAction getRerunChainAction() {
        return RERUN_CHAIN_ACTION;
    }

    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain}
     * to stop executing phase and start post executing filters.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain}
     * to stop executing phase and start post executing filters.
     */
    public NextAction getStopAction() {
        return STOP_ACTION;
    }


    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain}
     * stop executing phase and start post executing filters.
     * Passed {@link com.sun.grizzly.Appendable} data will be saved and reused
     * during the next {@link FilterChain} invokation.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain}
     * to stop executing phase and start post executing filters.
     * Passed {@link com.sun.grizzly.Appendable} data will be saved and reused
     * during the next {@link FilterChain} invokation.
     */
    public <E> NextAction getStopAction(final E remainder,
            com.sun.grizzly.Appender<E> appender) {
        
        cachedStopAction.setRemainder(remainder, appender);
        return cachedStopAction;
    }

    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain}
     * stop executing phase and start post executing filters.
     * Passed {@link com.sun.grizzly.Appendable} data will be saved and reused
     * during the next {@link FilterChain} invokation.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain}
     * to stop executing phase and start post executing filters.
     * Passed {@link com.sun.grizzly.Appendable} data will be saved and reused
     * during the next {@link FilterChain} invokation.
     */
    public NextAction getStopAction(com.sun.grizzly.Appendable appendable) {
        cachedStopAction.setRemainder(appendable);
        return cachedStopAction;
    }


    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain}
     * stop executing phase and start post executing filters.
     * Passed {@link Buffer} data will be saved and reused during the next
     * {@link FilterChain} invokation.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain}
     * to stop executing phase and start post executing filters.
     * Passed {@link Buffer} data will be saved and reused during the next
     * {@link FilterChain} invokation.
     */
    public NextAction getStopAction(Object unknownObject) {
        if (unknownObject instanceof Buffer) {
            return getStopAction(unknownObject, BufferUtils.BUFFER_APPENDER);
        }

        return getStopAction((Appendable) unknownObject);
    }
    
    /**
     * Get {@link NextAction}, which instructs {@link FilterChain} to suspend filter
     * chain execution, both execute and post-execute phases.
     *
     * @return {@link NextAction}, which instructs {@link FilterChain} to suspend
     * filter chain execution, both execute and post-execute phases.
     */
    public NextAction getSuspendAction() {
        return SUSPEND_ACTION;
    }

    public GrizzlyFuture write(Object message) throws IOException {
        return write(null, message, null);
    }

    public GrizzlyFuture write(Object message,
            CompletionHandler completionHandler) throws IOException {
        return write(null, message, completionHandler);
    }

    public GrizzlyFuture write(Object address, Object message,
            CompletionHandler completionHandler) throws IOException {
        final FutureImpl futureImpl = FutureImpl.create();
        final FilterChainContext newContext = (FilterChainContext) getProcessor().context();
        newContext.setIoEvent(IOEvent.WRITE);
        newContext.setConnection(getConnection());
        newContext.setMessage(message);
        newContext.setAddress(address);
        newContext.setCompletionFuture(futureImpl);
        newContext.setCompletionHandler(completionHandler);
        newContext.setStartIdx(filterIdx - 1);
        newContext.setFilterIdx(filterIdx - 1);

        getFilterChain().execute(newContext);
        return futureImpl;
    }

    /**
     * Release the context associated resources.
     */
    @Override
    public void reset() {
        message = null;
        address = null;
        filterIdx = NO_FILTER_INDEX;
        super.reset();
    }

    @Override
    public void recycle() {
        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(384);
        sb.append("FilterChainContext [");
        sb.append("connection=").append(getConnection());
        sb.append(", message=").append(getMessage());
        sb.append(", address=").append(getAddress());
        sb.append(']');

        return sb.toString();
    }
}
