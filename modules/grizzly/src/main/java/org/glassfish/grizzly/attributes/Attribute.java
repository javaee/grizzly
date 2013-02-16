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

package org.glassfish.grizzly.attributes;

import org.glassfish.grizzly.utils.NullaryFunction;

/** 
 * Class used to define dynamic typed attributes on {@link AttributeHolder}
 * instances.
 * Storing attribute values in {@link AttributeHolder} has two advantage
 * comparing to Map storage:
 *      1) <tt>Attribute</tt> value is typed, and could be checked at
 *         compile time.
 *      2) Access to <tt>Attribute</tt> value, if used with
 *         {@link IndexedAttributeHolder}, could be as fast as access to array.
 */
public final class Attribute<T> {
    
    /**
     * Create Attribute using default attribute builder
     * {@link AttributeBuilder#DEFAULT_ATTRIBUTE_BUILDER} with name.
     * 
     * Equivalent of {@link AttributeBuilder#createAttribute(java.lang.String)}.
     * 
     * @param <T> Type of attribute value
     * @param name attribute name

     * @return Attribute<T>
     */
    public static <T> Attribute<T> create(final String name) {
        return create(AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER, name);
    }

    /**
     * Create Attribute with name.
     * 
     * Equivalent of {@link AttributeBuilder#createAttribute(java.lang.String)}.
     * 
     * @param <T> Type of attribute value
     * @param builder {@link AttributeBuilder} to be used
     * @param name attribute name

     * @return Attribute<T>
     */
    public static <T> Attribute<T> create(final AttributeBuilder builder,
            final String name) {
        return builder.createAttribute(name);
    }

    /**
     * Create Attribute using default attribute builder
     * {@link AttributeBuilder#DEFAULT_ATTRIBUTE_BUILDER} with name and default value.
     * 
     * Equivalent of {@link AttributeBuilder#createAttribute(java.lang.String, java.lang.Object)}.
     * 
     * @param <T> Type of attribute value
     * @param name attribute name
     * @param defaultValue attribute's default value
     * 
     * @return Attribute<T>
     */
    public static <T> Attribute<T> create(final String name,
            final T defaultValue) {
        return create(AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER,
                name, defaultValue);
    }

    /**
     * Create Attribute with name and default value.
     * 
     * Equivalent of {@link AttributeBuilder#createAttribute(java.lang.String, java.lang.Object)}.
     * 
     * @param <T> Type of attribute value
     * @param builder {@link AttributeBuilder} to be used
     * @param name attribute name
     * @param defaultValue attribute's default value
     * 
     * @return Attribute<T>
     */
    public static <T> Attribute<T> create(final AttributeBuilder builder,
            final String name, final T defaultValue) {
        return builder.createAttribute(name, defaultValue);
        
    }

    /**
     * Create Attribute using default attribute builder
     * {@link AttributeBuilder#DEFAULT_ATTRIBUTE_BUILDER}
     * with name and initializer, which will be called, if
     * Attribute's value is null on a AttributedObject.
     * 
     * Equivalent of {@link AttributeBuilder#createAttribute(java.lang.String, org.glassfish.grizzly.utils.NullaryFunction)}.
     * 
     * @param <T> Type of attribute value
     * @param name attribute name
     * @param initializer NullaryFunction, which will be called, if Attribute's
     *                    value is null on a AttributedObject 
     * 
     * @return Attribute<T>
     */
    public static <T> Attribute<T> create(final String name,
            final NullaryFunction<T> initializer) {
        return create(AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER,
                name, initializer);
    }

    /**
     * Create Attribute with name and initializer, which will be called, if
     * Attribute's value is null on a AttributedObject
     * 
     * Equivalent of {@link AttributeBuilder#createAttribute(java.lang.String, org.glassfish.grizzly.utils.NullaryFunction)}.
     * 
     * @param <T> Type of attribute value
     * @param builder {@link AttributeBuilder} to be used
     * @param name attribute name
     * @param initializer NullaryFunction, which will be called, if Attribute's
     *                    value is null on a AttributedObject 
     * 
     * @return Attribute<T>
     */
    public static <T> Attribute<T> create(final AttributeBuilder builder,
            final String name, final NullaryFunction<T> initializer) {
        return builder.createAttribute(name, initializer);
    }

    /**
     * AttributeBuilder, which was used to create this attribute
     */
    private final AttributeBuilder builder;
    /**
     * Attribute name
     */
    private final String name;
    /**
     * Attribute initializer, which will be called, if attribute is not set.
     */
    private final NullaryFunction<T> initializer;
    /**
     * Attribute default value, which will be used, if attribute is not set.
     */
    private final T defaultValue;
    /**
     * Attribute index in AttributeBuilder
     */
    private final int attributeIndex;

    @Override
    public String toString() {
        return "Attribute[" + name + ':' + attributeIndex + ']';
    }

    protected Attribute(AttributeBuilder builder, String name, int index,
            T defaultValue) {
        this.builder = builder;
        this.name = name;
        this.attributeIndex = index;
        this.initializer = null;
        this.defaultValue = defaultValue;
    }

