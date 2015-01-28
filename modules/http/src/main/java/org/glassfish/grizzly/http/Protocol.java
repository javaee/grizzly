/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import java.io.UnsupportedEncodingException;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.ByteChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Predefined HTTP protocol versions
 * 
 * @author Alexey Stashok
 */
public enum Protocol {
    HTTP_0_9 (0, 9),
    HTTP_1_0 (1, 0),
    HTTP_1_1 (1, 1),
    HTTP_2_0 (2, 0);

    public static Protocol valueOf(final byte[] protocolBytes,
            final int offset, final int len) {
        if (len == 0) {
            return Protocol.HTTP_0_9;
        } else if (equals(HTTP_1_1, protocolBytes, offset, len)) {
            return Protocol.HTTP_1_1;
        } else if (equals(HTTP_1_0, protocolBytes, offset, len)) {
            return Protocol.HTTP_1_0;
        } else if (equals(HTTP_2_0, protocolBytes, offset, len)) {
            return Protocol.HTTP_2_0;
        } else if (equals(HTTP_0_9, protocolBytes, offset, len)) {
            return Protocol.HTTP_0_9;
        }
        
        throw new IllegalStateException("Unknown protocol " +
                new String(protocolBytes, offset, len, Charsets.ASCII_CHARSET));
    }

    public static Protocol valueOf(final Buffer protocolBuffer,
                                   final int offset, final int len) {
        if (len == 0) {
            return Protocol.HTTP_0_9;
        } else if (equals(HTTP_1_1, protocolBuffer, offset, len)) {
            return Protocol.HTTP_1_1;
        } else if (equals(HTTP_1_0, protocolBuffer, offset, len)) {
            return Protocol.HTTP_1_0;
        } else if (equals(HTTP_2_0, protocolBuffer, offset, len)) {
            return Protocol.HTTP_2_0;
        } else if (equals(HTTP_0_9, protocolBuffer, offset, len)) {
            return Protocol.HTTP_0_9;
        }

        throw new IllegalStateException("Unknown protocol " +
                protocolBuffer.toStringContent(Charsets.ASCII_CHARSET, offset, len));
    }

    public static Protocol valueOf(final DataChunk protocolC) {
        if (protocolC.getLength() == 0) {
            return Protocol.HTTP_0_9;
        } else if (protocolC.equals(Protocol.HTTP_1_1.getProtocolBytes())) {
            return Protocol.HTTP_1_1;
        } else if (protocolC.equals(Protocol.HTTP_1_0.getProtocolBytes())) {
            return Protocol.HTTP_1_0;
        } else if (protocolC.equals(Protocol.HTTP_2_0.getProtocolBytes())) {
            return Protocol.HTTP_2_0;
        } else if (protocolC.equals(Protocol.HTTP_0_9.getProtocolBytes())) {
            return Protocol.HTTP_0_9;
        }
        
        throw new IllegalStateException("Unknown protocol " + protocolC.toString());
    }    

    private static boolean equals(final Protocol protocol,
            final byte[] protocolBytes,
            final int offset, final int len) {
        
        final byte[] knownProtocolBytes = protocol.getProtocolBytes();
        
        return ByteChunk.equals(knownProtocolBytes, 0, knownProtocolBytes.length,
                protocolBytes, offset, len);
    }

    private static boolean equals(final Protocol protocol,
                                  final Buffer protocolBuffer,
                                  final int offset,
                                  final int len) {


        final byte[] knownProtocolBytes = protocol.getProtocolBytes();
        return BufferChunk.equals(knownProtocolBytes, 0, knownProtocolBytes.length,
                protocolBuffer, offset, len);
    }
    
    private final String protocolString;
    private final int majorVersion;
    private final int minorVersion;
    private final byte[] protocolBytes;

    Protocol(final int majorVersion, final int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.protocolString = "HTTP/" + majorVersion + '.' + minorVersion;
        
        byte[] protocolBytes0;
        try {
            protocolBytes0 = protocolString.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException ignored) {
            protocolBytes0 = protocolString.getBytes(Charsets.ASCII_CHARSET);
        }
        
        this.protocolBytes = protocolBytes0;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public String getProtocolString() {
        return protocolString;
    }

    public byte[] getProtocolBytes() {
        return protocolBytes;
    }

    public boolean equals(final String s) {
        return s != null &&
                ByteChunk.equals(protocolBytes, 0, protocolBytes.length, s);
    }
    
    public boolean equals(final DataChunk protocolC) {
        return protocolC != null &&
                !protocolC.isNull() &&
                protocolC.equals(protocolBytes);
    }
}
