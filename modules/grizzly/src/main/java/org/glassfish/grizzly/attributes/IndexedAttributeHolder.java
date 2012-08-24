/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.attributes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link AttributeHolder}, which supports indexed access to stored
 * {@link Attribute}s. Access to such indexed {@link Attribute}s could be as
 * fast as access to array.
 *
 * @see AttributeHolder
 * @see NamedAttributeHolder
 *
 * @author Alexey Stashok
 */
public final class IndexedAttributeHolder implements AttributeHolder {
    // dummy volatile
    private volatile int count;
    
    // number of values mapped
    private int size;

    private Object[] attributeValues;

    // Attribute index -> value index map. Maps attribute index to
    // the index in the attributeValues array
    private int[] i2v;

    protected final DefaultAttributeBuilder attributeBuilder;
    protected final IndexedAttributeAccessor indexedAttributeAccessor;

    public IndexedAttributeHolder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = (DefaultAttributeBuilder) attributeBuilder;
        attributeValues = new Object[4];
        i2v = ensureSize(new int[0], 16);
        indexedAttributeAccessor = new IndexedAttributeAccessorImpl();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(final String name) {
        final Attribute attribute = attributeBuilder.getAttributeByName(name);
        if (attribute != null) {
            return indexedAttributeAccessor.getAttribute(attribute.index());
        }
        
        return null;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttribute(final String name, final Object value) {
        Attribute attribute = attributeBuilder.getAttributeByName(name);
        if (attribute == null) {
            attribute = attributeBuilder.createAttribute(name);
        }

        indexedAttributeAccessor.setAttribute(attribute.index(), value);        
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Object removeAttribute(final String name) {
        final Attribute attribute = attributeBuilder.getAttributeByName(name);
        if (attribute != null) {
            final int index = attribute.index();

            final Object value = indexedAttributeAccessor.getAttribute(index);
            if (value != null) {
                indexedAttributeAccessor.setAttribute(index, null);
            }

            return value;
        }
        
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getAttributeNames() {
        final Set<String> result = new HashSet<String>();

        final int localSize = size;

        final Object[] localAttributeValues = attributeValues;
        for (int i = 0; i < localSize; i++) {
            final Object value = localAttributeValues[i];
            if (value != null) {
                Attribute attribute = attributeBuilder.getAttributeByIndex(i);
                result.add(attribute.name());
            }
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle() {
        // Recycle is not synchronized
        for (int i = 0; i < size; i++) {
            attributeValues[i] = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        attributeValues = new Object[attributeValues.length];
        count++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeBuilder getAttributeBuilder() {
        return attributeBuilder;
    }

    /**
     * Returns {@link IndexedAttributeAccessor} for accessing {@link Attribute}s
     * by index.
     *
     * @return {@link IndexedAttributeAccessor} for accessing {@link Attribute}s
     * by index.
     */
    @Override
    public IndexedAttributeAccessor getIndexedAttributeAccessor() {
        return indexedAttributeAccessor;
    }
    
    /**
     * {@link IndexedAttributeAccessor} implementation.
     */
    protected final class IndexedAttributeAccessorImpl implements IndexedAttributeAccessor {
        /**
         * {@inheritDoc}
         */
        @Override
        public Object getAttribute(final int index) {
            if (count != 0) {
                final int[] i2vLocal = i2v;
                if (index < i2vLocal.length) {
                    final int idx = i2vLocal[index];
                    if (idx != -1) {
                        return attributeValues[idx];
                    }
                }
            }

            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setAttribute(final int index, final Object value) {
            final int[] localI2v = i2v;

            int mappedIdx;
            if (index >= localI2v.length || (mappedIdx = localI2v[index]) == -1) {
                mappedIdx = mapIndex(index);
            }

            attributeValues[mappedIdx] = value;
            count++;
        }

        private synchronized int mapIndex(final int index) {
            if (index >= i2v.length) {
                i2v = ensureSize(i2v, index + 1);
            }

            int mappedIdx = i2v[index];
            if (mappedIdx == -1) {
                if (size == attributeValues.length) {
                    attributeValues = ensureSize(attributeValues, size + 1);
                }

                count++;
                i2v[index] = mappedIdx = size++;
            }


            return mappedIdx;
        }
    }
    
    private static Object[] ensureSize(final Object[] array, final int size) {

        final int arrayLength = array.length;
        final int delta = size - arrayLength;

        final int newLength = Math.max(arrayLength + delta,
                (arrayLength * 3) / 2 + 1);

        return Arrays.copyOf(array, newLength);
    }

    private static int[] ensureSize(final int[] array, final int size) {

        final int arrayLength = array.length;
        final int delta = size - arrayLength;

        final int newLength = Math.max(arrayLength + delta,
                (arrayLength * 3) / 2 + 1);

        final int[] newArray = Arrays.copyOf(array, newLength);
        Arrays.fill(newArray, array.length, newLength, -1);

        return newArray;
    }


}
