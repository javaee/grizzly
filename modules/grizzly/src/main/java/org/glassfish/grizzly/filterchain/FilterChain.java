/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.NoSuchElementException;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Event;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ProcessorResult;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.WriteResult;

/**
 * <p>
 * This class implement the "Chain of Responsibility" pattern (for more info, 
 * take a look at the classic "Gang of Four" design patterns book). Towards 
 * that end, the Chain API models a computation as a series of "protocol filter"
 * that can be combined into a "protocol chain".
 * 
 * The FilterChain is <tt>unmodifiable</tt>.
 * 
 * </p><p>
 * The API for Filter consists of methods (handleXXX()) which get a 
 * "protocol context" parameter containing the
 * dynamic state of the computation, and whose return value is a
 * {@link NextAction} that instructs <tt>FilterChain</tt>, how it should
 * continue processing.
 * </p><p>
 * The following picture describe how it Filter(s) 
 * </p><p><pre><code>
 * -----------------------------------------------------------------------------
 * - Filter1.handleXXX() --> Filter2.handleXXX()                    |          -
 * -----------------------------------------------------------------------------
 * </code></pre></p><p>
 * The "context" abstraction is designed to isolate Filter
 * implementations from the environment in which they are run 
 * (such as a Filter that can be used in either IIOP or HTTP parsing, 
 * without being tied directly to the API contracts of either of these 
 * environments).
 * </p>
 *
 * @see Filter
 *
 * @author Grizzly team
 */
public interface FilterChain extends Processor<Context>, Iterable<Filter> {
    /**
     * Returns the registration object {@link FilterReg} for the first {@link Filter}
     * in the chain.
     * 
     * @return the registration object {@link FilterReg} for the first {@link Filter}
     *         in the chain
     * @throws NoSuchElementException if the <tt>FilterChain</tt> is empty
     */
    public FilterReg firstFilterReg();
    
    /**
     * Returns the registration object {@link FilterReg} for the last {@link Filter}
     * in the chain.
     * 
     * @return the registration object {@link FilterReg} for the last {@link Filter}
     *         in the chain
     * @throws NoSuchElementException if the <tt>FilterChain</tt> is empty
     */    
    public FilterReg lastFilterReg();
    
    /**
     * Gets the registration object {@link FilterReg} for the {@link Filter}
     * identified by name.
     * 
     * @param name the {@link Filter} name, given during the registration
     * 
     * @return the registration object {@link FilterReg} for the {@link Filter}
     *         identified by its name
     */  
    public FilterReg getFilterReg(String name);
    
    /**
     * Gets the registration object {@link FilterReg} for the first occurrence
     * of the {@link Filter} in this <tt>FilterChain</tt>.
     * 
     * The same {@link Filter} instance could be registered on a <tt>FilterChain</tt>,
     * so this method will return the registration object for the first occurrence
     * of the given {@link Filter} in this <tt>FilterChain</tt>.
     * 
     * @param filter the {@link Filter}
     * @return the registration object {@link FilterReg} for the first occurrence
     *         of the {@link Filter} in this <tt>FilterChain</tt>
     */
    public FilterReg getFilterReg(Filter filter);

    /**
     * Gets the registration object {@link FilterReg} for the first occurrence
     * of the given filter type in this <tt>FilterChain</tt>.
     * 
     * @param filterType the {@link Filter} type, which is a class name of the {@link Filter}
     * 
     * @return the registration object {@link FilterReg} for the first occurrence
     *         of the given filter type in this <tt>FilterChain</tt>
     */
    public FilterReg getRegByType(Class<? extends Filter> filterType);
    
    /**
     * Gets all the registration objects {@link FilterReg} for the {@link Filter}s
     * in this <tt>FilterChain</tt>, whose type (class name) corresponds the
     * passed filter type.
     * 
     * @param filterType the {@link Filter} type, which is a class name of the {@link Filter}
     * 
     * @return all the registration objects {@link FilterReg} for the {@link Filter}s
     *         in this <tt>FilterChain</tt>, whose type (class name)
     *         corresponds the passed filter type
     */
    public FilterReg[] getAllRegsByType(Class<? extends Filter> filterType);

