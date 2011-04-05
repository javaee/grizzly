/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.io;

import java.io.EOFException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import static org.glassfish.grizzly.http.util.Charsets.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.concurrent.CancellationException;
import org.glassfish.grizzly.http.util.Charsets;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.utils.Exceptions;

/**
 * Abstraction exposing both byte and character methods to read content
 * from the HTTP messaging system in Grizzly.
 */
public class InputBuffer {

    /**
     * The {@link org.glassfish.grizzly.http.HttpRequestPacket} associated with this <code>InputBuffer</code>
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
    private boolean  processingChars;


    /**
     * Flag indicating whether or not this <code>InputBuffer</code> has been
     * closed.
     */
    private boolean closed;

    /**
     * The {@link Buffer} consisting of the bytes from the HTTP
     * message chunk(s).
     */
    private Buffer inputContentBuffer;

    /**
     * The {@link Connection} associated with this {@link org.glassfish.grizzly.http.HttpRequestPacket}.
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
     * {@link org.glassfish.grizzly.http.HttpRequestPacket}.
     */
    private String encoding = DEFAULT_CHARACTER_ENCODING;

    /**
     * The {@link CharsetDecoder} used to convert binary to character data.
     */
    private CharsetDecoder decoder;

    /**
     * Flag indicating all request content has been read.
     */
    private boolean contentRead;

    /**
     * The {@link ReadHandler} to be notified as content is read.
     */
    private ReadHandler handler;

    /**
     * The length of the content that must be read before notifying the
     * {@link ReadHandler}.
     */
    private int requestedSize;

    /**
     * Flag indicating whether or not async operations are being used on the
     * input streams.
     */
    private boolean asyncEnabled;

    /**
     * {@link CharBuffer} for converting a single character at a time.
     */
    private final CharBuffer singleCharBuf = CharBuffer.allocate(1);

    /**
     * Used to estimate how many characters can be produced from a variable
     * number of bytes.
     */
    private float averageCharsPerByte = 1.0f;


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
    public void initialize(final HttpRequestPacket request,
            final FilterChainContext ctx) {

        if (request == null) {
            throw new IllegalArgumentException("request cannot be null.");
        }
        if (ctx == null) {
            throw new IllegalArgumentException("ctx cannot be null.");
        }
        this.request = request;
        this.ctx = ctx;
        connection = ctx.getConnection();
        final Object message = ctx.getMessage();
        if (message instanceof HttpContent) {
            final HttpContent content = (HttpContent) message;
            inputContentBuffer = content.getContent();
            contentRead = content.isLast();
            content.recycle();
        }

    }


    /**
     * <p>
     * Recycle this <code>InputBuffer</code> for reuse.
     * </p>
     */
    public void recycle() {

        inputContentBuffer.tryDispose();
        inputContentBuffer = null;

        connection = null;
        decoder = null;
        ctx = null;
        handler = null;

        processingChars = false;
        closed = false;
        contentRead = false;
        asyncEnabled = false;

        markPos = -1;
        readAheadLimit = -1;
        requestedSize = -1;
        readCount = 0;

        averageCharsPerByte = 1.0f;

        encoding = DEFAULT_CHARACTER_ENCODING;

    }


