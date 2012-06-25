/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.ssl;

import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * Utility class, which implements the set of useful SSL related operations.
 * 
 * @author Alexey Stashok
 */
public class SSLUtils {
    public static final String SSL_ENGINE_ATTR_NAME = "SSLEngineAttr";

    public static final Attribute<SSLEngine> sslEngineAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(SSL_ENGINE_ATTR_NAME);

    private static final byte CHANGE_CIPHER_SPECT_CONTENT_TYPE = 20;
    private static final byte APPLICATION_DATA_CONTENT_TYPE = 23;
    private static final int SSLV3_RECORD_HEADER_SIZE = 5; // SSLv3 record header
    private static final int SSL20_HELLO_VERSION = 0x0002;
    private static final int MIN_VERSION = 0x0300;
    private static final int MAX_MAJOR_VERSION = 0x03;

    public static SSLEngine getSSLEngine(AttributeStorage storage) {
        return sslEngineAttribute.get(storage);
    }

    public static void setSSLEngine(AttributeStorage storage,
            SSLEngine sslEngine) {
        sslEngineAttribute.set(storage, sslEngine);
    }

    /*
     * Check if there is enough inbound data in the ByteBuffer
     * to make a inbound packet.  Look for both SSLv2 and SSLv3.
     *
     * @return -1 if there are not enough bytes to tell (small header),
     */
    public static int getSSLPacketSize(final Buffer buf) throws SSLException {

        /*
         * SSLv2 length field is in bytes 0/1
         * SSLv3/TLS length field is in bytes 3/4
         */
        if (buf.remaining() < 5) {
            return -1;
        }

        int pos = buf.position();
        byte byteZero = buf.get(pos);

        int len;

        /*
         * If we have already verified previous packets, we can
         * ignore the verifications steps, and jump right to the
         * determination.  Otherwise, try one last hueristic to
         * see if it's SSL/TLS.
         */
        if (byteZero >= CHANGE_CIPHER_SPECT_CONTENT_TYPE
                && byteZero <= APPLICATION_DATA_CONTENT_TYPE) {
            /*
             * Last sanity check that it's not a wild record
             */
            final byte major = buf.get(pos + 1);
            final byte minor = buf.get(pos + 2);
            final int v = (major << 8) | minor & 0xff;

            // Check if too old (currently not possible)
            // or if the major version does not match.
            // The actual version negotiation is in the handshaker classes
            if ((v < MIN_VERSION)
                    || (major > MAX_MAJOR_VERSION)) {
                throw new SSLException("Unsupported record version major="
                        + major + " minor=" + minor);
            }

            /*
             * One of the SSLv3/TLS message types.
             */
            len = ((buf.get(pos + 3) & 0xff) << 8)
                    + (buf.get(pos + 4) & 0xff) + SSLV3_RECORD_HEADER_SIZE;

        } else {
            /*
             * Must be SSLv2 or something unknown.
             * Check if it's short (2 bytes) or
             * long (3) header.
             *
             * Internals can warn about unsupported SSLv2
             */
            boolean isShort = ((byteZero & 0x80) != 0);

            if (isShort
                    && ((buf.get(pos + 2) == 1) || buf.get(pos + 2) == 4)) {

                final byte major = buf.get(pos + 3);
                final byte minor = buf.get(pos + 4);
                final int v = (major << 8) | minor & 0xff;

                // Check if too old (currently not possible)
                // or if the major version does not match.
                // The actual version negotiation is in the handshaker classes
                if ((v < MIN_VERSION)
                        || (major > MAX_MAJOR_VERSION)) {

                    // if it's not SSLv2, we're out of here.
                    if (v != SSL20_HELLO_VERSION) {
                        throw new SSLException("Unsupported record version major="
                                + major + " minor=" + minor);
                    }
                }

                /*
                 * Client or Server Hello
                 */
                int mask = 0x7f;
                len = ((byteZero & mask) << 8) + (buf.get(pos + 1) & 0xff) + (2);

            } else {
                // Gobblygook!
                throw new SSLException(
                        "Unrecognized SSL message, plaintext connection?");
            }
        }

        return len;
    }
    
    /**
     * Complete handshakes operations.
     * @param sslEngine The SSLEngine used to manage the SSL operations.
     */
    public static void executeDelegatedTask(SSLEngine sslEngine) {

        Runnable runnable;
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
    }


    public static boolean isHandshaking(SSLEngine sslEngine) {
        HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        return !(handshakeStatus == HandshakeStatus.FINISHED ||
                handshakeStatus == HandshakeStatus.NOT_HANDSHAKING);
    }

    public static SSLEngineResult handshakeUnwrap(final Connection connection,
            final SSLEngine sslEngine, final Buffer inputBuffer)
            throws SSLException {

        final int expectedLength = getSSLPacketSize(inputBuffer);
        if (expectedLength == -1
                || inputBuffer.remaining() < expectedLength) {
            return null;
        }

        final MemoryManager memoryManager =
                connection.getTransport().getMemoryManager();

        final int pos = inputBuffer.position();
        final int appBufferSize = sslEngine.getSession().getApplicationBufferSize();
        
        final SSLEngineResult sslEngineResult;

        if (!inputBuffer.isComposite()) {
            final ByteBuffer inputBB = inputBuffer.toByteBuffer();

            final Buffer outputBuffer = memoryManager.allocate(
                    appBufferSize);

            sslEngineResult = sslEngine.unwrap(inputBB,
                    outputBuffer.toByteBuffer());
            outputBuffer.dispose();

            inputBuffer.position(pos + sslEngineResult.bytesConsumed());
        } else {
            final ByteBuffer inputByteBuffer =
                    inputBuffer.toByteBuffer(pos,
                    pos + expectedLength);

            final Buffer outputBuffer = memoryManager.allocate(
                    appBufferSize);

            sslEngineResult = sslEngine.unwrap(inputByteBuffer,
                    outputBuffer.toByteBuffer());

            inputBuffer.position(pos + sslEngineResult.bytesConsumed());

            outputBuffer.dispose();
        }

        return sslEngineResult;
    }

    public static Buffer handshakeWrap(final Connection connection,
            final SSLEngine sslEngine) throws SSLException {

        final MemoryManager memoryManager =
                connection.getTransport().getMemoryManager();
        
        final Buffer buffer = memoryManager.allocate(
                sslEngine.getSession().getPacketBufferSize());
        buffer.allowBufferDispose(true);

        try {
            final SSLEngineResult sslEngineResult =
                    sslEngine.wrap(Buffers.EMPTY_BYTE_BUFFER,
                    buffer.toByteBuffer());

            buffer.position(sslEngineResult.bytesProduced());
            buffer.trim();

            return buffer;
        } catch (SSLException e) {
            buffer.dispose();
            throw e;
        }
    }

}
