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
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.util.LightArrayList;
import org.glassfish.grizzly.util.MessageHolder;
import org.glassfish.grizzly.util.ObjectPool;

/**
 *
 * @author oleksiys
 */
public class FilterChainContext extends Context {

    private Filter currentFilter;
    private MessageHolder messageHolder;
    private StreamReader streamReader;
    private StreamWriter streamWriter;

    private List<Filter> executedFilters;

    private List<Filter> nextFiltersList;
    
    public FilterChainContext(ObjectPool parentPool) {
        super(parentPool);
        messageHolder = new MessageHolder();
        executedFilters = new LightArrayList<Filter>();
        nextFiltersList = new LightArrayList<Filter>();
    }

    
    protected List<Filter> getExecutedFilters() {
        return executedFilters;
    }

    public void setExecutedFilters(List<Filter> executedFilters) {
        this.executedFilters = executedFilters;
    }

    protected List<Filter> getNextFiltersList() {
        return nextFiltersList;
    }

    public void setNextFiltersList(List<Filter> nextFiltersList) {
        this.nextFiltersList = nextFiltersList;
    }

    public FilterChain getFilterChain() {
        return (FilterChain) getProcessor();
    }

    public Filter getCurrentFilter() {
        return currentFilter;
    }

    protected void setCurrentFilter(Filter currentFilter) {
        this.currentFilter = currentFilter;
    }

    public Object getMessage() {
        return messageHolder.getMessage();
    }

    public void setMessage(Object message) {
        messageHolder.setMessage(message);
    }

    public Object getAddress() {
        return messageHolder.getAddress();
    }

    public void setAddress(Object address) {
        messageHolder.setAddress(address);
    }

    public StreamReader getStreamReader() {
        return streamReader;
    }

    public void setStreamReader(StreamReader streamReader) {
        this.streamReader = streamReader;
    }

    public StreamWriter getStreamWriter() {
        return streamWriter;
    }

    public void setStreamWriter(StreamWriter streamWriter) {
        this.streamWriter = streamWriter;
    }

    @Override
    public void release() {
        currentFilter = null;
        messageHolder.setAddress(null);
        messageHolder.setMessage(null);
        streamReader = null;
        streamWriter = null;
        executedFilters.clear();
        nextFiltersList.clear();
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