    /**
     * <p>
     * This method should be called if the InputBuffer is being used in conjunction
     * with a {@link java.io.Reader} implementation.  If this method is not called,
     * any character-based methods called on this <code>InputBuffer</code> will
     * throw a {@link IllegalStateException}.
     * </p>
     */
    public void processingChars() {

        processingChars = true;
        String enc = request.getCharacterEncoding();
        if (enc != null) {
            encoding = enc;
            final CharsetDecoder localDecoder = getDecoder();
            averageCharsPerByte = localDecoder.averageCharsPerByte();
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
        if (!inputContentBuffer.hasRemaining()) {
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
        return inputContentBuffer.get() & 0xFF;

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
        if (!asyncEnabled && !inputContentBuffer.hasRemaining()) {
            if (fill(len) == -1) {
                return -1;
            }
        }

        int nlen = Math.min(inputContentBuffer.remaining(), len);
        if (readAheadLimit != -1) {
            readCount += nlen;
            if (readCount > readAheadLimit) {
                markPos = -1;
            }
        }
        inputContentBuffer.get(b, off, nlen);
        inputContentBuffer.shrink();
        return nlen;
        
    }


    /**
     * @see java.io.InputStream#available()
     */
    public int available() {

        return ((closed) ? 0 : inputContentBuffer.remaining());

    }


    /**
     * @return the underlying {@link Buffer} used to buffer incoming request
     *  data.
     */
    public Buffer getBuffer() {
        return inputContentBuffer;
    }


    /**
     * @return the {@link ReadHandler} current in use, if any.
     */
    public ReadHandler getReadHandler() {
        return handler;
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
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null.");
        }

        return fillChar(target.capacity(), target, !asyncEnabled, true);

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

        singleCharBuf.position(0);
        int read = read(singleCharBuf);
        if (read == -1) {
            return -1;
        }
        final char c = singleCharBuf.get(0);

        singleCharBuf.position(0);
        return c;

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

        final CharBuffer buf = CharBuffer.wrap(cbuf, off, len);
        return read(buf);

    }


    /**
     * @see java.io.Reader#ready()
     */
    public boolean ready() {

        if (closed) {
            return false;
        }
        if (!processingChars) {
            throw new IllegalStateException();
        }
        return (inputContentBuffer.hasRemaining()
                   || request.isExpectContent());

    }

    /**
     * Fill the buffer (blocking) up to the requested length.
     * 
     * @param length
     * @throws IOException
     */
    public void fillFully(final int length) throws IOException {
        int remaining = length - inputContentBuffer.remaining();

        if (remaining > 0) {
            fill(remaining);
        }
    }

    public int availableChar() {

        return ((int) (inputContentBuffer.remaining() * averageCharsPerByte));

    }


    // ---------------------------------------------------- Common Input Methods


    /**
     * <p>
     * Only supported with binary data.
     * </p>
     *
     * @see java.io.InputStream#mark(int)
     */
    public void mark(int readAheadLimit) {

        if (processingChars) {
            throw new IllegalStateException();
        }

        if (readAheadLimit > 0) {
            markPos = inputContentBuffer.position();
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
        inputContentBuffer.position(markPos);

    }


    /**
     * @see java.io.Closeable#close()
     */
    public void close() throws IOException {

        closed = true;

    }


    /**
     * Skips the specified number of bytes/characters.
     *
     * @see java.io.InputStream#skip(long)
     * @see java.io.Reader#skip(long)
     *
     * @throws IllegalStateException if the stream that is using this <code>InputBuffer</code>
     *  is configured for asynchronous communication and the number of bytes/characters
     *  being skipped exceeds the number of bytes available in the buffer.
     */
    public long skip(final long n, final boolean block) throws IOException {

        if (closed) {
            throw new IOException();
        }

        if (!block) {
            if (n > inputContentBuffer.remaining()) {
                throw new IllegalStateException("Can not skip more bytes than available");
            }
        }

        if (!processingChars) {
            if (n <= 0) {
                return 0L;
            }
            if (block) {
                if (!inputContentBuffer.hasRemaining()) {
                    if (fill((int) n) == -1) {
                        return -1;
                    }
                }
                if (inputContentBuffer.remaining() < n) {
                    fill((int) n);
                }
            }
            long nlen = Math.min(inputContentBuffer.remaining(), n);
            inputContentBuffer.position(inputContentBuffer.position() + (int) nlen);
            inputContentBuffer.shrink();
            
            return nlen;
        } else {
            if (n < 0) { // required by java.io.Reader.skip()
                throw new IllegalArgumentException();
            }
            if (n == 0) {
                return 0L;
            }
            final CharBuffer skipBuffer = CharBuffer.allocate((int) n);
                if (fillChar((int) n, skipBuffer, block, true) == -1) {
                    return 0;
                }
            return Math.min(skipBuffer.remaining(), n);
        }

    }


    /**
     * When invoked, this method will call {@link org.glassfish.grizzly.http.server.io.ReadHandler#onAllDataRead()}
     * on the current {@link ReadHandler} (if any).
     *
     * This method shouldn't be invoked by developers directly.
     */
    public void finished() throws IOException {
        if (!contentRead) {
            contentRead = true;
            final ReadHandler localHandler = handler;
            if (localHandler != null) {
                handler = null;
                try {
                    localHandler.onAllDataRead();
                } catch (Throwable t) {
                    localHandler.onError(t);
                    throw Exceptions.makeIOException(t);
                }
            }
        }
    }


    /**
     * @return <code>true</code> if all request data has been read, otherwise
     *  returns <code>false</code>.
     */
    public boolean isFinished() {
        return contentRead;
    }


    /**
     * Installs a {@link ReadHandler} that will be notified when any data
     * becomes available to read without blocking.
     *
     * @param handler the {@link ReadHandler} to invoke.
     *
     * @return <code>true<code> if the specified <code>handler</code> has
     *  been accepted and will be notified as data becomes available to write,
     *  otherwise returns <code>false</code> which means data is available to
     *  be read without blocking.
     *
     * @throws IllegalArgumentException if <code>handler</code> is <code>null</code>,
     *  or if <code>size</code> is less than zero.
     * @throws IllegalStateException if an attempt is made to register a handler
     *  before an existing registered handler has been invoked or if all request
     *  data has already been read.
     */
    public void notifyAvailable(final ReadHandler handler) {
        notifyAvailable(handler, 0);
    }


    /**
     * Installs a {@link ReadHandler} that will be notified when the specified
     * amount of data is available to be read without blocking.
     *
     * @param handler the {@link ReadHandler} to invoke.
     * @param size the minimum number of bytes that must be available before
     *  the {@link ReadHandler} is notified.
     *
     * @throws IllegalArgumentException if <code>handler</code> is <code>null</code>,
     *  or if <code>size</code> is less than zero.
     * @throws IllegalStateException if an attempt is made to register a handler
     *  before an existing registered handler has been invoked.
     */
    public void notifyAvailable(final ReadHandler handler, final int size) {

        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null.");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }
        if (this.handler != null) {
            throw new IllegalStateException("Illegal attempt to register a new handler before the existing handler has been notified");
        }

        // If we don't expect more data - call onAllDataRead() directly
        if (closed || isFinished()) {
            try {
                handler.onAllDataRead();
            } catch (IOException ioe) {
                handler.onError(ioe);
            }

            return;
        }

        final int available = ((processingChars) ? availableChar() : available());
        if (shouldNotifyNow(size, available)) {
            try {
                handler.onDataAvailable();
            } catch (IOException ioe) {
                handler.onError(ioe);
            }
            return;
        }

        requestedSize = size;
        this.handler = handler;

    }


    /**
     * Appends the specified {@link Buffer} to the internal composite
     * {@link Buffer}.
     *
     * @param buffer the {@link Buffer} to append
     *
     * @return <code>true</code> if {@link ReadHandler#onDataAvailable()}
     *  callback was invoked, otherwise returns <code>false</code>.
     *
     * @throws IOException if an error occurs appending the {@link Buffer}
     */
    public boolean append(final Buffer buffer) throws IOException {

        if (buffer == null) {
            return false;
        }

        if (closed) {
            buffer.dispose();
        } else {
            final int addSize = buffer.remaining();
            if (addSize > 0) {
                updateInputContentBuffer(buffer);
                if (handler != null) {
                    final int available = ((processingChars)
                            ? availableChar()
                            : available());
                    if (available > requestedSize) {
                        final ReadHandler localHandler = handler;
                        handler = null;
                        try {
                            localHandler.onDataAvailable();
                        } catch (Throwable t) {
                            localHandler.onError(t);
                            throw Exceptions.makeIOException(t);
                        }

                        return true;
                    }
                }
            }
        }

        return false;
        
    }


    /**
     * @return if this buffer is being used to process asynchronous data.
     */
    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }


    /**
     * Sets the asynchronous processing state of this <code>InputBuffer</code>.
     *
     * @param asyncEnabled <code>true</code> if this <code>InputBuffer<code>
     *  is to be used to process asynchronous request data.
     */
    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }


