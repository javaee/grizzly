/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy.compression;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 *
 * @author oleksiys
 */
public class SpdyDeflaterOutputStream extends OutputStream {

    private final Deflater deflater;
    private final MemoryManager mm;
    
    private final byte[] tmpBA = new byte[1];
    
    private CompositeBuffer compositeBuffer;
    private Buffer currentOutputBuffer;
    private Buffer prevOutputBuffer;
    
    private byte[] tmpInBuffer;
    private byte[] tmpOutBuffer;
    
    private int bufferSize = 2048;
    
    /** true if {@link #close()} has been called. */
    private boolean closed = false;
    
    private boolean isUsingOwnDeflater;
    
    public SpdyDeflaterOutputStream(final MemoryManager mm,
            final int level) {
        this.mm = mm;
        this.deflater = new Deflater(level);
        Utils.setSpdyCompressionDictionary(deflater);
        isUsingOwnDeflater = true;
    }

    public SpdyDeflaterOutputStream(final MemoryManager mm,
            final Deflater deflater) {
        this.mm = mm;
        this.deflater = deflater;
        isUsingOwnDeflater = false;
    }

    /**
     * Returns the underlying {@link Deflater}.
     */
    public Deflater getDeflater() {
        return deflater;
    }
    
    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public void setInitialOutputBuffer(final Buffer buffer) {
        if (currentOutputBuffer != null) {
            throw new IllegalStateException("Can't set initial buffer in the middle of deflate");
        }
        
        currentOutputBuffer = buffer;
    }
    
    public Buffer checkpoint() {
        if (/*syncFlush && */!deflater.finished()) {
            deflate(Deflater.SYNC_FLUSH);
        }
        
        if (currentOutputBuffer != null) {
            currentOutputBuffer.trim();
        }
        
        final CompositeBuffer cpb = compositeBuffer;
        final Buffer pb = prevOutputBuffer;
        final Buffer cb = currentOutputBuffer;
        
        prevOutputBuffer = currentOutputBuffer = compositeBuffer = null;
        
        if (cpb != null) {
            
            cpb.append(pb);
            if (cb.hasRemaining()) {
                cpb.append(cb);
            }
            
            return cpb;
        } else if (pb != null) {
            if (cb.hasRemaining()) {
                return CompositeBuffer.newBuffer(mm, pb, cb);
            }
            
            return pb;
        }
        
        return cb;
    }
    
    @Override
    public void write(final int b) throws IOException {
        tmpBA[0] = (byte) b;
        write(tmpBA, 0, 1);
    }

    @Override
    public void write(final byte[] b, int off, int len)
            throws IOException {
        if (deflater.finished()) {
            throw new IOException("write beyond end of stream");
        }
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        if (!deflater.finished()) {
            deflater.setInput(b, off, len);
            while (!deflater.needsInput()) {
                deflate();
            }
        }
    }
    
    public void write(final Buffer buffer) throws IOException {
        if (buffer.hasArray()) {
            write(buffer.array(), buffer.arrayOffset() +
                    buffer.position(), buffer.remaining());
        } else {
            if (buffer.isComposite()) {
                final BufferArray bufferArray = buffer.toBufferArray();
                final Buffer[] array = bufferArray.getArray();
                
                for (int i = 0; i < bufferArray.size(); i++) {
                    writeSimpleBuffer(array[i]);
                }
            } else {
                writeSimpleBuffer(buffer);
            }
        }
        
        buffer.position(buffer.limit());
    }
    

    private void writeSimpleBuffer(final Buffer buffer) throws IOException {
        assert !buffer.isComposite();
        if (buffer.hasArray()) {
            write(buffer.array(), buffer.arrayOffset() +
                    buffer.position(), buffer.remaining());
        } else {
            final int oldPos = buffer.position();
            try {
                final byte[] tmpInput = getTmpInputArray();
                int remaining = buffer.remaining();
                
                while(remaining > 0) {
                    final int chunkSize = Math.min(remaining, tmpInput.length);
                    buffer.get(tmpInput, 0, chunkSize);
                    
                    write(tmpInput, 0, chunkSize);
                    
                    remaining -= chunkSize;
                }
            } finally {
                buffer.position(oldPos);
            }
        }
    }
    
    /**
     * Writes next block of compressed data to the output stream.
     * @throws IOException if an I/O error has occurred
     */
    protected int deflate() {
        return deflate(Deflater.NO_FLUSH);
    }
    
    /**
     * Writes next block of compressed data to the output stream.
     * @throws IOException if an I/O error has occurred
     */
    protected int deflate(final int flush) {
        if (currentOutputBuffer == null) {
            currentOutputBuffer = mm.allocate(bufferSize);
        } else if (!currentOutputBuffer.hasRemaining()) {
            currentOutputBuffer.flip();
            
            if (prevOutputBuffer != null) {
                if (compositeBuffer == null) {
                    compositeBuffer = CompositeBuffer.newBuffer(mm, prevOutputBuffer);
                } else {
                    compositeBuffer.append(prevOutputBuffer);
                }
            }
            prevOutputBuffer = currentOutputBuffer;
            
            currentOutputBuffer = mm.allocate(bufferSize);
        }
        
        if (currentOutputBuffer.hasArray()) {
            final int position = currentOutputBuffer.position();
            
            final int n = deflater.deflate(currentOutputBuffer.array(),
                    currentOutputBuffer.arrayOffset() + position,
                    currentOutputBuffer.remaining(), flush);
            if (n > 0) {
                currentOutputBuffer.position(position + n);
            }

            return n;
        } else {
            tmpOutBuffer = getTmpOutputArray();
            
            final int n = deflater.deflate(tmpOutBuffer, 0, tmpOutBuffer.length,
                    flush);
            if (n > 0) {
                currentOutputBuffer.put(tmpOutBuffer, 0, n);
            }
            
            return n;
        }
    }
    
    @Override
    public void flush() throws IOException {
    }

    /**
     * Finishes writing uncompressed data to the output stream without closing
     * the underlying stream.  Use this method when applying multiple filters in
     * succession to the same output stream.
     *
     * @throws IOException if an I/O error occurs or this stream is already
     * closed
     */
    public void finish() throws IOException {
        if (!deflater.finished()) {
            deflater.finish();
            while (!deflater.finished()) {
                deflate();
            }
        }
    }
    
    /**
     * Closes the stream, but doesn't finishes the internal {@link Deflater}.
     */
    public void closeStreamOnly() {
        closed = true;
    }
    
    /**
     * Writes any remaining uncompressed data to the output stream and closes
     * the underlying output stream.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            finish();
            if (isUsingOwnDeflater) {
                deflater.end();
            }
            closed = true;
        }
    }
    
    public void reset() {
        deflater.reset();
        closed = false;
    }
    
    private byte[] getTmpOutputArray() {
        if (tmpOutBuffer == null || tmpOutBuffer.length < bufferSize) {
            tmpOutBuffer = new byte[bufferSize];
        }
        
        return tmpOutBuffer;
    }
    
    private byte[] getTmpInputArray() {
        if (tmpInBuffer == null || tmpInBuffer.length < bufferSize) {
            tmpInBuffer = new byte[bufferSize];
        }
        
        return tmpInBuffer;
    }    
}
