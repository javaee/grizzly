/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.http.server.io;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.util.Constants;
import com.sun.grizzly.http.util.Utils;
import com.sun.grizzly.memory.BuffersBuffer;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * Abstraction exposing both byte and character methods to read content
 * from the HTTP messaging system in Grizzly.
 */
public class InputBuffer {

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 8;

    /**
     * The {@link com.sun.grizzly.http.HttpRequestPacket} associated with this <code>InputBuffer</code>
     */
    private HttpRequestPacket request;

    /**
     * The {@link FilterChainContext} associated with this <code>InputBuffer</code>.
     * The {@link FilterChainContext} will be using to read <code>POST</code>
     * data in blocking fashion.
     */
    private FilterChainContext ctx;

    /**
     * Flag indicating whether or not this <code>InputBuffer</code> is processing
     * character data.
     */
    private boolean processingChars;

    /**
     * Flag indicating whether or not all message chunks have been processed.
     */
//    private boolean contentRead;

    /**
     * Flag indicating whether or not this <code>InputBuffer</code> has been
     * closed.
     */
    private boolean closed;

    /**
     * Composite {@link Buffer} consisting of the bytes from the HTTP
     * message chunks.
     */
    private BuffersBuffer compositeBuffer;

    /**
     * {@link CharBuffer} containing charset decoded bytes.  This is only
     * allocated if {@link #processingChars} is <code>true</code>
     */
    private CharBuffer charBuf = CharBuffer.allocate(DEFAULT_BUFFER_SIZE);

    /**
     * The {@link Connection} associated with this {@link com.sun.grizzly.http.HttpRequestPacket}.
     */
    private Connection connection;

    /**
     * The mark position within the current binary content.  Marking is not
     * supported for character content.
     */
    private int markPos = -1;

    /**
     * Represents how many bytes can be read before {@link #markPos} is
     * invalidated.
     */
    private int readAheadLimit = -1;

    /**
     * Counter to track how many bytes have been read.  This counter is only
     * used with the byte stream has been marked.
     */
    private int readCount = 0;

    /**
     * The default character encoding to use if none is specified by the
     * {@link com.sun.grizzly.http.HttpRequestPacket}.
     */
    private String encoding = Constants.DEFAULT_CHARACTER_ENCODING;

    /**
     * The {@link CharsetDecoder} used to convert binary to character data.
     */
    private CharsetDecoder decoder;

    /**
     * This {@link ByteBuffer} is used when content being converted to characters
     * exceeds the capacity of the {@link #charBuf}.
     */
    private ByteBuffer remainder;

    /**
     * TODO: Documentation
     */
    private boolean contentRead;

    /**
     * TODO: Documentation
     */
    private ReadHandler handler;

    /**
     * TODO: Documentation
     */
    private int requestedSize;

    /**
     * TODO: Documentation
     */
    private boolean asyncEnabled;



    // ------------------------------------------------------------ Constructors


    /**
     * <p>
     * Per-request initialization required for the InputBuffer to function
     * properly.
     * </p>
     *
     * @param request the current request
     * @param ctx the FilterChainContext for the chain processing this request
     */
    public void initialize(HttpRequestPacket request, FilterChainContext ctx) {

        if (request == null) {
            throw new IllegalArgumentException();
        }
        if (ctx == null) {
            throw new IllegalArgumentException();
        }
        this.request = request;
        this.ctx = ctx;
        connection = ctx.getConnection();
        compositeBuffer = BuffersBuffer.create(connection.getTransport().getMemoryManager());
        Object message = ctx.getMessage();
        if (message instanceof HttpContent) {
            HttpContent content = (HttpContent) ctx.getMessage();
            if (content.getContent().hasRemaining()) {
                compositeBuffer.append(content.getContent());
            }
            contentRead = content.isLast();
        }

    }


