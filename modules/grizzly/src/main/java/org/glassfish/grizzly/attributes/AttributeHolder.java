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

package org.glassfish.grizzly.attributes;

import java.util.Set;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * Interface declares common functionality for objects, which have associated
 * {@link Attribute}s.
 *
 * @see IndexedAttributeHolder
 * @see NamedAttributeHolder
 * 
 * @author Alexey Stashok
 */
public interface AttributeHolder {
    /**
     * Remove a name/value object.
     * @param name - name of an attribute
     * @return  attribute which has been removed
     */
    Object removeAttribute(String name);
    
    
    /**
     * Set a name/value object.
     * @param name - name of an attribute
     * @param value - value of named attribute
     */
    void setAttribute(String name, Object value);

    
    /**
     * Return an object based on a name.
     * @param name - name of an attribute
     * @return - attribute value for the <tt>name</tt>, null if <tt>name</tt>
     *           does not exist in <tt>attributes</tt>
     */
    Object getAttribute(String name);

    /**
     * Return an object based on a name.
     * @param name - name of an attribute
     * @param initializer the initializer to be used to assign a default attribute value,
     *          in case it hasn't been assigned
     * @return - attribute value for the <tt>name</tt>, null if <tt>name</tt>
     *           does not exist in <tt>attributes</tt>
     * 
     * @since 2.3.18
     */
    Object getAttribute(String name, NullaryFunction initializer);

    /**
     * Return a {@link Set} of attribute names.
     * 
     * @return - {@link Set} of attribute names
     */
    Set<String> getAttributeNames();
    
    
    /**
     * Clear all the attributes.
     */
    void clear();
    
    /**
     * Recycle <tt>AttributeHolder</tt>
     */
    void recycle();
    
    /**
     * Get AttributeBuilder, associated with this holder
     * @return AttributeBuilder
     */
    AttributeBuilder getAttributeBuilder();
    
    
    /**
     * If AttributeHolder supports attribute access by index - it will return
     * an {@link IndexedAttributeAccessor}, which will make {@link Attribute}
     * access as fast as access to array element.
     * 
     * @return {@link IndexedAttributeAccessor}.
     */
    IndexedAttributeAccessor getIndexedAttributeAccessor();

    /**
     * Copies attributes from this <tt>AttributeHolder</tt> to the dstAttributes.
     * 
     * @param dstAttributes 
     */
    void copyTo(AttributeHolder dstAttributes);

    /**
     * Copies attributes from the srcAttributes to this <tt>AttributeHolder</tt>
     * 
     * @param srcAttributes 
     */
    void copyFrom(AttributeHolder srcAttributes);
}
