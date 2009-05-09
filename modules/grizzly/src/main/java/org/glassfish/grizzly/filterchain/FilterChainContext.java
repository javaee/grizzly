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

import java.util.List;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.ssl.SSLFilter;
import org.glassfish.grizzly.ssl.SSLStreamReader;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.util.LightArrayList;
import org.glassfish.grizzly.util.MessageHolder;
import org.glassfish.grizzly.util.ObjectPool;

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

    Filter getDefaultTransportFilter() {
        return defaultTransportFilter;
    }

    void setDefaultTransportFilter(final Filter defaultTransportFilter) {
        this.defaultTransportFilter = defaultTransportFilter;
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
