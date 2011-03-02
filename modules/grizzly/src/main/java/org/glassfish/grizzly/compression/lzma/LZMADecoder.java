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
package org.glassfish.grizzly.compression.lzma;

import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.compression.lzma.impl.Decoder;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

import java.io.IOException;

public class LZMADecoder extends AbstractTransformer<Buffer,Buffer> {

    private static final ThreadCache.CachedTypeIndex<LZMAInputState> CACHE_IDX =
            ThreadCache.obtainIndex(LZMAInputState.class, 2);


    // ---------------------------------------- Methods from AbstractTransformer


    @Override
    public String getName() {
        return "lzma-decoder";
    }

    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
        return input.hasRemaining();
    }

    @Override
    protected TransformationResult<Buffer, Buffer> transformImpl(AttributeStorage storage, Buffer input) throws TransformationException {
        final MemoryManager memoryManager = obtainMemoryManager(storage);

        final LZMAInputState state = (LZMAInputState) obtainStateObject(storage);
        if (!state.isInitialized()) {
            if (!initializeInput(state, input)) {
                return TransformationResult.createIncompletedResult(input);
            }
        }
        Buffer decodedBuffer = null;
        final boolean canDecode = input.hasRemaining() && eosMarkerPresent(input);
        if (canDecode) {
            decodedBuffer = decodeBuffer(memoryManager, input, state);
        }


        final boolean hasRemainder = input.hasRemaining();

        if (decodedBuffer == null || !decodedBuffer.hasRemaining()) {
            return TransformationResult.createIncompletedResult(hasRemainder ? input : null);
        }

        return TransformationResult.createCompletedResult(decodedBuffer,
                hasRemainder ? input : null);
    }

    private boolean eosMarkerPresent(Buffer input) {
        // if the end-of-stream marker is present, we can successfully
        // decode the message
        final int idx = input.limit() - 1;
        final byte b = input.get(idx);
        return (b == 0x00);
    }

    @Override
    protected LastResultAwareState<Buffer, Buffer> createStateObject() {
        return create();
    }

    // ---------------------------------------------------------- Public Methods


    public static LZMAInputState create() {
        final LZMAInputState state =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (state != null) {
            return state;
        }

        return new LZMAInputState();
    }


    public void finish(AttributeStorage storage) {
        final LZMAInputState state = (LZMAInputState) obtainStateObject(storage);
        state.recycle();
    }


    // --------------------------------------------------------- Private Methods


    private boolean initializeInput(final LZMAInputState state,
                                    final Buffer input) {

        return (input.remaining() >= 13 && state.initialize(input));

    }


    private Buffer decodeBuffer(final MemoryManager memoryManager,
                                final Buffer buffer,
                                final LZMAInputState state) {

        state.setSrc(buffer);

        Buffer resultBuffer = null;


        final int pos = buffer.position();
        Buffer decodedBuffer = memoryManager.allocate(512);
        state.setDst(decodedBuffer);
        try {
            state.getDecoder().code(state.getSrc(),
                    state.getDst(),
                    -1);
        } catch (IOException e) {
            decodedBuffer.dispose();
            throw new IllegalStateException(e);
        }
        decodedBuffer = state.getDst();

        if (decodedBuffer.position() > 0) {
            decodedBuffer.trim();
            resultBuffer = Buffers.appendBuffers(memoryManager,
                    resultBuffer, decodedBuffer);
        } else {
            decodedBuffer.dispose();
            buffer.position(pos);
        }

        return resultBuffer;

    }


    // ---------------------------------------------------------- Nested Classes


    private static class LZMAInputState extends LastResultAwareState<Buffer,Buffer> implements Cacheable {

        private Decoder decoder = new Decoder();
        private boolean initialized;
        private byte[] decoderConfigBits = new byte[5];
        private Buffer src;
        private Buffer dst;


        // ------------------------------------------------------ Public Methods


        public boolean initialize(final Buffer buffer) {
            buffer.get(decoderConfigBits);
            initialized = decoder.setDecoderProperties(decoderConfigBits);
            return initialized;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public Decoder getDecoder() {
            return decoder;
        }

        public Buffer getSrc() {
            return src;
        }

        public void setSrc(Buffer src) {
            this.src = src;
        }

        public Buffer getDst() {
            return dst;
        }

        public void setDst(Buffer dst) {
            this.dst = dst;
        }

        // ---------------------------------------------- Methods from Cacheable


        @Override
        public void recycle() {
            src = null;
            dst = null;
            initialized = false;
            lastResult = null;
            ThreadCache.putToCache(CACHE_IDX, this);
        }

    } // END LZMAInputState

}