    /**
     * <p>
     * Invoke {@link ReadHandler#onError(Throwable)} (assuming a {@link ReadHandler}
     * is available) } passing a  {#link CancellationException}
     * if the current {@link Connection} is open, or a {#link EOFException} if
     * the connection was unexpectedly closed.
     * </p>
     *
     * @since 2.0.1
     */
    public void terminate() {
        final ReadHandler localHandler = handler;
        if (localHandler != null) {
            handler = null;
            if (connection.isOpen()) {
                localHandler.onError(new CancellationException());
            } else {
                localHandler.onError(new EOFException());
            }
        }
    }


    // --------------------------------------------------------- Private Methods


    /**
     * <p>
     * Used to add additional http message chunk content to {@link #inputContentBuffer}.
     * </p>
     *
     * @param requestedLen how much content should attempt to be read
     *
     * @return the number of bytes actually read
     *
     * @throws IOException if an I/O error occurs while reading content
     */
    private int fill(final int requestedLen) throws IOException {

        int read = 0;
        while (read < requestedLen && request.isExpectContent()) {
            final ReadResult rr = ctx.read();
            final HttpContent c = (HttpContent) rr.getMessage();
            final Buffer b = c.getContent();
            read += b.remaining();
            updateInputContentBuffer(b);
            rr.recycle();
            c.recycle();
        }

        if (read > 0 || requestedLen == 0) {
            return read;
        }

        return -1;

    }


