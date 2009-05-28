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

package com.sun.grizzly.filterchain;

import java.util.ArrayList;
import java.util.List;
import com.sun.grizzly.Context;
import com.sun.grizzly.ssl.SSLFilter;
import com.sun.grizzly.ssl.SSLStreamReader;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.LightArrayList;
import com.sun.grizzly.utils.MessageHolder;
import com.sun.grizzly.utils.ObjectPool;

/**
 * {@link FilterChain} {@link Context} implementation.
 *
 * @see Context
 * @see FilterChain
 * 
 * @author Alexey Stashok
 */
public class FilterChainContext extends Context {
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
     * Cached InvokeAction, used by {@link DefaultFilterChain}, to avoid
     * creating it each time.
     */
    private InvokeAction cachedInvokeAction;

    /**
     * Current processing {@link Filter}
     */
    private Filter currentFilter;

    /**
     * {@link MessageHolder}, containing information about processing message
     */
    private MessageHolder messageHolder;

    /**
     * Current context {@link StreamReader}
     */
    private StreamReader streamReader;
    /**
     * Current context {@link StreamWriter}
     */
    private StreamWriter streamWriter;

    /**
     * List of {@link Filter}s, which have been executed already
     */
    private List<Filter> executedFilters;

    /**
     * Index of the currently executing {@link Filter} in
     * the {@link FilterChainContext#filters} list.
     */
    private int currentFilterIdx;

    /**
     * List of {@link Filter}s.
     */
    private List<Filter> filters;

    private Filter defaultTransportFilter;
    
    public FilterChainContext(ObjectPool parentPool) {
        super(parentPool);
        messageHolder = new MessageHolder();
        executedFilters = new LightArrayList<Filter>();
        currentFilterIdx = 0;
    }

    
    /**
     * Get {@link List} of executed {@link Filter}s.
     * 
     * @return {@link List} of executed {@link Filter}s.
     */
    public List<Filter> getExecutedFilters() {
        return executedFilters;
    }

    /**
     * Set {@link List} of executed {@link Filter}s.
     *
     * @param executedFilters {@link List} of executed {@link Filter}s.
     */
    protected void setExecutedFilters(List<Filter> executedFilters) {
        this.executedFilters = executedFilters;
    }

    /**
     * Get {@link List} of {@link Filter}s.
     *
     * @return {@link List} of {@link Filter}s.
     */
    public List<Filter> getFilters() {
        return filters;
    }

    /**
     * Set {@link List} of {@link Filter}s.
     *
     * @param filters {@link List} of {@link Filter}s.
     */
    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    /**
     * Get index of the currently executing {@link Filter} in
     * the {@link FilterChainContext#filters} list.
     *
     * @return index of the currently executing {@link Filter} in
     * the {@link FilterChainContext#filters} list.
     */
    public int getCurrentFilterIdx() {
        return currentFilterIdx;
    }

