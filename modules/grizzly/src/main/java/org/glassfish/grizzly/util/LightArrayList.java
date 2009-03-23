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

package org.glassfish.grizzly.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Lighter version of {@link ArrayList}, which internaly uses array offset, to
 * avoid redundant array shifting when executing add, remove methods.
 *
 * @author Alexey Stashok
 */
public class LightArrayList<E> implements List<E> {
    private Object[] array;
    
    private int size;
    private int offset;

    public LightArrayList() {
        this(16);
    }

    public LightArrayList(int initialCapacity) {
        array = new Object[initialCapacity];
    }
    
    public LightArrayList(List<E> parentList, int fromIndex, int toIndex) {
        this(Math.max(16, toIndex - fromIndex));
        size = toIndex - fromIndex;
        for(int i=0; i<size; i++) {
            array[i] = parentList.get(i + fromIndex);
        }
    }

    public int size() {
        return size;
    }

    public void size(int size) {
        int diff = size - this.size;
        if (diff > 0) {
            ensureCapacity(size + diff);
        }

        this.size = size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(Object o) {
    	return indexOf(o) >= 0;
    }

    public Object[] toArray() {
        Object[] result = new Object[size];
        System.arraycopy(array, offset, result, 0, size);
        return result;
    }

    public <T> T[] toArray(T[] a) {
        if (a.length < size) {
            a = (T[]) java.lang.reflect.Array.newInstance(
                    a.getClass().getComponentType(), size);
        }
        System.arraycopy(array, offset, a, 0, size);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    public boolean add(E e) {
        ensureCapacity(size + 1);
        array[offset + size++] = e;
        return true;
    }

    public void add(int index, E element) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean addAll(Collection<? extends E> c) {
        Object[] a = c.toArray();
        int numNew = a.length;
        ensureCapacity(numNew + size);  // Increments modCount
        System.arraycopy(a, 0, array, offset + size, numNew);
        size += numNew;
        return numNew != 0;
    }

    public boolean addAll(int index, Collection<? extends E> c) {
        Object[] a = c.toArray();
        int numNew = a.length;
        ensureCapacity(numNew + size);  // Increments modCount

        int numMoved = size - index;
        if (numMoved > 0) {
            System.arraycopy(array, index + offset, array, index + offset + numNew,
                    numMoved);
        }

        System.arraycopy(a, 0, array, index + offset, numNew);
        size += numNew;
        return numNew != 0;
    }

    public E remove(int index) {
        E oldValue = (E) array[offset + index];
        if (index == offset) {
            offset++;
            size--;
            return oldValue;
        }


        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(array, offset + index + 1, array, offset + index,
                    numMoved);
        }
        size--;

        return oldValue;
    }

    public boolean remove(Object o) {
        if (o == null) {
            for (int index = 0; index < size; index++) {
                if (array[index] == null) {
                    remove(index);
                    return true;
                }
            }
        } else {
            for (int index = 0; index < size; index++) {
                if (o.equals(array[index])) {
                    remove(index);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        Iterator<?> e = iterator();
        while (e.hasNext()) {
            if (c.contains(e.next())) {
                e.remove();
                modified = true;
            }
        }
        return modified;
    }

    public boolean containsAll(Collection<?> c) {
        Iterator<?> e = c.iterator();
        while (e.hasNext()) {
            if (!contains(e.next())) {
                return false;
            }
        }
        
        return true;
    }

    public boolean retainAll(Collection<?> c) {
        boolean modified = false;
        Iterator<E> e = iterator();
        while (e.hasNext()) {
            if (!c.contains(e.next())) {
                e.remove();
                modified = true;
            }
        }
        return modified;
    }

    public void clear() {
        offset = 0;
        size = 0;
    }

    public E get(int index) {
        return (E) array[index + offset];
    }

    public E set(int index, E element) {
        int i = index + offset;
        E oldValue = (E) array[i];
        array[i] = element;

        return oldValue;
    }

    public int indexOf(Object o) {
        if (o == null) {
            for (int i = 0; i < size; i++) {
                if (array[i + offset] == null) {
                    return i;
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (o.equals(array[i + offset])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int lastIndexOf(Object o) {
        if (o == null) {
            for (int i = size - 1; i >= 0; i--) {
                if (array[i + offset] == null) {
                    return i;
                }
            }
        } else {
            for (int i = size - 1; i >= 0; i--) {
                if (o.equals(array[i + offset])) {
                    return i;
                }
            }
        }
        return -1;
    }


    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private int index = -1;

            public boolean hasNext() {
                return (index + 1) < size;
            }

            public E next() {
                return get(++index);
            }

            public void remove() {
                LightArrayList.this.remove(index);
            }
        };
    }

    public ListIterator<E> listIterator() {
        return listIterator(-1);
    }

    public ListIterator<E> listIterator(final int i) {
        return new ListIterator<E>() {
            private int index = i;

            public boolean hasNext() {
                return index + 1 < size;
            }

            public E next() {
                return get(++index);
            }

            public boolean hasPrevious() {
                return index - 1 >= 0;
            }

            public E previous() {
                return get(--index);
            }

            public int nextIndex() {
                return index + 1;
            }

            public int previousIndex() {
                return index - 1;
            }

            public void remove() {
                LightArrayList.this.remove(index);
            }

            public void set(E e) {
                LightArrayList.this.set(index, e);
            }

            public void add(E e) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

        };
    }

    public List<E> subList(int fromIndex, int toIndex) {
        return new LightArrayList(this, fromIndex, toIndex);
    }

    // Comparison and hashing

    /**
     * Compares the specified object with this list for equality.  Returns
     * {@code true} if and only if the specified object is also a list, both
     * lists have the same size, and all corresponding pairs of elements in
     * the two lists are <i>equal</i>.  (Two elements {@code e1} and
     * {@code e2} are <i>equal</i> if {@code (e1==null ? e2==null :
     * e1.equals(e2))}.)  In other words, two lists are defined to be
     * equal if they contain the same elements in the same order.<p>
     *
     * This implementation first checks if the specified object is this
     * list. If so, it returns {@code true}; if not, it checks if the
     * specified object is a list. If not, it returns {@code false}; if so,
     * it iterates over both lists, comparing corresponding pairs of elements.
     * If any comparison returns {@code false}, this method returns
     * {@code false}.  If either iterator runs out of elements before the
     * other it returns {@code false} (as the lists are of unequal length);
     * otherwise it returns {@code true} when the iterations complete.
     *
     * @param o the object to be compared for equality with this list
     * @return {@code true} if the specified object is equal to this list
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof List)) {
            return false;
        }

        ListIterator<E> e1 = listIterator();
        ListIterator e2 = ((List) o).listIterator();
        while (e1.hasNext() && e2.hasNext()) {
            E o1 = e1.next();
            Object o2 = e2.next();
            if (!(o1 == null ? o2 == null : o1.equals(o2))) {
                return false;
            }
        }
        return !(e1.hasNext() || e2.hasNext());
    }

    /**
     * Returns the hash code value for this list.
     *
     * <p>This implementation uses exactly the code that is used to define the
     * list hash function in the documentation for the {@link List#hashCode}
     * method.
     *
     * @return the hash code value for this list
     */
    @Override
    public int hashCode() {
        int hashCode = 1;
        Iterator<E> i = iterator();
        while (i.hasNext()) {
            E obj = i.next();
            hashCode = 31 * hashCode + (obj == null ? 0 : obj.hashCode());
        }
        return hashCode;
    }

    public void ensureCapacity(int newCapacity) {
        int requiredLength = offset + newCapacity;
        int diff = requiredLength - array.length;

        if (diff <= 0) {
            return;
        }

        if (offset >= diff) {
            for(int i = 0; i < size; i++) {
                array[i] = array[i + offset];
            }

            offset = 0;
        } else {
            int newLength = Math.max(newCapacity, size * 2);
            Object[] newArray = new Object[newLength];
            System.arraycopy(array, 0, newArray, 0, size);
        }
    }

}