    /**
     * Returns the {@link Filter} registered in the <tt>FilterChain</tt> by the name,
     * or <tt>null</tt> there is no {@link Filter} registered with this name.
     *
     * @param name the registered {@link Filter} name
     * @return the {@link Filter} registered in the <tt>FilterChain</tt> with a name,
     *         or <tt>null</tt> there is no {@link Filter} registered with this name
     */
    public Filter get(String name);
    
    /**
     * Gets the first {@link Filter} from the <tt>FilterChain</tt>, which type 
     * corresponds to the passed class.
     * 
     * @param <E> {@link Filter} type
     * @param filterType the {@link Filter} type, which is a class name of the {@link Filter}
     * 
     * @return the first {@link Filter} from the <tt>FilterChain</tt>, which type 
     *         corresponds to the passed class
     */
    public <E extends Filter> E getByType(Class<E> filterType);
    
    /**
     * Gets all the {@link Filter}s from the <tt>FilterChain</tt>, whose type 
     * corresponds to the passed class.
     * 
     * @param filterType the {@link Filter} type, which is a class name of the {@link Filter}
     * 
     * @return all the {@link Filter}s from the <tt>FilterChain</tt>, whose type 
     *         corresponds to the passed class.
     */
    public Filter[] getAllByType(Class<? extends Filter> filterType);
 
    /**
     * Gets the current <tt>FilterChain</tt> size, which is the number of
     * {@link Filter}s registered in the <tt>FilterChain</tt>.
     * 
     * @return the current <tt>FilterChain</tt> size, which is the number of
     *         {@link Filter}s registered in the <tt>FilterChain</tt>
     */
    public int size();
    
    /**
     * Returns <tt>true</tt> if there is at least one {@link Filter} registered
     * in the <tt>FilterChain</tt>, or <tt>false</tt> otherwise.
     * 
     * @return <tt>true</tt> if there is at least one {@link Filter} registered
     *         in the <tt>FilterChain</tt>, or <tt>false</tt> otherwise
     */
    public boolean isEmpty();
    
    /**
     * Returns the first {@link Filter} in the chain.
     * 
     * @return the first {@link Filter} in the chain
     * @throws NoSuchElementException if the <tt>FilterChain</tt> is empty
     */
    public Filter first();
    
    /**
     * Returns the last {@link Filter} in the chain.
     * 
     * @return the last {@link Filter} in the chain
     * @throws NoSuchElementException if the <tt>FilterChain</tt> is empty
     */
    public Filter last();
    
    /**
     * Adds the passed {@link Filter}(s) to the tail of the <tt>FilterChain</tt>.
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * This call is equivalent to {@link #addLast(org.glassfish.grizzly.filterchain.Filter...)}.
     * 
     * @param filter the {@link Filter}(s) to be registered
     */
    public void add(Filter... filter);

    /**
     * Adds the {@link Filter} with the given unique name to the
     * tail of the <tt>FilterChain</tt>.
     * 
     * This call is equivalent to {@link #addLast(org.glassfish.grizzly.filterchain.Filter, java.lang.String)}.
     * 
     * @param filter the {@link Filter} to be added
     * @param filterName the unique name for the {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>filterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     */
    public void add(Filter filter, String filterName);
    
    /**
     * Adds the passed {@link Filter}(s) to the head of the <tt>FilterChain</tt>.
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * @param filter the {@link Filter}(s) to be registered
     */
    public void addFirst(Filter... filter);
    
    /**
     * Adds the {@link Filter} with the given unique name to the
     * head of the <tt>FilterChain</tt>.
     * 
     * @param filter the {@link Filter} to be added
     * @param filterName the unique name for the {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>filterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     */
    public void addFirst(Filter filter, String filterName);
    
