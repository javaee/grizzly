/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.MemoryManager;

import static org.glassfish.grizzly.ssl.SSLUtils.*;

/**
 * SSL context associated with a {@link Connection}.
 * 
 * @author Alexey Stashok
 */
public final class SSLConnectionContext {
    private static final Logger LOGGER = Grizzly.logger(SSLConnectionContext.class);
    private static final float BUFFER_SIZE_COEF;
    
    static {
        final String coef = System.getProperty(
                SSLConnectionContext.class.getName(), "1.5");
        
        float coeff = 1.5f;
        
        try {
            coeff = Float.parseFloat(coef);
        } catch (NumberFormatException ignored) {
        }
        
        BUFFER_SIZE_COEF = coeff;
    }
    
    final ByteBufferArray outputByteBufferArray =
            ByteBufferArray.create();
    
    final ByteBufferArray inputByteBufferArray =
            ByteBufferArray.create();

    private Buffer lastOutputBuffer;
    private final InputBufferWrapper inputBuffer = new InputBufferWrapper();
    private InputBufferWrapper lastInputBuffer;
    
    private boolean isServerMode;
    private SSLEngine sslEngine;

    private volatile int appBufferSize;
    private volatile int netBufferSize;
    
    private final Connection connection;
    private FilterChain newConnectionFilterChain;

    public SSLConnectionContext(Connection connection) {
        this.connection = connection;
    }    
    
    public SSLEngine getSslEngine() {
        return sslEngine;
    }

    public Connection getConnection() {
        return connection;
    }
    
    public void attach() {
        SSLUtils.SSL_CTX_ATTR.set(connection, this);
    }
    
    public void configure(final SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
        this.isServerMode = !sslEngine.getUseClientMode();
        updateBufferSizes();
    }

    public boolean isServerMode() {
        return isServerMode;
    }
    
    void updateBufferSizes() {
        final SSLSession session = sslEngine.getSession();
        appBufferSize = session.getApplicationBufferSize();
        netBufferSize = session.getPacketBufferSize();
    }
    
    public int getAppBufferSize() {
        return appBufferSize;
    }

    public int getNetBufferSize() {
        return netBufferSize;
    }

    public FilterChain getNewConnectionFilterChain() {
        return newConnectionFilterChain;
    }

    public void setNewConnectionFilterChain(FilterChain newConnectionFilterChain) {
        this.newConnectionFilterChain = newConnectionFilterChain;
    }

    Buffer resetLastOutputBuffer() {
        final Buffer tmp = lastOutputBuffer;
        lastOutputBuffer = null;
        return tmp;
    }

    void setLastOutputBuffer(final Buffer lastOutputBuffer) {
        this.lastOutputBuffer = lastOutputBuffer;
    }

    InputBufferWrapper resetLastInputBuffer() {
        final InputBufferWrapper tmp = lastInputBuffer;
        lastInputBuffer = null;
        return tmp;
    }

    InputBufferWrapper useInputBuffer() {
        lastInputBuffer = inputBuffer;
        return lastInputBuffer;
    }

