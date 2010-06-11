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

package com.sun.grizzly.websockets;

import java.util.Random;

/**
 * Class represents {@link WebSocket}'s security key, used during the handshake phase.
 * See Sec-WebSocket-Key1, Sec-WebSocket-Key2.
 *
 * @author Alexey Stashok
 */
public class SecKey {
    private static final Random random = new Random();

    private static final char[] CHARS = new char[84];

    static {
        int idx = 0;
        for (int i = 0x21; i <= 0x2F; i++) {
            CHARS[idx++] = (char) i;
        }

        for (int i = 0x3A; i <= 0x7E; i++) {
            CHARS[idx++] = (char) i;
        }
    }

    private static final long MAX_SEC_KEY_VALUE = 4294967295l;

    /**
     * Security key string representation, which includes chars and spaces.
     */
    private final String secKey;

    /**
     * Original security key value (already divided by number of spaces).
     */
    private final long secKeyValue;

    /**
     * Create a <tt>SecKey</tt> object basing on {@link String} representation, which
     * includes spaces and chars. Method also performs key validation.
     *
     * @param secKey security key string representation with spaces and chars included.
     *
     * @return <tt>SecKey</tt>
     */
    public static SecKey create(String secKey) {
        return validateSecKey(secKey);
    }

    private SecKey(String secKey, long secKeyValue) {
        this.secKey = secKey;
        this.secKeyValue = secKeyValue;
    }

    /**
     * Gets security key string representation, which includes chars and spaces.
     * 
     * @return Security key string representation, which includes chars and spaces.
     */
    public String getSecKey() {
        return secKey;
    }

    /**
     * Gets original security key value (already divided by number of spaces).
     * @return original security key value (already divided by number of spaces).
     */
    public long getSecKeyValue() {
        return secKeyValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(secKey.length() + 16);
        sb.append(secKey).append('(').append(secKeyValue).append(')');
        return sb.toString();
    }

    /**
     * Generates random security key.
     *
     * @return <tt>SecKey</tt>
     */
    public static SecKey generateSecKey() {
        // generate number of spaces we will use
        int spacesNum = random.nextInt(12) + 1;

        // generate the original key value
        long number = Math.abs(random.nextLong()) % (MAX_SEC_KEY_VALUE / spacesNum);

        // product number
        long product = number * spacesNum;

        StringBuilder key = new StringBuilder();
        key.append(product);

        // insert chars into product number
        int charsNum = random.nextInt(12) + 1;
        for (int i = 0; i < charsNum; i++) {
            int position = random.nextInt(key.length());
            char c = CHARS[random.nextInt(CHARS.length)];

            key.insert(position, c);
        }

        // insert spaces into product number
        for (int i = 0; i < spacesNum; i++) {
            int position = random.nextInt(key.length() - 1) + 1;

            key.insert(position, ' ');
        }

        // create SecKey
        return new SecKey(key.toString(), number);
    }

    /**
     * Validate security key, represented as string value (which includes chars and spaces).
     *
     * @param key security key, represented as string value (which includes chars and spaces).
     *
     * @return validated <tt>SecKey</tt>
     */
    public static SecKey validateSecKey(String key) {
        if (key.length() > 256) return null;

        // chars counter
        int charsNum = 0;
        // spaces counter
        int spacesNum = 0;
        // product numeric value
        long productValue = 0;

        for (int i=0; i<key.length(); i++) {
            char c = key.charAt(i);
            if (c >= '0' && c <= '9') {
                productValue = productValue * 10 + (c - '0');
                if (productValue > MAX_SEC_KEY_VALUE) { // check if productValue is not biffer than max
                    return null;
                }
            } else if (c == ' ') { // count spaces
                spacesNum++;
                if (spacesNum > 12) return null;
            } else if ((c >= 0x21 && c <= 0x2F) || (c >= 0x3A && c <= 0x7E)) {  // count chars
                charsNum++;
                if (charsNum > 12) {
                    return null;
                }
            } else {  // unexpected char
                return null;
            }
        }

        if (spacesNum < 1) {  // number of spaces should be more than 1
            return null;
        }

        if (charsNum < 1) {  // number of chars should be more than 1
            return null;
        }

        if ((productValue % spacesNum) != 0) {  // check if remainder is 0
            return null;
        }

        return new SecKey(key, productValue / spacesNum);
    }
}
