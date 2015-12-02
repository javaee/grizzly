/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.ThreadCache.CachedTypeIndex;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferWrapper;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.ssl.SSLConnectionContext.Allocator;
import org.glassfish.grizzly.ssl.SSLConnectionContext.SslResult;

/**
 * Utility class, which implements the set of useful SSL related operations.
 * 
 * @author Alexey Stashok
 */
public final class SSLUtils {
    
    /**
     * Workaround for Android 5 (GRIZZLY-1783)
     */
    private static final boolean ANDROID_WORKAROUND_NEEDED;
    private static final int LOLLIPOP_VER = 21;
    static {
        boolean isNeedWorkAround = false;
        if ("android runtime".equalsIgnoreCase(
                System.getProperty("java.runtime.name"))) {
            try {
                // try to figure out the version via reflection
                final int version = Class.forName("android.os.Build$VERSION")
                        .getField("SDK_INT").getInt(null);
                
                isNeedWorkAround = (version >= LOLLIPOP_VER);
            } catch (Throwable ignored) {
            }
        }
        
        ANDROID_WORKAROUND_NEEDED = isNeedWorkAround;
    }
    
    private static final String SSL_CONNECTION_CTX_ATTR_NAME =
            SSLUtils.class + ".ssl-connection-context";

    static final Attribute<SSLConnectionContext> SSL_CTX_ATTR =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            SSL_CONNECTION_CTX_ATTR_NAME);

    private static final Allocator HS_UNWRAP_ALLOCATOR =
            new Allocator() {
        @Override
        public Buffer grow(final SSLConnectionContext sslCtx,
            final Buffer oldBuffer, final int newSize) {
//            if (oldBuffer != null) {
//                oldBuffer.dispose();
//            }
            return allocateOutputBuffer(newSize);
        }
    };
    
    private static final Allocator HS_WRAP_ALLOCATOR =
            new Allocator() {
        @Override
        public Buffer grow(final SSLConnectionContext sslCtx,
            final Buffer oldBuffer, final int newSize) {
            return allocateOutputBuffer(newSize);
        }
    };

    private static final byte CHANGE_CIPHER_SPECT_CONTENT_TYPE = 20;
    private static final byte APPLICATION_DATA_CONTENT_TYPE = 23;
    private static final int SSLV3_RECORD_HEADER_SIZE = 5; // SSLv3 record header
    private static final int SSL20_HELLO_VERSION = 0x0002;
    private static final int MIN_VERSION = 0x0300;
    private static final int MAX_MAJOR_VERSION = 0x03;

    public static SSLConnectionContext getSslConnectionContext(
            final Connection connection) {
        return SSL_CTX_ATTR.get(connection);
    }

    public static SSLEngine getSSLEngine(final Connection connection) {
        final SSLConnectionContext sslCtx = getSslConnectionContext(connection);
        return sslCtx == null ? null : sslCtx.getSslEngine();
    }

    public static void setSSLEngine(final Connection connection,
            final SSLEngine sslEngine) {
        SSLConnectionContext ctx = getSslConnectionContext(connection);
        if (ctx == null) { // set first time outside of standard SSLFilter
            ctx = new SSLConnectionContext(connection);
            SSL_CTX_ATTR.set(connection, ctx);
        }

        ctx.configure(sslEngine);
    }

    /*
     * Check if there is enough inbound data in the ByteBuffer
     * to make a inbound packet.  Look for both SSLv2 and SSLv3.
     *
     * @return -1 if there are not enough bytes to tell (small header),
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    public static int getSSLPacketSize(final Buffer buf) throws SSLException {
        
        /*
         * SSLv2 length field is in bytes 0/1
         * SSLv3/TLS length field is in bytes 3/4
         */
        if (buf.remaining() < 5) {
            return -1;
        }

        
        final byte byte0;
        final byte byte1;
        final byte byte2;
        final byte byte3;
        final byte byte4;
        
        if (buf.hasArray()) {
            final byte[] array = buf.array();
            int pos = buf.arrayOffset() + buf.position();
            byte0 = array[pos++];
            byte1 = array[pos++];
            byte2 = array[pos++];
            byte3 = array[pos++];
            byte4 = array[pos];
        } else {
            int pos = buf.position();
            byte0 = buf.get(pos++);
            byte1 = buf.get(pos++);
            byte2 = buf.get(pos++);
            byte3 = buf.get(pos++);
            byte4 = buf.get(pos);
        }

        int len;

        /*
         * If we have already verified previous packets, we can
         * ignore the verifications steps, and jump right to the
         * determination.  Otherwise, try one last hueristic to
         * see if it's SSL/TLS.
         */
        if (byte0 >= CHANGE_CIPHER_SPECT_CONTENT_TYPE
                && byte0 <= APPLICATION_DATA_CONTENT_TYPE) {
            /*
             * Last sanity check that it's not a wild record
             */
            final byte major = byte1;
            final byte minor = byte2;
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
            len = ((byte3 & 0xff) << 8)
                    + (byte4 & 0xff) + SSLV3_RECORD_HEADER_SIZE;

        } else {
            /*
             * Must be SSLv2 or something unknown.
             * Check if it's short (2 bytes) or
             * long (3) header.
             *
             * Internals can warn about unsupported SSLv2
             */
            boolean isShort = ((byte0 & 0x80) != 0);

            if (isShort
                    && ((byte2 == 1) || byte2 == 4)) {

                final byte major = byte3;
                final byte minor = byte4;
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
                len = ((byte0 & mask) << 8) + (byte1 & 0xff) + (2);

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
    public static void executeDelegatedTask(final SSLEngine sslEngine) {

        Runnable runnable;
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
    }


    public static boolean isHandshaking(final SSLEngine sslEngine) {
        final HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        return !(handshakeStatus == HandshakeStatus.FINISHED ||
                handshakeStatus == HandshakeStatus.NOT_HANDSHAKING);
    }

    public static SSLEngineResult handshakeUnwrap(final int length,
                                                  final SSLConnectionContext sslCtx,
                                                  final Buffer inputBuffer,
                                                  final Buffer tmpOutputBuffer)
    throws SSLException {

        SslResult result =
                sslCtx.unwrap(length, inputBuffer, tmpOutputBuffer, HS_UNWRAP_ALLOCATOR);

        final Buffer output = result.getOutput();
        
        assert !output.isComposite();
        
        if (output != tmpOutputBuffer) {
            output.dispose();
        }
        
        if (result.isError()) {
            throw result.getError();
        }
        
        return result.getSslEngineResult();
    }

    public static Buffer handshakeWrap(final Connection connection,
            final SSLConnectionContext sslCtx, final Buffer netBuffer)
            throws SSLException {

        final int packetBufferSize = sslCtx.getNetBufferSize();
        
        Buffer buffer;
        if (netBuffer != null && !netBuffer.isComposite() &&
                netBuffer.capacity() - netBuffer.limit() >= packetBufferSize) {
            netBuffer.position(netBuffer.limit());
            netBuffer.limit(netBuffer.capacity());
            
            buffer = netBuffer;
        } else {
            buffer = allocateOutputBuffer(packetBufferSize * 2);
        }
        
        final SslResult result =
                sslCtx.wrap(Buffers.EMPTY_BUFFER, buffer, HS_WRAP_ALLOCATOR);
        
        Buffer output = result.getOutput();
        
        output.flip();
        if (buffer != output) {
            if (netBuffer != null && buffer == netBuffer) {
                netBuffer.flip();
            }
        }
        
        if (result.isError()) {
            if (output != netBuffer) {
                output.dispose();
            }
            
            throw result.getError();
        }
        
        if (output != netBuffer) {
            output = allowDispose(Buffers.appendBuffers(
                    connection.getMemoryManager(),
                    netBuffer, output));
        }

        return output;
    }

    private static final CachedTypeIndex<Buffer> SSL_OUTPUT_BUFFER_IDX =
            ThreadCache.obtainIndex(SSLBaseFilter.class.getName() + ".output-buffer-cache",
            Buffer.class, 4);
    
    static Buffer allocateOutputBuffer(final int size/*, final int counter*/) {
        
        Buffer buffer = ThreadCache.takeFromCache(SSL_OUTPUT_BUFFER_IDX);
        final boolean hasBuffer = (buffer != null);
        if (!hasBuffer || buffer.remaining() < size) {
            final ByteBuffer byteBuffer;
                byteBuffer = ByteBuffer.allocate(size);
            
            buffer = new ByteBufferWrapper(byteBuffer) {

                @Override
                public void dispose() {
                    clear();
                    ThreadCache.putToCache(SSL_OUTPUT_BUFFER_IDX, this);
                }
            };
        }

        return buffer;
    }
    
    public static Buffer allocateInputBuffer(final SSLConnectionContext sslCtx) {
        
        final SSLEngine sslEngine = sslCtx.getSslEngine();
        if (sslEngine == null) {
            return null;
        }
        
        // Direct buffer input
//        final InputBufferWrapper buffer = sslCtx.useInputBuffer();
//        return buffer.prepare(sslCtx.getNetBufferSize() * 2);
        // Heap buffer input
        return allocateOutputBuffer(sslCtx.getNetBufferSize() * 2);
    }

    static Buffer makeInputRemainder(
            final SSLConnectionContext sslCtx,
            final FilterChainContext context,
            final Buffer buffer) {
        
        if (buffer == null) {
            return null;
        }
        
        if (!buffer.hasRemaining()) {
            buffer.tryDispose();
            return null;
        }
        
        final Buffer inputBuffer = sslCtx.resetLastInputBuffer();
        if (inputBuffer == null) { // SSLTransportWrapper hasn't been used
            final Buffer remainder = buffer.split(buffer.position());
            buffer.tryDispose();
            return remainder;
        } else {
            return move(context.getMemoryManager(), buffer);
        }
    }
    
    static Buffer copy(final MemoryManager memoryManager,
            final Buffer buffer) {
        final Buffer tmpBuf = memoryManager.allocate(buffer.remaining());
        tmpBuf.put(buffer);

        return tmpBuf.flip();

    }
    
    static Buffer move(final MemoryManager memoryManager,
            final Buffer buffer) {
        final Buffer tmpBuf = copy(memoryManager, buffer);
        buffer.tryDispose();

        return tmpBuf;

    }
    
    public static Buffer allowDispose(final Buffer buffer) {
        if (buffer == null) {
            return null;
        }
        
        buffer.allowBufferDispose(true);
        if (buffer.isComposite()) {
            ((CompositeBuffer) buffer).allowInternalBuffersDispose(true);
        }
        
        return buffer;
    }
    
    static SSLEngineResult sslEngineWrap(final SSLEngine engine,
            final ByteBuffer in, final ByteBuffer out) throws SSLException {
        return engine.wrap(in, out);
    }
    
    static SSLEngineResult sslEngineWrap(final SSLEngine engine,
            final ByteBuffer[] in, final int inOffs, final int inLen,
            final ByteBuffer out)
            throws SSLException {
        return ANDROID_WORKAROUND_NEEDED
                ? AndroidWorkAround.wrapArray(engine, in, inOffs, inLen, out)
                : engine.wrap(in, inOffs, inLen, out);
    }

    static SSLEngineResult sslEngineUnwrap(final SSLEngine engine,
            final ByteBuffer in, final ByteBuffer out) throws SSLException {
        return engine.unwrap(in, out);
    }

    static SSLEngineResult sslEngineUnwrap(final SSLEngine engine,
            final ByteBuffer in,
            final ByteBuffer[] out, final int outOffs, final int outLen)
            throws SSLException {
        return ANDROID_WORKAROUND_NEEDED
                ? AndroidWorkAround.unwrapArray(engine, in, out, outOffs, outLen)
                : engine.unwrap(in, out, outOffs, outLen);
    }
    
    private static class AndroidWorkAround {
        public static SSLEngineResult wrapArray(final SSLEngine engine,
                final ByteBuffer[] in, final int inOffs, final int inLen,
                final ByteBuffer out) throws SSLException {
            if (inOffs == 0 && inLen == in.length) {
                return engine.wrap(in, out);
            } else {
                final ByteBuffer[] tmp = new ByteBuffer[inLen];
                System.arraycopy(in, inOffs, tmp, 0, inLen);
                return engine.wrap(tmp, out);
            }
        }
        
        public static SSLEngineResult unwrapArray(final SSLEngine engine,
                final ByteBuffer in,
                final ByteBuffer[] out, final int outOffs, final int outLen)
                throws SSLException {
            if (outOffs == 0 && outLen == out.length) {
                return engine.unwrap(in, out);
            } else {
                final ByteBuffer[] tmp = new ByteBuffer[outLen];
                System.arraycopy(out, outOffs, tmp, 0, outLen);
                return engine.unwrap(in, tmp);
            }
        }
    }    
}
