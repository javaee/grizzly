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
    private final String secKey;
    private final long secKeyValue;

    public static SecKey create(String secKey) {
        return checkSecKey(secKey);
    }

    private SecKey(String secKey, long secKeyValue) {
        this.secKey = secKey;
        this.secKeyValue = secKeyValue;
    }

    public String getSecKey() {
        return secKey;
    }

    public long getSecKeyValue() {
        return secKeyValue;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(secKey.length() + 16);
        sb.append(secKey).append('(').append(secKeyValue).append(')');
        return sb.toString();
    }

    public static SecKey generateSecKey() {
        int spacesNum = random.nextInt(12) + 1;
        long number = Math.abs(random.nextLong()) % (MAX_SEC_KEY_VALUE / spacesNum);

        long product = number * spacesNum;

        StringBuilder key = new StringBuilder();
        key.append(product);

        int charsNum = random.nextInt(12) + 1;
        for (int i = 0; i < charsNum; i++) {
            int position = random.nextInt(key.length());
            char c = CHARS[random.nextInt(CHARS.length)];

            key.insert(position, c);
        }

        for (int i = 0; i < spacesNum; i++) {
            int position = random.nextInt(key.length() - 1) + 1;

            key.insert(position, ' ');
        }

        return new SecKey(key.toString(), number);
    }

    public static SecKey checkSecKey(String key) {
        if (key.length() > 256) return null;

        int charsNum = 0;
        int spacesNum = 0;
        long keyValue = 0;

        for (int i=0; i<key.length(); i++) {
            char c = key.charAt(i);
            if (c >= '0' && c <= '9') {
                keyValue = keyValue * 10 + (c - '0');
                if (keyValue > MAX_SEC_KEY_VALUE) {
                    return null;
                }
            } else if (c == ' ') {
                spacesNum++;
                if (spacesNum > 12) return null;
            } else if ((c >= 0x21 && c <= 0x2F) || (c >= 0x3A && c <= 0x7E)) {
                charsNum++;
                if (charsNum > 12) {
                    return null;
                }
            }
        }

        if (spacesNum < 1) {
            return null;
        }

        if (charsNum < 1) {
            return null;
        }

        if ((keyValue % spacesNum) != 0) {
            return null;
        }

        return new SecKey(key, keyValue / spacesNum);
    }
}
