/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.UnsupportedCharsetException;
import junit.framework.TestCase;
import org.glassfish.grizzly.utils.Charsets;

/**
 * {@link Charsets} test.
 * 
 * @author Alexey Stashok
 */
public class CharsetsTest extends TestCase{
    private final Charset[] charsets = new Charset[] {
        Charsets.UTF8_CHARSET, Charset.forName("UTF-16"),
        Charset.forName("UTF-32"), Charsets.ASCII_CHARSET,
        Charset.forName("GB2312")
    };
    
    public void testDecodersCache() {
        final CharsetDecoder decoder0 =
                Charsets.getCharsetDecoder(charsets[0]);
        final CharsetDecoder decoder1 =
                Charsets.getCharsetDecoder(charsets[1]);
        final CharsetDecoder decoder2 =
                Charsets.getCharsetDecoder(charsets[2]);
        final CharsetDecoder decoder3 =
                Charsets.getCharsetDecoder(charsets[3]);
        
        assertTrue("Decoder is not the same",
                decoder0 == Charsets.getCharsetDecoder(charsets[0]));
        assertTrue("Decoder is not the same",
                decoder1 == Charsets.getCharsetDecoder(charsets[1]));
        assertTrue("Decoder is not the same",
                decoder2 == Charsets.getCharsetDecoder(charsets[2]));
        assertTrue("Decoder is not the same",
                decoder3 == Charsets.getCharsetDecoder(charsets[3]));
        
        final CharsetDecoder decoder4 =
                Charsets.getCharsetDecoder(charsets[4]);

        assertTrue("Decoder should be different", decoder0 != Charsets.getCharsetDecoder(charsets[0]));

        assertTrue("Decoder is not the same",
                decoder4 == Charsets.getCharsetDecoder(charsets[4]));
    }
    
    public void testEncodersCache() {
        final CharsetEncoder encoder0 =
                Charsets.getCharsetEncoder(charsets[0]);
        final CharsetEncoder encoder1 =
                Charsets.getCharsetEncoder(charsets[1]);
        final CharsetEncoder encoder2 =
                Charsets.getCharsetEncoder(charsets[2]);
        final CharsetEncoder encoder3 =
                Charsets.getCharsetEncoder(charsets[3]);
        
        assertTrue("Encoder is not the same",
                encoder0 == Charsets.getCharsetEncoder(charsets[0]));
        assertTrue("Encoder is not the same",
                encoder1 == Charsets.getCharsetEncoder(charsets[1]));
        assertTrue("Encoder is not the same",
                encoder2 == Charsets.getCharsetEncoder(charsets[2]));
        assertTrue("Encoder is not the same",
                encoder3 == Charsets.getCharsetEncoder(charsets[3]));
        
        final CharsetEncoder encoder4 =
                Charsets.getCharsetEncoder(charsets[4]);

        assertTrue("Encoder should be different", encoder0 != Charsets.getCharsetEncoder(charsets[0]));

        assertTrue("Encoder is not the same",
                encoder4 == Charsets.getCharsetEncoder(charsets[4]));
    }
    
    public void testPreloadedCharsets() {
        Charsets.preloadAllCharsets();
        try {
            Charsets.lookupCharset("NON-EXISTED-CHARSET");
        } catch (UnsupportedCharsetException e) {
            StackTraceElement[] elements = e.getStackTrace();
            assertEquals("Exception is not thrown from Charsets class",
                    elements[0].getClassName(), Charsets.class.getName());
        }
        
        Charsets.drainAllCharsets();
        
        try {
            Charsets.lookupCharset("NON-EXISTED-CHARSET");
        } catch (UnsupportedCharsetException e) {
            StackTraceElement[] elements = e.getStackTrace();
            assertFalse("Exception is unexpectedly thrown from Charsets class",
                    elements[0].getClassName().equals(Charsets.class.getName()));
        }
        
    }    
}
