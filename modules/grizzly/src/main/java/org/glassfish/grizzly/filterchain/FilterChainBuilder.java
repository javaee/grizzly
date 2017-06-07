/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * {@link FilterChainBuilder} implementation, which is responsible for
 * constructing {@link FilterChain}s.
 * 
 *
 */
public final class FilterChainBuilder {
    private final List<FilterRecord> patternFilterChain;
    private Set<String> namesMap;
    
    
    FilterChainBuilder() {
        patternFilterChain = new ArrayList<FilterRecord>(8);
    }

    public static FilterChainBuilder newInstance() {
        return new FilterChainBuilder();
    }
    
    public FilterChain build() {
        final DefaultFilterChain filterChain = new DefaultFilterChain();
        for (FilterRecord record : patternFilterChain) {
            if (record.name != null) {
                filterChain.add(record.filter, record.name);
            } else {
                filterChain.add(record.filter);
            }
        }

        return filterChain;
    }
        
    /**
     * Adds the passed {@link Filter}(s) to the tail of the <tt>FilterChain</tt>.
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * This call is equivalent to {@link #addLast(org.glassfish.grizzly.filterchain.Filter...)}.
     * 
     * @param filter the {@link Filter}(s) to be registered
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder add(final Filter... filter) {
        return addLast(filter);
    }
    

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
     *         unique in this <tt>FilterChainBuilder</tt>
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder add(final Filter filter,
            final String filterName) {
        return addLast(filter, filterName);
    }
    
    /**
     * Adds the passed {@link Filter}(s) to the head of the <tt>FilterChain</tt>.
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * @param filter the {@link Filter}(s) to be registered
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addFirst(final Filter... filter) {
        if (filter == null || filter.length == 0) {
            throw new IllegalArgumentException("At least one filter has to be passed");
        }
        
        for (int i = filter.length - 1; i >= 0; i--) {
            patternFilterChain.add(0, new FilterRecord(null, filter[i]));
        }
        
        return this;
    }
    
    /**
     * Adds the {@link Filter} with the given unique name to the
     * head of the <tt>FilterChain</tt>.
     * 
     * @param filter the {@link Filter} to be added
     * @param filterName the unique name for the {@link Filter} registration
     * 
     * @throws IllegalStateException if the passed <tt>filterName</tt> is not
     *         unique in this <tt>FilterChain</tt>
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addFirst(final Filter filter,
            final String filterName) {
        checkName(filterName);
        
        addName(filterName);
        patternFilterChain.add(0, new FilterRecord(filterName, filter));
        
        return this;
    }

    /**
     * Adds the passed {@link Filter}(s) to the tail of the <tt>FilterChain</tt>.
     * The unique names for the {@link Filter} registrations will be
     * automatically generated.
     * 
     * This call is equivalent to {@link #add(org.glassfish.grizzly.filterchain.Filter...)}.
     * 
     * @param filter the {@link Filter}(s) to be registered
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addLast(final Filter... filter) {
        if (filter == null || filter.length == 0) {
            throw new IllegalArgumentException("At least one filter has to be passed");
        }
        
        for (Filter f : filter) {
            patternFilterChain.add(new FilterRecord(null, f));
        }
        
        return this;
    }

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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addLast(final Filter filter,
            final String filterName) {
        checkName(filterName);
        
        addName(filterName);
        patternFilterChain.add(new FilterRecord(filterName, filter));
        
        return this;
    }

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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addAfter(final Filter baseFilter,
            final Filter... filter) {
        if (filter == null || filter.length == 0) {
            throw new IllegalArgumentException("At least one filter has to be passed");
        }

        final int i = idx(baseFilter);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + baseFilter + " is not found");
        }
        
        for (int j = filter.length - 1; j >= 0; j--) {
            patternFilterChain.add(i + 1, new FilterRecord(null, filter[j]));
        }
        
        return this;
    }
    
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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addAfter(final Filter baseFilter,
            final Filter filter, final String filterName) {
        checkName(filterName);
        
        final int i = idx(baseFilter);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + baseFilter + " is not found");
        }
        
        addName(filterName);
        patternFilterChain.add(i + 1, new FilterRecord(filterName, filter));
        
        return this;
    }
    
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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addAfter(final String baseFilterName,
            final Filter... filter) {
        if (filter == null || filter.length == 0) {
            throw new IllegalArgumentException("At least one filter has to be passed");
        }

        final int i = idx(baseFilterName);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + baseFilterName + " is not found");
        }
        
        for (int j = filter.length - 1; j >= 0; j--) {
            patternFilterChain.add(i + 1, new FilterRecord(null, filter[j]));
        }
        
        return this;
    }

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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addAfter(final String baseFilterName,
            final Filter filter, final String filterName) {
        checkName(filterName);
        
        final int i = idx(baseFilterName);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + baseFilterName + " is not found");
        }
        
        addName(filterName);
        patternFilterChain.add(i + 1, new FilterRecord(filterName, filter));
        
        return this;
    }

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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addBefore(final Filter baseFilter,
            final Filter... filter) {
        if (filter == null || filter.length == 0) {
            throw new IllegalArgumentException("At least one filter has to be passed");
        }

        final int i = idx(baseFilter);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + baseFilter + " is not found");
        }
        
        for (int j = filter.length - 1; j >= 0; j--) {
            patternFilterChain.add(i, new FilterRecord(null, filter[j]));
        }
        
        return this;
    }
    
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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addBefore(final Filter baseFilter,
            final Filter filter, final String filterName) {
        checkName(filterName);
        
        final int i = idx(baseFilter);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + baseFilter + " is not found");
        }
        
        addName(filterName);
        patternFilterChain.add(i, new FilterRecord(filterName, filter));
        
        return this;
    }
    
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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addBefore(final String baseFilterName,
            final Filter... filter) {
        if (filter == null || filter.length == 0) {
            throw new IllegalArgumentException("At least one filter has to be passed");
        }

        final int i = idx(baseFilterName);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + baseFilterName + " is not found");
        }
        
        for (int j = filter.length - 1; j >= 0; j--) {
            patternFilterChain.add(i, new FilterRecord(null, filter[j]));
        }
        
        return this;
    }
    
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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder addBefore(final String baseFilterName,
            final Filter filter, final String filterName) {
        checkName(filterName);
        
        final int i = idx(baseFilterName);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + baseFilterName + " is not found");
        }
        
        addName(filterName);
        patternFilterChain.add(i, new FilterRecord(filterName, filter));
        
        return this;
    }
    
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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder replace(final Filter oldFilter,
            final Filter newFilter) {
        final int i = idx(oldFilter);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + oldFilter + " is not found");
        }
        
        final FilterRecord oldRecord = patternFilterChain.get(i);
        if (oldRecord.name != null) {
            removeName(oldRecord.name);
        }
        
        patternFilterChain.set(i, new FilterRecord(null, newFilter));
        return this;
    }

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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder replace(final Filter oldFilter,
            final Filter newFilter, final String newFilterName) {
        
        final int i = idx(oldFilter);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + oldFilter + " is not found");
        }
        
        final FilterRecord oldRecord = patternFilterChain.get(i);
        
        if (containsName(newFilterName) &&
                !newFilterName.equals(oldRecord.name)) {
            throw new IllegalStateException("The Filter name has to be unique within the given FilterChain scope");
        }
        
        if (oldRecord.name != null) {
            removeName(oldRecord.name);
        }

        
        addName(newFilterName);
        patternFilterChain.set(i, new FilterRecord(newFilterName, newFilter));
        return this;
    }
    
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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder replace(final String oldFilterName,
            final Filter newFilter) {
        if (!removeName(oldFilterName)) {
            throw new NoSuchElementException("The filter " + oldFilterName + " is not found");
        }
        
        final int i = idx(oldFilterName);
        assert i >= 0;
        
        patternFilterChain.set(i, new FilterRecord(null, newFilter));
        return this;
    }

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
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder replace(final String oldFilterName,
            final Filter newFilter, final String newFilterName) {
        
        final int i = idx(oldFilterName);
        if (i == -1) {
            throw new NoSuchElementException("The filter " + oldFilterName + " is not found");
        }
        
        if (containsName(newFilterName) &&
                !newFilterName.equals(oldFilterName)) {
            throw new IllegalStateException("The Filter name has to be unique within the given FilterChain scope");
        }
        
        removeName(oldFilterName);
        addName(newFilterName);
        
        patternFilterChain.set(i, new FilterRecord(newFilterName, newFilter));
        return this;
    }

    /**
     * Removes the first occurrence of the {@link Filter}.
     * 
     * @param filter the {@link Filter} to be removed
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder remove(final Filter filter) {
        final int i = idx(filter);
        if (i >= 0) {
            final FilterRecord record = patternFilterChain.get(i);
            if (record.name != null) {
                removeName(record.name);
            }
            
            patternFilterChain.remove(i);
        }
        
        return this;
    }
    
    /**
     * Removes the {@link Filter} represented by the unique <tt>filterName</tt>.
     * 
     * @param filterName the name of the {@link Filter} registration to be removed
     * @return this {@link FilterChainBuilder}
     */
    public FilterChainBuilder remove(final String filterName) {
        final int i = idx(filterName);
        if (i >= 0) {
            removeName(filterName);
            patternFilterChain.remove(i);
        }
        
        return this;
    }
    