    /**
     * <p>
     * Used to convert bytes to char.
     * </p>
     *
     * @param requestedLen how much content should attempt to be read
     *
     * @return the number of bytes actually read
     *
     * @throws IOException if an I/O error occurs while reading content
     */
    private int fillChar(int requestedLen,
                         CharBuffer dst,
                         boolean block,
                         boolean flip) throws IOException {

        if (inputContentBuffer.hasRemaining() || !block) {
            final CharsetDecoder decoderLocal = getDecoder();
            int charPos = dst.position();
            final ByteBuffer bb = inputContentBuffer.toByteBuffer();
            int bbPos = bb.position();
            CoderResult result = decoderLocal.decode(bb, dst, false);

            int readChars = dst.position() - charPos;
            int readBytes = bb.position() - bbPos;
            bb.position(bbPos);
            if (result == CoderResult.UNDERFLOW) {
                inputContentBuffer.position(inputContentBuffer.position() + readBytes);
                inputContentBuffer.shrink();
            } else {
                inputContentBuffer.position(inputContentBuffer.position() + readBytes);
                inputContentBuffer.shrink();
            }
            
            if (inputContentBuffer.hasRemaining() && readChars < requestedLen) {
                readChars += fillChar(0, dst, false, false);
            }

            if (flip) {
                dst.flip();
            }
            return readChars;
        }
        
        if (request.isExpectContent()) {
            int read = 0;
            CharsetDecoder decoderLocal = getDecoder();

            boolean isNeedMoreInput = false; // true, if content in composite buffer is not enough to produce even 1 char
            boolean last = false;

            while (read < requestedLen && request.isExpectContent()) {

                if (isNeedMoreInput || !inputContentBuffer.hasRemaining()) {
                    final ReadResult rr = ctx.read();
                    final HttpContent c = (HttpContent) rr.getMessage();
                    updateInputContentBuffer(c.getContent());
                    last = c.isLast();

                    rr.recycle();
                    c.recycle();
                    isNeedMoreInput = false;
                }

                final ByteBuffer bytes = inputContentBuffer.toByteBuffer();

                final int bytesPos = bytes.position();
                final int dstPos = dst.position();

                final CoderResult result = decoderLocal.decode(bytes, dst, false);

                final int producesChars = dst.position() - dstPos;
                final int consumedBytes = bytes.position() - bytesPos;
               
                read += producesChars;
                
                if (consumedBytes > 0) {
                    bytes.position(bytesPos);
                    inputContentBuffer.position(inputContentBuffer.position() + consumedBytes);
                    inputContentBuffer.shrink();
                } else {
                    isNeedMoreInput = true;
                }

                if (last || result == CoderResult.OVERFLOW) {
                    break;
                }
            }

            if (flip) {
                dst.flip();
            }
            
            if (last && read == 0) {
                read = -1;
            }
            return read;
        }
        return -1;

    }


    private void updateInputContentBuffer(final Buffer buffer)  {

        if (inputContentBuffer.hasRemaining()) {
            toCompositeInputContentBuffer().append(buffer);
        } else {
            inputContentBuffer.tryDispose();
            inputContentBuffer = buffer;
        }

    }


    /**
     * @param size the amount of data that must be available for a {@link ReadHandler}
     *  to be notified.
     * @param available the amount of data currently available.
     *
     * @return <code>true</code> if the handler should be notified during a call
     *  to {@link #notifyAvailable(ReadHandler)} or {@link #notifyAvailable(ReadHandler, int)},
     *  otherwise <code>false</code>
     */
    private static boolean shouldNotifyNow(int size, int available) {

        return (available != 0 && available >= size);

    }


    /**
     * @return the {@link CharsetDecoder} that should be used when converting
     *  content from binary to character
     */
    private CharsetDecoder getDecoder() {

        if (decoder == null) {
            Charset cs = Charsets.lookupCharset(encoding);
            decoder = cs.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPLACE);
            decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        }

        return decoder;

    }

    private CompositeBuffer toCompositeInputContentBuffer() {
        if (!inputContentBuffer.isComposite()) {
            final CompositeBuffer compositeBuffer = CompositeBuffer.newBuffer(
                    connection.getTransport().getMemoryManager());

            compositeBuffer.allowBufferDispose(true);
            compositeBuffer.allowInternalBuffersDispose(true);

            compositeBuffer.append(inputContentBuffer);
            inputContentBuffer = compositeBuffer;
        }

        return (CompositeBuffer) inputContentBuffer;
    }

}
