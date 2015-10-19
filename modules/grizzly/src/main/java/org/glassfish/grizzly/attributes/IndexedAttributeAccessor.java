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

import org.glassfish.grizzly.utils.NullaryFunction;

/** 
 * The interface declares, that {@link AttributeHolder} supports
 * indexed {@link Attribute} access.
 *
 * @see AttributeHolder
 */
public interface IndexedAttributeAccessor {

    /** 
     * Internal method for dynamic attribute support.
     * Return the value of the attribute by index.
     * @param index the attribute index
     * @return the value of the attribute by index
     */
    Object getAttribute(int index);

    /** 
     * Internal method for dynamic attribute support.
     * Return the value of the attribute by index.  If
     * the attribute with such index is not set, set it to the
     * default value, using the <tt>initializer</tt>, and return the default.
     * @param index the attribute index
     * @param initializer the default value {@link NullaryFunction}
     * @return the value of the attribute by index
     * @since 2.3.18
     */
    Object getAttribute(int index, NullaryFunction initializer);
    
    /** 
     * Internal method for dynamic attribute support.
     * Set the attribute with the index to value.
     * @param index the attribute index
     * @param value the value
     */
    void setAttribute(int index, Object value);
    
    /** 
     * Internal method for dynamic attribute support.
     * Removes the attribute with the index and returns its previous value.
     * 
     * @param index the attribute index
     * @return the previous value associated with the attribute
     * @since 2.3.18
     */
    Object removeAttribute(int index);
    
}
