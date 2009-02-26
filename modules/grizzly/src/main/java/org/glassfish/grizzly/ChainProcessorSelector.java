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

package org.glassfish.grizzly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 *
 * @author oleksiys
 */
public class ChainProcessorSelector implements ProcessorSelector,
        List<ProcessorSelector> {

    private List<ProcessorSelector> selectorChain;

    public ChainProcessorSelector() {
        this(new ArrayList<ProcessorSelector>());
    }

    public ChainProcessorSelector(ProcessorSelector... selectorChain) {
        this(new ArrayList(Arrays.asList(selectorChain)));
    }

    public ChainProcessorSelector(List<ProcessorSelector> selectorChain) {
        this.selectorChain = selectorChain;
    }

    public Processor select(IOEvent ioEvent,
            Connection connection) {
        for(ProcessorSelector processorSelector : selectorChain) {
            Processor processor = processorSelector.select(ioEvent, connection);
            if (processor != null) {
                return processor;
            }
        }

        return null;
    }

    /* List implementation */
    public int size() {
        return selectorChain.size();
    }

    public boolean isEmpty() {
        return selectorChain.isEmpty();
    }

    public boolean contains(Object o) {
        return selectorChain.contains(o);
    }

    public Iterator<ProcessorSelector> iterator() {
        return selectorChain.iterator();
    }

    public Object[] toArray() {
        return selectorChain.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return selectorChain.toArray(a);
    }

    public boolean add(ProcessorSelector o) {
        return selectorChain.add(o);
    }

    public boolean remove(Object o) {
        return selectorChain.remove(o);
    }

    public boolean containsAll(Collection<?> c) {
        return selectorChain.containsAll(c);
    }

    public boolean addAll(Collection<? extends ProcessorSelector> c) {
        return selectorChain.addAll(c);
    }

    public boolean addAll(int index,
            Collection<? extends ProcessorSelector> c) {
        return selectorChain.addAll(index, c);
    }

    public boolean removeAll(Collection<?> c) {
        return selectorChain.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return selectorChain.retainAll(c);
    }

    public void clear() {
        selectorChain.clear();
    }

    public ProcessorSelector get(int index) {
        return selectorChain.get(index);
    }

    public ProcessorSelector set(int index,
            ProcessorSelector element) {
        return selectorChain.set(index, element);
    }

    public void add(int index, ProcessorSelector element) {
        selectorChain.add(index, element);
    }

    public ProcessorSelector remove(int index) {
        return selectorChain.remove(index);
    }

    public int indexOf(Object o) {
        return selectorChain.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        return selectorChain.lastIndexOf(o);
    }

    public ListIterator<ProcessorSelector> listIterator() {
        return selectorChain.listIterator();
    }

    public ListIterator<ProcessorSelector> listIterator(int index) {
        return selectorChain.listIterator(index);
    }

    public List<ProcessorSelector> subList(int fromIndex, int toIndex) {
        return selectorChain.subList(fromIndex, toIndex);
    }

}
