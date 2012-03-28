/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.core;

import java.io.CharConversionException;
import java.net.URLEncoder;
import junit.framework.TestCase;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.Constants;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.RequestURIRef;
import org.glassfish.grizzly.memory.Buffers;

/**
 * Test the {@link RequestURIRef} decoding.
 * @author Alexey Stashok
 */
public class RequestURITest extends TestCase {
    private final String rus = "\u043F\u0440\u0438\u0432\u0435\u0442\u043C\u0438\u0440";
    
    private final String rusEncoded;

    public RequestURITest() throws Exception {
        rusEncoded = URLEncoder.encode(rus, "UTF-8");
    }

    public void testBufferChunk() throws Exception {
        final String url = "http://localhost:4848/management/domain/resources/jdbc-resource/" +
                rusEncoded + "/jdbc%2F__TimerPool.xml";
        
        Buffer buffer = Buffers.wrap(null, url);
        
        RequestURIRef rur = new RequestURIRef();
        DataChunk originalURIDataChunk = rur.getOriginalRequestURIBC();
        assertTrue(originalURIDataChunk.isNull());

        rur.init(buffer, 0, buffer.capacity());

        try {
            rur.getDecodedRequestURIBC(false, false);
            fail("Exception must be thrown");
        } catch (CharConversionException e) {
        }

        // Try wrong charset
        DataChunk decodedDC = rur.getDecodedRequestURIBC(true, false,
                Constants.DEFAULT_HTTP_CHARSET);
        assertEquals(DataChunk.Type.Chars, decodedDC.getType());
        // there shouldn't be our decoded word
        assertEquals(-1, decodedDC.toString().indexOf(rus));

        // Try correct charset
        decodedDC = rur.getDecodedRequestURIBC(true, false, Charsets.UTF8_CHARSET);
        assertEquals(DataChunk.Type.Chars, decodedDC.getType());
        // there should be our decoded word
        assertTrue(decodedDC.toString().indexOf(rus) >= 0);

        // One more time the same
        decodedDC = rur.getDecodedRequestURIBC(true, false, Charsets.UTF8_CHARSET);
        assertEquals(DataChunk.Type.Chars, decodedDC.getType());
        // there should be our decoded word
        assertTrue(decodedDC.toString().indexOf(rus) >= 0);

        // there shouldn't be our decoded word
        assertTrue(rur.getURI().indexOf(rus) < 0);

        // Original should be the same
        assertEquals(url, rur.getOriginalRequestURIBC().toString());
    }

    public void testURIChangeTrigger() {
        final String url = "http://localhost:4848/management/domain/resources/jdbc-resource/" +
                rusEncoded + "/jdbc%2F__TimerPool.xml";
        
        Buffer buffer = Buffers.wrap(null, url);

        RequestURIRef rur = new RequestURIRef();
        rur.init(buffer, 0, buffer.capacity());

        final DataChunk originalRequestURIBC = rur.getOriginalRequestURIBC();
        final DataChunk actualRequestURIBC = rur.getRequestURIBC();

        assertTrue(originalRequestURIBC.getBufferChunk().getBuffer() ==
                actualRequestURIBC.getBufferChunk().getBuffer());

        final BufferChunk actualBufferChunk = actualRequestURIBC.getBufferChunk();
        actualBufferChunk.notifyDirectUpdate();
        actualBufferChunk.delete(actualBufferChunk.getStart(), actualBufferChunk.getStart() + 7);

        assertFalse(originalRequestURIBC.getBufferChunk().getBuffer() ==
                actualRequestURIBC.getBufferChunk().getBuffer());

        assertEquals(url, originalRequestURIBC.toString());
        assertEquals(url.substring(7), actualRequestURIBC.toString());
    }
    
    public void testBackSlashes() throws Exception {
        final String windowsPath = "giveme\\windows\\file";
        final String url = "http://localhost:4848/" + windowsPath;
        final String normalizedWindowsPath = "giveme/windows/file";
        
        final Buffer buffer = Buffers.wrap(null, url);

        RequestURIRef rur = new RequestURIRef();

        rur.init(buffer, 0, buffer.capacity());

        try {
            rur.getDecodedRequestURIBC(false, false);
            fail("Exception must be thrown");
        } catch (CharConversionException e) {
        }

        DataChunk decodedDC = rur.getDecodedRequestURIBC(false, true);
        // there should be our decoded word
        assertTrue(decodedDC.toString().indexOf(normalizedWindowsPath) >= 0);
        
    }    
}