    /**
     * Removes the first {@link Filter} in the chain and returns it as a result.
     * 
     * @return this {@link FilterChainBuilder}
     * @throws NoSuchElementException if the <tt>FilterChain</tt> is empty
     */
    public FilterChainBuilder removeFirst() {
        if (patternFilterChain.isEmpty()) {
            throw new NoSuchElementException("The filter chain is empty");
        }
        
        final FilterRecord record = patternFilterChain.remove(0);
        if (record.name != null) {
            removeName(record.name);
        }
        
        return this;
    }
    
    /**
     * Removes the last {@link Filter} in the chain and returns it as a result.
     * 
     * @return this {@link FilterChainBuilder}
     * @throws NoSuchElementException if the <tt>FilterChain</tt> is empty
     */    
    public FilterChainBuilder removeLast() {
        if (patternFilterChain.isEmpty()) {
            throw new NoSuchElementException("The filter chain is empty");
        }
        
        final FilterRecord record = patternFilterChain.remove(
                patternFilterChain.size() - 1);
        if (record.name != null) {
            removeName(record.name);
        }
        
        return this;
    }

    private void checkName(final String name) throws IllegalStateException {
        if (containsName(name)) {
            throw new IllegalStateException("The Filter name has to be unique within the given FilterChain scope");
        }
    }

    private boolean containsName(final String name) {
        return namesMap != null && namesMap.contains(name);
    }
    
    private boolean removeName(final String name) {
        return namesMap != null && namesMap.remove(name);
    }
    
    private void addName(final String name) {
        if (namesMap == null) {
            namesMap = new HashSet<String>(4);
        }
        
        namesMap.add(name);
    }
    
    private int idx(final Filter filter) {
        for (int i = 0; i < patternFilterChain.size(); i++) {
            if (filter.equals(patternFilterChain.get(i).filter)) {
                return i;
            }
        }
        
        return -1;
    }
    
    private int idx(final String name) {
        for (int i = 0; i < patternFilterChain.size(); i++) {
            if (name.equals(patternFilterChain.get(i).name)) {
                return i;
            }
        }
        
        return -1;
    }
    
    
    private static class FilterRecord {
        private final String name;
        private final Filter filter;
        
        public FilterRecord(final String name, final Filter filter) {
            this.name = name;
            this.filter = filter;
        }
    }
}
