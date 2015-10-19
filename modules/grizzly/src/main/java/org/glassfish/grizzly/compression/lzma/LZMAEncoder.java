/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.compression.lzma.impl.Encoder;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

import java.io.IOException;

public class LZMAEncoder extends AbstractTransformer<Buffer,Buffer> {

    private static final ThreadCache.CachedTypeIndex<LZMAOutputState> CACHE_IDX =
            ThreadCache.obtainIndex(LZMAOutputState.class, 2);

    private final LZMAProperties lzmaProperties;

    // ------------------------------------------------------------ Constructors


    public LZMAEncoder() {
        this(new LZMAProperties());
    }


    public LZMAEncoder(LZMAProperties lzmaProperties) {
        this.lzmaProperties = lzmaProperties;
    }


    // ---------------------------------------- Methods from AbstractTransformer


    @Override
    public String getName() {
        return "lzma-encoder";
    }


    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
         return input.hasRemaining();
    }


    @Override
    protected TransformationResult<Buffer, Buffer> transformImpl(AttributeStorage storage, Buffer input) throws TransformationException {
        final MemoryManager memoryManager = obtainMemoryManager(storage);
        final LZMAOutputState state = (LZMAOutputState) obtainStateObject(storage);

        if (!state.isInitialized()) {
            initializeOutput(state);
        }

        Buffer encodedBuffer = null;
        if (input != null && input.hasRemaining()) {
            try {
                state.setMemoryManager(memoryManager);
                encodedBuffer = encodeBuffer(input, state);
            } catch (IOException ioe) {
                throw new TransformationException(ioe);
            }
        }

        if (encodedBuffer == null) {
            return TransformationResult.createIncompletedResult(null);
        }

        return TransformationResult.createCompletedResult(encodedBuffer, null);

    }


    @Override
    protected LastResultAwareState<Buffer,Buffer> createStateObject() {
        return create();
    }


    // ---------------------------------------------------------- Public Methods


    public static LZMAOutputState create() {
        final LZMAOutputState state =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (state != null) {
            return state;
        }

        return new LZMAOutputState();
    }

    public void finish(AttributeStorage storage) {
        final LZMAOutputState state = (LZMAOutputState) obtainStateObject(storage);
        state.recycle();
    }


    // --------------------------------------------------------- Private Methods


    private void initializeOutput(final LZMAOutputState state) {
        final Encoder encoder = state.getEncoder();
        encoder.setAlgorithm(lzmaProperties.getAlgorithm());
        encoder.setDictionarySize(lzmaProperties.getDictionarySize());
        encoder.setNumFastBytes(lzmaProperties.getNumFastBytes());
        encoder.setMatchFinder(lzmaProperties.getMatchFinder());
        encoder.setLcLpPb(lzmaProperties.getLc(), lzmaProperties.getLp(), lzmaProperties.getPb());
        encoder.setEndMarkerMode(true);
        state.setInitialized(true);
    }


    private Buffer encodeBuffer(Buffer input,
                                LZMAOutputState state) throws IOException {

        Buffer resultBuffer = null;

        state.setSrc(input);
        final Buffer encoded = encode(state);
        if (encoded != null) {
            resultBuffer = Buffers.appendBuffers(state.getMemoryManager(),
                    resultBuffer,
                    encoded);
        }

        input.position(input.limit());

        return resultBuffer;
    }


    private Buffer encode(LZMAOutputState outputState)
    throws IOException {


        final Encoder encoder = outputState.getEncoder();
        Buffer dst = outputState.getMemoryManager().allocate(512);
        outputState.setDst(dst);

        if (!outputState.isHeaderWritten()) {
            // writes a 5-byte header that the decoder will use in order
            // to achieve parity with the encoder's properties.
            encoder.writeCoderProperties(outputState.getDst());
            outputState.setHeaderWritten(true);
        }

        encoder.code(outputState, -1, -1);
        dst = outputState.getDst();
        int len = dst.position();
        if (len <= 0) {
            dst.dispose();
            return null;
        }

        dst.trim();

        return dst;
    }


    // ---------------------------------------------------------- Nested Classes


    public static class LZMAOutputState extends LastResultAwareState<Buffer,Buffer> implements Cacheable {

        private boolean initialized;

        /**
         * Compressor for this stream.
         */
        private final Encoder encoder = new Encoder();
        private Buffer dst;
        private Buffer src;
        private boolean headerWritten = false;
        private MemoryManager mm;

        public boolean isInitialized() {
            return initialized;
        }

        public void setInitialized(boolean initialized) {
            this.initialized = initialized;
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

        public Encoder getEncoder() {
            return encoder;
        }

        public boolean isHeaderWritten() {
            return headerWritten;
        }

        public void setHeaderWritten(boolean headerWritten) {
            this.headerWritten = headerWritten;
        }

        public MemoryManager getMemoryManager() {
            return mm;
        }

        public void setMemoryManager(MemoryManager mm) {
            this.mm = mm;
        }

        @Override
        public void recycle() {
            lastResult = null;
            initialized = false;
            headerWritten = false;
            mm = null;
            dst = null;
            src = null;
            ThreadCache.putToCache(CACHE_IDX, this);
        }

    }

    public static class LZMAProperties {

        private int algorithm = 2;
        private int dictionarySize = 1 << 16;
        private int numFastBytes = 128;
        private int matchFinder = 1;
        private int lc = 3;
        private int lp = 0;
        private int pb = 2;

        public LZMAProperties() {
            loadProperties(this);
        }

        public LZMAProperties(int algorithm,
                              int dictionarySize,
                              int numFastBytes,
                              int matchFinder,
                              int lc,
                              int lp,
                              int pb) {
            this.algorithm = algorithm;
            this.dictionarySize = dictionarySize;
            this.numFastBytes = numFastBytes;
            this.matchFinder = matchFinder;
            this.lc = lc;
            this.lp = lp;
            this.pb = pb;
        }

        public int getLc() {
            return lc;
        }

        public void setLc(int Lc) {
            this.lc = Lc;
        }

        public int getLp() {
            return lp;
        }

        public void setLp(int Lp) {
            this.lp = Lp;
        }

        public int getPb() {
            return pb;
        }

        public void setPb(int Pb) {
            this.pb = Pb;
        }

        public int getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(int algorithm) {
            this.algorithm = algorithm;
        }

        public int getDictionarySize() {
            return dictionarySize;
        }

        public void setDictionarySize(int dictionarySize) {
            this.dictionarySize = dictionarySize;
        }

        public int getMatchFinder() {
            return matchFinder;
        }

        public void setMatchFinder(int matchFinder) {
            this.matchFinder = matchFinder;
        }

        public int getNumFastBytes() {
            return numFastBytes;
        }

        public void setNumFastBytes(int numFastBytes) {
            this.numFastBytes = numFastBytes;
        }

        public static void loadProperties(LZMAProperties properties) {
            properties.algorithm = Integer.getInteger("lzma-filter.algorithm", 2);
            properties.dictionarySize = 1 << Integer.getInteger("lzma-filter.dictionary-size", 16);
            properties.numFastBytes = Integer.getInteger("lzma-filter.num-fast-bytes", 128);
            properties.matchFinder = Integer.getInteger("lzma-filter.match-finder", 1);

            properties.lc = Integer.getInteger("lzma-filter.lc", 3);
            properties.lp = Integer.getInteger("lzma-filter.lp", 0);
            properties.pb = Integer.getInteger("lzma-filter.pb", 2);
        }

    } // END LZMAProperties

}
