/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.tcp.http11.filters;

import com.sun.grizzly.lzma.compression.lzma.Encoder;
import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.OutputFilter;
import com.sun.grizzly.util.buf.ByteChunk;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LZMA {@link OutputFilter} implementation.
 * 
 * @author Noemax
 */
public class LzmaOutputFilter implements OutputFilter {

    private static final boolean isLog = Boolean.getBoolean("lzma-filter.isLog");
    private static final Logger logger = Logger.getLogger(LzmaOutputFilter.class.getName());
    // -------------------------------------------------------------- Constants
    public static final String ENCODING_NAME = "lzma";
    protected static final ByteChunk ENCODING = new ByteChunk();

    // ----------------------------------------------------- Static Initializer
    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(), 0, ENCODING_NAME.length());
    }
    // ----------------------------------------------------- Instance Variables
    /**
     * Next buffer in the pipeline.
     */
    protected OutputBuffer buffer;
    /**
     * Compression output stream.
     */
    protected Encoder encoder;
    protected final LzmaProperties lzmaProperties = new LzmaProperties();
    protected final ReusableByteArrayInputStream byteArrayInputStream =
            new ReusableByteArrayInputStream();
    /**
     * Fake internal output stream.
     */
    protected OutputStream fakeOutputStream = new FakeOutputStream();

    // --------------------------------------------------- OutputBuffer Methods
    /**
     * Write some bytes.
     *
     * @return number of bytes written by the filter
     */
    public int doWrite(ByteChunk chunk, Response res)
            throws IOException {
        if (encoder == null) {
            initEncoder();
        }

        byteArrayInputStream.setArray(chunk.getBytes(), chunk.getStart(),
                chunk.getLength());

        encoder.Code(byteArrayInputStream, fakeOutputStream, -1, -1, null);
        fakeOutputStream.flush();

        return chunk.getLength();
    }

    // --------------------------------------------------- OutputFilter Methods
    /**
     * Some filters need additional parameters from the response. All the
     * necessary reading can occur in that method, as this method is called
     * after the response header processing is complete.
     */
    public void setResponse(Response response) {
    }

    /**
     * Set the next buffer in the filter pipeline.
     */
    public void setBuffer(OutputBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * End the current request. It is acceptable to write extra bytes using
     * buffer.doWrite during the execution of this method.
     */
    public long end() throws IOException {
        if (encoder == null) {
            initEncoder();
        }

        fakeOutputStream.flush();
        return ((OutputFilter) buffer).end();
    }

    /**
     * Make the filter ready to process the next request.
     */
    public void recycle() {
        encoder = null;
    }

    /**
     * Return the name of the associated encoding; Here, the value is
     * "identity".
     */
    public ByteChunk getEncodingName() {
        return ENCODING;
    }

    private void initEncoder() throws IOException {
        encoder = new Encoder();
        encoder.SetAlgorithm(lzmaProperties.getAlgorithm());
        encoder.SetDictionarySize(lzmaProperties.getDictionarySize());
        encoder.SetNumFastBytes(lzmaProperties.getNumFastBytes());
        encoder.SetMatchFinder(lzmaProperties.getMatchFinder());
        encoder.SetLcLpPb(lzmaProperties.getLc(), lzmaProperties.getLp(), lzmaProperties.getPb());
        encoder.SetEndMarkerMode(true);
        encoder.WriteCoderProperties(fakeOutputStream);
    }

    // ------------------------------------------- FakeOutputStream Inner Class
    protected class FakeOutputStream
            extends OutputStream {

        protected ByteChunk outputChunk = new ByteChunk();
        protected byte[] singleByteBuffer = new byte[4096];
        protected int offset = 0;

        public void write(int b)
                throws IOException {
            if (offset >= singleByteBuffer.length) {
                flush();
            }

            singleByteBuffer[offset++] = (byte) (b & 0xff);
        }

        @Override
        public void write(byte[] b, int off, int len)
                throws IOException {
            flush();

            if (isLog) {
                log(b, off, len);
            }

            outputChunk.setBytes(b, off, len);
            buffer.doWrite(outputChunk, null);
        }

        @Override
        public void flush() throws IOException {
            if (offset > 0) {
                final int localOffset = offset;
                offset = 0;

                outputChunk.setBytes(singleByteBuffer, 0, localOffset);
                if (isLog) {
                    log(singleByteBuffer, 0, localOffset);
                }

                buffer.doWrite(outputChunk, null);
            }
        }

        @Override
        public void close() throws IOException {
            flush();
        }

        protected void log(byte[] buffer, int offset, int length) {
            final StringBuilder sb = new StringBuilder();
            for (int i = offset; i < length; i++) {
                sb.append(Integer.toHexString(buffer[i] & 0xFF));
                sb.append(' ');
            }

            // Ignoring this for standard logging practices as it's only
            // displayed if the isLog flag is explicitly set.`
            if (logger.isLoggable(Level.INFO)) {
                logger.info("LzmaOutputFilter write: " + sb.toString());
            }
        }
    }

    public static class ReusableByteArrayInputStream extends ByteArrayInputStream {

        private static final byte[] ZERO_ARRAY = new byte[0];

        public ReusableByteArrayInputStream() {
            super(ZERO_ARRAY);
        }

        public void setArray(byte[] array) {
            setArray(array, 0, array.length);
        }

        public void setArray(byte[] array, int offset, int length) {
            this.buf = array;
            this.pos = offset;
            this.count = Math.min(offset + length, buf.length);
            this.mark = offset;
        }
    }

    public static class LzmaProperties {

        private int algorithm = 2;
        private int dictionarySize = 1 << 16;
        private int numFastBytes = 128;
        private int matchFinder = 1;
        private int lc = 3;
        private int lp = 0;
        private int pb = 2;

        public LzmaProperties() {
            loadProperties(this);
        }

        public LzmaProperties(int algorithm, int dictionarySize, int numFastBytes,
                int matchFinder, int lc, int lp, int pb) {
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

        public static void loadProperties(LzmaProperties properties) {
            properties.algorithm = Integer.getInteger("lzma-filter.algorithm", 2);
            properties.dictionarySize = 1 << Integer.getInteger("lzma-filter.dictionary-size", 16);
            properties.numFastBytes = Integer.getInteger("lzma-filter.num-fast-bytes", 128);
            properties.matchFinder = Integer.getInteger("lzma-filter.match-finder", 1);

            properties.lc = Integer.getInteger("lzma-filter.lc", 3);
            properties.lp = Integer.getInteger("lzma-filter.lp", 0);
            properties.pb = Integer.getInteger("lzma-filter.pb", 2);
        }
    }
}
