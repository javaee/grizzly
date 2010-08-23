/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import com.sun.grizzly.util.buf.B2CConverter;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.CharChunk;
import junit.framework.TestCase;

/**
 * B2C NIO test
 *
 * @author Alexey Stashok
 */
public class B2CConverterTest extends TestCase {

    public void testOneByOne() throws Exception {
        char jp[] = "\u3068\u4eba\u6587".toCharArray();
        char[] text = new char[jp.length * 16384];
        for(int i=0; i<text.length; i++) {
            text[i] = jp[i % jp.length];
        }

        String s = new String(text);
        byte[] src = s.getBytes("UTF-8");

        B2CConverter converter = new B2CConverter("UTF-8");
        ByteChunk bc = new ByteChunk();
        bc.setBytes(src, 0, src.length);

        CharChunk cc = new CharChunk(16);
        cc.setLimit(src.length);

        final int bcStart = bc.getStart();
        final int bcEnd = bc.getEnd();

        for(int i=bcStart + 1; i<=bcEnd; i++) {
            bc.setEnd(i);
            converter.convert(bc, cc, src.length * 2);
            bc.setOffset(bc.getEnd());
        }

        assertEquals(s, cc.toString());
    }
}
