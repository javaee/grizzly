/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.io;

import org.glassfish.grizzly.http.HttpBrokenContent;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpTrailer;
import java.io.EOFException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.threadpool.Threads;
import org.glassfish.grizzly.utils.Charsets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.HttpBrokenContentException;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.utils.Exceptions;

import static org.glassfish.grizzly.http.util.Constants.*;

/**
 * Abstraction exposing both byte and character methods to read content
 * from the HTTP messaging system in Grizzly.
 */
public class InputBuffer {
    private static final Logger LOGGER = Grizzly.logger(InputBuffer.class);
    private static final Level LOGGER_LEVEL = Level.FINER;
    
    /**
     * The {@link org.glassfish.grizzly.http.HttpHeader} associated with this <code>InputBuffer</code>
     */
    private HttpHeader httpHeader;

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
    private String encoding = DEFAULT_HTTP_CHARACTER_ENCODING;

    /**
     * The {@link CharsetDecoder} used to convert binary to character data.
     */
    private CharsetDecoder decoder;

    /**
     * CharsetDecoders cache
     */
    private final Map<String, CharsetDecoder> decoders =
            new HashMap<String, CharsetDecoder>();

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
     * {@link CharBuffer} for converting a single character at a time.
     */
    private final CharBuffer singleCharBuf =
            (CharBuffer) CharBuffer.allocate(1).position(1); // create CharBuffer w/ 0 chars remaining

    /**
     * Used to estimate how many characters can be produced from a variable
     * number of bytes.
     */
    private float averageCharsPerByte = 1.0f;

    /**
     * Flag shows if we're currently waiting for input data asynchronously
     * (is OP_READ enabled for the Connection)
     */
    private boolean isWaitingDataAsynchronously;
    
    // ------------------------------------------------------------ Constructors


    /**
     * <p>
     * Per-request initialization required for the InputBuffer to function
     * properly.
     * </p>
     *
     * @param httpHeader the header from which input will be obtained.
     * @param ctx the FilterChainContext for the chain processing this request
     */
    public void initialize(final HttpHeader httpHeader,
                           final FilterChainContext ctx) {


        if (ctx == null) {
            throw new IllegalArgumentException("ctx cannot be null.");
        }
        this.httpHeader = httpHeader;
        
        this.ctx = ctx;
        connection = ctx.getConnection();
        final Object message = ctx.getMessage();
        if (message instanceof HttpContent) {
            final HttpContent content = (HttpContent) message;
            
            // Check if HttpContent is chunked message trailer w/ headers
            checkHttpTrailer(content);
            updateInputContentBuffer(content.getContent());
            contentRead = content.isLast();
            content.recycle();
            
            if (LOGGER.isLoggable(LOGGER_LEVEL)) {
                log("InputBuffer %s initialize with ready content: %s",
                        this, inputContentBuffer);
            }
        }

    }

    /**
     * Set the default character encoding for this <tt>InputBuffer</tt>, which
     * would be applied if no encoding was explicitly set on HTTP
     * {@link org.glassfish.grizzly.http.HttpRequestPacket} and character decoding
     * wasn't started yet.
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setDefaultEncoding(final String encoding) {
        this.encoding = encoding;
    }
    
    /**
     * <p>
     * Recycle this <code>InputBuffer</code> for reuse.
     * </p>
     */
    public void recycle() {

        inputContentBuffer.tryDispose();
        inputContentBuffer = null;

        singleCharBuf.position(singleCharBuf.limit());
        
        connection = null;
        decoder = null;
        ctx = null;
        handler = null;

        processingChars = false;
        closed = false;
        contentRead = false;

        markPos = -1;
        readAheadLimit = -1;
        requestedSize = -1;
        readCount = 0;

        averageCharsPerByte = 1.0f;
        
        isWaitingDataAsynchronously = false;

        encoding = DEFAULT_HTTP_CHARACTER_ENCODING;

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

        if (!processingChars) {
            processingChars = true;
            final String enc = httpHeader.getCharacterEncoding();
            if (enc != null) {
                encoding = enc;
                final CharsetDecoder localDecoder = getDecoder();
                averageCharsPerByte = localDecoder.averageCharsPerByte();
            }
        }

    }


    // --------------------------------------------- InputStream-Related Methods



