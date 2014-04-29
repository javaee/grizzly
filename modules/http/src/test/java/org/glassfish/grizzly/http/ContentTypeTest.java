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

        assertEquals("text/plain",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
        ct.reset();
        
        ct.set("text/plain;charset=UTF-8");
        assertEquals("text/plain", ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        assertEquals("text/plain;charset=UTF-8",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
        ct.reset();

        ct.set("text/plain;charset=UTF-8;abc=xyz");
        assertEquals("text/plain;abc=xyz", ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        assertEquals("Incorrect value=" + new String(ct.getByteArray(), 0,
                                                     ct.getArrayLen(),
                                                     Charsets.ASCII_CHARSET),
                     "text/plain;charset=UTF-8;abc=xyz",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
        
        ct.setMimeType("text/html");
        assertEquals("text/html", ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        assertEquals("text/html;charset=UTF-8",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));

        ct.setCharacterEncoding("UTF-16");
        assertEquals("text/html", ct.getMimeType());
        assertEquals("UTF-16", ct.getCharacterEncoding());
        
        assertEquals("text/html;charset=UTF-16",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
    
        ct.setCharacterEncoding(null);
        assertEquals("text/html", ct.getMimeType());
        assertEquals(null, ct.getCharacterEncoding());
        
        assertEquals("text/html",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));

        ct.reset();

        ct.set("text/html;charset=Shift_Jis");
        ct.set("text/xml");
        assertEquals("text/xml;charset=Shift_Jis", ct.get());
        assertNotNull(ct.getCharacterEncoding());
        
        ct.reset();
        
        ContentType prepared = ContentType.newContentType("application/json;charset=UTF-8").prepare();
        ct.set(prepared);
        
        assertEquals("application/json;charset=UTF-8",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
        assertEquals("application/json", ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        prepared = ContentType.newContentType("text/plain", "UTF-16");
        ct.set(prepared);
        
        assertEquals("text/plain;charset=UTF-16",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
        assertEquals("text/plain", ct.getMimeType());
        assertEquals("UTF-16", ct.getCharacterEncoding());

        ct.reset();
        
        final String longCt = "text/plain;aaa=aaa1;bbb=bbb1;charset=UTF-8;ccc=ccc1;ddd=ddd1;eee=eee1;fff=fff1";
        final String longMt = longCt.replace("charset=UTF-8;", "");
        
        // test long content-type
        ct.set(longCt);
        
        assertEquals(longCt,
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
        assertEquals(longMt, ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());
        
        ct.reset();

        // test long content-type
        ct.setCharacterEncoding("charset=Shift_Jis");
        ct.set(longCt);
        
        assertEquals(longMt + ";charset=UTF-8",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
        assertEquals(longMt, ct.getMimeType());
        assertEquals("UTF-8", ct.getCharacterEncoding());

        ct.reset();
        
        ct.setMimeType(longMt);
        ct.setCharacterEncoding("UTF-16");
        
        assertEquals(longMt + ";charset=UTF-16",
                     new String(ct.getByteArray(), 0, ct.getArrayLen(),
                                Charsets.ASCII_CHARSET));
        assertEquals(longMt, ct.getMimeType());
        assertEquals("UTF-16", ct.getCharacterEncoding());
        
    }
}
