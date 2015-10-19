/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import junit.framework.TestCase;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.MimeHeaders;

public class HttpResponsePacketTest extends TestCase {
    
    private HttpResponsePacket response;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        response = HttpResponsePacket.builder(
                HttpRequestPacket.builder().uri("/").protocol(Protocol.HTTP_1_1).build())
                .build();
    }

    public void testSpecialHeadersTest() throws Exception {
        
        assertFalse(response.containsHeader("Content-Length"));
        assertFalse(response.containsHeader("Content-type"));
        assertFalse(response.containsHeader(Header.ContentLength));
        assertFalse(response.containsHeader(Header.ContentType));
        
        assertNull(response.getHeader("Content-Length"));
        assertNull(response.getHeader("Content-type"));
        assertNull(response.getHeader(Header.ContentLength));
        assertNull(response.getHeader(Header.ContentType));
        
        response.setHeader("Content-Length", "1");
        assertEquals(1L, response.getContentLength());
        assertEquals("1", response.getHeader("Content-length"));
        assertTrue(response.containsHeader("content-length"));
        response.setHeader(Header.ContentLength, "2");
        assertEquals(2L, response.getContentLength());
        assertEquals("2", response.getHeader(Header.ContentLength));
        assertTrue(response.containsHeader(Header.ContentLength));
        
        response.addHeader("content-Length", "3");
        assertEquals(3L, response.getContentLength());
        assertEquals("3", response.getHeader("Content-length"));
        response.addHeader(Header.ContentLength, "4");
        assertEquals(4L, response.getContentLength());
        assertEquals("4", response.getHeader(Header.ContentLength));

        response.setHeader("Content-Type", "text/plain");
        assertEquals("text/plain", response.getContentType());
        assertEquals("text/plain", response.getHeader("Content-type"));
        assertTrue(response.containsHeader("content-Type"));
        response.setHeader(Header.ContentType, "text/xml");
        assertEquals("text/xml", response.getContentType());
        assertEquals("text/xml", response.getHeader(Header.ContentType));
        assertTrue(response.containsHeader(Header.ContentType));

        response.addHeader("content-Type", "text/plain");
        assertEquals("text/plain", response.getContentType());
        assertEquals("text/plain", response.getHeader("Content-type"));
        response.addHeader(Header.ContentType, "text/xml");
        assertEquals("text/xml", response.getContentType());
        assertEquals("text/xml", response.getHeader(Header.ContentType));
    }

    /**
     * http://java.net/jira/browse/GRIZZLY-1295
     * "NullPointer while trying to get next value via ValuesIterator in MimeHeaders"
     */
    public void testMimeHeaderIterators() {
        response.setHeader("Content-Length", "1");
        response.setHeader("Content-Type", "text/plain");
        response.setHeader("Host", "localhost");
        
        // Headers iterator test
        boolean removed = false;
        
        final MimeHeaders headers = response.getHeaders();
        for (Iterator<String> it = headers.names().iterator(); it.hasNext();) {
            it.next();
            
            if (!removed) {
                it.remove();
                removed = true;
            }
        }
        
        removed = false;
        
        final String multiValueHeader = "Multi-Value";
        
        response.addHeader(multiValueHeader, "value-1");
        response.addHeader(multiValueHeader, "value-2");
        response.addHeader(multiValueHeader, "value-3");
        
        for (Iterator<String> it = headers.values(multiValueHeader).iterator(); it.hasNext();) {
            it.next();
            
            if (!removed) {
                it.remove();
                removed = true;
            }
        }
    }
    
}
