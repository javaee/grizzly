/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.util;

import java.util.HashMap;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.Buffers;

/**
 * Utility class.
 *
 * @author Alexey Stashok
 */
public class Utils {
    private static final HashMap<Integer, byte[]> statusMessages =
        new HashMap<Integer, byte[]>(HttpStatus.values().length);
    private static final HashMap<Integer, byte[]> statusCodes =
        new HashMap<Integer, byte[]>(HttpStatus.values().length);

    static {
        for (final HttpStatus status : HttpStatus.values()) {
            statusMessages.put(status.getStatusCode(), status.getReasonPhraseBytes());
            statusCodes.put(status.getStatusCode(), status.getStatusBytes());
        }
    }
    // ---------------------------------------------------------- Public Methods

    /**
     * @param httpStatus HTTP status code
     *
     * @return the standard HTTP message associated with the specified status code.  If there is no message for the
     *         specified <code>httpStatus</code>, <code>null</code> shall be returned.
     */
    public static Buffer getHttpStatusMessage(final int httpStatus) {
        return Buffers.wrap(null, statusMessages.get(httpStatus));

    }

    /**
     * @param httpStatus HTTP status code
     *
     * @return {@link Buffer} representation of the status.
     */
    public static Buffer getHttpStatus(final int httpStatus) {
        return Buffers.wrap(null, statusCodes.get(httpStatus));

    }

    /**
     * Converts the specified long as a string representation to the provided buffer.
     *
     * This code is based off {@link Long#toString()}
     *
     * @param value the long to convert.
     * @param buffer the buffer to write the conversion result to.
     */
    public static void longToBuffer(long value, final Buffer buffer) {
        if (value == 0) {
            buffer.put(0, (byte) '0');
            buffer.limit(1);
            return;
        }
        final int radix = 10;
        final boolean negative;
        if (value < 0) {
            negative = true;
            value = -value;
        } else {
            negative = false;
        }
        int position = buffer.limit();
        do {
            final int ch = '0' + (int) (value % radix);
            buffer.put(--position, (byte) ch);
        } while ((value /= radix) != 0);
        if (negative) {
            buffer.put(--position, (byte) '-');
        }
        buffer.position(position);

    }
    // --------------------------------------------------------- Private Methods

    private static DataChunk newChunk(byte[] bytes) {
        final DataChunk dc = DataChunk.newInstance();
        final Buffer b = Buffers.wrap(null, bytes);
        dc.setBuffer(b, 0, bytes.length);
        return dc;
    }

    /**
     * Filter the specified message string for characters that are sensitive in HTML.  This avoids potential attacks
     * caused by including JavaScript codes in the request URL that is often reported in error messages.
     *
     * @param message The message string to be filtered
     */
    public static String filter(String message) {
        if (message == null) {
            return null;
        }
        char content[] = new char[message.length()];
        message.getChars(0, message.length(), content, 0);
        StringBuilder result = new StringBuilder(content.length + 50);
        for (char aContent : content) {
            switch (aContent) {
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                case '&':
                    result.append("&amp;");
                    break;
                case '"':
                    result.append("&quot;");
                    break;
                default:
                    result.append(aContent);
            }
        }
        return result.toString();
    }

}
