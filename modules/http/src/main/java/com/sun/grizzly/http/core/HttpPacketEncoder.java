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
package com.sun.grizzly.http.core;

import com.sun.grizzly.AbstractTransformer;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.util.MimeHeaders;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.memory.ByteBuffersBuffer;
import com.sun.grizzly.memory.CompositeBuffer;
import com.sun.grizzly.memory.MemoryManager;
import java.nio.charset.Charset;

/**
 *
 * @author oleksiys
 */
public abstract class HttpPacketEncoder
        extends AbstractTransformer<HttpPacket, Buffer> {

    protected static final Charset ASCII_CHARSET = Charset.forName("ASCII");

    protected abstract Buffer encodeInitialLine(HttpPacket httpPacket,
            Buffer encodedBuffer, MemoryManager memoryManager);
    
    @Override
    public final String getName() {
        return getClass().getName();
    }

    @Override
    protected TransformationResult<HttpPacket, Buffer> transformImpl(
            AttributeStorage storage, HttpPacket input)
            throws TransformationException {

        if (!input.isHeader()) {
            return TransformationResult.<HttpPacket, Buffer>createCompletedResult(
                    ((HttpContentPacket) input).getContent(), null, false);
        }

        final MemoryManager memoryManager = obtainMemoryManager(storage);
        Buffer encodedBuffer = memoryManager.allocate(8192);

        encodedBuffer = encodeInitialLine(input, encodedBuffer, memoryManager);
        encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);

        final HttpHeaderPacket httpHeaderPacket = (HttpHeaderPacket) input;
        
        final MimeHeaders mimeHeaders = httpHeaderPacket.getHeaders();
        final int mimeHeadersNum = mimeHeaders.size();

        for (int i = 0; i < mimeHeadersNum; i++) {
            encodedBuffer = put(memoryManager, encodedBuffer,
                    mimeHeaders.getName(i));

            encodedBuffer = put(memoryManager, encodedBuffer,
                    Constants.COLON_BYTES);

            encodedBuffer = put(memoryManager, encodedBuffer,
                    mimeHeaders.getValue(i));

            encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);
        }

        encodedBuffer = put(memoryManager, encodedBuffer, Constants.CRLF_BYTES);
        encodedBuffer.trim();

        final Buffer content = httpHeaderPacket.getContent();
        if (content != null && content.hasRemaining()) {
            CompositeBuffer compositeBuffer = ByteBuffersBuffer.create(memoryManager);
            compositeBuffer.append(encodedBuffer);
            compositeBuffer.append(content);

            encodedBuffer = compositeBuffer;
        }

        return TransformationResult.<HttpPacket, Buffer>createCompletedResult(
                encodedBuffer, null, false);
    }

    protected Buffer put(MemoryManager memoryManager,
            Buffer headerBuffer, BufferChunk bufferChunk) {

        if (bufferChunk.hasBuffer()) {
            final int length = bufferChunk.getEnd() - bufferChunk.getStart();
            if (headerBuffer.remaining() < length) {
                headerBuffer =
                        resizeBuffer(memoryManager, headerBuffer, length);
            }

            headerBuffer.put(bufferChunk.getBuffer(), bufferChunk.getStart(),
                    length);
        } else {
            byte[] bytes = bufferChunk.toString().getBytes(ASCII_CHARSET);
            if (headerBuffer.remaining() < bytes.length) {
                headerBuffer =
                        resizeBuffer(memoryManager, headerBuffer, bytes.length);
            }

            headerBuffer.put(bytes, 0, bytes.length);
        }

        return headerBuffer;
    }

    protected Buffer put(MemoryManager memoryManager,
            Buffer headerBuffer, byte[] array) {

        if (headerBuffer.remaining() < array.length) {
            headerBuffer =
                    resizeBuffer(memoryManager, headerBuffer, array.length);
        }

        headerBuffer.put(array);

        return headerBuffer;
    }

    protected Buffer put(MemoryManager memoryManager,
            Buffer headerBuffer, byte value) {

        if (!headerBuffer.hasRemaining()) {
            headerBuffer =
                    resizeBuffer(memoryManager, headerBuffer, 1);
        }

        headerBuffer.put(value);

        return headerBuffer;
    }

    protected Buffer resizeBuffer(MemoryManager memoryManager,
            Buffer headerBuffer, int grow) {

        return memoryManager.reallocate(headerBuffer, Math.max(
                headerBuffer.capacity() + grow,
                (headerBuffer.capacity() * 3) / 2 + 1));
    }
}
