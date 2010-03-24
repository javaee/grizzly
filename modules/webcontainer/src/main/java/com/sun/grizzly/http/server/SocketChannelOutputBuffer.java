/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 */

package com.sun.grizzly.http.server;

import com.sun.grizzly.tcp.FileOutputBuffer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalOutputBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.util.logging.Logger;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.NIOConnection;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.util.buf.ByteChunk;

/**
 * Output buffer.
 * Buffer the bytes until the {@link ByteChunk} is full or the request
 * is completed.
 * 
 * @author Jean-Francois Arcand
 * @author Scott Oaks
 * @author Alexey Stashok
 */
public class SocketChannelOutputBuffer extends InternalOutputBuffer
        implements FileOutputBuffer {
    protected static Logger logger = Grizzly.logger(SocketChannelOutputBuffer.class);

    /**
     * {@link StreamWriter}, which will be used to write data.
     */
    protected StreamWriter connectionStreamWriter;

    /**
     * Underlying {@link Buffer}
     */
    protected Buffer outputBuffer;


    /**
     * ACK static bytes.
     */
    protected final static Buffer ACK =
            MemoryUtils.wrap(
            TransportFactory.getInstance().getDefaultMemoryManager(),
            "HTTP/1.1 100 Continue\r\n\r\n");



    /**
     * Maximum cached bytes before flushing.
     */
    protected final static int MAX_BUFFERED_BYTES = 32 * 8192;


    /**
     * Default max cached bytes.
     */
    protected static int maxBufferedBytes = MAX_BUFFERED_BYTES;


     /**
     * Flag, which indicates if async HTTP write is enabled
     */
    protected boolean isAsyncHttpWriteEnabled;


    private Connection connection;


    // ----------------------------------------------------------- Constructors


    /**
     * Alternate constructor.
     */
    public SocketChannelOutputBuffer(Response response,
                                     Connection connection,
                                     int headerBufferSize,
                                     boolean useSocketBuffer) {
        super(response, headerBufferSize, useSocketBuffer);

        this.connection = connection;

        if (!useSocketBuffer){
            outputStream = new NIOOutputStream();
            outputBuffer = createBuffer(headerBufferSize * 16);
        }
    }


    // ------------------------------------------------------------- Properties


    /**
     * Create the output {@link Buffer}
     */
    protected Buffer createBuffer(int size) {
        if (connectionStreamWriter != null) {
            return connectionStreamWriter.getConnection().
                    getTransport().getMemoryManager().allocate(size);
        }

        return TransportFactory.getInstance().
                getDefaultMemoryManager().allocate(size);
    }


    /**
     * Set the underlying {@link StreamWriter}.
     */
    public void setStreamWriter(StreamWriter streamWriter) {
        this.connectionStreamWriter = streamWriter;
    }


    /**
     * Return the underlying {@link StreamWriter}.
     */
    public StreamWriter getStreamWriter() {
        return connectionStreamWriter;
    }

    /**
     * Is async HTTP write enabled.
     * @return <tt>true</tt>, if async HTTP write enabled, or <tt>false</tt>
     * otherwise.
     */
    public boolean isAsyncHttpWriteEnabled() {
        return isAsyncHttpWriteEnabled;
    }

    /**
     * Set if async HTTP write enabled.
     * @param isAsyncHttpWriteEnabled <tt>true</tt>, if async HTTP write
     * enabled, or <tt>false</tt> otherwise.
     */
    public void setAsyncHttpWriteEnabled(boolean isAsyncHttpWriteEnabled) {
        this.isAsyncHttpWriteEnabled = isAsyncHttpWriteEnabled;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Send an acknoledgement without buffering.
     */
    @Override
    public void sendAck() throws IOException {
        if (!committed)
            flushChannel(ACK.slice());
    }


    /**
     * Callback to write data from the buffer.
     */
    @Override
    public void realWriteBytes(byte cbuf[], int off, int len)
        throws IOException {
        if (len > 0) {
            if (!useSocketBuffer){
                int remaining = outputBuffer.remaining();
                if (len > remaining){
                    if (outputBuffer.capacity() >= maxBufferedBytes){
                        outputBuffer.put(cbuf,off,remaining);
                        flush();
                        realWriteBytes(cbuf,off+remaining,len-remaining);
                        return;
                    } else {
                        int size = Math.max(outputBuffer.capacity() * 2,
                                            len + outputBuffer.position());
                        Buffer tmp = createBuffer(size);
                        outputBuffer.flip();
                        tmp.put(outputBuffer);
                        outputBuffer = tmp;
                    }
                }
                outputBuffer.put(cbuf, off, len);
            } else {
                flushChannel(MemoryUtils.wrap(
                        connectionStreamWriter.getConnection().
                        getTransport().getMemoryManager(),
                        buf, off, len));
            }
        }
    }


    /**
     * Flush the buffer by looping until the {@link ByteBuffer} is empty
     * @param bb the ByteBuffer to write.
     */
    public void flushChannel(Buffer bb) throws IOException {
        connectionStreamWriter.writeBuffer(bb);
        connectionStreamWriter.flush();
        bb.clear();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSupportFileSend() {
        return true;
    }


    /**
     * {@inheritDoc}
     */
    public long sendFile(FileChannel fileChannel, long position,
            long length) throws IOException {

        //Connection connection = connectionStreamWriter.getConnection();
        SelectableChannel channel = ((NIOConnection) connection).getChannel();
        return fileChannel.transferTo(position, length,
                (WritableByteChannel) channel);
        
    }


    /**
     * Flush the buffered bytes,
     */
    @Override
    public void flush() throws IOException{
        super.flush();
        flushBuffer();
    }


     /**
     * End request.
     *
     * @throws IOException an undelying I/O error occured
     */
    @Override
    public void endRequest()
        throws IOException {
        super.endRequest();
        flushBuffer();
    }

    /**
     * Writes bytes to the underlying channel.
     */
    public void flushBuffer() throws IOException{
        if (!useSocketBuffer && outputBuffer.position() != 0){
            outputBuffer.flip();
            flushChannel(outputBuffer);
            outputBuffer.clear();
        }
    }


    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    @Override
    public void recycle() {
        response.recycle();
        socketBuffer.recycle();
        pos = 0;
        lastActiveFilter = -1;
        committed = false;
        finished = false;
        if (outputBuffer != null){
            outputBuffer.clear();
        }

        connectionStreamWriter = null;
    }


    // ---------------------------------------------- Class helper ----------//

    /**
     * OutputBuffer delegating all writes to the {@link OutputWriter}
     */
    private final class NIOOutputStream extends OutputStream{

        @Override
        public void write(byte[] b, int off, int len) throws IOException{
            realWriteBytes(b,off,len);
        }

        public void write(int b) throws IOException {
            write((byte)b);
        }

        public void write(byte b) throws IOException {
            if(!outputBuffer.hasRemaining()) {
                Buffer tmp = createBuffer(outputBuffer.capacity() * 2);
                outputBuffer.flip();
                tmp.put(outputBuffer);
                outputBuffer = tmp;
            }
            outputBuffer.put(b);
            return;
        }
    }

    /**
     * Reset current response.
     *
     * @throws IllegalStateException if the response has already been committed
     */
    @Override
    public void reset() {
        super.reset();
        outputBuffer.clear();
    }
}
