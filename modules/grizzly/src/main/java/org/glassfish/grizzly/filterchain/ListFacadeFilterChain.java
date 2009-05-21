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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * {@link FilterChain} facade, which implements all the {@link List} related
 * methods.
 *
 * @see FilterChain
 * 
 * @author Alexey Stashok
 */
public abstract class ListFacadeFilterChain extends AbstractFilterChain {
    
    /**
     * The list of Filters this chain will invoke.
     */
    protected final List<Filter> filters;

    public ListFacadeFilterChain(final FilterChainFactory factory,
            final List<Filter> filtersImpl) {
        super(factory);
        this.filters = filtersImpl;
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean add(Filter filter) {
        int size = filters.size();

        if (filters.add(filter)) {
            recalculateFilterIndexes(size);
            return true;
        }

        return false;
    }
        
    /**
     * {@inheritDoc}
     */
    public void add(int index, Filter filter){
        filters.add(index, filter);
        recalculateFilterIndexes(index);
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean addAll(Collection<? extends Filter> c) {
        int size = filters.size();

        if (filters.addAll(c)) {
            recalculateFilterIndexes(size);
            return true;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean addAll(int index, Collection<? extends Filter> c) {
        if (filters.addAll(index, c)) {
            recalculateFilterIndexes(index);
            return true;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public Filter set(int index,
            Filter filter) {
        Filter prevFilter = filters.set(index, filter);
        
        if (filter.isIndexable()) {
            filter.setIndex(index);
        }

        return prevFilter;
    }
    
    /**
     * {@inheritDoc}
     */
    public Filter get(int index) {
        return filters.get(index);
    }

    /**
     * {@inheritDoc}
     */
    public int indexOf(Object object) {
        Filter filter = (Filter) object;
        
        // if Filter is indexable - optimize the index search
        if (filter.isIndexable()) {
            return filter.getIndex();
        }
        
        return filters.indexOf(filter);
    }
    
    /**
     * {@inheritDoc}
     */
    public int lastIndexOf(Object filter) {
        return filters.lastIndexOf(filter);
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(Object filter) {
        return filters.contains(filter);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsAll(Collection<?> c) {
        return filters.containsAll(c);
    }

    /**
     * {@inheritDoc}
     */
    public Object[] toArray() {
        return filters.toArray();
    }

    /**
     * {@inheritDoc}
     */
    public <T> T[] toArray(T[] a) {
        return filters.toArray(a);
    }

    /**
     * {@inheritDoc}
     */
    public boolean retainAll(Collection<?> c) {
        if (filters.retainAll(c)) {
            recalculateFilterIndexes(0);
            return true;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(Object object) {
        Filter filter = (Filter) object;
        
        // if Filter is indexable - optimize the index search
        if (filter.isIndexable()) {
            return remove(filter.getIndex()) != null;
        }
        
        if (filters.remove(filter)) {
            recalculateFilterIndexes(0);
            return true;
        }

        return false;
    }
           
    /**
     * {@inheritDoc}
     */
    public Filter remove(int index) {
        Filter removingFilter = filters.remove(index);
        if (removingFilter != null) {
            recalculateFilterIndexes(index);
            return removingFilter;
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeAll(Collection<?> c) {
        if (filters.removeAll(c)) {
            recalculateFilterIndexes(0);
            return true;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return filters == null || filters.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        return filters.size();
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        filters.clear();
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<Filter> iterator() {
        return filters.iterator();
    }

    /**
     * {@inheritDoc}
     */
    public ListIterator<Filter> listIterator() {
        return filters.listIterator();
    }

    /**
     * {@inheritDoc}
     */
    public ListIterator<Filter> listIterator(int index) {
        return filters.listIterator(index);
    }

    /**
     * {@inheritDoc}
     */
    public List<Filter> subList(int fromIndex, int toIndex) {
        return filters.subList(fromIndex, toIndex);
    }

    protected void recalculateFilterIndexes(int startPosition) {
        for(int i = startPosition; i < filters.size(); i++) {
            Filter filter = filters.get(i);
            if (filter.isIndexable()) {
                filter.setIndex(i);
            }
        }
    }
}
