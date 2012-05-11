/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.res.StringManager;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;

public class InternalInputBuffer implements InputBuffer {
    private static final boolean IS_HEADER_CASE_SENSITIVE;

    static {
        IS_HEADER_CASE_SENSITIVE = Boolean.getBoolean("com.sun.grizzly.http.caseSensitiveHeader");
    }
    // -------------------------------------------------------------- Constants


    // ----------------------------------------------------------- Constructors
    /**
     * Void constructor.
     */
    public InternalInputBuffer() {
    }

    /**
     * Default constructor.
     */
    public InternalInputBuffer(Request request) {
        this(request, Constants.DEFAULT_HTTP_HEADER_BUFFER_SIZE);
    }


    /**
     * Alternate constructor.
     */
    public InternalInputBuffer(Request request, int headerBufferSize) {

        this.request = request;
        headers = request.getMimeHeaders();

        buf = new byte[headerBufferSize];

        inputStreamInputBuffer = new InputStreamInputBuffer();

        filterLibrary = new InputFilter[0];
        activeFilters = new InputFilter[0];
        lastActiveFilter = -1;

        parsingHeader = true;
        swallowInput = true;

    }


    // -------------------------------------------------------------- Variables


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package,
                                 Constants.class.getClassLoader());


    // ----------------------------------------------------- Instance Variables


    /**
     * Associated Coyote request.
     */
    protected Request request;


    /**
     * Headers of the associated request.
     */
    protected MimeHeaders headers;


    /**
     * State.
     */
    protected boolean parsingHeader;


    /**
     * Swallow input ? (in the case of an expectation)
     */
    protected boolean swallowInput;
    
    /**
     * Flag, which indicates if we're currently swallowing this request input
     */
    private boolean isSwallowingInput;
    
    /**
     * Number of bytes being read in "input-swallow" mode
     */
    private long swallowedBytesCounter;

    /**
     * Max number of bytes Grizzly will try to swallow in order to read off
     * the current request payload and prepare input to process next request.
     */
    private long maxSwallowingInputBytes = -1;

    /**
     * Pointer to the current read buffer.
     */
    protected byte[] buf;


    /**
     * Last valid byte.
     */
    protected int lastValid;


    /**
     * Position in the buffer.
     */
    protected int pos;
    
    /*
     * Pos of the end of the header in the buffer, which is also the
     * start of the body.
     **/
    protected int end;

    /**
     * Underlying input stream.
     */
    protected InputStream inputStream;


    /**
     * Underlying input buffer.
     */
    protected InputBuffer inputStreamInputBuffer;


    /**
     * Filter library.
     * Note: Filter[0] is always the "chunked" filter.
     */
    protected InputFilter[] filterLibrary;


    /**
     * Active filters (in order).
     */
    protected InputFilter[] activeFilters;


    /**
     * Index of the last active filter.
     */
    protected int lastActiveFilter;


    // START OF SJSAS 6231069
    /**
     * The stage we currently are during parsing the request line and
     * the headers
     */ 
    private int stage = 0;
    
    private MessageBytes headerValue = null;
    
    // END OF SJSAS 6231069

    
    // ------------------------------------------------------------- Properties


    /**
     * Set the underlying socket input stream.
     */
    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    
    /**
     * Get the underlying socket input stream.
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    /**
     * Get the max number of bytes Grizzly will try to swallow in order
     * to read off from the current request payload and prepare input to
     * process next request.
     * 
     * @return the max number of bytes Grizzly will try to swallow in order
     * to read off from the current request payload and prepare input to
     * process next request.
     */
    public long getMaxSwallowingInputBytes() {
        return maxSwallowingInputBytes;
    }

    /**
     * Set the max number of bytes Grizzly will try to swallow in order
     * to read off from the current request payload and prepare input to
     * process next request.
     * 
     * @param maxSwallowingInputBytes  the max number of bytes Grizzly will try
     * to swallow in order to read off from the current request payload and
     * prepare input to process next request.
     */
    public void setMaxSwallowingInputBytes(long maxSwallowingInputBytes) {
        this.maxSwallowingInputBytes = maxSwallowingInputBytes;
    }

        
    /**
     * Add an input filter to the filter library.
     */
    public void addFilter(InputFilter filter) {
        InputFilter[] newFilterLibrary = 
            new InputFilter[filterLibrary.length + 1];
        System.arraycopy(filterLibrary, 0, newFilterLibrary, 0, filterLibrary.length);
        newFilterLibrary[filterLibrary.length] = filter;
        filterLibrary = newFilterLibrary;

        activeFilters = new InputFilter[filterLibrary.length];
    }


    /**
     * Get filters.
     */
    public InputFilter[] getFilters() {
        return filterLibrary;
    }


    /**
     * Clear filters.
     */
    public void clearFilters() {
        filterLibrary = new InputFilter[0];
        lastActiveFilter = -1;
    }


    /**
     * Add an input filter to the filter library.
     */
    public void addActiveFilter(InputFilter filter) {
        if (lastActiveFilter == -1) {
            filter.setBuffer(inputStreamInputBuffer);
        } else {
            for (int i = 0; i <= lastActiveFilter; i++) {
                if (activeFilters[i] == filter)
                    return;
            }
            filter.setBuffer(activeFilters[lastActiveFilter]);
        }

        activeFilters[++lastActiveFilter] = filter;

        filter.setRequest(request);

    }


    /**
     * Set the swallow input flag.
     */
    public void setSwallowInput(boolean swallowInput) {
        this.swallowInput = swallowInput;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Recycle the input buffer. This should be called when closing the 
     * connection.
     */
    public void recycle() {

        // Recycle Request object
        request.recycle();

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        inputStream = null;
        lastValid = 0;
        pos = 0;
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;
        isSwallowingInput = false;
        swallowedBytesCounter = 0;
    }


    /**
     * End processing of current HTTP request.
     * Note: All bytes of the current request should have been already 
     * consumed. This method only resets all the pointers so that we are ready
     * to parse the next HTTP request.
     */
    public void nextRequest() {

        // Recycle Request object
        request.recycle();

        // Copy leftover bytes to the beginning of the buffer
        if (lastValid - pos > 0) {
            int npos = 0;
            int opos = pos;
            while (lastValid - opos > opos - npos) {
                System.arraycopy(buf, opos, buf, npos, opos - npos);
                npos += pos;
                opos += pos;
            }
            System.arraycopy(buf, opos, buf, npos, lastValid - opos);
        }

        // Recycle filters
        for (int i = 0; i <= lastActiveFilter; i++) {
            activeFilters[i].recycle();
        }

        // Reset pointers
        lastValid = lastValid - pos;
        pos = 0;
        lastActiveFilter = -1;
        parsingHeader = true;
        swallowInput = true;
        isSwallowingInput = false;
        swallowedBytesCounter = 0;
    }


    /**
     * End request (consumes leftover bytes).
     * 
     * @throws IOException an underlying I/O error occurred
     */
    public void endRequest()
        throws IOException {

        if (swallowInput && (lastActiveFilter != -1)) {
            isSwallowingInput = true;
            
            int extraBytes = (int) activeFilters[lastActiveFilter].end();
            pos = pos - extraBytes;
        }

    }

    
    /**
     * Read the request line. This function is meant to be used during the 
     * HTTP request header parsing. Do NOT attempt to read the request body 
     * using it.
     *
     * @throws IOException If an exception occurs during the underlying socket
     * read operations, or if the given buffer is not big enough to accomodate
     * the whole line.
     */
    public void parseRequestLine()
        throws IOException {
        
        int start = 0;
        // END OF SJSAS 6231069
            
        byte chr = 0;
        do {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos++];

        } while ((chr == Constants.CR) || (chr == Constants.LF));

        pos--;

        // Mark the current buffer position
        start = pos;

        //
        // Reading the method name
        // Method name is always US-ASCII
        //

        boolean space = false;

        while (!space) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            // Spec says single SP but it also says be tolerant of HT
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                space = true;
                request.method().setBytes(buf, start, pos - start);
            }

            pos++;
        }

        // Spec says single SP but also says be tolerant of multiple and/or HT
        while (space) {
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                pos++;
            } else {
                space = false;
            }
        }

        // Mark the current buffer position
        start = pos;
        end = 0;
        int questionPos = -1;

        //
        // Reading the URI
        //

        boolean eol = false;
        while (!space) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            // Spec says single SP but it also says be tolerant of HT
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                space = true;
                end = pos;
            } else if ((buf[pos] == Constants.CR) 
                       || (buf[pos] == Constants.LF)) {
                // HTTP/0.9 style request
                eol = true;
                space = true;
                end = pos;
            } else if ((buf[pos] == Constants.QUESTION) 
                       && (questionPos == -1)) {
                questionPos = pos;
            }

            pos++;

        }
        
        request.unparsedURI().setBytes(buf, start, end - start);
        if (questionPos >= 0) {
            request.queryString().setBytes(buf, questionPos + 1, 
                                           end - questionPos - 1);
            request.requestURI().setBytes(buf, start, questionPos - start);
        } else {
            request.requestURI().setBytes(buf, start, end - start);
        }

        // Spec says single SP but also says be tolerant of multiple and/or HT
        while (space) {
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                pos++;
            } else {
                space = false;
            }
        }

        // Mark the current buffer position
        start = pos;
        end = 0;

        //
        // Reading the protocol
        // Protocol is always US-ASCII
        //   
        while (!eol) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.CR) {
                end = pos;
            } else if (buf[pos] == Constants.LF) {
                if (end == 0)
                    end = pos;
                eol = true;
            }

            pos++;

        }
           
        if ((end - start) > 0) {
            request.protocol().setBytes(buf, start, end - start);
        } else {
            request.protocol().setString("");
        }
    }


    /**
     * Parse the HTTP headers.
     */
    public void parseHeaders()
        throws IOException {

        while (parseHeader()) {
        }

        parsingHeader = false;
        end = pos;
    }


    /**
     * Parse an HTTP header.
     * 
     * @return false after reading a blank line (which indicates that the
     * HTTP header parsing is done
     */
    public boolean parseHeader()
        throws IOException {

        //
        // Check for blank line
        //

        byte chr = 0;             
        while (true) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos];

            if ((chr == Constants.CR) || (chr == Constants.LF)) {
                if (chr == Constants.LF) {
                    pos++;        
                    return false;
                }
            } else {
                break;
            }

            pos++;

        }
        
        // Mark the current buffer position
        int start = pos;

        //
        // Reading the header name
        // Header name is always US-ASCII
        //

        boolean colon = false;
        //MessageBytes headerValue = null;
   
        while (!colon) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.COLON) {
                colon = true;
                headerValue = headers.addValue(buf, start, pos - start);
            }
            chr = buf[pos];
            if (!IS_HEADER_CASE_SENSITIVE && (chr >= Constants.A) && (chr <= Constants.Z)) {
                buf[pos] = (byte) (chr - Constants.LC_OFFSET);
            }

            pos++;

        } 
        
        // Mark the current buffer position
        start = pos;
        int realPos = pos;

        //
        // Reading the header value (which can be spanned over multiple lines)
        //

        boolean eol = false;
        boolean validLine = true;

        while (validLine) {

            boolean space = true;  
            // Skipping spaces
            while (space) {

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill())
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                if ((buf[pos] == Constants.SP) || (buf[pos] == Constants.HT)) {
                    pos++;
                } else {
                    space = false;
                }

            }
            
            int lastSignificantChar = realPos;                 
            // Reading bytes until the end of the line
            while (!eol) {

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill())
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                if (buf[pos] == Constants.CR) {
                } else if (buf[pos] == Constants.LF) {
                    eol = true;
                } else if (buf[pos] == Constants.SP) {
                    buf[realPos] = buf[pos];
                    realPos++;
                } else {
                    buf[realPos] = buf[pos];
                    realPos++;
                    lastSignificantChar = realPos;
                }

                pos++;

            }

            realPos = lastSignificantChar;        

            // Checking the first character of the new line. If the character
            // is a LWS, then it's a multiline header
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos];
            if ((chr != Constants.SP) && (chr != Constants.HT)) {
                validLine = false;
            } else {
                eol = false;
                // Copying one extra space in the buffer (since there must
                // be at least one space inserted between the lines)
                buf[realPos] = chr;
                realPos++;
            }
        }

        // Set the header value
        headerValue.setBytes(buf, start, realPos - start);

        return true;

    }

    /**
     * Return the number of bytes left after a valid http request has been 
     * processed.
     * @return the number of bytes left after a valid http request has been 
     * processed. 
     */
    public int available(){
        return lastValid - pos;
    }

    // ---------------------------------------------------- InputBuffer Methods


    /**
     * Read some bytes.
     */
    public int doRead(ByteChunk chunk, Request req) 
        throws IOException {
        
        if (request.getResponse().isSuspended()){
            request.action(ActionCode.RESET_SUSPEND_TIMEOUT, null);
        }  
        
        if (lastActiveFilter == -1)
            return inputStreamInputBuffer.doRead(chunk, req);
        else
            return activeFilters[lastActiveFilter].doRead(chunk,req);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Fill the internal buffer using data from the underlying input stream.
     * 
     * @return false if at end of stream
     */
    protected boolean fill()
        throws IOException {

        if (isSwallowingInput && maxSwallowingInputBytes >= 0
                && swallowedBytesCounter >= maxSwallowingInputBytes) {
            throw new EOFException("Can not skip more than " + maxSwallowingInputBytes + " bytes");
        }

        int nRead = 0;

        if (parsingHeader) {

            if (lastValid == buf.length) {
                throw new BufferOverflowException();
            }

            nRead = inputStream.read(buf, pos, buf.length - lastValid);
            if (nRead > 0) {
                request.setBytesRead(request.getBytesRead() + nRead);
                lastValid = pos + nRead;
            }

        } else {

            if (buf.length - end < 4500) {
                // In this case, the request header was really large, so we allocate a 
                // brand new one; the old one will get GCed when subsequent requests
                // clear all references
                buf = new byte[buf.length];
                end = 0;
            }
            pos = end;
            lastValid = pos;
            nRead = inputStream.read(buf, pos, buf.length - lastValid);
            if (nRead > 0) {
                request.setBytesRead(request.getBytesRead() + nRead);
                lastValid = pos + nRead;
            }

        }
        
        final boolean success = nRead > 0;
        
        if (success && isSwallowingInput) {
            swallowedBytesCounter += nRead;
        }
        
        return success;

    }

    // ------------------------------------- InputStreamInputBuffer Inner Class


    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    protected class InputStreamInputBuffer 
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         */
        public int doRead(ByteChunk chunk, Request req ) 
            throws IOException {

            if (pos >= lastValid) {
                if (!fill())
                    return -1;
            }

            int length = lastValid - pos;
            chunk.setBytes(buf, pos, length);
            pos = lastValid;

            return (length);

        }


    }


}

