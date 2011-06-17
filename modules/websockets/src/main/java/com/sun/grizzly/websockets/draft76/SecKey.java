/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets.draft76;

import com.sun.grizzly.websockets.HandshakeException;
import com.sun.grizzly.websockets.InvalidSecurityKeyException;
import com.sun.grizzly.websockets.WebSocket;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Class represents {@link WebSocket}'s security key, used during the handshake phase.
 * See Sec-WebSocket-Key1, Sec-WebSocket-Key2.
 *
 * @author Alexey Stashok
 */
public class SecKey {
    private static final Random random = new Random();

    private static final char[] CHARS = new char[84];

    private static final long MAX_SEC_KEY_VALUE = 4294967295L;

    /**
     * Security key string representation, which includes chars and spaces.
     */
    private final String secKey;

    /**
     * Original security key value (already divided by number of spaces).
     */
    private final long secKeyValue;
    private static final BlockingQueue<MessageDigest> mds = new ArrayBlockingQueue<MessageDigest>(5);

    static {
        int idx = 0;
        for (int i = 0x21; i <= 0x2F; i++) {
            CHARS[idx++] = (char) i;
        }

        for (int i = 0x3A; i <= 0x7E; i++) {
            CHARS[idx++] = (char) i;
        }
        init();
    }

    private SecKey(String secKey, long secKeyValue) {
        this.secKey = secKey;
        this.secKeyValue = secKeyValue;
    }

    /**
     * Create a <tt>SecKey</tt> object basing on {@link String} representation, which
     * includes spaces and chars. Method also performs key validation.
     *
     * @param secKey security key string representation with spaces and chars included.
     * @return <tt>SecKey</tt>
     */
    public static SecKey parse(String secKey) {
        return validateSecKey(secKey);
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
     *
     * @return original security key value (already divided by number of spaces).
     */
    public long getSecKeyValue() {
        return secKeyValue;
    }

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
     * Generate server-side security key, which gets passed to the client during
     * the handshake phase as part of message payload.
     *
     * @param clientKey1 client's Sec-WebSocket-Key1
     * @param clientKey2 client's Sec-WebSocket-Key2
     * @param clientKey3 client's key3, which is sent as part of handshake request payload.
     * @return server key.
     * @throws NoSuchAlgorithmException
     */
    public static byte[] generateServerKey(SecKey clientKey1, SecKey clientKey2, byte[] clientKey3)
            throws HandshakeException {

        final ByteBuffer b = ByteBuffer.allocate(8);
        b.putInt((int) clientKey1.getSecKeyValue());
        b.putInt((int) clientKey2.getSecKeyValue());
        b.flip();

        MessageDigest md5 = null;
        try {
            md5 = mds.poll(30, TimeUnit.SECONDS);
            md5.update(b);
            return md5.digest(clientKey3);
        } catch (InterruptedException e) {
            throw new HandshakeException(e.getMessage());
        } finally {
            offer(md5);
        }

    }

    /**
     * Validate security key, represented as string value (which includes chars and spaces).
     *
     * @param key security key, represented as string value (which includes chars and spaces).
     * @return validated <tt>SecKey</tt>
     */
    public static SecKey validateSecKey(String key) {
        if (key.length() > 256) {
            throw new InvalidSecurityKeyException("Key too long");
        }

        int charsNum = 0;
        int spacesNum = 0;
        long productValue = 0;

        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c >= '0' && c <= '9') {
                productValue = productValue * 10 + (c - '0');
                if (productValue > MAX_SEC_KEY_VALUE) {
                    throw new InvalidSecurityKeyException("Key value too large: " + productValue);
                }
            } else if (c == ' ') { // count spaces
                spacesNum++;
                if (spacesNum > 12) {
                    throw new InvalidSecurityKeyException("Too many spaces: " + spacesNum);
                }
            } else if (c >= 0x21 && c <= 0x2F || c >= 0x3A && c <= 0x7E) {  // count chars
                charsNum++;
                if (charsNum > 12) {
                    throw new InvalidSecurityKeyException("Too many characters: " + charsNum);
                }
            } else {
                throw new InvalidSecurityKeyException("unexpected character: '" + c + "'");
            }
        }

        if (spacesNum < 1) {
            throw new InvalidSecurityKeyException("number of spaces should be more than 1");
        }

        if (charsNum < 1) {
            throw new InvalidSecurityKeyException("number of chars should be more than 1");
        }

        if (productValue % spacesNum != 0) {  // check if remainder is 0
            throw new InvalidSecurityKeyException("remainder is not 0");
        }

        return new SecKey(key, productValue / spacesNum);
    }

    public static void init() {
        try {
            while (offer(MessageDigest.getInstance("MD5")) || mds.size() < 5) {
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static boolean offer(MessageDigest md5) {
        if (md5 != null) {
            md5.reset();
            return mds.offer(md5);
        }
        return false;
    }
}