    /**
     * <p>
     * Recycle this <code>InputBuffer</code> for reuse.
     * </p>
     */
    public void recycle() {

        compositeBuffer.tryDispose();

        charBuf.clear();

        connection = null;
        decoder = null;
        remainder = null;
        ctx = null;
        handler = null;

        processingChars = false;
        closed = false;
        contentRead = false;

        markPos = -1;
        readAheadLimit = -1;
        requestedSize = -1;
        readCount = 0;

        encoding = Constants.DEFAULT_CHARACTER_ENCODING;

    }


    /**
     * <p>
     * This method should be called if the InputBuffer is being used in conjunction
     * with a {@link java.io.Reader} implementation.  If this method is not called,
     * any character-based methods called on this <code>InputBuffer</code> will
     * throw a {@link IllegalStateException}.
     * </p>
     *
     * @throws IOException if an I/O error occurs enabling character processing
     */
    public void processingChars() throws IOException {

        processingChars = true;
        String enc = request.getCharacterEncoding();
        if (enc != null) {
            encoding = enc;
        }
        if (compositeBuffer.remaining() > 0) {
            remainder = compositeBuffer.toByteBuffer();
            charBuf.flip();
        }

    }


    // --------------------------------------------- InputStream-Related Methods



    /**
     * This method always blocks.
     * @see java.io.InputStream#read()
     */
    public int readByte() throws IOException {

        if (closed) {
            throw new IOException();
        }
        if (compositeBuffer.remaining() == 0) {
            if (fill(1) == -1) {
                return -1;
            }
        }
        if (readAheadLimit != -1) {
            readCount++;
            if (readCount > readAheadLimit) {
                markPos = -1;
            }
        }
        return compositeBuffer.get();

    }


    /**
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public int read(byte b[], int off, int len) throws IOException {

        if (closed) {
            throw new IOException();
        }

        if (len == 0) {
            return 0;
        }
        if (!asyncEnabled && compositeBuffer.remaining() == 0) {
            if (fill(len) == -1) {
                return -1;
            }
            if (compositeBuffer.remaining() < len) {
                fill(len);
            }
        }

        int nlen = Math.min(compositeBuffer.remaining(), len);
        if (readAheadLimit != -1) {
            readCount += nlen;
            if (readCount > readAheadLimit) {
                markPos = -1;
            }
        }
        compositeBuffer.get(b, off, nlen);
        compositeBuffer.shrink();
        return nlen;
        
    }


    /**
     * @see java.io.InputStream#available()
     */
    public int available() {

        return ((closed) ? 0 : compositeBuffer.remaining());

    }


    public Buffer getBuffer() {
        return compositeBuffer;
    }


    // -------------------------------------------------- Reader-Related Methods


    /**
     * @see java.io.Reader#read(java.nio.CharBuffer)
     */
    public int read(CharBuffer target) throws IOException {

        if (closed) {
            throw new IOException();
        }
        if (!processingChars) {
            throw new IllegalStateException();
        }
        
        if (charBuf.remaining() == 0) {
            if (fillChar(target.capacity(), !asyncEnabled) == -1) {
                return -1;
            }
        }

        int pos = target.position();
        charBuf.read(target);
        return (target.position() - pos);

    }


    /**
     * @see java.io.Reader#read()
     */
    public int readChar() throws IOException {
      
        if (closed) {
            throw new IOException();
        }
        if (!processingChars) {
            throw new IllegalStateException();
        }
        if (charBuf.remaining() == 0) {
            if (fillChar(1, true) == -1) {
                return -1;
            }
        }
        
        return charBuf.get();

    }


    /**
     * @see java.io.Reader#read(char[], int, int)
     */
    public int read(char cbuf[], int off, int len)
    throws IOException {

        if (closed) {
            throw new IOException();
        }
        if (!processingChars) {
            throw new IllegalStateException();
        }

        if (len == 0) {
            return 0;
        }
        if (charBuf.remaining() == 0) {
            if (fillChar(len, !asyncEnabled) == -1) {
                return -1;
            }
        }
        int nlen = Math.min(charBuf.remaining(), len);
        charBuf.get(cbuf, off, nlen);
        return nlen;

    }