    /**
     * This method always blocks.
     * @see java.io.InputStream#read()
     */
    public int readByte() throws IOException {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s readByte. Ready content: %s",
                    this, inputContentBuffer);
        }

        if (closed) {
            throw new IOException("Already closed");
        }
        if (!inputContentBuffer.hasRemaining()) {
            if (fill(1) == -1) {
                return -1;
            }
        }
        
        checkMarkAfterRead(1);
        return inputContentBuffer.get() & 0xFF;

    }


    /**
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public int read(final byte b[], final int off, final int len) throws IOException {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s read byte array of len: %s. Ready content: %s",
                    this, len, inputContentBuffer);
        }

        if (closed) {
            throw new IOException("Already closed");
        }

        if (len == 0) {
            return 0;
        }
        if (!inputContentBuffer.hasRemaining()) {
            if (fill(1) == -1) {
                return -1;
            }
        }

        int nlen = Math.min(inputContentBuffer.remaining(), len);
        inputContentBuffer.get(b, off, nlen);
        
        if (!checkMarkAfterRead(nlen)) {
            inputContentBuffer.shrink();
        }
        
        return nlen;
        
    }

    /**
     * Depending on the <tt>InputBuffer</tt> mode, method will return either
     * number of available bytes or characters ready to be read without blocking.
     * 
     * @return depending on the <tt>InputBuffer</tt> mode, method will return
     * either number of available bytes or characters ready to be read without
     * blocking.
     */
    public int readyData() {
        if (closed) return 0;
        
        return ((processingChars) ? availableChar() : available());
    }
    
    /**
     * @see java.io.InputStream#available()
     */
    public int available() {

        return ((closed) ? 0 : inputContentBuffer.remaining());

    }

    /**
     * Returns the duplicate of the underlying {@link Buffer} used to buffer
     * incoming request data. The content of the returned buffer will be that
     * of the underlying buffer. Changes to returned buffer's content will be
     * visible in the underlying buffer, and vice versa; the two buffers'
     * position, limit, and mark values will be independent.
     * 
     * @return the duplicate of the underlying
     * {@link Buffer} used to buffer incoming request data.
     */
    public Buffer getBuffer() {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s getBuffer. Ready content: %s",
                    this, inputContentBuffer);
        }

        return inputContentBuffer.duplicate();
    }

    /**
     * @return the underlying {@link Buffer} used to buffer incoming request
     *  data. Unlike {@link #getBuffer()}, this method detaches the returned
     * {@link Buffer}, so user code becomes responsible for handling
     * the {@link Buffer}.
     */
    public Buffer readBuffer() {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s readBuffer. Ready content: %s",
                    this, inputContentBuffer);
        }
        
        return readBuffer(inputContentBuffer.remaining());
    }
    
    /**
     * @param size the requested size of the {@link Buffer} to be returned.
     * 
     * @return the {@link Buffer} of a given size, which represents a chunk
     * of the underlying {@link Buffer} which contains incoming request
     *  data. This method detaches the returned
     * {@link Buffer}, so user code becomes responsible for handling its life-cycle.
     */
    public Buffer readBuffer(final int size) {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s readBuffer(size), size: %s. Ready content: %s",
                    this, size, inputContentBuffer);
        }
        
        final int remaining = inputContentBuffer.remaining();
        if (size > remaining) {
            throw new IllegalStateException("Can not read more bytes than available");
        }
        
        final Buffer buffer;
        if (size == remaining) {
            buffer = inputContentBuffer;
            inputContentBuffer = Buffers.EMPTY_BUFFER;
        } else {
            final Buffer tmpBuffer = inputContentBuffer.split(
                    inputContentBuffer.position() + size);
            buffer = inputContentBuffer;
            inputContentBuffer = tmpBuffer;
        }
        
        return buffer;
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
    public int read(final CharBuffer target) throws IOException {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s read(CharBuffer). Ready content: %s",
                    this, inputContentBuffer);
        }

        if (closed) {
            throw new IOException("Already closed");
        }
        if (!processingChars) {
            throw new IllegalStateException();
        }
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null.");
        }
        final int read = fillChars(1, target);
        checkMarkAfterRead(read);
        
        return read;

    }


    /**
     * @see java.io.Reader#read()
     */
    public int readChar() throws IOException {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s readChar. Ready content: %s",
                    this, inputContentBuffer);
        }
      
        if (closed) {
            throw new IOException("Already closed");
        }
        if (!processingChars) {
            throw new IllegalStateException();
        }

        if (!singleCharBuf.hasRemaining()) {
            singleCharBuf.clear();
            int read = read(singleCharBuf);
            if (read == -1) {
                return -1;
            }
        }

        return singleCharBuf.get();

    }


    /**
     * @see java.io.Reader#read(char[], int, int)
     */
    public int read(final char cbuf[], final int off, final int len)
    throws IOException {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s read char array, len: %s. Ready content: %s",
                    this, len, inputContentBuffer);
        }

        if (closed) {
            throw new IOException("Already closed");
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
                   || httpHeader.isExpectContent());

    }
    
    /**

    /**
     * Fill the buffer (blocking) up to the requested length.
     * 
     * @param length how much content should attempt to buffer,
     * <code>-1</code> means buffer entire request.
     * 
     * @throws IOException
     */
    public void fillFully(final int length) throws IOException {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s fillFully, len: %s. Ready content: %s",
                this, length, inputContentBuffer);
        }
        
        if (length == 0) return;
        
        if (length > 0) {
            final int remaining = length - inputContentBuffer.remaining();

            if (remaining > 0) {
                fill(remaining);
            }
        } else {
            fill(-1);
        }
    }

    public int availableChar() {

        if (!singleCharBuf.hasRemaining()) {
            // fill the singleCharBuf to make sure we have at least one char
            singleCharBuf.clear();
            if (fillAvailableChars(1, singleCharBuf) == 0) {
                singleCharBuf.position(singleCharBuf.limit());
                return 0;
            }
            
            singleCharBuf.flip();
        }
        
        // we have 1 char pre-decoded + estimation for the rest byte[]->char[] count.
        return 1 + ((int) (inputContentBuffer.remaining() * averageCharsPerByte));

    }


    // ---------------------------------------------------- Common Input Methods


    /**
     * <p>
     * Supported with binary and character data.
     * </p>
     *
     * @see java.io.InputStream#mark(int)
     * @see java.io.Reader#mark(int)
     */
    public void mark(final int readAheadLimit) {
        
        if (readAheadLimit > 0) {
            markPos = inputContentBuffer.position();
            readCount = 0;
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
            throw new IOException("Already closed");
        }

        if (readAheadLimit == -1) {
            throw new IOException("Mark not set");
        }
        
        readCount = 0;
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
     * @deprecated pls. use {@link #skip(long)}, the <tt>block</tt> parameter will be ignored
     */
    public long skip(final long n, @SuppressWarnings("UnusedParameters") final boolean block) throws IOException {
        return skip(n);
    }

    /**
     * Skips the specified number of bytes/characters.
     *
     * @see java.io.InputStream#skip(long)
     * @see java.io.Reader#skip(long)
     */
    public long skip(final long n) throws IOException {
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s skip %s bytes. Ready content: %s",
                    this, n, inputContentBuffer);
        }

        if (!processingChars) {
            if (n <= 0) {
                return 0L;
            }
            
            if (!inputContentBuffer.hasRemaining()) {
                if (fill((int) n) == -1) {
                    return -1;
                }
            }
            if (inputContentBuffer.remaining() < n) {
                fill((int) n);
            }
            
            long nlen = Math.min(inputContentBuffer.remaining(), n);
            inputContentBuffer.position(inputContentBuffer.position() + (int) nlen);
            
            if (!checkMarkAfterRead(n)) {
                inputContentBuffer.shrink();
            }
            
            return nlen;
        } else {
            if (n < 0) { // required by java.io.Reader.skip()
                throw new IllegalArgumentException();
            }
            if (n == 0) {
                return 0L;
            }
            final CharBuffer skipBuffer = CharBuffer.allocate((int) n);
            if (fillChars((int) n, skipBuffer) == -1) {
                return 0;
            }
            return Math.min(skipBuffer.remaining(), n);
        }

    }


    /**
     * When invoked, this method will call {@link org.glassfish.grizzly.ReadHandler#onAllDataRead()}
     * on the current {@link ReadHandler} (if any).
     *
     * This method shouldn't be invoked by developers directly.
     */
    protected void finished() {
        if (!contentRead) {
            contentRead = true;
            final ReadHandler localHandler = handler;
            if (localHandler != null) {
                handler = null;
                invokeHandlerAllRead(localHandler, getThreadPool());
            }
        }
    }
    
    private void finishedInTheCurrentThread(final ReadHandler readHandler) {
        if (!contentRead) {
            contentRead = true;
            if (readHandler != null) {
                invokeHandlerAllRead(readHandler, null);
            }
        }
    }

    private void invokeHandlerAllRead(final ReadHandler readHandler,
            final Executor executor) {
        if (executor != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        readHandler.onAllDataRead();
                    } catch (Throwable t) {
                        readHandler.onError(t);
                    }
                }
            });
        } else {
            try {
                readHandler.onAllDataRead();
            } catch (Throwable t) {
                readHandler.onError(t);
            }
        }
    }
    

    public void replayPayload(final Buffer buffer) {
        if (!isFinished()) {
            throw new IllegalStateException("Can't replay when InputBuffer is not closed");
        }
        
        if (LOGGER.isLoggable(LOGGER_LEVEL)) {
            log("InputBuffer %s replayPayload to %s", this, buffer);
        }
        
        closed = false;
        readCount = 0;
        
        readAheadLimit = -1;
        markPos = -1;
        
        inputContentBuffer = buffer;
    }
    
    /**
     * @return <code>true</code> if all request data has been read, otherwise
     *  returns <code>false</code>.
     */
    public boolean isFinished() {
        return contentRead;
    }

    /**
     * @return <code>true</code> if this <tt>InputBuffer</tt> is closed, otherwise
     *  returns <code>false</code>.
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * Installs a {@link ReadHandler} that will be notified when any data
     * becomes available to read without blocking.
     *
     * @param handler the {@link ReadHandler} to invoke.
     *
     * @throws IllegalArgumentException if <code>handler</code> is <code>null</code>.
     * @throws IllegalStateException if an attempt is made to register a handler
     *  before an existing registered handler has been invoked or if all request
     *  data has already been read.
     */
    public void notifyAvailable(final ReadHandler handler) {
        notifyAvailable(handler, 1);
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
     *  or if <code>size</code> is less or equal to zero.
     * @throws IllegalStateException if an attempt is made to register a handler
     *  before an existing registered handler has been invoked.
     */
    public void notifyAvailable(final ReadHandler handler, final int size) {

        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size should be positive integer");
        }
        if (this.handler != null) {
            throw new IllegalStateException("Illegal attempt to register a new handler before the existing handler has been notified");
        }

        // If we don't expect more data - call onAllDataRead() directly
        if (closed || isFinished()) {
            try {
                handler.onAllDataRead();
            } catch (Throwable ioe) {
                handler.onError(ioe);
            }

            return;
        }

        final int available = readyData();
        if (shouldNotifyNow(size, available)) {
            try {
                handler.onDataAvailable();
            } catch (Throwable ioe) {
                handler.onError(ioe);
            }
            return;
        }

        requestedSize = size;
        this.handler = handler;

        if (!isWaitingDataAsynchronously) {
            isWaitingDataAsynchronously = true;
            initiateAsyncronousDataReceiving();
        }
    }


    /**
     * Appends the specified {@link Buffer} to the internal composite
     * {@link Buffer}.
     *
     * @param httpContent the {@link HttpContent} to append
     *
     * @return <code>true</code> if {@link ReadHandler}
     *  callback was invoked, otherwise returns <code>false</code>.
     *
     * @throws IOException if an error occurs appending the {@link Buffer}
     */
    public boolean append(final HttpContent httpContent) throws IOException {
        
        // Stop waiting for data asynchronously and enable it again
        // only if we have a handler registered, which requirement
        // (expected size) is not met.
        isWaitingDataAsynchronously = false;
        
        // check if it's broken HTTP content message or not
        if (!HttpContent.isBroken(httpContent)) {
            final Buffer buffer = httpContent.getContent();

            if (closed) {
                buffer.dispose();
                return false;
            }
            
            final ReadHandler localHandler = handler;
            
            final boolean isLast = httpContent.isLast();
            
            // if we have a handler registered - switch the flag to true
            boolean askForMoreDataInThisThread = !isLast && localHandler != null;
            boolean invokeDataAvailable = false;

            if (buffer.hasRemaining()) {
                updateInputContentBuffer(buffer);
                if (localHandler != null) {
                    final int available = readyData();
                    if (available >= requestedSize) {
                        invokeDataAvailable = true;
                        askForMoreDataInThisThread = false;
                    }
                }
            }
            
            if (askForMoreDataInThisThread) {
                // There is a ReadHandler registered, but it requested more
                // data to be available before we can notify it - so wait for
                // more data to come
                isWaitingDataAsynchronously = true;
                return true;
            }
            
            handler = null;
            
            if (isLast) {
                checkHttpTrailer(httpContent);
            }
            
            invokeHandlerOnProperThread(localHandler,
                    invokeDataAvailable, isLast);
            
        } else { // broken content
            final ReadHandler localHandler = handler;
            handler = null;
            invokeErrorHandlerOnProperThread(localHandler,
                                             ((HttpBrokenContent) httpContent).getException());
        }
        
        return false;
    }


    /**
     * @return if this buffer is being used to process asynchronous data.
     * @deprecated will always return true
     */
    public boolean isAsyncEnabled() {
//        return asyncEnabled;
        return true;
    }


    /**
     * Sets the asynchronous processing state of this <code>InputBuffer</code>.
     *
     * @param asyncEnabled <code>true</code> if this <code>InputBuffer<code>
     *  is to be used to process asynchronous request data.
     * @deprecated <tt>InputBuffer</tt> always supports async mode
     */
    @SuppressWarnings("UnusedDeclaration")
    public void setAsyncEnabled(boolean asyncEnabled) {
//        this.asyncEnabled = asyncEnabled;
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
            // call in the current thread, because otherwise handler executed
            // in the different thread may deal with recycled Request/Response objects
            localHandler.onError(
                    connection.isOpen()
                    ? new CancellationException()
                    : new EOFException());
        }
    }

    /**
     * Initiates asynchronous data receiving.
     *
     * This is service method, usually users don't have to call it explicitly.
     */
    public void initiateAsyncronousDataReceiving() {
        // fork the FilterChainContext execution
        // keep the current FilterChainContext suspended, but make a copy and resume it
        ctx.fork(ctx.getStopAction());
    }
    
    // --------------------------------------------------------- Private Methods

    /**
     * @return {@link Executor}, which will be used for notifying user
     * registered {@link ReadHandler}.
     */
    protected Executor getThreadPool() {
        if (!Threads.isService()) {
            return null;
        }
        final ExecutorService es = connection.getTransport().getWorkerThreadPool();
        return es != null && !es.isShutdown() ? es : null;
    }

    private void invokeErrorHandlerOnProperThread(final ReadHandler localHandler,
                                                  final Throwable error) {
        if (!closed && localHandler != null) {
            final Executor executor = getThreadPool();
            if (executor != null) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        localHandler.onError(error);
                    }
                });
            } else {
                localHandler.onError(error);
            }
        }
    }

    private void invokeHandlerOnProperThread(final ReadHandler localHandler,
                                             final boolean invokeDataAvailable,
                                             final boolean isLast)
    throws IOException {
        final Executor executor = getThreadPool();

        if (executor != null) {
            executor.execute(new Runnable() {

                @Override
                public void run() {
                    invokeHandler(localHandler, invokeDataAvailable, isLast);
                }
            });
        } else {
            invokeHandler(localHandler, invokeDataAvailable, isLast);
        }
    }

    private void invokeHandler(final ReadHandler localHandler,
                               final boolean invokeDataAvailable,
                               final boolean isLast) {
        try {
            if (invokeDataAvailable) {
                localHandler.onDataAvailable();
            }

            if (isLast) {
                finishedInTheCurrentThread(localHandler);
            }
        } catch (Throwable t) {
            localHandler.onError(t);
        }
    }

    /**
     * Read next chunk of data in this thread, block if needed.
     * 
     * @return {@link HttpContent}
     * @throws IOException 
     */
    protected HttpContent blockingRead() throws IOException {
        final ReadResult rr = ctx.read();
        final HttpContent c = (HttpContent) rr.getMessage();
        rr.recycle();
        return c;
    }

    /**
     * <p>
     * Used to add additional HTTP message chunk content to {@link #inputContentBuffer}.
     * </p>
     *
     * @param requestedLen how much content should attempt to be read,
     * <code>-1</code> means read till the end of the message.
     *
     * @return the number of bytes actually read
     *
     * @throws IOException if an I/O error occurs while reading content
     */
    private int fill(final int requestedLen) throws IOException {

        int read = 0;
        while ((requestedLen == -1 || read < requestedLen) &&
                httpHeader.isExpectContent()) {
            
            final HttpContent c = blockingRead();
            
            final boolean isLast = c.isLast();
            // Check if HttpContent is chunked message trailer w/ headers
            checkHttpTrailer(c);
            
            final Buffer b;
            try {
                b = c.getContent();
            } catch (HttpBrokenContentException e) {
                final Throwable cause = e.getCause();
                throw Exceptions.makeIOException(cause != null ? cause : e);
            }
            
            read += b.remaining();
            updateInputContentBuffer(b);
            c.recycle();
            
            if (isLast) {
                finished();
                break;
            }
        }

        if (read > 0 || requestedLen == 0) {
            return read;
        }

        return -1;

    }


    /**
     * <p>
     * Used to convert bytes to chars.
     * </p>
     *
     * @param requestedLen how much content should attempt to be read
     *
     * @return the number of chars actually read
     *
     * @throws IOException if an I/O error occurs while reading content
     */
    private int fillChars(final int requestedLen,
                         final CharBuffer dst) throws IOException {

        int read = 0;
        
        // 1) Check pre-decoded singleCharBuf
        if (dst != singleCharBuf && singleCharBuf.hasRemaining()) {
            dst.put(singleCharBuf.get());
            read = 1;
        }
        
        // 2) Decode available byte[] -> char[]
        if (inputContentBuffer.hasRemaining()) {
            read += fillAvailableChars(requestedLen - read, dst);
        }
        
        if (read >= requestedLen) {
            dst.flip();
            return read;
        }
        
        // 3) If we don't expect more data - return what we've read so far
        if (!httpHeader.isExpectContent()) {
            dst.flip();
            return read > 0 ? read : -1;
        }
        
        // 4) Try to read more data (we may block)
        CharsetDecoder decoderLocal = getDecoder();

        boolean isNeedMoreInput = false; // true, if content in composite buffer is not enough to produce even 1 char
        boolean last = false;

        while (read < requestedLen && httpHeader.isExpectContent()) {

            if (isNeedMoreInput || !inputContentBuffer.hasRemaining()) {
                final HttpContent c = blockingRead();
                updateInputContentBuffer(c.getContent());
                last = c.isLast();

                c.recycle();
                isNeedMoreInput = false;
            }

            final ByteBuffer bytes = inputContentBuffer.toByteBuffer();

            final int bytesPos = bytes.position();
            final int dstPos = dst.position();

            final CoderResult result = decoderLocal.decode(bytes, dst, false);

            final int producedChars = dst.position() - dstPos;
            final int consumedBytes = bytes.position() - bytesPos;

            read += producedChars;

            if (consumedBytes > 0) {
                bytes.position(bytesPos);
                inputContentBuffer.position(inputContentBuffer.position() + consumedBytes);
                if (readAheadLimit == -1) {
                    inputContentBuffer.shrink();
                }
            } else {
                isNeedMoreInput = true;
            }

            if (last || result == CoderResult.OVERFLOW) {
                break;
            }
        }

        dst.flip();

        if (last && read == 0) {
            read = -1;
        }
        return read;
    }

    /**
     * <p>
     * Used to convert pre-read (buffered) bytes to chars.
     * </p>
     *
     * @param requestedLen how much content should attempt to be read
     *
     * @return the number of chars actually read
     */
    private int fillAvailableChars(final int requestedLen, final CharBuffer dst) {
        
        final CharsetDecoder decoderLocal = getDecoder();
        final ByteBuffer bb = inputContentBuffer.toByteBuffer();
        final int oldBBPos = bb.position();
        
        int producedChars = 0;
        int consumedBytes = 0;
        
        int producedCharsNow;
        int consumedBytesNow;
        CoderResult result;
        
        int remaining = requestedLen;
        
        do {
            final int charPos = dst.position();
            final int bbPos = bb.position();
            result = decoderLocal.decode(bb, dst, false);

            producedCharsNow = dst.position() - charPos;
            consumedBytesNow = bb.position() - bbPos;

            producedChars += producedCharsNow;
            consumedBytes += consumedBytesNow;
            
            remaining -= producedCharsNow;

        } while (remaining > 0 &&
                (producedCharsNow > 0 || consumedBytesNow > 0) &&
                bb.hasRemaining() &&
                result == CoderResult.UNDERFLOW);

        bb.position(oldBBPos);
        inputContentBuffer.position(inputContentBuffer.position() + consumedBytes);
        
        if (readAheadLimit == -1) {
            inputContentBuffer.shrink();
        }
        
        return producedChars;
    }


    protected void updateInputContentBuffer(final Buffer buffer)  {

        buffer.allowBufferDispose(true);
        
        if (inputContentBuffer == null) {
            inputContentBuffer = buffer;
        } else if (inputContentBuffer.hasRemaining()
                || readAheadLimit > 0) { // if the stream is marked - we can't dispose the inputContentBuffer, even if it's been read off
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
    private static boolean shouldNotifyNow(final int size, final int available) {

        return (available != 0 && available >= size);

    }


    /**
     * @return the {@link CharsetDecoder} that should be used when converting
     *  content from binary to character
     */
    private CharsetDecoder getDecoder() {
        if (decoder == null) {
            decoder = decoders.get(encoding);
            
            if (decoder == null) {
                final Charset cs = Charsets.lookupCharset(encoding);
                decoder = cs.newDecoder();
                decoder.onMalformedInput(CodingErrorAction.REPLACE);
                decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
                
                decoders.put(encoding, decoder);
            } else {
                decoder.reset();
            }
        }

        return decoder;

    }

    private CompositeBuffer toCompositeInputContentBuffer() {
        if (!inputContentBuffer.isComposite()) {
            final CompositeBuffer compositeBuffer = CompositeBuffer.newBuffer(
                    connection.getMemoryManager());

            compositeBuffer.allowBufferDispose(true);
            compositeBuffer.allowInternalBuffersDispose(true);

            int posAlign = 0;
            
            if (readAheadLimit > 0) { // the simple inputContentBuffer is marked
                // make the marked data still available
                inputContentBuffer.position(
                        inputContentBuffer.position() - readCount);
                posAlign = readCount;
                
                markPos = 0; // for the CompositeBuffer markPos is 0
            }
            
            compositeBuffer.append(inputContentBuffer);
            compositeBuffer.position(posAlign);
            
            inputContentBuffer = compositeBuffer;
        }

        return (CompositeBuffer) inputContentBuffer;
    }

    /**
     * @param n read bytes count
     * @return <tt>true</tt> if mark is still active, or <tt>false</tt> if the
     *      mark hasn't been set or has been invalidated
     */
    private boolean checkMarkAfterRead(final long n) {
        if (n > 0 && readAheadLimit != -1) {
            if ((long) readCount + n <= readAheadLimit) {
                readCount += n;
                return true;
            }

            // invalidate the mark
            readAheadLimit = -1;
            markPos = -1;
            readCount = 0;
        }
        
        return false;
    }
    
    /**
     * Check if passed {@link HttpContent} is {@link HttpTrailer}, which represents
     * trailer chunk (when chunked Transfer-Encoding is used), if it is a trailer
     * chunk - then copy all the available trailer headers to request headers map.
     * 
     * @param httpContent 
     */
    private static void checkHttpTrailer(final HttpContent httpContent) {
        if (HttpTrailer.isTrailer(httpContent)) {
            final HttpTrailer httpTrailer = (HttpTrailer) httpContent;
            final HttpHeader httpHeader = httpContent.getHttpHeader();
            
            final MimeHeaders trailerHeaders = httpTrailer.getHeaders();
            final int size = trailerHeaders.size();
            for (int i = 0; i < size; i++) {
                httpHeader.addHeader(
                        trailerHeaders.getName(i).toString(),
                        trailerHeaders.getValue(i).toString());
            }
        }
    }    
    
    private static void log(final String message, Object... params) {
        final String preparedMsg = String.format(message, params);

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, preparedMsg, new Exception("Logged at"));
        } else {
            LOGGER.log(LOGGER_LEVEL, preparedMsg);
        }
    }
}
