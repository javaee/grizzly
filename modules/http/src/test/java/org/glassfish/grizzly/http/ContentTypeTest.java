/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import java.util.Arrays;
import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.utils.Charsets;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test {@link ContentType}
 */
public class ContentTypeTest {
    @Test
    public void testContentType() throws Exception {
        final ContentType.SettableContentType ct =
                ContentType.newSettableContentType();
        
        ct.set("text/plain");
        assertEquals("text/plain", ct.getMimeType());
        assertNull(ct.getCharacterEncoding());
        
        assertArrayEquals("text/plain".getBytes(Charsets.ASCII_CHARSET), ct.getByteArray());
        ct.reset();
        
        ct.set("text/plain;charset=UTF-8");
        assertEquals("text/plain", ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        assertArrayEquals("text/plain;charset=UTF-8".getBytes(Charsets.ASCII_CHARSET), ct.getByteArray());
        ct.reset();

        ct.set("text/plain;charset=UTF-8;abc=xyz");
        assertEquals("text/plain;abc=xyz", ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        assertArrayEquals("Incorrect value=" + new String(ct.getByteArray()),
                "text/plain;charset=UTF-8;abc=xyz".getBytes(Charsets.ASCII_CHARSET), ct.getByteArray());
        
        ct.setMimeType("text/html");
        assertEquals("text/html", ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        assertArrayEquals("text/html;charset=UTF-8".getBytes(Charsets.ASCII_CHARSET), ct.getByteArray());

        ct.setCharacterEncoding("UTF-16");
        assertEquals("text/html", ct.getMimeType());
        assertEquals("UTF-16", ct.getCharacterEncoding());
        
        assertArrayEquals("text/html;charset=UTF-16".getBytes(Charsets.ASCII_CHARSET), ct.getByteArray());
    
        ct.setCharacterEncoding(null);
        assertEquals("text/html", ct.getMimeType());
        assertEquals(null, ct.getCharacterEncoding());
        
        assertArrayEquals("text/html".getBytes(Charsets.ASCII_CHARSET), ct.getByteArray());

        ct.reset();

        ct.set("text/html;charset=Shift_Jis");
        ct.set("text/xml");
        assertEquals("text/xml;charset=Shift_Jis", ct.get());
        assertNotNull(ct.getCharacterEncoding());
        
        ct.reset();
        
        ContentType prepared = ContentType.newContentType("application/json;charset=UTF-8").prepare();
        ct.set(prepared);
        
        assertArrayEquals("application/json;charset=UTF-8".getBytes(Charsets.ASCII_CHARSET), ct.getByteArray());
        assertEquals("application/json", ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        prepared = ContentType.newContentType("text/plain", "UTF-16");
        ct.set(prepared);
        
        assertTrue(Arrays.equals("text/plain;charset=UTF-16".getBytes(Charsets.ASCII_CHARSET), ct.getByteArray()));
        assertEquals("text/plain", ct.getMimeType());
        assertEquals("UTF-16", ct.getCharacterEncoding());
        
    }
}
