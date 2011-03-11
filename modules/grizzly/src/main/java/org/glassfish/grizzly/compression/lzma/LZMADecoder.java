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
import org.glassfish.grizzly.memory.MemoryManager;

import java.io.IOException;
import org.glassfish.grizzly.compression.lzma.impl.Base;
import org.glassfish.grizzly.compression.lzma.impl.Decoder.LiteralDecoder;

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
        state.setMemoryManager(memoryManager);

        Buffer decodedBuffer = null;
        Decoder.State decState = null;
        if (input.hasRemaining()) {
            decState = decodeBuffer(memoryManager, input, state);
            decodedBuffer = state.getDst();
        }

        final boolean hasRemainder = input.hasRemaining();

        if (decState == Decoder.State.NEED_MORE_DATA
                || decodedBuffer == null) {
            return TransformationResult.createIncompletedResult(hasRemainder ? input : null);
        }

        return TransformationResult.createCompletedResult(decodedBuffer.flip(),
                hasRemainder ? input : null);
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


    private Decoder.State decodeBuffer(final MemoryManager memoryManager,
                                       final Buffer buffer,
                                       final LZMAInputState state) {

        state.setSrc(buffer);

        Decoder.State decState;
        try {
            decState = state.getDecoder().code(state, -1);
        } catch (IOException e) {
            disposeDstBuffer(state);
            throw new IllegalStateException(e);
        }
        if (decState == Decoder.State.ERR) {
            disposeDstBuffer(state);
            throw new IllegalStateException("Invalid decoder state.");
        }

        return decState;

    }

    private static void disposeDstBuffer(LZMAInputState state) {
        final Buffer dstBuffer = state.getDst();
        if (dstBuffer != null) {
            dstBuffer.dispose();
            state.setDst(null);
        }
    }


    // ---------------------------------------------------------- Nested Classes


    public static class LZMAInputState extends LastResultAwareState<Buffer,Buffer> implements Cacheable {

        private Decoder decoder = new Decoder();
        private boolean initialized;
        private byte[] decoderConfigBits = new byte[5];
        private Buffer src;
        private Buffer dst;
        private MemoryManager mm;

        public int state;
        public int rep0;
        public int rep1;
        public int rep2;
        public int rep3;
        public long nowPos64;
        public byte prevByte;
        public boolean decInitialized;
        
        public int posState;
        public int lastMethodResult;

        public int inner1State;
        public int inner2State;

        public LiteralDecoder.Decoder2 decoder2;

        // BitTreeDecoder static reverseDecode state
        public int staticReverseDecodeMethodState;
        public int staticM;
        public int staticBitIndex;
        public int staticSymbol;

        // Decoder.processState3 method state
        public int state3Len;
        
        // Decoder.processState31 method state
        public int state31;

        // Decoder.processState311 method state
        public int state311;
        public int state311Distance;

        // Decoder.processState32 method state
        public int state32;
        public int state32PosSlot;

        // Decoder.processState321 method state
        public int state321;
        public int state321NumDirectBits;

        // ------------------------------------------------------ Public Methods


        public boolean initialize(final Buffer buffer) {
            buffer.get(decoderConfigBits);
            initialized = decoder.setDecoderProperties(decoderConfigBits);
            state = Base.stateInit();
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

        public MemoryManager getMemoryManager() {
            return mm;
        }

        public void setMemoryManager(MemoryManager mm) {
            this.mm = mm;
        }

        // ---------------------------------------------- Methods from Cacheable


        @Override
        public void recycle() {
            state = 0;
            rep0 = 0;
            rep1 = 0;
            rep2 = 0;
            rep3 = 0;
            nowPos64 = 0;
            prevByte = 0;
            src = null;
            dst = null;
            lastResult = null;
            initialized = false;
            decInitialized = false;
            mm = null;

            posState = 0;
            lastMethodResult = 0;

            inner1State = 0;
            inner2State = 0;

            decoder2 = null;

            staticReverseDecodeMethodState = 0;

            state31 = 0;
            state311 = 0;
            state32 = 0;
            state321 = 0;

            ThreadCache.putToCache(CACHE_IDX, this);
        }

    } // END LZMAInputState

}
