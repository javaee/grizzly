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

package org.glassfish.grizzly.attributes;

/** 
 * Class used to define dynamic typed attributes on {@link AttributeHolder}
 * instances.
 * Storing attribute values in {@link AttributeHolder} has two advantage
 * comparing to Map storage:
 *      1) <tt>Attribute</tt> value is typed, and could be checked at
 *         compiletime.
 *      2) Access to <tt>Attribute</tt> value, if used with
 *         {@link IndexedAttributeHolder}, could be as fast as access to array.
 */
public class Attribute<T> {
    /**
     * AttributeBuilder, which was used to create this attribute
     */
    private AttributeBuilder builder;
    /**
     * Attribute name
     */
    private String name;
    /**
     * Attribute initializer, which will be called, if attribute is not set.
     */
    private NullaryFunction<T> initializer;
    /**
     * Attribute default value, which will be used, if attribute is not set.
     */
    private T defaultValue;
    /**
     * Attribute index in AttributeBuilder
     */
    private int attributeIndex;

    @Override
    public String toString() {
        return "Attribute[" + name + ":" + attributeIndex + "]";
    }

    protected Attribute(AttributeBuilder builder, String name, T defaultValue) {
        this.builder = builder;
        this.name = name;
        this.initializer = null;
        this.defaultValue = defaultValue;
    }

    protected Attribute(AttributeBuilder builder, String name,
            NullaryFunction<T> initializer) {
        this.builder = builder;
        this.name = name;
        this.initializer = initializer;
        this.defaultValue = null;
    }

    /**
     * Get attribute value, stored on the {@link AttributeHolder}.
     * 
     * @param attributeHolder {@link AttributeHolder}.
     * @return attribute value
     */
    public T get(AttributeHolder attributeHolder) {
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
    public T get(AttributeStorage storage) {
        AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            return get(holder);
        }

        T result;
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
    public void set(AttributeHolder attributeHolder, T value) {
        IndexedAttributeAccessor indexedAccessor = 
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
    public void set(AttributeStorage storage, T value) {
        set(storage.obtainAttributes(), value);
    }

    /**
     * Remove attribute value, stored on the {@link AttributeHolder}.
     *
     * @param attributeHolder {@link AttributeHolder}.
     */
    public T remove(AttributeHolder attributeHolder) {
        T result = weakGet(attributeHolder);
        
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
    public T remove(AttributeStorage storage) {
        AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            remove(holder);
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
    public boolean isSet(AttributeHolder attributeHolder) {
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
    public boolean isSet(AttributeStorage storage) {
        AttributeHolder holder = storage.getAttributes();
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
    
    /**
     * Assign integer index to {@link Attribute}, to make access to its value
     * as fast as array[index].
     * 
     * @param index attribute index.
     */
    protected void setIndex(int index) {
        attributeIndex = index;
    }
    
    private T weakGet(AttributeHolder attributeHolder) {
        T result = null;
        IndexedAttributeAccessor indexedAccessor = 
                attributeHolder.getIndexedAttributeAccessor();
        
        if (indexedAccessor != null) {
            result = (T) indexedAccessor.getAttribute(attributeIndex);
        } else {
            result = (T) attributeHolder.getAttribute(name);
        }
        
        return result;
    }
}