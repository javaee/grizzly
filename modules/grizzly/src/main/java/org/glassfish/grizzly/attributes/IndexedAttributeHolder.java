/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * {@link AttributeHolder}, which supports indexed access to stored
 * {@link Attribute}s. Access to such indexed {@link Attribute}s could be as
 * fast as access to array.
 * 
 * This implementation is thread-safe.
 *
 * @see AttributeHolder
 * @see NamedAttributeHolder
 *
 * @author Alexey Stashok
 */
public final class IndexedAttributeHolder implements AttributeHolder {
    private final Object sync = new Object();
    
    // dummy volatile
    private volatile int count;
    
    private Snapshot state;
    
    protected final DefaultAttributeBuilder attributeBuilder;
    protected final IndexedAttributeAccessor indexedAttributeAccessor;

    /**
     * @param attributeBuilder
     * @deprecated use {@link AttributeBuilder#createSafeAttributeHolder()}
     */
    public IndexedAttributeHolder(final AttributeBuilder attributeBuilder) {
        this.attributeBuilder = (DefaultAttributeBuilder) attributeBuilder;
        state = new Snapshot(
                new Object[4], new int[] {-1, -1, -1, -1}, 0);
        
        indexedAttributeAccessor = new IndexedAttributeAccessorImpl();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(final String name) {
        return getAttribute(name, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(final String name,
            final NullaryFunction initializer) {
        final Attribute attribute = attributeBuilder.getAttributeByName(name);
        if (attribute != null) {
            return indexedAttributeAccessor.getAttribute(
                    attribute.index(), initializer);
        }
        
        return initializer != null ? initializer : null;
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
            return indexedAttributeAccessor.removeAttribute(attribute.index());
        }
        
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getAttributeNames() {
        if (count != 0) {
            final Set<String> result = new HashSet<String>();

            final Snapshot stateNow = state;
            final int localSize = stateNow.size;

            final Object[] localAttributeValues = stateNow.values;
            for (int i = 0; i < localSize; i++) {
                final Object value = localAttributeValues[i];
                if (value != null) {
                    Attribute attribute = attributeBuilder.getAttributeByIndex(i);
                    result.add(attribute.name());
                }
            }

            return result;
        } else {
            return Collections.<String>emptySet();
        }
    }

    @Override
    public void copyFrom(final AttributeHolder srcAttributes) {
        if (srcAttributes instanceof IndexedAttributeHolder) {
            final IndexedAttributeHolder iah = 
                    (IndexedAttributeHolder) srcAttributes;

            final Snapshot stateNow = state;
            final Snapshot srcState = iah.state;

            int[] newI2v = stateNow.i2v;
            if (newI2v.length < srcState.i2v.length) {
                newI2v = Arrays.copyOf(srcState.i2v, srcState.i2v.length);
            } else {
                System.arraycopy(srcState.i2v, 0, newI2v, 0, srcState.i2v.length);
                for (int i = srcState.i2v.length; i < newI2v.length; i++) {
                    newI2v[i] = -1;
                }
            }

            Object[] newValues = stateNow.values;
            if (newValues.length < srcState.size) {
                newValues = Arrays.copyOf(srcState.values, srcState.size);
            } else {
                System.arraycopy(srcState.values, 0, newValues, 0, srcState.size);
                for (int i = srcState.size; i < stateNow.size; i++) {
                    newValues[i] = null;
                }
            }

            final Snapshot newState = new Snapshot(newValues, newI2v, srcState.size);

            state = newState;
            count++;
        } else {
            clear();

            final Set<String> names = srcAttributes.getAttributeNames();
            if (names.isEmpty()) {
                return;
            }
            
            for (String name : names) {
                setAttribute(name, srcAttributes.getAttribute(name));
            }
        }
    }
    
    @Override
    public void copyTo(final AttributeHolder dstAttributes) {
        if (count != 0) {
            if (dstAttributes instanceof IndexedAttributeHolder) {
                final IndexedAttributeHolder iah =
                        (IndexedAttributeHolder) dstAttributes;

                final Snapshot stateNow = state;
                final Snapshot dstState = iah.state;

                int[] newI2v = dstState.i2v;
                if (newI2v.length < stateNow.i2v.length) {
                    newI2v = Arrays.copyOf(stateNow.i2v, stateNow.i2v.length);
                } else {
                    System.arraycopy(stateNow.i2v, 0, newI2v, 0, stateNow.i2v.length);
                    for (int i = stateNow.i2v.length; i < newI2v.length; i++) {
                        newI2v[i] = -1;
                    }
                }

                Object[] newValues = dstState.values;
                if (newValues.length < stateNow.size) {
                    newValues = Arrays.copyOf(stateNow.values, stateNow.size);
                } else {
                    System.arraycopy(stateNow.values, 0, newValues, 0, stateNow.size);
                    for (int i = stateNow.size; i < dstState.size; i++) {
                        newValues[i] = null;
                    }
                }

                final Snapshot newState = new Snapshot(newValues, newI2v, stateNow.size);

                iah.state = newState;
                iah.count++;
            } else {
                dstAttributes.clear();
                final Snapshot stateNow = state;

                final int localSize = stateNow.size;

                final Object[] localAttributeValues = stateNow.values;
                for (int i = 0; i < localSize; i++) {
                    final Object value = localAttributeValues[i];
                    if (value != null) {
                        final Attribute attribute = attributeBuilder.getAttributeByIndex(i);
                        dstAttributes.setAttribute(attribute.name(), value);
                    }
                }
            }
        } else {
            dstAttributes.clear();
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle() {
        if (count != 0) {
            final Snapshot stateNow = state;
            // Recycle is not synchronized
            for (int i = 0; i < stateNow.size; i++) {
                stateNow.values[i] = null;
            }
        } else {
            count = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        if (count != 0) {
            count = 0;
            
            for (int i = 0; i < state.size; i++) {
                state.values[i] = null;
            }
        }
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
            return getAttribute(index, null);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getAttribute(final int index,
                final NullaryFunction initializer) {
            Object value = weakGet(index);

            if (value == null && initializer != null) {
                synchronized (sync) {
                    // we want to make sure that parallel getAttribute(int, NullaryFunction)
                    // won't create multiple value instances (everyone will call NullaryFunction.evaluate())
                    value = weakGet(index);
                    
                    if (value == null) {
                        value = initializer.evaluate();
                        setAttribute(index, value);
                    }
                }
            }
            
            return value;
        }

        private Object weakGet(final int index) {
            if (count != 0) {
                final Snapshot stateNow = state;
                if (index < stateNow.i2v.length) {
                    final int idx = stateNow.i2v[index];
                    if (idx != -1 && idx < stateNow.size) {
                        return stateNow.values[idx];
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
            final Snapshot stateNow = state;

            int mappedIdx;
            if (index < stateNow.i2v.length &&
                    (mappedIdx = stateNow.i2v[index]) != -1) {
                stateNow.values[mappedIdx] = value;
                count++;
            } else if (value != null) {
                setSync(index, value);
            }
        }

        @Override
        public Object removeAttribute(final int index) {
            final Snapshot stateNow = state;
            
            Object oldValue = null;
            
            int mappedIdx;
            if (index < stateNow.i2v.length &&
                    (mappedIdx = stateNow.i2v[index]) != -1) {
                oldValue = stateNow.values[mappedIdx];
                stateNow.values[mappedIdx] = null;
                count++;
            }
            
            return oldValue;
        }
        
        private void setSync(final int index, final Object value) {
            synchronized (sync) {
                final Snapshot stateNow = state;
                
                int mappedIdx;
                final int[] newI2v;
                if (index < stateNow.i2v.length) {
                    if ((mappedIdx = stateNow.i2v[index]) != -1
                            && mappedIdx < stateNow.size) {
                        stateNow.values[mappedIdx] = value;
                        count++;
                        return;
                    }
                    
                    newI2v = stateNow.i2v;
                } else {
                    newI2v = ensureSize(stateNow.i2v, index + 1);
                }

                mappedIdx = stateNow.size;
                final int newSize = mappedIdx + 1;
                
                final Object[] newValues = mappedIdx < stateNow.values.length ?
                        stateNow.values :
                        ensureSize(stateNow.values, newSize);

                newValues[mappedIdx] = value;
                newI2v[index] = mappedIdx;

                state = new Snapshot(newValues, newI2v, newSize);
                
                count++;
            }
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

    private static class Snapshot {
        private final Object[] values;
        private final int[] i2v;

        private final int size;
        
        public Snapshot(Object[] values, int[] i2v, int size) {
            this.values = values;
            this.i2v = i2v;
            this.size = size;
        }
    }
}
