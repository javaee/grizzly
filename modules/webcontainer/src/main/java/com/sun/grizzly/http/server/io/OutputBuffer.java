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
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.http.Constants;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.HttpTrailer;
import com.sun.grizzly.http.util.Utils;
import com.sun.grizzly.memory.ByteBufferManager;
import com.sun.grizzly.memory.ByteBufferWrapper;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.tcp.FileOutputBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * TODO DOCS
 */
public class OutputBuffer implements FileOutputBuffer, WritableByteChannel {

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 8;

    private static final int CAPACITY_OK = -1;


    private HttpResponsePacket response;

    private FilterChainContext ctx;

    private Buffer buf;

    private boolean committed;

    private boolean finished;

    private boolean closed;

    private boolean processingChars;

    private String encoding = Constants.DEFAULT_CHARACTER_ENCODING; // should this be UTF-8?

    private CharsetEncoder encoder;

    private CharBuffer charBuf = CharBuffer.allocate(DEFAULT_BUFFER_SIZE);

    private MemoryManager memoryManager;


    // ---------------------------------------------------------- Public Methods


    public void initialize(HttpResponsePacket response,
                           FilterChainContext ctx) {

        this.response = response;
        this.ctx = ctx;
        memoryManager = ctx.getConnection().getTransport().getMemoryManager();
        buf = memoryManager.allocate(DEFAULT_BUFFER_SIZE);

    }


