/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.DefaultAttributeBuilder;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Testing {@link Attribute}s.
 * 
 * @author Alexey Stashok
 */
@RunWith(Parameterized.class)
public class AttributesTest {
    
    @Parameterized.Parameters
    public static Collection<Object[]> isSafe() {
        return Arrays.asList(new Object[][]{
                    {Boolean.FALSE},
                    {Boolean.TRUE}
                });
    }
    
    private final boolean isSafe;
    
    public AttributesTest(final boolean isSafe) {
        this.isSafe = isSafe;
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testAttributes() {
        AttributeBuilder builder = new DefaultAttributeBuilder();
        AttributeHolder holder = isSafe
                ? builder.createSafeAttributeHolder()
                : builder.createUnsafeAttributeHolder();
        
        final int attrCount = 10;
        
        final Attribute[] attrs = new Attribute[attrCount];
        
        for (int i = 0; i < attrCount; i++) {
            attrs[i] = builder.createAttribute("attribute-" + i);
        }
        
        // set values
        for (int i = 0; i < attrCount; i++) {
            attrs[i].set(holder, "value-" + i);
        }
        
        // check values
        for (int i = 0; i < attrCount; i++) {
            assertTrue(attrs[i].isSet(holder));
            assertEquals("value-" + i, attrs[i].get(holder));
        }
        
        assertNotNull(attrs[0].remove(holder));
        assertFalse(attrs[0].isSet(holder));
        assertNull(attrs[0].remove(holder));
        assertNull(attrs[0].get(holder));
        
        assertNotNull(attrs[attrCount - 1].remove(holder));
        assertFalse(attrs[attrCount - 1].isSet(holder));
        assertNull(attrs[attrCount - 1].remove(holder));
        assertNull(attrs[attrCount - 1].get(holder));
        
        
        final Set<String> attrNames = holder.getAttributeNames();
        assertEquals(attrCount - 2, attrNames.size());
        
        for (int i = 1; i < attrCount - 1; i++) {
            assertTrue(attrNames.contains(attrs[i].name()));
        }
    }
}
