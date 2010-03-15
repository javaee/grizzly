/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.http.util;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.http.util.ByteChunk;
import com.sun.grizzly.http.util.HexUtils;

/**
 *
 * @author oleksiys
 */
public class URLDecoder {
    public static void decode(BufferChunk bufferChunk) {
        decode(bufferChunk, true);
    }
    
    /**
     * URLDecode the {@link ByteChunk}
     */
    public static void decode(final BufferChunk bufferChunk,
            final boolean allowEncodedSlash) {

        boolean onContentChanged = false;
        
        final Buffer buffer = bufferChunk.getBuffer();
        int start = bufferChunk.getStart();
        int end = bufferChunk.getEnd();

        int idx = start;
        for (int j = start; j < end; j++, idx++) {
            final byte b = buffer.get(j);

            if (b == '+') {
                buffer.put(idx , (byte) ' ');
            } else if (b != '%') {
                buffer.put(idx, b);
            } else {
                // read next 2 digits
                if (j + 2 >= end) {
                    throw new IllegalStateException("Unexpected termination");
                }
                byte b1 = buffer.get(j + 1);
                byte b2 = buffer.get(j + 2);
                
                if (!HexUtils.isHexDigit(b1) || !HexUtils.isHexDigit(b2)) {
                    throw new IllegalStateException("isHexDigit");
                }

                j += 2;
                int res = x2c(b1, b2);
                if (!allowEncodedSlash && (res == '/')) {
                    throw new IllegalStateException("noSlash");
                }
                buffer.put(idx, (byte) res);
            }
        }

        bufferChunk.setEnd(idx);

        if (onContentChanged) {
            bufferChunk.onContentChanged();
        }
        return;
    }

    private static int x2c(byte b1, byte b2) {
        return (HexUtils.hexDigit2Dec(b1) << 4) + HexUtils.hexDigit2Dec(b2);
    }
}
