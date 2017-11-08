/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016-2017 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.Charsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class ChunkTest {

    private final static String CONTENT = "    one fish,\ttwo fish, \rred fish,  \nblue fish";
    private final static char[] CONTENT_CHARS = CONTENT.toCharArray();
    private final static byte[] CONTENT_BYTES = CONTENT.getBytes(Charsets.UTF8_CHARSET);
    private final static String TRIM1 = "one fish,\ttwo fish, \rred fish,  \nblue fish";
    private final static String TRIM2 = "two fish, \rred fish,  \nblue fish";
    private final static String TRIM3 = "red fish,  \nblue fish";
    private final static String TRIM4 = "blue fish";


    // ---------------------------------------------------------------------------------------------------- Test Methods


    @Test
    public void testTrimLeft() throws Exception {
        DataChunk dc = DataChunk.newInstance();
        dc.setChars(CONTENT_CHARS, 0, CONTENT_CHARS.length);
        trimAndAssertCorrect(dc);

        dc.setBytes(CONTENT_BYTES, 0, CONTENT_BYTES.length);
        trimAndAssertCorrect(dc);

        dc.setBuffer(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, CONTENT_BYTES));
        trimAndAssertCorrect(dc);
    }


    // ------------------------------------------------------------------------------------------------- Private Methods


    private static void trimAndAssertCorrect(final DataChunk dc) {
        assertEquals(CONTENT, dc.toString(Charsets.UTF8_CHARSET));

        dc.trimLeft();
        assertEquals(TRIM1, dc.toString(Charsets.UTF8_CHARSET));

        dc.setStart(dc.getStart() + dc.indexOf(',', 0) + 1);
        dc.trimLeft();
        assertEquals(TRIM2, dc.toString(Charsets.UTF8_CHARSET));

        dc.setStart(dc.getStart() + dc.indexOf(',', 0) + 1);
        dc.trimLeft();
        assertEquals(TRIM3, dc.toString(Charsets.UTF8_CHARSET));

        dc.setStart(dc.getStart() + dc.indexOf(',', 0) + 1);
        dc.trimLeft();
        assertEquals(TRIM4, dc.toString(Charsets.UTF8_CHARSET));
    }
}