    protected Attribute(AttributeBuilder builder, String name, int index,
            NullaryFunction<T> initializer) {
        this.builder = builder;
        this.name = name;
        this.attributeIndex = index;
        this.initializer = initializer;
        this.defaultValue = null;
    }

    /**
     * Get attribute value, stored on the {@link AttributeHolder},
     * the difference from {@link #get(org.glassfish.grizzly.attributes.AttributeHolder)} is
     * that default value or {@link NullaryFunction} won't be invoked.
     *
     * @param attributeHolder {@link AttributeHolder}.
     * @return attribute value
     */
    public T peek(final AttributeHolder attributeHolder) {
        return weakGet(attributeHolder);
    }

    /**
     * Get attribute value, stored on the {@link AttributeStorage},
     * the difference from {@link #get(AttributeStorage)} is
     * that default value or {@link NullaryFunction} won't be invoked.
     *
     * @param storage {@link AttributeStorage}.
     * @return attribute value
     */
    public T peek(final AttributeStorage storage) {
        final AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            return peek(holder);
        }

        return null;
    }

    /**
     * Get attribute value, stored on the {@link AttributeHolder}.
     * 
     * @param attributeHolder {@link AttributeHolder}.
     * @return attribute value
     */
    public T get(final AttributeHolder attributeHolder) {
        T result = weakGet(attributeHolder);
        
        if (result == null) {
            if (initializer != null) {
                result = initializer.evaluate();
            } else {
                result = defaultValue;
            }

            if (result != null) {
                set(attributeHolder, result);
            }
        }
        
        return result;
    }

    /**
     * Get attribute value, stored on the {@link AttributeStorage}.
     *
     * @param storage {@link AttributeStorage}.
     * @return attribute value
     */
    public T get(final AttributeStorage storage) {
        final AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            return get(holder);
        }

        final T result;
        if (initializer != null) {
            result = initializer.evaluate();
        } else {
            result = defaultValue;
        }

        if (result != null) {
            set(storage, result);
        }

        return result;
    }

    /**
     * Set attribute value, stored on the {@link AttributeHolder}.
     *
     * @param attributeHolder {@link AttributeHolder}.
     * @param value attribute value to set.
     */
    public void set(final AttributeHolder attributeHolder, final T value) {
        final IndexedAttributeAccessor indexedAccessor =
                attributeHolder.getIndexedAttributeAccessor();
        
        if (indexedAccessor != null) {
            indexedAccessor.setAttribute(attributeIndex, value);
        } else {
            attributeHolder.setAttribute(name, value);
        }
    }

    /**
     * Set attribute value, stored on the {@link AttributeStorage}.
     *
     * @param storage {@link AttributeStorage}.
     * @param value attribute value to set.
     */
    public void set(final AttributeStorage storage, final T value) {
        set(storage.getAttributes(), value);
    }

    /**
     * Remove attribute value, stored on the {@link AttributeHolder}.
     *
     * @param attributeHolder {@link AttributeHolder}.
     */
    public T remove(final AttributeHolder attributeHolder) {
        final T result = weakGet(attributeHolder);
        
        if (result != null) {
            set(attributeHolder, null);
        }
        
        return result;
    }

    /**
     * Remove attribute value, stored on the {@link AttributeStorage}.
     *
     * @param storage {@link AttributeStorage}.
     */
    public T remove(final AttributeStorage storage) {
        final AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            return remove(holder);
        }

        return null;
    }
    
    /**
     * Checks if this attribute is set on the {@link AttributeHolder}.
     * Returns <tt>true</tt>, if attribute is set, of <tt>false</tt> otherwise.
     * 
     * @param attributeHolder {@link AttributeHolder}.
     * 
     * @return <tt>true</tt>, if attribute is set, of <tt>false</tt> otherwise.
     */
    public boolean isSet(final AttributeHolder attributeHolder) {
        return weakGet(attributeHolder) != null;
    }

    /**
     * Checks if this attribute is set on the {@link AttributeStorage}.
     * Returns <tt>true</tt>, if attribute is set, of <tt>false</tt> otherwise.
     *
     * @param storage {@link AttributeStorage}.
     *
     * @return <tt>true</tt>, if attribute is set, of <tt>false</tt> otherwise.
     */
    public boolean isSet(final AttributeStorage storage) {
        final AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            return isSet(holder);
        }

        return false;
    }
    
    /**
     * Return attribute name, which is used as attribute key on
     * non-indexed {@link AttributeHolder}s.
     * 
     * @return attribute name.
     */
    public String name() {
        return name;
    }

    /**
     * Return attribute name, which is used as attribute key on
     * indexed {@link AttributeHolder}s.
     *
     * @return attribute indexed.
     */
    public int index() {
        return attributeIndex;
    }
    
    @SuppressWarnings("unchecked")
    private T weakGet(final AttributeHolder attributeHolder) {
        final IndexedAttributeAccessor indexedAccessor =
                attributeHolder.getIndexedAttributeAccessor();
        
        final T result;
        if (indexedAccessor != null) {
            result = (T) indexedAccessor.getAttribute(attributeIndex);
        } else {
            result = (T) attributeHolder.getAttribute(name);
        }
        
        return result;
    }
}