    /**
     * Set index of the currently executing {@link Filter} in
     * the {@link FilterChainContext#filters} list.
     *
     * @param currentFilterIdx index of the currently executing {@link Filter}
     * in the {@link FilterChainContext#filters} list.
     */
    public void setCurrentFilterIdx(int currentFilterIdx) {
        this.currentFilterIdx = currentFilterIdx;
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
     * Get {@link Filter}, which is currently running.
     * 
     * @return {@link Filter}, which is currently running.
     */
    public Filter getCurrentFilter() {
        return currentFilter;
    }

    /**
     * Set {@link Filter}, which is currently running.
     *
     * @param currentFilter {@link Filter}, which is currently running.
     */
    protected void setCurrentFilter(Filter currentFilter) {
        this.currentFilter = currentFilter;
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
        return messageHolder.getMessage();
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
        messageHolder.setMessage(message);
    }

    /**
     * Get address, associated with the current {@link IOEvent} processing.
     * When we process {@link IOEvent#READ} event - it represents sender address,
     * or when process {@link IOEvent#WRITE} - address of receiver.
     * 
     * @return address, associated with the current {@link IOEvent} processing.
     */
    public Object getAddress() {
        return messageHolder.getAddress();
    }

    /**
     * Set address, associated with the current {@link IOEvent} processing.
     * When we process {@link IOEvent#READ} event - it represents sender address,
     * or when process {@link IOEvent#WRITE} - address of receiver.
     *
     * @param address address, associated with the current {@link IOEvent} processing.
     */
    public void setAddress(Object address) {
        messageHolder.setAddress(address);
    }

    /**
     * Get the {@link StreamReader}, associated with processing.
     * {@link Filter}s are allowed to change context associated
     * {@link StreamReader}. For example {@link SSLFilter} wraps original
     * <tt>FilterChainContext</tt>'s {@link StreamReader} with
     * {@link SSLStreamReader} and next filter on chain will work with
     * SSL-enabled {@link StreamReader}.
     *
     * @return the {@link StreamReader}, associated with processing.
     */
    public StreamReader getStreamReader() {
        return streamReader;
    }

    /**
     * Set the {@link StreamReader}, associated with processing.
     * {@link Filter}s are allowed to change context associated
     * {@link StreamReader}. For example {@link SSLFilter} wraps original
     * <tt>FilterChainContext</tt>'s {@link StreamReader} with
     * {@link SSLStreamReader} and next filter on chain will work with
     * SSL-enabled {@link StreamReader}.
     *
     * @param streamReader the {@link StreamReader}, associated with processing.
     */
    public void setStreamReader(StreamReader streamReader) {
        this.streamReader = streamReader;
    }

    /**
     * Get the {@link StreamWriter}, associated with processing.
     * {@link Filter}s are allowed to change context associated
     * {@link StreamWriter}.
     *
     * @return the {@link StreamWriter}, associated with processing.
     */
    public StreamWriter getStreamWriter() {
        return streamWriter;
    }

    /**
     * Set the {@link StreamWriter}, associated with processing.
     * {@link Filter}s are allowed to change context associated
     * {@link StreamWriter}.
     *
     * @param streamWriter the {@link StreamWriter}, associated with processing.
     */
    public void setStreamWriter(StreamWriter streamWriter) {
        this.streamWriter = streamWriter;
    }

    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     *
     * Normally, after receiving this instruction from {@link Filter},
     * {@link FilterChain} takes {@link Filter} with index:
     * {@link InvokeAction#getNextFilterIdx()} from {@link InvokeAction#getFilters()}
     * chain.
     *
     * Any {@link Filter} implementation is free to change the {@link Filter}
     * execution sequence.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     *
     * @see #getInvokeAction(java.util.List)
     * @see #getInvokeAction(java.util.List, int) 
     */
    public NextAction getInvokeAction() {
        return INVOKE_ACTION;
    }

    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     *
     * Normally, after receiving this instruction from {@link Filter},
     * {@link FilterChain} takes {@link Filter} with index:
     * {@link InvokeAction#getNextFilterIdx()} from {@link InvokeAction#getFilters()}
     * chain.
     *
     * Any {@link Filter} implementation is free to change the {@link Filter}
     * execution sequence.
     *
     * @param filters new list of the filters to be invoked in the chain processing
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     *
     * @see #getInvokeAction()
     * @see #getInvokeAction(java.util.List, int)
     */
    public NextAction getInvokeAction(List<Filter> filters) {
        return new InvokeAction(new ArrayList<Filter>(filters));
    }

    /**
     * Get {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     *
     * Normally, after receiving this instruction from {@link Filter},
     * {@link FilterChain} takes {@link Filter} with index:
     * {@link InvokeAction#getNextFilterIdx()} from {@link InvokeAction#getFilters()}
     * chain.
     *
     * Any {@link Filter} implementation is free to change the {@link Filter}
     * execution sequence.
     *
     * @param filters new list of the filters to be invoked in the chain processing
     * @param nextFilterIdx new index of the {@link Filter} in {@link NextAction#getFilters()}
     * list, which should be executed next.
     *
     * @return {@link NextAction} implementation, which instructs {@link FilterChain} to
     * process next {@link Filter} in chain.
     *
     * @see #getInvokeAction()
     * @see #getInvokeAction(java.util.List)
     */
    public NextAction getInvokeAction(List<Filter> filters, int nextFilterIdx) {
        return new InvokeAction(new ArrayList<Filter>(filters), nextFilterIdx);
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
     * Get {@link NextAction}, which instructs {@link FilterChain} to suspend filter
     * chain execution, both execute and post-execute phases.
     *
     * @return {@link NextAction}, which instructs {@link FilterChain} to suspend
     * filter chain execution, both execute and post-execute phases.
     */
    public NextAction getSuspendAction() {
        return SUSPEND_ACTION;
    }

    Filter getDefaultTransportFilter() {
        return defaultTransportFilter;
    }

    void setDefaultTransportFilter(final Filter defaultTransportFilter) {
        this.defaultTransportFilter = defaultTransportFilter;
    }

    final InvokeAction getCachedInvokeAction() {
        return cachedInvokeAction;
    }

    final void setCachedInvokeAction(InvokeAction cachedInvokeAction) {
        this.cachedInvokeAction = cachedInvokeAction;
    }

    /**
     * Release the context associated resources.
     */
    @Override
    public void release() {
        currentFilter = null;
        messageHolder.setAddress(null);
        messageHolder.setMessage(null);
        streamReader = null;
        streamWriter = null;
        filters = null;
        executedFilters.clear();
        defaultTransportFilter = null;
        super.release();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(384);
        sb.append("FilterChainContext [");
        sb.append("connection=").append(getConnection());
        sb.append(", message=").append(getMessage());
        sb.append(", address=").append(getAddress());
        sb.append(", executedFilters=").append(executedFilters);
        sb.append(']');

        return sb.toString();
    }
}
