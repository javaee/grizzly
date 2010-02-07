/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.attributes;

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
    private static final Object[] ZERO_ARRAY = new Object[0];

    private Object[] attributeValues;

    protected final DefaultAttributeBuilder attributeBuilder;
    protected final IndexedAttributeAccessor indexedAttributeAccessor;

    public IndexedAttributeHolder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = (DefaultAttributeBuilder) attributeBuilder;
        attributeValues = new Object[16];
        indexedAttributeAccessor = new IndexedAttributeAccessorImpl();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String name) {
        Attribute attribute = attributeBuilder.getAttributeByName(name);
        if (attribute != null) {
            return indexedAttributeAccessor.getAttribute(attribute.index());
        }
        
        return null;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttribute(String name, Object value) {
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
    public Object removeAttribute(String name) {
        Attribute attribute = attributeBuilder.getAttributeByName(name);
        if (attribute != null) {
            int index = attribute.index();
            
            Object value = indexedAttributeAccessor.getAttribute(index);
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
        Set<String> result = new HashSet<String>();

        for (int i = 0; i < attributeValues.length; i++) {
            Object value = attributeValues[i];
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
    public void clear() {
        Arrays.fill(attributeValues, null);
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
        public Object getAttribute(int index) {
            if (index < attributeValues.length) {
                return attributeValues[index];
            }

            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setAttribute(int index, Object value) {
            ensureSize(index + 1);
            attributeValues[index] = value;
        }

        private void ensureSize(int size) {
            final int delta = size - attributeValues.length;
            if (delta > 0) {
                final int newLength = Math.max(attributeValues.length + delta,
                        (attributeValues.length * 3) / 2 + 1);

                attributeValues = Arrays.copyOf(attributeValues, newLength);
            }
        }
    }
}