    /**
     * Adds the passed {@link Filter}(s) to the tail of the <tt>FilterChain</tt>.
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * This call is equivalent to {@link #add(org.glassfish.grizzly.filterchain.Filter...)}.
     * 
     * @param filter the {@link Filter}(s) to be registered
     */
    public void addLast(Filter... filter);

    /**
     * Adds the {@link Filter} with the given unique name to the
     * tail of the <tt>FilterChain</tt>.
     * 
     * This call is equivalent to {@link #add(org.glassfish.grizzly.filterchain.Filter, java.lang.String)}.
     * 
     * @param filter the {@link Filter} to be added
     * @param filterName the unique name for the {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>filterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     */
    public void addLast(Filter filter, String filterName);
    
    /**
     * Adds the passed {@link Filter}(s) after the first occurrence of the
     * given base {@link Filter} instance in the <tt>FilterChain</tt>.
     * 
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * @param baseFilter the {@link Filter} after which new {@link Filter}s will
     *        be registered
     * @param filter the {@link Filter}(s) to be registered
     * @throws NoSuchElementException if baseFilter is not found registered in
     *         this <tt>FilterChain</tt>
     */
    public void addAfter(Filter baseFilter, Filter... filter);
    
    /**
     * Adds the {@link Filter} with the given unique name after the first
     * occurrence of the given base {@link Filter} instance
     * in the <tt>FilterChain</tt>.
     * 
     * @param baseFilter the {@link Filter} after which new {@link Filter}s will
     *        be registered
     * @param filter the {@link Filter} to be added
     * @param filterName the unique name for the {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>filterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     * @throws NoSuchElementException if baseFilter is not found registered in
     *         this <tt>FilterChain</tt>
     */
    public void addAfter(Filter baseFilter, Filter filter, String filterName);
    
    /**
     * Adds the passed {@link Filter}(s) after the base {@link Filter}
     * represented by the <tt>baseFilterName</tt> in the <tt>FilterChain</tt>.
     * 
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * @param baseFilterName the unique name of the registered {@link Filter},
     *        after which new {@link Filter}s will be registered
     * @param filter the {@link Filter}(s) to be registered
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>baseFilterName</tt> was not found in this
     *         <tt>FilterChain</tt>
     */
    public void addAfter(String baseFilterName, Filter... filter);

    /**
     * Adds the {@link Filter} with the given unique name after the base
     * {@link Filter} represented by the <tt>baseFilterName</tt> in the
     * <tt>FilterChain</tt>.
     * 
     * @param baseFilterName the unique name of the registered {@link Filter},
     *        after which new {@link Filter} will be registered
     * @param filter the {@link Filter} to be added
     * @param filterName the unique name for the {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>filterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>baseFilterName</tt> was not found in this
     *         <tt>FilterChain</tt>
     */
    public void addAfter(String baseFilterName, Filter filter, String filterName);

    /**
     * Adds the passed {@link Filter}(s) before the first occurrence of the
     * given base {@link Filter} instance in the <tt>FilterChain</tt>.
     * 
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * @param baseFilter the {@link Filter} before which new {@link Filter}s will
     *        be registered
     * @param filter the {@link Filter}(s) to be registered
     * @throws NoSuchElementException if baseFilter is not found registered in
     *         this <tt>FilterChain</tt>
     */
    public void addBefore(Filter baseFilter, Filter... filter);

    /**
     * Adds the {@link Filter} with the given unique name before the first
     * occurrence of the given base {@link Filter} instance
     * in the <tt>FilterChain</tt>.
     * 
     * @param baseFilter the {@link Filter} before which new {@link Filter}s will
     *        be registered
     * @param filter the {@link Filter} to be added
     * @param filterName the unique name for the {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>filterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     * @throws NoSuchElementException if baseFilter is not found registered in
     *         this <tt>FilterChain</tt>
     */
    public void addBefore(Filter baseFilter, Filter filter, String filterName);
    
