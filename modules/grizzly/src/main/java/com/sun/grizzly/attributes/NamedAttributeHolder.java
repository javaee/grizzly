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

package com.sun.grizzly.attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@link AttributeHolder} implementation, which doesn't support indexed access
 * to {@link Attribute}s.
 *
 * @see AttributeHolder
 * @see IndexedAttributeHolder
 * 
 * @author Alexey Stashok
 */
public class NamedAttributeHolder implements AttributeHolder {
    
    protected Map<String, Object> attributesMap;
    protected DefaultAttributeBuilder attributeBuilder;

    public NamedAttributeHolder(AttributeBuilder attributeBuilder) {
        this.attributeBuilder = (DefaultAttributeBuilder) attributeBuilder;
        attributesMap = new HashMap<String, Object>();
    }
    
    /**
     * {@inheritDoc}
     */
    public Object getAttribute(String name) {
        return attributesMap.get(name);
    }
    
    /**
     * {@inheritDoc}
     */
    public void setAttribute(String name, Object value) {
        Attribute attribute = attributeBuilder.getAttributeByName(name);
        if (attribute == null) {
            attribute = attributeBuilder.createAttribute(name);
        }

        attributesMap.put(name, value);
    }
    
    /**
     * {@inheritDoc}
     */
    public Object removeAttribute(String name) {
        return attributesMap.remove(name);
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getAttributeNames() {
        return attributesMap.keySet();
    }


    /**
     * {@inheritDoc}
     */
    public void clear() {
        attributesMap.clear();
    }

    /**
     * {@inheritDoc}
     */
    public AttributeBuilder getAttributeBuilder() {
        return attributeBuilder;
    }

    /**
     * Always returns null, as <tt>NamedAttributeHolder</tt> doesn't support
     * indexing.
     * 
     * @return <tt>null</tt>
     */
    public IndexedAttributeAccessor getIndexedAttributeAccessor() {
        return null;
    }
}