    SslResult unwrap(final Buffer input, Buffer output,
            final Allocator allocator) {
            
        output = ensureBufferSize(output, appBufferSize, allocator);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "unwrap engine: {0} input: {1} output: {2}",
                    new Object[] {sslEngine, input, output});
        }
        
        final int inPos = input.position();
        final int outPos = output.position();
        
        final ByteBuffer inputByteBuffer = input.toByteBuffer();
        final SSLEngineResult sslEngineResult;
        
        try {
            if (!output.isComposite()) {
                sslEngineResult = sslEngineUnwrap(sslEngine, inputByteBuffer,
                        output.toByteBuffer());

            } else {
                final ByteBufferArray bba =
                        output.toByteBufferArray(this.outputByteBufferArray);
                final ByteBuffer[] outputArray = bba.getArray();

                try {
                    sslEngineResult = sslEngineUnwrap(sslEngine, inputByteBuffer,
                            outputArray, 0, bba.size());
                } finally {
                    bba.restore();
                    bba.reset();
                }
            }
        } catch (SSLException e) {
            return new SslResult(output, e);
        }
        
        final Status status = sslEngineResult.getStatus();
        final boolean isOverflow = (status == SSLEngineResult.Status.BUFFER_OVERFLOW);
        
        if (allocator != null && isOverflow) {
            updateBufferSizes();
            output = ensureBufferSize(output, appBufferSize, allocator);
            return unwrap(input, output, null);
        } else if (isOverflow || status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            return new SslResult(output, new SSLException("SSL unwrap error: " + status));
        }
        
        input.position(inPos + sslEngineResult.bytesConsumed());
        output.position(outPos + sslEngineResult.bytesProduced());

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "unwrap done engine: {0} result: {1} input: {2} output: {3}",
                    new Object[] {sslEngine, sslEngineResult, input, output});
        }
        
        return new SslResult(output, sslEngineResult);
    }

    Buffer wrapAll(final Buffer input,
            final Allocator allocator) throws SSLException {
        final MemoryManager memoryManager = connection.getMemoryManager();
        
        final ByteBufferArray bba =
                input.toByteBufferArray(inputByteBufferArray);
        final ByteBuffer[] inputArray = bba.getArray();
        final int inputArraySize = bba.size();
        
        Buffer output = null;
        SslResult result = null;
        try {
            result = wrap(input, inputArray, inputArraySize, null, allocator);
            
            if (result.isError()) {
                throw result.getError();
            }
            
            output = result.getOutput();
            output.trim();
            
            if (input.hasRemaining()) {
                do {
                    result = wrap(input, inputArray, inputArraySize,
                            null, allocator);
                    
                    if (result.isError()) {
                        throw result.getError();
                    }
                    
                    final Buffer newOutput = result.getOutput();
                    newOutput.trim();
                    
                    output = Buffers.appendBuffers(memoryManager, output,
                            newOutput);
                } while (input.hasRemaining());
            }
            
            return output;
        } finally {
            bba.restore();
            bba.reset();
            if (result != null && result.isError()) {
                if (output != null) {
                    output.dispose();
                }
                
                result.getOutput().dispose();
            }
        }
    }
    
    private SslResult wrap(final Buffer input, final ByteBuffer[] inputArray,
            final int inputArraySize,
            Buffer output,
            final Allocator allocator) {
            
        output = ensureBufferSize(output, netBufferSize, allocator);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "wrap engine: {0} input: {1} output: {2}",
                    new Object[] {sslEngine, input, output});
        }
        
        final int inPos = input.position();
        final int outPos = output.position();
        
        final ByteBuffer outputByteBuffer = output.toByteBuffer();
        final SSLEngineResult sslEngineResult;
        
        try {
            sslEngineResult = sslEngineWrap(sslEngine,
                    inputArray, 0, inputArraySize,
                    outputByteBuffer);
        } catch (SSLException e) {
            return new SslResult(output, e);
        }
        
        final Status status = sslEngineResult.getStatus();
        
        if (status == SSLEngineResult.Status.CLOSED) {
            return new SslResult(output, new SSLException("SSLEngine is CLOSED"));
        }
        
        final boolean isOverflow = (status == SSLEngineResult.Status.BUFFER_OVERFLOW);
        
        if (allocator != null && isOverflow) {
            updateBufferSizes();
            output = ensureBufferSize(output, netBufferSize, allocator);
            return wrap(input, inputArray, inputArraySize, output, null);
        } else if (isOverflow || status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            return new SslResult(output, new SSLException("SSL wrap error: " + status));
        }
        
        input.position(inPos + sslEngineResult.bytesConsumed());
        output.position(outPos + sslEngineResult.bytesProduced());

        lastOutputBuffer = output;
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "wrap done engine: {0} result: {1} input: {2} output: {3}",
                    new Object[] {sslEngine, sslEngineResult, input, output});
        }
        
        return new SslResult(output, sslEngineResult);
    }

    SslResult wrap(final Buffer input, Buffer output,
            final Allocator allocator) {
            
        output = ensureBufferSize(output, netBufferSize, allocator);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "wrap engine: {0} input: {1} output: {2}",
                    new Object[] {sslEngine, input, output});
        }
        
        final int inPos = input.position();
        final int outPos = output.position();
        
        final ByteBuffer outputByteBuffer = output.toByteBuffer();
        final SSLEngineResult sslEngineResult;
        
        try {
            if (!input.isComposite()) {
                sslEngineResult = sslEngineWrap(sslEngine, input.toByteBuffer(),
                        outputByteBuffer);

            } else {
                final ByteBufferArray bba =
                        input.toByteBufferArray(this.inputByteBufferArray);
                final ByteBuffer[] inputArray = bba.getArray();

                try {
                    sslEngineResult = sslEngineWrap(sslEngine,
                            inputArray, 0, bba.size(),
                            outputByteBuffer);
                } finally {
                    bba.restore();
                    bba.reset();
                }
            }
        } catch (SSLException e) {
            return new SslResult(output, e);
        }
        
        final Status status = sslEngineResult.getStatus();
        
        final boolean isOverflow = (status == SSLEngineResult.Status.BUFFER_OVERFLOW);
        
        if (allocator != null && isOverflow) {
            updateBufferSizes();
            output = ensureBufferSize(output, netBufferSize, allocator);
            return wrap(input, output, null);
        } else if (isOverflow || status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            return new SslResult(output, new SSLException("SSL wrap error: " + status));
        }
        
        input.position(inPos + sslEngineResult.bytesConsumed());
        output.position(outPos + sslEngineResult.bytesProduced());

        lastOutputBuffer = output;
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "wrap done engine: {0} result: {1} input: {2} output: {3}",
                    new Object[] {sslEngine, sslEngineResult, input, output});
        }
        
        return new SslResult(output, sslEngineResult);
    }
    
    private Buffer ensureBufferSize(Buffer output,
            final int size, final Allocator allocator) {
        final int sz = (int) ((float) size * BUFFER_SIZE_COEF);
        
        if (output == null) {
            assert allocator != null;
            output = allocator.grow(this, null, sz);
        } else if (output.remaining() < sz) {
            assert allocator != null;
            output = allocator.grow(this, output,
                    output.capacity() + (sz - output.remaining()));
        }
        return output;
    }
    
    interface Allocator {
        public Buffer grow(final SSLConnectionContext sslCtx,
                final Buffer oldBuffer, final int newSize);
    }
    
    final static class SslResult {
        private final Buffer output;
        private final SSLException error;
        private final SSLEngineResult sslEngineResult;

        public SslResult(final Buffer output,
                final SSLEngineResult sslEngineResult) {
            this.output = output;
            this.sslEngineResult = sslEngineResult;
            this.error = null;
        }

        public SslResult(final Buffer output,
                final SSLException error) {
            this.output = output;
            this.error = error;
            this.sslEngineResult = null;
        }

        public Buffer getOutput() {
            return output;
        }

        public boolean isError() {
            return error != null;
        }
        
        public SSLException getError() {
            return error;
        }

        public SSLEngineResult getSslEngineResult() {
            return sslEngineResult;
        }
    }
}
