/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.grizzly.tcp.http11;

import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.HttpMessages;
import com.sun.grizzly.util.http.MimeHeaders;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Output buffer.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 */
public class InternalOutputBuffer
        implements OutputBuffer, ByteChunk.ByteOutputChannel {


    // -------------------------------------------------------------- Constants


    // ----------------------------------------------------------- Constructors


    /**
     * Default constructor.
     */
    public InternalOutputBuffer(Response response) {
        this(response, Constants.DEFAULT_HTTP_HEADER_BUFFER_SIZE);
    }


    // START GlassFish Issue 798

    /**
     * Create a new InternalOutputBuffer and configure the enable/disable the
     * socketBuffer mechanism.
     */
    public InternalOutputBuffer(Response response, int headerBufferSize,
            boolean useSocketBuffer) {

        this(response, headerBufferSize);
        this.useSocketBuffer = useSocketBuffer;
        if (useSocketBuffer) {
            socketBuffer.allocate(headerBufferSize, headerBufferSize);
        }
    }
    // END GlassFish Issue 798


    /**
     * Alternate constructor.
     */
    public InternalOutputBuffer(Response response, int headerBufferSize) {

        this.response = response;
        headers = response.getMimeHeaders();

        buf = new byte[headerBufferSize];

        outputStreamOutputBuffer = new OutputStreamOutputBuffer();

        filterLibrary = new OutputFilter[0];
        activeFilters = new OutputFilter[0];
        lastActiveFilter = -1;

        socketBuffer = new ByteChunk();
        socketBuffer.setByteOutputChannel(this);

        committed = false;
        finished = false;

    }

    protected InternalOutputBuffer() {}


    // ----------------------------------------------------- Instance Variables

    /**
     * The last {@link OutputFilter} to be added to the activeFilters list.
     */
    private OutputFilter lastOutputFilter = null;


    /**
     * Associated Coyote response.
     */
    protected Response response;


    /**
     * Headers of the associated request.
     */
    protected MimeHeaders headers;


    /**
     * Committed flag.
     */
    protected boolean committed;


    /**
     * Finished flag.
     */
    protected boolean finished;


    /**
     * Pointer to the current read buffer.
     */
    protected byte[] buf;


    /**
     * Position in the buffer.
     */
    protected int pos;


    /**
     * Underlying output stream.
     */
    protected OutputStream outputStream;


    /**
     * Underlying output buffer.
     */
    protected OutputBuffer outputStreamOutputBuffer;


    /**
     * Filter library.
     * Note: Filter[0] is always the "chunked" filter.
     */
    protected OutputFilter[] filterLibrary;


    /**
     * Active filter (which is actually the top of the pipeline).
     */
    protected OutputFilter[] activeFilters;


    /**
     * Index of the last active filter.
     */
    protected int lastActiveFilter;


    /**
     * Socket buffer.
     */
    protected ByteChunk socketBuffer;


    /**
     * Socket buffer (extra buffering to reduce number of packets sent).
     */
    protected boolean useSocketBuffer = false;


    // ------------------------------------------------------------- Properties


    /**
     * Set the underlying socket output stream.
     */
    public void setOutputStream(OutputStream outputStream) {

        // FIXME: Check for null ?

        this.outputStream = outputStream;

    }


    /**
     * Get the underlying socket output stream.
     */
    public OutputStream getOutputStream() {

        return outputStream;

    }


    /**
     * Set the socket buffer size.
     */
    public void setSocketBuffer(int socketBufferSize) {

        if (socketBufferSize > 500) {
            useSocketBuffer = true;
            socketBuffer.allocate(socketBufferSize, socketBufferSize);
        } else {
            useSocketBuffer = false;
        }

    }


    /**
     * Add an output filter to the filter library.
     */
    public void addFilter(OutputFilter filter) {

        OutputFilter[] newFilterLibrary =
                new OutputFilter[filterLibrary.length + 1];
        System.arraycopy(filterLibrary, 0, newFilterLibrary, 0, filterLibrary.length);
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new OutputFilter[filterLibrary.length];

    }


    /**
     * Get filters.
     */
    public OutputFilter[] getFilters() {

        return filterLibrary;

    }


    /**
     * Clear filters.
     */
    public void clearFilters() {

        filterLibrary = new OutputFilter[0];
        lastActiveFilter = -1;

    }


    /**
     * Add the last {@link OutputFilter} that will be invoked when processing
     * the writing of the response bytes.
     */
    public void addLastOutputFilter(OutputFilter lastOutputFilter) {
        this.lastOutputFilter = lastOutputFilter;
    }


    /**
     * Add an output filter to the filter library.
     */
    public void addActiveFilter(OutputFilter filter) {

        if (lastActiveFilter == -1) {
            filter.setBuffer(outputStreamOutputBuffer);
        } else {
            for (int i = 0; i <= lastActiveFilter; i++) {
                if (activeFilters[i] == filter) {
                    return;
                }
            }
            filter.setBuffer(activeFilters[lastActiveFilter]);
        }

        activeFilters[++lastActiveFilter] = filter;

        filter.setResponse(response);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Flush the response.
     *
     * @throws IOException an undelying I/O error occured
     */
    public void flush()
            throws IOException {

        if (!committed) {

            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeader) and 
            // set the filters accordingly.
            response.action(ActionCode.ACTION_COMMIT, null);

        }

        // Flush the current buffer
        if (useSocketBuffer) {
            socketBuffer.flushBuffer();
        }

    }


    // START GlassFish Issue 646

    /**
     * Flush the buffer.
     */
    protected void flush(boolean isFull) throws IOException {
        // Sending the response header buffer
        realWriteBytes(buf, 0, pos);

        if (isFull) {
            pos = 0;
        }
    }
    // END GlassFish Issue 646


    /**
     * Reset current response.
     *
     * @throws IllegalStateException if the response has already been committed
     */
    public void reset() {

        if (committed) {
            throw new IllegalStateException(/*FIXME:Put an error message*/);
        }

        // Recycle Request object
        response.recycle();

    }


    /**
     * Recycle the output buffer. This should be called when closing the
     * connection.
     */
    public void recycle() {

        // Recycle Request object
        response.recycle();
        socketBuffer.recycle();

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        outputStream = null;
        pos = 0;
        lastActiveFilter = -1;
        committed = false;
        finished = false;

    }


    /**
     * End processing of current HTTP request.
     * Note: All bytes of the current request should have been already
     * consumed. This method only resets all the pointers so that we are ready
     * to parse the next HTTP request.
     */
    public void nextRequest() {

        // Recycle Request object
        response.recycle();
        if (socketBuffer != null) {
            socketBuffer.recycle();
        }

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        // Reset pointers
        pos = 0;
        lastActiveFilter = -1;
        committed = false;
        finished = false;

    }


    /**
     * End request.
     *
     * @throws IOException an underlying I/O error occurred
     */
    public void endRequest()
            throws IOException {


        if (!committed) {

            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeader) and 
            // set the filters accordingly.
            response.action(ActionCode.ACTION_COMMIT, null);

        }

        if (finished) {
            return;
        }

        if (lastActiveFilter != -1) {
            activeFilters[lastActiveFilter].end();
        }

        if (useSocketBuffer) {
            socketBuffer.flushBuffer();
        }

        finished = true;

    }


    // ------------------------------------------------ HTTP/1.1 Output Methods


    /**
     * Send an acknoledgement.
     */
    public void sendAck()
            throws IOException {

        if (!committed) {
            realWriteBytes(Constants.ACK_BYTES, 0,
                    Constants.ACK_BYTES.length);
        }
    }


    /**
     * Send the response status line.
     */
    public void sendStatus() {

        // Write protocol name
        write("HTTP/1.1 ");

        // Write status code
        int status = response.getStatus();
        switch (status) {
            case 200:
                write("200");
                break;
            case 400:
                write("400");
                break;
            case 404:
                write("404");
                break;
            default:
                write(status);
        }

        write(" ");

        // Write message
        String message = null;
        // Servlet 3.0, section 5:3, the message from sendError is for content body
        if (response.isAllowCustomReasonPhrase()) {
            message = response.getMessage();
        }
        if (message == null) {
            write(getMessage(status));
        } else {
            write(message, true);
        }

        // End the response status line
        if (System.getSecurityManager() != null) {
            AccessController.doPrivileged(
                    new PrivilegedAction() {
                        public Object run() {
                            write(Constants.CRLF_BYTES);
                            return null;
                        }
                    }
            );
        } else {
            write(Constants.CRLF_BYTES);
        }

    }

    private String getMessage(final int message) {
        if (System.getSecurityManager() != null) {
            return (String) AccessController.<String>doPrivileged(
                    new PrivilegedAction<String>() {
                        public String run() {
                            return HttpMessages.getMessage(message);
                        }
                    }
            );
        } else {
            return HttpMessages.getMessage(message);
        }
    }

    /**
     * Send a header.
     *
     * @param name  Header name
     * @param value Header value
     */
    public void sendHeader(MessageBytes name, MessageBytes value) {

        write(name);
        write(": ");
        write(value);
        write(Constants.CRLF_BYTES);

    }


    /**
     * Send a header.
     *
     * @param name  Header name
     * @param value Header value
     */
    public void sendHeader(ByteChunk name, ByteChunk value) {

        write(name);
        write(": ");
        write(value);
        write(Constants.CRLF_BYTES);

    }


    /**
     * Send a header.
     *
     * @param name  Header name
     * @param value Header value
     */
    public void sendHeader(String name, String value) {

        write(name);
        write(": ");
        write(value);
        write(Constants.CRLF_BYTES);

    }


    /**
     * End the header block.
     */
    public void endHeaders() {

        write(Constants.CRLF_BYTES);

    }


    // --------------------------------------------------- OutputBuffer Methods


    /**
     * Write the contents of a byte chunk.
     *
     * @param chunk byte chunk
     * @return number of bytes written
     * @throws IOException an undelying I/O error occured
     */
    public int doWrite(ByteChunk chunk, Response res)
            throws IOException {

        if (response.isSuspended()) {
            response.action(ActionCode.RESET_SUSPEND_TIMEOUT, null);
        }

        if (!committed) {

            // Send the connector a request for commit. The connector should
            // then validate the headers, send them (using sendHeaders) and 
            // set the filters accordingly.
            response.action(ActionCode.ACTION_COMMIT, null);

        }

        if (lastOutputFilter != null && activeFilters[0] != lastOutputFilter) {
            addActiveFilter(lastOutputFilter);
        }

        if (lastActiveFilter == -1) {
            return outputStreamOutputBuffer.doWrite(chunk, res);
        } else {
            return activeFilters[lastActiveFilter].doWrite(chunk, res);
        }

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Commit the response.
     *
     * @throws IOException an undelying I/O error occured
     */
    public void commit()
            throws IOException {

        // The response is now committed
        committed = true;
        response.setCommitted(true);

        /* GlassFish Issue 646
        if (pos > 0) {
            // Sending the response header buffer
            if (useSocketBuffer) {
                socketBuffer.append(buf, 0, pos);
            } else {
                outputStream.write(buf, 0, pos);
            }
            flush(false);
        }*/
        // START GlassFish Issue 646
        if (pos > 0) {
            flush(false);
        }
        // END GlassFish Issue 646

    }


    /**
     * This method will write the contents of the specyfied message bytes
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     *
     * @param mb data to be written
     */
    protected void write(MessageBytes mb) {

        if (mb.getType() == MessageBytes.T_BYTES) {
            ByteChunk bc = mb.getByteChunk();
            write(bc);
        } else if (mb.getType() == MessageBytes.T_CHARS) {
            CharChunk cc = mb.getCharChunk();
            write(cc);
        } else {
            write(mb.toString());
        }

    }


    /**
     * This method will write the contents of the specyfied message bytes
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     *
     * @param bc data to be written
     */
    protected void write(ByteChunk bc) {
        try {
            realWriteBytes(bc.getBytes(), bc.getStart(), bc.getLength());
        } catch (IOException ex) {
        }
    }


    /**
     * This method will write the contents of the specified char
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     *
     * @param cc data to be written
     */
    protected void write(CharChunk cc) {

        int start = cc.getStart();
        int end = cc.getEnd();
        char[] cbuf = cc.getBuffer();
        for (int i = start; i < end; i++) {
            char c = cbuf[i];
            if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                c = ' ';
            }

            if (pos >= buf.length) {
                try {
                    flush(true);
                } catch (IOException e) {
                }
            }

            buf[pos++] = (byte) c;
        }

        try {
            flush(true);
        } catch (IOException ex) {
        }
    }


    /**
     * This method will write the contents of the specyfied byte
     * buffer to the output stream, without filtering. This method is meant to
     * be used to write the response header.
     *
     * @param b data to be written
     */
    protected void write(byte[] b) {
        try {
            realWriteBytes(b, 0, b.length);
        } catch (IOException ex) {
        }
    }


    /**
     * This method will write the contents of the specyfied String to the
     * output stream, without filtering. This method is meant to be used to
     * write the response header.
     *
     * @param s data to be written
     */
    protected void write(String s) {
        write(s, false);
    }


    /**
     * This method will write the contents of the specyfied String to the
     * output stream, without filtering. This method is meant to be used to
     * write the response header.
     *
     * @param s             data to be written
     * @param replacingCRLF replacing char with lower byte that is \n or \r
     */
    protected void write(String s, boolean replacingCRLF) {

        if (s == null) {
            return;
        }

        // From the Tomcat 3.3 HTTP/1.0 connector
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            // Note:  This is clearly incorrect for many strings,
            // but is the only consistent approach within the current
            // servlet framework.  It must suffice until servlet output
            // streams properly encode their output.
            if ((c <= 31 && c != 9) || c == 127 || c > 255) {
                c = ' ';
            }

            byte b = (byte) c;
            if (replacingCRLF && (b == 10 || b == 13)) {  // \n or \r
                b = 32; // space
            }

            if (pos >= buf.length) {
                try {
                    flush(true);
                } catch (IOException e) {
                }
            }

            buf[pos++] = b;
        }

        try {
            flush(true);
        } catch (IOException ex) {
        }
    }


    /**
     * This method will print the specified integer to the output stream,
     * without filtering. This method is meant to be used to write the
     * response header.
     *
     * @param i data to be written
     */
    protected void write(int i) {

        write(String.valueOf(i));

    }


    /**
     * Callback to write data from the buffer.
     */
    public void realWriteBytes(byte cbuf[], int off, int len)
            throws IOException {
        if (len > 0) {
            if (useSocketBuffer) {
                socketBuffer.append(cbuf, off, len);
            } else {
                outputStream.write(cbuf, off, len);
            }
        }
    }


    // ----------------------------------- OutputStreamOutputBuffer Inner Class


    /**
     * This class is an output buffer which will write data to an output
     * stream.
     */
    public class OutputStreamOutputBuffer
            implements OutputBuffer {


        /**
         * Write chunk.
         */
        public int doWrite(ByteChunk chunk, Response res)
                throws IOException {

            realWriteBytes(chunk.getBuffer(), chunk.getStart(),
                    chunk.getLength());
            return chunk.getLength();
        }


    }


}
