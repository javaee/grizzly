/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.http;

import java.util.Set;
import junit.framework.TestCase;

/**
 * Test the {@link HttpRequestPacket} attributes.
 * 
 * @author Alexey Stashok
 */
public class RequestAttributesTest extends TestCase {

    public void testAttributes() throws Exception {
        final String attrName = "testAttr";
        final String value1 = "value1";
        final String value2 = "value2";
        
        HttpRequestPacket packet = HttpRequestPacket.builder().build();

        assertNull(packet.getAttribute(attrName));
        
        packet.setAttribute(attrName, value1);
        assertEquals(value1, packet.getAttribute(attrName));
        
        packet.setAttribute(attrName, value2);
        assertEquals(value2, packet.getAttribute(attrName));
        
        final Set<String> attributeNames = packet.getAttributeNames();
        assertTrue(attributeNames.contains(attrName));
        try {
            attributeNames.remove(attrName);
            fail("we shouldn't be able to remove the attribute name from the set");
        } catch (UnsupportedOperationException e) {
            // readonly set, so this is expected
        }
        
        packet.removeAttribute(attrName);
        assertNull(packet.getAttribute(attrName));        
    }
    
    public void testReadOnlyAttributes() throws Exception {
        final String attrName =
                HttpRequestPacket.READ_ONLY_ATTR_PREFIX + "testAttr";
        final String originalValue = "original value";
        
        HttpRequestPacket packet = HttpRequestPacket.builder().build();

        assertNull(packet.getAttribute(attrName));
        
        packet.setAttribute(attrName, originalValue);
        assertEquals(originalValue, packet.getAttribute(attrName));
        
        packet.setAttribute(attrName, "another value");
        assertEquals(originalValue, packet.getAttribute(attrName));
        
        packet.removeAttribute(attrName);
        assertEquals(originalValue, packet.getAttribute(attrName));
        
        // the service/readonly attr shouldn't be returned in getAttributeNames()
        assertFalse(packet.getAttributeNames().contains(attrName));
    }
}
