/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;

/**
 * {@link FilterChainBuilder} implementation, which is responsible for
 * constructing {@link FilterChain}s.
 * 
 * @author Alexey Stashok
 */
public abstract class FilterChainBuilder {
    protected final List<Filter> patternFilterChain;
    
    private FilterChainBuilder() {
        patternFilterChain = new DefaultFilterChain();
    }

    public static FilterChainBuilder stateless() {
        return new StatelessFilterChainBuilder();
    }
    
    public static FilterChainBuilder stateful() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public abstract FilterChain build();


    public FilterChainBuilder add(Filter filter) {
        return addLast(filter);
    }

    public FilterChainBuilder addFirst(Filter filter) {
        patternFilterChain.add(0, filter);
        return this;
    }
    
    public FilterChainBuilder addLast(Filter filter) {
        patternFilterChain.add(filter);
        return this;
    }

    public FilterChainBuilder add(int index, Filter filter) {
        patternFilterChain.add(index, filter);
        return this;
    }

    public FilterChainBuilder set(int index, Filter filter) {
        patternFilterChain.set(index, filter);
        return this;
    }

    public Filter get(int index)  {
        return patternFilterChain.get(index);
    }

    public FilterChainBuilder remove(int index) {
        patternFilterChain.remove(index);
        return this;
    }

    public FilterChainBuilder remove(Filter filter) {
        patternFilterChain.remove(filter);
        return this;
    }

    public FilterChainBuilder addAll(Filter[] array) {
        patternFilterChain.addAll(patternFilterChain.size(), Arrays.asList(array));
        return this;
    }

    public FilterChainBuilder addAll(int filterIndex, Filter[] array) {
        patternFilterChain.addAll(filterIndex, Arrays.asList(array));
        return this;
    }

    public FilterChainBuilder addAll(List<Filter> list) {
        return addAll(patternFilterChain.size(), list);
    }

    public FilterChainBuilder addAll(int filterIndex, List<Filter> list) {
        patternFilterChain.addAll(filterIndex, list);
        return this;
    }

    public FilterChainBuilder addAll(final FilterChainBuilder source) {
        patternFilterChain.addAll(source.patternFilterChain);
        return this;
    }

    public int indexOf(final Filter filter) {
        return patternFilterChain.indexOf(filter);
    }

    public int indexOfType(final Class<? extends Filter> filterType) {
        final int size = patternFilterChain.size();
        for (int i = 0; i < size; i++) {
            final Filter filter = get(i);
            if (filterType.isAssignableFrom(filter.getClass())) {
                return i;
            }
        }

        return -1;
    }

    public static class StatelessFilterChainBuilder extends FilterChainBuilder {
        @Override
        public FilterChain build() {
            final FilterChain fc = new DefaultFilterChain();
            fc.addAll(patternFilterChain);
            return fc;
        }
    }
}