    /**
     * @see java.io.Reader#ready()
     */
    public boolean ready() throws IOException {

        if (closed) {
            throw new IOException();
        }
        if (!processingChars) {
            throw new IllegalStateException();
        }
        return ((remainder != null && remainder.remaining() > 0)
                   || (request.isExpectContent() && (charBuf.remaining() != 0)));

    }


    public int availableChar() {
        int remaining = compositeBuffer.remaining();
        if (remaining == 0) {
            return 0;
        }

        final CharsetDecoder decoderLocal = getDecoder();
        return ((Float.valueOf(remaining / decoderLocal.averageCharsPerByte())).intValue());
    }


    // ---------------------------------------------------- Common Input Methods


    /**
     * <p>
     * Not Supported.
     * </p>
     *
     * @see java.io.InputStream#mark(int)
     */
    public void mark(int readAheadLimit) {

        if (processingChars) {
            throw new IllegalStateException();
        }
        markPos = compositeBuffer.position();
        if (readAheadLimit > 0) {
            this.readAheadLimit = readAheadLimit;
        }

    }


    /**
     * <p>
     * Only supported with binary data.
     * </p>
     *
     * @see java.io.InputStream#markSupported()
     */
    public boolean markSupported() {

        if (processingChars) {
            throw new IllegalStateException();
        }
        return true;

    }


    /**
     * <p>
     * Only supported with binary data.
     * </p>
     *
     * @see java.io.InputStream#reset()
     */
    public void reset() throws IOException {

        if (closed) {
            throw new IOException();
        }
        if (processingChars) {
            throw new IllegalStateException();
        }
        if (readAheadLimit == -1 && markPos == -1) {
            throw new IOException();
        }
        if (readAheadLimit != -1) {
            if (markPos == -1) {
                throw new IOException();
            }
            readCount = 0;
        }
        compositeBuffer.position(markPos);

    }


    /**
     * @see java.io.Closeable#close()
     */
    public void close() throws IOException {

        closed = true;
        compositeBuffer.dispose();

    }


    /**
     * TODO: Documentation
     * @see java.io.InputStream#skip(long)
     * @see java.io.Reader#skip(long)
     */
    public long skip(long n, boolean block) throws IOException {

        if (closed) {
            throw new IOException();
        }

        if (!block) {
            if (n > compositeBuffer.remaining()) {
                throw new IllegalStateException("Can not skip more bytes than available");
            }
        }

        if (!processingChars) {
            if (n <= 0) {
                return 0L;
            }
            if (block) {
                if (compositeBuffer.remaining() == 0) {
                    if (fill((int) n) == -1) {
                        return -1;
                    }
                }
                if (compositeBuffer.remaining() < n) {
                    fill((int) n);
                }
            }
            long nlen = Math.min(compositeBuffer.remaining(), n);
            compositeBuffer.position(compositeBuffer.position() + (int) nlen);
            return nlen;
        } else {
            if (n < 0) {
                throw new IllegalArgumentException();
            }
            if (n == 0) {
                return 0L;
            }
            if (charBuf.remaining() == 0) {
                if (fillChar((int) n, block) == -1) {
                    return 0;
                }
            }
            long nlen = Math.min(charBuf.remaining(), n);
            charBuf.position(charBuf.position() + (int) nlen);
            return nlen;
        }

    }


    /**
     * TODO: Documentation
     */
    public void finished() {
        if (!contentRead) {
            contentRead = true;
            handler.onAllDataRead();
        }
    }


    /**
     * TODO: Documentation
     * @return
     */
    public boolean isFinished() {
        return contentRead;
    }


    /**
     * TODO: Documentation
     *
     * @param handler
     */
    public void notifyAvailable(final ReadHandler handler) {
        notifyAvailable(handler, 0);
    }


    /**
     * TODO: Documentation
     *
     * @param handler
     * @param size
     */
    public void notifyAvailable(final ReadHandler handler,
                                final int size) {

        if (closed) {
            return;
        }
        requestedSize = size;
        this.handler = handler;

    }


