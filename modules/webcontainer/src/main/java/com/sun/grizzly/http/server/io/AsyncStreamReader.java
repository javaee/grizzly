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
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.Processor;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.memory.ByteBuffersBuffer;
import com.sun.grizzly.memory.CompositeBuffer;
import com.sun.grizzly.threadpool.DefaultWorkerThread;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.CharBuffer;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * <p>
 * <code>AsyncStreamReader</code> provides the ability to perform asynchronous
 * HTTP read operations by allowing the registration of {@link DataAvailableHandler}
 * instances which will be notified as data becomes available.
 * </p>
 *
 * TODO: add example
 *
 * @since 2.0
 */
public class AsyncStreamReader {

    protected final FilterChainContext ctx;
    protected final CompositeBuffer compositeBuffer;

    protected boolean closed;
    protected boolean contentRead;
    protected int requestedSize;
    protected DataAvailableHandler handler;


    // ------------------------------------------------------------ Constructors


    /**
     * TODO: Documentation
     * @param initialContent
     * @param ctx
     */
    public AsyncStreamReader(final HttpContent initialContent,
                             final FilterChainContext ctx) {

        this.ctx = ctx;
        this.compositeBuffer = ByteBuffersBuffer.create();
        if (initialContent != null) {
            Buffer content = initialContent.getContent();
            if (content.remaining() > 0) {
                compositeBuffer.append(initialContent.getContent());
            }
            if (initialContent.isLast()) {
                contentRead = true;
            }
        }

    }



    // ---------------------------------------------------------- Public Methods


    /**
     * TODO: Documentation
     *
     * @param handler
     */
    public void notifyAvailable(final DataAvailableHandler handler) {
        notifyAvailable(handler, 0);
    }


    /**
     * TODO: Documentation
     *
     * @param handler
     * @param size
     */
    public void notifyAvailable(final DataAvailableHandler handler,
                                final int size) {

        if (closed) {
            return;
        }
        requestedSize = size;
        this.handler = handler;

    }


    /**
     * Return <tt>true</tt> if <tt>StreamReader</tt> has available data, which
     * could be read, or <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt> if <tt>StreamReader</tt> has available data, which
     * could be read, or <tt>false</tt> otherwise.
     */
    public boolean hasAvailable() {
        return (!closed && (available() != 0));
    }


    /**
     * Return the number of bytes available for get calls.  An attempt to
     * get more data than is present in the stream will either result in
     * blocking (if isBlocking() returns true) or a BufferUnderflowException.
     */
    public int available() {
        return ((closed) ? 0 : compositeBuffer.remaining());
    }


    /**
     * Fill data with bytes from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    public void readByteArray(final byte[] data) throws IOException {

        if (closed) {
            return;
        }
        readByteArray(data, 0, data.length);
        
    }


    /**
     * Fill data with bytes from the stream.
     * If this method returns normally, data has been filled completely.
     * Requires data.length bytes.
     */
    public void readByteArray(final byte[] data,
                              final int offset,
                              final int length)
    throws IOException {

        if (closed) {
            return;
        }
        arraySizeCheck(length);
        compositeBuffer.get(data, offset, length);
        compositeBuffer.shrink();

    }


    /**
     * Fill the buffer with data from the stream (that is, copy data
     * from the stream to fill buffer from position to limit).
     * This is useful when data must be read
     * from one stream and then added to another stream for
     * further processing.
     */
    public void readBytes(final Buffer buffer) throws IOException {

        if (closed || !buffer.hasRemaining()) {
            return;
        }

        arraySizeCheck(buffer.remaining());
        final int diff = buffer.remaining() - compositeBuffer.remaining();
        if (diff >= 0) {
            buffer.put(compositeBuffer);
        } else {
            final int save = compositeBuffer.limit();
            compositeBuffer.limit(save + diff);
            buffer.put(compositeBuffer);
            compositeBuffer.limit(save);
        }

        compositeBuffer.shrink();

    }


    /**
     * TODO: Documentation
     */
    public void readCharArray(final char[] data) throws IOException {

    }


    /**
     * TODO: Documentation
     * @param buffer
     * @throws IOException
     */
    public void readCharBuffer(final CharBuffer buffer) throws IOException {

    }


    /**
     * TODO: Documentation
     *
     * @param length
     */
    public void skip(final int length) {

        if (closed) {
            return;
        }
        if (length > available()) {
            throw new IllegalStateException("Can not skip more bytes than available");
        }

        compositeBuffer.position(compositeBuffer.position() + length);
        compositeBuffer.shrink();
    }


    /**
     * @return <tt>true</tt>, if <tt>StreamReader</tt> has been closed,
     * or <tt>false</tt> otherwise.
     */
    public boolean isClosed() {
        return closed;
    }


    /**
     * Closes the <tt>StreamReader</tt> and causes all subsequent method calls
     * on this object to throw IllegalStateException.
     */
    public void close() {
        closed = true;
    }


    /**
     * TODO: Documentation
     *
     * @param buffer
     *
     * @return
     */
    public boolean append(final Buffer buffer) {

        if (buffer == null || closed) {
            return true;
        }

        if (isClosed()) {
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


    /**
     * TODO: Documentation
     */
    public void finished() {
        if (!contentRead) {
            contentRead = true;
            handler.onAllDataRead();
            ctx.resume();
        }
    }


    /**
     * TODO: Documentation
     * @return
     */
    public boolean isFinished() {
        return contentRead;
    }


    // --------------------------------------------------------- Private Methods


    /**
     * TODO: Documentation
     *
     * @param sizeInBytes
     */
    private void arraySizeCheck(final int sizeInBytes) {
        if (sizeInBytes > available()) {
            throw new BufferUnderflowException();
        }
    }

}