    /**
     * Adds the passed {@link Filter}(s) before the base {@link Filter}
     * represented by the <tt>baseFilterName</tt> in the <tt>FilterChain</tt>.
     * 
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * @param baseFilterName the unique name of the registered {@link Filter},
     *        before which new {@link Filter}s will be registered
     * @param filter the {@link Filter}(s) to be registered
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>baseFilterName</tt> was not found in this
     *         <tt>FilterChain</tt>
     */
    public void addBefore(String baseFilterName, Filter... filter);

    /**
     * Adds the {@link Filter} with the given unique name before the base
     * {@link Filter} represented by the <tt>baseFilterName</tt> in the
     * <tt>FilterChain</tt>.
     * 
     * @param baseFilterName the unique name of the registered {@link Filter},
     *        before which new {@link Filter} will be registered
     * @param filter the {@link Filter} to be added
     * @param filterName the unique name for the {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>filterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>baseFilterName</tt> was not found in this
     *         <tt>FilterChain</tt>
     */
    public void addBefore(String baseFilterName, Filter filter, String filterName);
    
    /**
     * Replaces the first occurrence of the old {@link Filter} with the new {@link Filter}.
     * 
     * The unique name for the new {@link Filter} registration will be
     * automatically generated.
     * 
     * @param oldFilter the old {@link Filter} to be removed
     * @param newFilter the new {@link Filter} to be added
     * 
     * @throws NoSuchElementException if oldFilter is not found registered in
     *         this <tt>FilterChain</tt>
     */
    public void replace(Filter oldFilter, Filter newFilter);

    /**
     * Replaces the first occurrence of the old {@link Filter} with
     * the new {@link Filter} with the given unique name.
     * 
     * @param oldFilter the old {@link Filter} to be removed
     * @param newFilter the new {@link Filter} to be added
     * @param newFilterName the unique name for the new {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>newFilterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     * @throws NoSuchElementException if oldFilter is not found registered in
     *         this <tt>FilterChain</tt>
     */
    public void replace(Filter oldFilter, Filter newFilter, String newFilterName);
    
    /**
     * Replaces the old {@link Filter} represented by the unique
     * <tt>oldFilterName</tt> with the new {@link Filter}.
     * 
     * The unique name for the new {@link Filter} registration will be
     * automatically generated.
     * 
     * @param oldFilterName the unique name of the registered old {@link Filter}
     *        to be removed
     * @param newFilter the new {@link Filter} to be added
     * 
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>oldFilterName</tt> was not found in this
     *         <tt>FilterChain</tt>
     */
    public void replace(String oldFilterName, Filter newFilter);
    
    /**
     * Replaces the old {@link Filter} represented by the unique
     * <tt>oldFilterName</tt> with the new {@link Filter}.
     * 
     * @param oldFilterName the unique name of the registered old {@link Filter}
     *        to be removed
     * @param newFilter the new {@link Filter} to be added
     * @param newFilterName the unique name for the new {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>newFilterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>oldFilterName</tt> was not found in this
     *         <tt>FilterChain</tt>
     */
    public void replace(String oldFilterName, Filter newFilter, String newFilterName);

    /**
     * Removes the first occurrence of the {@link Filter}.
     * 
     * @param filter the {@link Filter} to be removed
     * @return <tt>true</tt> if the {@link Filter} was found in the <tt>FilterChain</tt>
     *         and successfully removed, or <tt>false</tt> otherwise
     */
    public boolean remove(Filter filter);
    
    /**
     * Removes the {@link Filter} represented by the unique <tt>filterName</tt>.
     * 
     * @param filterName the name of the {@link Filter} registration to be removed
     * @return <tt>true</tt> if the {@link Filter} was found in the <tt>FilterChain</tt>
     *         and successfully removed, or <tt>false</tt> otherwise
     */
    public boolean remove(String filterName);
    
    /**
     * Removes the first {@link Filter} in the chain and returns it as a result.
     * 
     * @return the removed {@link Filter}
     * @throws NoSuchElementException if the <tt>FilterChain</tt> is empty
     */
    public Filter removeFirst();
    