    public void processingChars() {
        processingChars = true;
    }


    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }


    public String getEncoding() {
        return encoding;
    }


    /**
     * Reset current response.
     *
     * @throws IllegalStateException if the response has already been committed
     */
    public void reset() {

        if (committed)
            throw new IllegalStateException(/*FIXME:Put an error message*/);

        // Recycle Request object
        response.recycle();

    }


    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    public void recycle() {

        response = null;

        buf.tryDispose();
        charBuf.clear();

        buf = null;
        encoder = null;
        ctx = null;
        memoryManager = null;

        committed = false;
        finished = false;
        closed = false;
        processingChars = false;

        encoding = Constants.DEFAULT_CHARACTER_ENCODING;

    }


    public void endRequest()
        throws IOException {

        if (finished) {
            return;
        }

        //if (lastActiveFilter != -1)
        //    activeFilters[lastActiveFilter].end();

        //if (useSocketBuffer) {
        //    socketBuffer.flushBuffer();
        //}

        close();

        finished = true;

    }


    /**
     * Commit the response.
     */
    public void commit() {

        if (committed) {
            return;
        }
        // The response is now committed
        committed = true;

    }


    // ---------------------------------------------------- Writer-Based Methods


    public void write(char cbuf[], int off, int len) throws IOException {
        if (!processingChars) {
            throw new IllegalStateException();
        }
        if (closed) {
            return;
        }

        int total = len;
        do {
            int writeLen = requiresDrainChar(total);
            if (writeLen == CAPACITY_OK) {
                charBuf.put(cbuf, off, total);
                total = 0;
            } else if (writeLen == DEFAULT_BUFFER_SIZE) {
                charBuf.put(cbuf, off, writeLen);
                total -= writeLen;
                flushCharsToBuf();
            } else {
                charBuf.put(cbuf, off, writeLen);
                flushCharsToBuf();
                charBuf.put(cbuf, off + writeLen, total - writeLen);
                total -= (total + total - writeLen);
            }
            if (charBuf.remaining() == 0) {
                flushCharsToBuf();
            }
            off += DEFAULT_BUFFER_SIZE;
        } while (total > 0);

    }


    public void writeChar(int c) throws IOException {
        if (!processingChars) {
            throw new IllegalStateException();
        }
        if (closed) {
            return;
        }
        int writeLen = requiresDrainChar(1);
        if (writeLen == CAPACITY_OK) {
            charBuf.put((char) c);
        } else {
            flushCharsToBuf();
            charBuf.put((char) c);
        }
    }


    public void write(char cbuf[]) throws IOException {
        write(cbuf, 0, cbuf.length);
    }


    public void write(String str) throws IOException {
        write(str, 0, str.length());
    }


    public void write(String str, int off, int len) throws IOException {
        if (!processingChars) {
            throw new IllegalStateException();
        }
        if (closed) {
            return;
        }
        int total = len;
        do {
            int writeLen = requiresDrainChar(total);
            if (writeLen == CAPACITY_OK) {
                charBuf.put(str, off, total + off);
                total = 0;
            } else if (writeLen == DEFAULT_BUFFER_SIZE) {
                charBuf.put(str, off, writeLen + off);
                total -= (writeLen);
                flushCharsToBuf();
            } else {
                charBuf.put(str, off, off + writeLen);
                flushCharsToBuf();
                int rem = total - writeLen;
                charBuf.put(str, off + writeLen, off + writeLen + rem);
                total -= (writeLen + rem);
            }
            if (charBuf.remaining() == 0) {
                flushCharsToBuf();
            }
            off += DEFAULT_BUFFER_SIZE;

        } while (total > 0);
        
    }


    // ---------------------------------------------- OutputStream-Based Methods

    public void writeByte(int b) throws IOException {
        if (closed) {
            return;
        }
        int writeLen = requiresDrain(1);
        if (writeLen == CAPACITY_OK) {
            buf.put((byte) b);
        } else {
            flush();
            buf.put((byte) b);
        }
    }


    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }


    public void write(byte b[], int off, int len) throws IOException {
        if (closed) {
            return;
        }
        int total = len;
        do {
            int writeLen = requiresDrain(total);
            if (writeLen == CAPACITY_OK) {
                buf.put(b, off, total);
                total = 0;
            } else if (writeLen == DEFAULT_BUFFER_SIZE) {
                buf.put(b, off, writeLen);
                total -= writeLen;
                flush();
            } else {
                buf.put(b, off, writeLen);
                flush();
                buf.put(b, off + writeLen, total - writeLen);
                total -= (total + total - writeLen);
            }
            if (buf.remaining() == 0) {
                flush();
            }
            off += DEFAULT_BUFFER_SIZE;
        } while (total > 0);
    }


    // --------------------------------------------------- Common Output Methods


    public void close() throws IOException {

        if (closed) {
            return;
        }
        closed = true;
        doCommit();
        if (processingChars) {
            flushCharsToBuf();
        }
        writeContentChunk(true);

    }




    /**
     * Flush the response.
     *
     * @throws java.io.IOException an undelying I/O error occured
     */
    public void flush() throws IOException {

        doCommit();
        if (processingChars) {
            flushCharsToBuf();
        }
        writeContentChunk(false);

    }


    /**
     * <p>
     * Writes the contents of the specified {@link ByteBuffer} to the client.
     * </p>
     *
     * @param byteBuffer the {@link ByteBuffer} to write
     * @throws IOException if an error occurs during the write
     */
    @SuppressWarnings({"unchecked"})
    public void writeByteBuffer(ByteBuffer byteBuffer) throws IOException {
        Buffer w = MemoryUtils.wrap(memoryManager, byteBuffer);
        int total = w.remaining();
        int off = w.position();
        do {
            int writeLen = requiresDrain(total);
            if (writeLen == CAPACITY_OK) {
                buf.put(w, off, total);
                total = 0;
            } else if (writeLen == DEFAULT_BUFFER_SIZE) {
                buf.put(w, off, writeLen);
                total -= writeLen;
                flush();
            } else {
                buf.put(w, off, writeLen);
                flush();
                buf.put(w, off + writeLen, total - writeLen);
                total -= (total + total - writeLen);
            }
            if (buf.remaining() == 0) {
                flush();
            }
            off += DEFAULT_BUFFER_SIZE;
        } while (total > 0);
    }


    // ------------------------------------------- Methods from FileOutputBuffer

    /**
     * <p>
     * The use of {@link FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
     * is supported.
     * </p>
     *
     * @return <code>true</code>
     */
    @Override
    public boolean isSupportFileSend() {
        return true;
    }

    /**
     * @see FileOutputBuffer#sendFile(java.nio.channels.FileChannel, long, long)
     */
    @Override
    public long sendFile(FileChannel fileChannel, long position, long length)
    throws IOException {
        return fileChannel.transferTo(position, length, this);
    }


    // ---------------------------------------- Methods from WritableByteChannel

    @Override
    public int write(ByteBuffer src) throws IOException {
        int len = src.remaining();
        writeByteBuffer(src);
        return len;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }


    // --------------------------------------------------------- Private Methods


    private void writeContentChunk(boolean includeTrailer) throws IOException {
        if (buf.position() > 0) {
            HttpContent.Builder builder = response.httpContentBuilder();
            buf.flip();
            builder.content(buf);
            ctx.write(builder.build());
            if (!includeTrailer) {
                buf = memoryManager.allocate(DEFAULT_BUFFER_SIZE);
            }
        }
        if (response.isChunked() && includeTrailer) {
            ctx.write(response.httpTrailerBuilder().build());
        }
    }


    private void flushCharsToBuf() throws IOException {

        // flush the buffer - need to take care of encoding at this point
        CharsetEncoder enc = getEncoder();
        charBuf.flip();
        CoderResult res = enc.encode(charBuf,
                                     (ByteBuffer) buf.underlying(),
                                     true);
        while (res == CoderResult.OVERFLOW) {
            commit();
            writeContentChunk(false);
            res = enc.encode(charBuf, (ByteBuffer) buf.underlying(), true);
        }

        if (res != CoderResult.UNDERFLOW) {
            throw new IOException("Encoding error");
        }

        charBuf.clear();

    }


    private int requiresDrainChar(int len) {
        int rem = charBuf.remaining();
        if (len < rem) {
            return CAPACITY_OK;
        } else {
            if (len == DEFAULT_BUFFER_SIZE) {
                return DEFAULT_BUFFER_SIZE;
            }
            if (len > DEFAULT_BUFFER_SIZE) {
                return DEFAULT_BUFFER_SIZE - charBuf.position();
            }
            return (len - rem);
        }
    }


    private int requiresDrain(int len) {
        int rem = buf.remaining();
        if (len < rem) {
            return CAPACITY_OK;
        } else {
            int ret = (len - rem);
            return ((ret > DEFAULT_BUFFER_SIZE) ? rem : ret);
        }
    }


    private CharsetEncoder getEncoder() {

        if (encoder == null) {
            Charset cs = Utils.lookupCharset(getEncoding());
            encoder = cs.newEncoder();
            encoder.onMalformedInput(CodingErrorAction.REPLACE);
            encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        }

        return encoder;

    }

    
    private void doCommit() throws IOException {
        if (!committed) {
            committed = true;
            // flush the message header to the client
            ctx.write(response);
        }
    }
}
