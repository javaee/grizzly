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
 * Class used to define dynamic attributes on AttributedObject instances.
 * Note that T cannot be a generic type, due to problems with
 * Class<T> when T is a generic.  To work around this problem,
 * simply create an interface that extends the generic type
 * (you are programming to interfaces, right?).
 */
public class Attribute<T> {
    private AttributeBuilder builder;
    private String name;
    private NullaryFunction<T> initializer;
    private T defaultValue;
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

    public void set(AttributeHolder attributeHolder, T arg) {
        IndexedAttributeAccessor indexedAccessor = 
                attributeHolder.getIndexedAttributeAccessor();
        
        if (indexedAccessor != null) {
            indexedAccessor.setAttribute(attributeIndex, arg);
        } else {
            attributeHolder.setAttribute(name, arg);
        }
    }

    public T remove(AttributeHolder attributeHolder) {
        T result = weakGet(attributeHolder);
        
        if (result != null) {
            set(attributeHolder, null);
        }
        
        return result;    }

    public boolean isSet(AttributeHolder attributeHolder) {
        return weakGet(attributeHolder) != null;
    }

    public String name() {
        return name;
    }

    public int index() {
        return attributeIndex;
    }
    
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