    /**
     * TODO: Documentation
     *
     * @param buffer
     *
     * @return
     */
    public boolean append(final Buffer buffer) {

        if (buffer == null) {
            return true;
        }

        if (closed) {
            buffer.dispose();
        } else {
            final int addSize = buffer.remaining();
            if (addSize > 0) {
                compositeBuffer.append(buffer);
                if (compositeBuffer.remaining() > requestedSize) {
                    handler.onDataAvailable();
                    return true;
                }
            }
        }

        return false;
        
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }


    // --------------------------------------------------------- Private Methods


    /**
     * <p>
     * Used to add additional http message chunk content to {@link #compositeBuffer}.
     * </p>
     *
     * @param requestedLen how much content should attempt to be read
     *
     * @return the number of bytes actually read
     *
     * @throws IOException if an I/O error occurs while reading content
     */
    private int fill(int requestedLen) throws IOException {

        if (request.isExpectContent()) {
            try {
                connection.configureBlocking(true);
                int read = 0;
                while (read < requestedLen && request.isExpectContent()) {
                    ReadResult rr = ctx.read();
                    HttpContent c = (HttpContent) rr.getMessage();
                    Buffer b = c.getContent();
                    read += b.remaining();
                    compositeBuffer.append(c.getContent());
                }
                return read;
            } finally {
                connection.configureBlocking(false);
            }
        }
        return -1;

    }


    /**
     * <p>
     * Used to add additional http message chunk content to {@link #charBuf}.
     * </p>
     *
     * @param requestedLen how much content should attempt to be read
     *
     * @return the number of bytes actually read
     *
     * @throws IOException if an I/O error occurs while reading content
     */
    private int fillChar(int requestedLen, boolean block) throws IOException {

        if ((remainder != null && remainder.hasRemaining()) || !block) {
            final CharsetDecoder decoderLocal = getDecoder();
            charBuf.compact();
            int curPos = charBuf.position();
            final ByteBuffer bb = ((remainder != null) ? remainder : compositeBuffer.toByteBuffer());
            CoderResult result = decoderLocal.decode(bb, charBuf, false);
            int read = charBuf.position() - curPos;
            if (result == CoderResult.UNDERFLOW) {
                if (remainder == null) {
                    compositeBuffer.position(compositeBuffer.position() + bb.limit());
                    compositeBuffer.shrink();
                } else {
                    compositeBuffer.position(compositeBuffer.remaining());
                    compositeBuffer.shrink();
                    remainder = null;
                }
            }

            charBuf.flip();
            return read;
        }
        if (request.isExpectContent()) {
            try {
                connection.configureBlocking(true);
                int read = 0;
                CharsetDecoder decoderLocal = getDecoder();
                charBuf.compact();
                if (charBuf.position() == charBuf.capacity()) {
                    charBuf.clear();
                }

                while (read < requestedLen && request.isExpectContent()) {
                    ReadResult rr = ctx.read();
                    HttpContent c = (HttpContent) rr.getMessage();
                    ByteBuffer bytes = c.getContent().toByteBuffer();
                    CoderResult result = decoderLocal.decode(bytes, charBuf, false);
                    if (result == CoderResult.UNDERFLOW) {
                        read += charBuf.capacity() - charBuf.remaining();
                    }
                    if (c.isLast()) {
                        if (result == CoderResult.OVERFLOW) {
                            remainder = bytes;
                        }
                        break;
                    }
                    if (result == CoderResult.OVERFLOW) {
                        remainder = bytes;
                        break;
                    }
                }
                charBuf.flip();
                return read;
            } finally {
                connection.configureBlocking(false);
            }
        } 
        return -1;

    }


    /**
     * @return the {@link CharsetDecoder} that should be used when converting
     *  content from binary to character
     */
    private CharsetDecoder getDecoder() {

        if (decoder == null) {
            Charset cs = Utils.lookupCharset(encoding);
            decoder = cs.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPLACE);
            decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        }

        return decoder;

    }


    /**
     * TODO: Documentation
     *
     * @param sizeInBytes
     */
    private void arraySizeCheck(final int sizeInBytes) throws IOException {
        if (sizeInBytes > available()) {
            throw new BufferUnderflowException();
        }
    }

}
