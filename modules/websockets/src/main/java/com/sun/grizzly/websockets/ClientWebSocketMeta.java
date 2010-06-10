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

import com.sun.grizzly.Grizzly;
import java.net.URI;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public class ClientWebSocketMeta extends WebSocketMeta {
    private static final Logger logger = Grizzly.logger(ClientWebSocketMeta.class);

    private static final Random random = new Random();    
    
    private final String host;
    
    private final SecKey key1;
    private final SecKey key2;

    private byte[] key3;

    public ClientWebSocketMeta(URI uri) {
        this(uri, null, null, null, null, null, null);
    }

    public ClientWebSocketMeta(URI uri, String host, String origin,
            String protocol, String key1, String key2, byte[] key3) {
        super(uri, origin != null ? origin : "http://localhost", protocol);

        if (uri == null) throw new IllegalArgumentException("URI can not be null");
        
        this.host = host != null ? host : uri.getHost() + getPort(uri);

        if (key1 == null || key2 == null || key3 == null) {
            final GeneratedKeys generatedKeys = generateKeys();
            this.key1 = generatedKeys.getKey1();
            this.key2 = generatedKeys.getKey2();
            this.key3 = generatedKeys.getKey3();
        } else {
            if (key3.length != 8)
                throw new IllegalArgumentException("key3 length should be 8 bytes");

            SecKey secKey = SecKeyUtils.checkSecKey(key1);
            if (secKey == null) {
                throw new IllegalArgumentException("key1 is not correct: " + key1);
            }
            this.key1 = secKey;

            secKey = SecKeyUtils.checkSecKey(key2);
            if (secKey == null) {
                throw new IllegalArgumentException("key2 is not correct: " + key2);
            }
            this.key2 = secKey;

            this.key3 = key3;
        }
    }

    public String getHost() {
        return host;
    }

    public SecKey getKey1() {
        return key1;
    }

    public SecKey getKey2() {
        return key2;
    }

    public byte[] getKey3() {
        return key3;
    }

    @Override
    public final boolean isServerMetaInfo() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(super.toString())
                .append(" host=").append(host)
                .append(" key1=").append(key1)
                .append(" key2=").append(key2)
                .append(" key3=").append(Arrays.toString(key3));

        return sb.toString();
    }

    private static String getPort(URI uri) {
        final int port = uri.getPort();
        if (port == -1) {
            final String schema = uri.getScheme();
            if ("ws".equals(schema) || "wss".equals(schema)) {
                return "";
            }

            throw new IllegalStateException("Unexpected protocol!");
        }

        return ":" + port;
    }

    private static GeneratedKeys generateKeys() {
        SecKey key1 = SecKeyUtils.generateSecKey();
        SecKey key2 = SecKeyUtils.generateSecKey();
        byte[] key3 = new byte[8];
        random.nextBytes(key3);

        return new GeneratedKeys(key1, key2, key3);
    }

    
    private static class GeneratedKeys {
        private final SecKey key1;
        private final SecKey key2;

        private final byte[] key3;

        public GeneratedKeys(SecKey key1, SecKey key2, byte[] key3) {

            this.key1 = key1;
            this.key2 = key2;
            this.key3 = key3;
        }

        public SecKey getKey1() {
            return key1;
        }

        public SecKey getKey2() {
            return key2;
        }

        public byte[] getKey3() {
            return key3;
        }
    }
}