    /**
     * Removes the last {@link Filter} in the chain and returns it as a result.
     * 
     * @return the removed {@link Filter}
     * @throws NoSuchElementException if the <tt>FilterChain</tt> is empty
     */    
    public Filter removeLast();
    
    /**
     * Removes all the {@link Filter}s in the chain preceding the specified {@link Filter},
     * represented by the name.
     * 
     * @param filterName the name of the {@link Filter}
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>filterName</tt> was not found in this
     *         <tt>FilterChain</tt>
     */
    public void removeAllBefore(final String filterName);
    
    /**
     * Removes all the {@link Filter}s in the chain following the specified {@link Filter},
     * represented by the name.
     * 
     * @param filterName the name of the {@link Filter}
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>filterName</tt> was not found in this
     *         <tt>FilterChain</tt>
     */
    public void removeAllAfter(final String filterName);
    
    /**
     * Returns <tt>true</tt> if the {@link Filter} is registered in this
     * <tt>FilterChain</tt>, or <tt>false</tt> otherwise.
     * 
     * @param filter the {@link Filter}
     * @return <tt>true</tt> if the {@link Filter} is registered in this
     *         <tt>FilterChain</tt>, or <tt>false</tt> otherwise
     */
    public boolean contains(Filter filter);
    
    /**
     * Returns <tt>true</tt> if the {@link Filter} represented by the
     * <tt>filterName</tt> is registered in this <tt>FilterChain</tt>,
     * or <tt>false</tt> otherwise.
     * 
     * @param filterName the {@link Filter} registration name
     * @return <tt>true</tt> if the {@link Filter} represented by the
     *         <tt>filterName</tt> is registered in this <tt>FilterChain</tt>,
     *         or <tt>false</tt> otherwise.
     */    
    public boolean contains(String filterName);

    /**
     * Returns an unmodifiable ordered {@link List} of all {@link Filter}
     * registration names in this <tt>FilterChain</tt>.
     * 
     * The returned {@link List} represents <tt>FilterChain</tt> state at the time
     * of the method invocation, all the following changes made simultaneously
     * on a different {@link Thread} will not be reflected.
     * 
     * @return an unmodifiable ordered {@link List} of all {@link Filter}
     *         registration names in this <tt>FilterChain</tt>
     */
    public List<String> names();
    
    /**
     * Removes all the {@link Filter}s from this <tt>FilterChain</tt>.
     */
    public void clear();
    
    /**
     * {@inheritDoc}
     */
    @Override
    public FilterChainIterator iterator();

    /**
     * Returns a chain iterator over the filters in this <tt>FilterChain</tt>
     * (in proper sequence), starting at the specified {@link Filter} represented
     * by the unique <tt>name</tt>.
     * The specified name indicates the first {@link Filter} that would be
     * returned by an initial call to {@link FilterChainIterator#next next}.
     * An initial call to {@link FilterChainIterator#previous previous} would
     * return the {@link Filter} previous to the one specified by the <tt>name</tt>.
     *
     * @param name represents the {@link Filter} be returned from the
     *        chain iterator (by a call to {@link FilterChainIterator#next next})
     * @return a chain iterator over the {@link Filter}s in this <tt>FilterChain</tt>
     *         (in proper sequence), starting at the specified {@link Filter}
     *         represented by the unique <tt>name</tt>
     * @throws NoSuchElementException if the {@link Filter} registered
     *         with the <tt>name</tt> was not found in this <tt>FilterChain</tt>
     */
    public FilterChainIterator iterator(String name);

    /**
     * Returns a copy of this <tt>FilterChain</tt>, which contains the same set
     * of {@link Filter}s. These two <tt>FilterChain</tt>s are independent, so
     * changes made on one <tt>FilterChain</tt> will not be visible by other.
     * 
     * NOTE: The copied {@link Filter}s in the new <tt>FilterChain</tt> will
     * share a {@link FilterState} with the original {@link Filter}s from this
     * <tt>FilterChain</tt>.
     * 
     * @return a copy of this <tt>FilterChain</tt>, which contains the same set
     * of {@link Filter}s
     * 
     * @see FilterState
     */
    public FilterChain copy();
    
    /**
     * Method processes occurred {@link Event} on this {@link FilterChain}.
     *
     * @param context processing context
     * @return {@link ProcessorResult}
     */
    public abstract ProcessorResult execute(FilterChainContext context);

    /**
     * Sends {@link TransportFilter#FLUSH_EVENT} associated with the specified
     * {@link Connection} to all the filters in this <tt>FilterChain</tt>.
     * 
     * @param connection the {@link Connection}
     * @param completionHandler the {@link CompletionHandler}
     */
    public abstract void flush(Connection connection,
            CompletionHandler<WriteResult> completionHandler);

    /**
     * Sends custom {@link Event} associated with the specified
     * {@link Connection} to all the filters in this <tt>FilterChain</tt>.
     * 
     * The {@link Filter}s in this <tt>FilterChain</tt> are invoked in
     * head-to-tail order.
     * 
     * @param connection the {@link Connection}
     * @param event the {@link Event}
     * @param completionHandler the {@link CompletionHandler}
     */
    public abstract void fireEventUpstream(Connection connection,
            Event event,
            CompletionHandler<FilterChainContext> completionHandler);
    
    /**
     * Sends custom {@link Event} associated with the specified
     * {@link Connection} to all the filters in this <tt>FilterChain</tt>.
     * 
     * The {@link Filter}s in this <tt>FilterChain</tt> are invoked in
     * tail-to-head order.
     * 
     * @param connection the {@link Connection}
     * @param event the {@link Event}
     * @param completionHandler the {@link CompletionHandler}
     */
    public abstract void fireEventDownstream(Connection connection,
            Event event,
            CompletionHandler<FilterChainContext> completionHandler);

    /**
     * Executed blocking read operation using specified {@link FilterChainContext},
     * where user can set first and last {@link Filter} in the <tt>FilterChain</tt>,
     * which will take part in the result processing.
     * 
     * After the last {@link Filter} is invoked, the result of
     * {@link FilterChainContext#getMessage()} will be returned as the result
     * of this method invocation.
     * 
     * @param context the {@link FilterChainContext}
     * @return the result of blocking read
     * 
     * @throws IOException 
     */
    public abstract ReadResult read(FilterChainContext context) throws IOException;

    /**
     * Notifies the {@link Filter}s in this <tt>FilterChain</tt> about the error
     * by calling their {@link Filter#exceptionOccurred(org.glassfish.grizzly.filterchain.FilterChainContext, java.lang.Throwable)}
     * method.
     * 
     * The method will only notify the {@link Filter}s, which took part in
     * {@link FilterChainContext} processing.
     * 
     * @param context the {@link FilterChainContext} for the current event.
     * @param failure the failure that triggered this method.
     */
    public abstract void fail(FilterChainContext context, Throwable failure);
    
    /**
     * Returns the {@link FilterChainContext} for the specified {@link Connection},
     * which could be used to initialize filter chain processing.
     * 
     * @param connection the {@link Connection}
     * @return the {@link FilterChainContext} for the specified {@link Connection},
     *         which could be used to initialize filter chain processing
     */
    public FilterChainContext obtainFilterChainContext(Connection connection);

    /**
     * Returns the {@link FilterChainContext} with the specified {@link Connection},
     * start filter, current filter, end filter information, which could be
     * used to initialize filter chain processing.
     * 
     * @param connection the {@link Connection}
     * @param startFilterReg the start {@link FilterReg}
     * @param endFilterReg the end {@link FilterReg}
     * @param currentFilterReg the current {@link FilterReg}
     * 
     * @return the {@link FilterChainContext} for the specified {@link Connection},
     *         which could be used to initialize filter chain processing
     */
    public FilterChainContext obtainFilterChainContext(Connection connection,
            FilterReg startFilterReg, FilterReg endFilterReg,
            FilterReg currentFilterReg);
}
