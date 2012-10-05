/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.InputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;

import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AjpInputBuffer extends InternalInputBuffer {
    private static final Logger LOGGER = SelectorThread.logger();
    
    private static final byte[] GET_BODY_CHUNK_PACKET;
    
    static {
         byte[] getBodyChunkPayload = new byte[2];
         AjpMessageUtils.putShort(getBodyChunkPayload, 0,
                 (short) AjpConstants.MAX_READ_SIZE);
         
         GET_BODY_CHUNK_PACKET =  AjpMessageUtils.createAjpPacket(
                 AjpConstants.JK_AJP13_GET_BODY_CHUNK,
                    getBodyChunkPayload);
    }

    private final AjpConfiguration configuration;

    private final AjpProcessorTask ajpProcessorTask;
    
    private int thisPacketEnd;
    private int dataPacketRemaining;
    private boolean isIOExceptionOccurred;
    
    public AjpInputBuffer(final AjpConfiguration configuration,
            final AjpProcessorTask ajpProcessorTask, final Request request,
            final int requestBufferSize) {
        super(request, requestBufferSize);
    
        this.ajpProcessorTask = ajpProcessorTask;
        this.configuration = configuration;
        inputStreamInputBuffer = new AjpInputStreamInputBuffer();
    }

    protected void readAjpMessageHeader() throws IOException {
        if (!ensureAvailable(4)) {
            throw new EOFException(sm.getString("iib.eof.error"));
        }
        
        final int magic = AjpMessageUtils.getShort(buf, pos);
        if (magic != 0x1234) {
            throw new IOException(ajpProcessorTask.getSelectionKey().channel() + ": Invalid packet magic number: " +
                    Integer.toHexString(magic) + " pos=" + pos + " lastValid=" + lastValid + " end=" + end);
        }
        
        final int size = AjpMessageUtils.getShort(buf, pos + 2);
        
        if (size + AjpConstants.H_SIZE > AjpConstants.MAX_PACKET_SIZE) {
            throw new IOException(ajpProcessorTask.getSelectionKey().channel() + ": The message is too large. " +
                    (size + AjpConstants.H_SIZE) + ">" +
                    AjpConstants.MAX_PACKET_SIZE);
        }
        
        pos += 4;
        
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        ajpRequest.setLength(size);
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "{0}: Header parsed. Size={1}",
                    new Object[] {ajpProcessorTask.getSelectionKey().channel(), size});
        }
    }

    @Override
    public void parseRequestLine() throws IOException {
        readAjpPacketPayload();
    }

    @Override
    public void parseHeaders() throws IOException {
        super.parseHeaders();
        
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        if (ajpRequest.getType() == AjpConstants.JK_AJP13_FORWARD_REQUEST) {
            parseAjpHttpHeaders();
        }        
    }

    
    @Override
    public boolean parseHeader() throws IOException {
        return false;
    }

    protected void readAjpPacketPayload() throws IOException {
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        final int length = ajpRequest.getLength();
        
        if (!ensureAvailable(length)) {
            throw new EOFException(sm.getString("iib.eof.error"));
        }

        
        thisPacketEnd = pos + length;
        
        final int type = ajpRequest.isForwardRequestProcessing() ?
              AjpConstants.JK_AJP13_DATA : buf[pos++] & 0xFF;
        
        ajpRequest.setType(type);
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "{0}: Payload parsed. Type={1}",
                    new Object[] {ajpProcessorTask.getSelectionKey().channel(), type});
        }
    }

    protected void parseAjpHttpHeaders() {
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        pos = end = AjpMessageUtils.decodeForwardRequest(buf, pos,
                configuration.isTomcatAuthentication(), ajpRequest);

        final String secret = configuration.getSecret();
        
        if (secret != null) {
            final String epSecret = ajpRequest.getSecret();
            if (epSecret == null || !secret.equals(epSecret)) {
                throw new IllegalStateException("Secret doesn't match");
            }
        }

        final long contentLength = request.getContentLength();
        if (contentLength > 0) {
            // if content-length > 0 - the first data chunk will come immediately,
            ajpRequest.setContentBytesRemaining((int) contentLength);
            ajpRequest.setExpectContent(true);
        } else {
            // content-length == 0 - no content is expected
            ajpRequest.setExpectContent(false);
        }
    }
    
    final void getBytesToMB(final MessageBytes messageBytes) {
        pos = AjpMessageUtils.getBytesToByteChunk(buf, pos, messageBytes);
    }

    @Override
    protected final boolean fill() throws IOException {
        throw new IOException("No I/O stream for AJP (should never be called)");
    }

    protected boolean ensureAvailable(final int length) throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "{0}: ensureAvailable:start. length={1}, pos={2}, lastValid={3}, end={4}, thisPacketEnd={5}",
                    new Object[] {getChannel(), length, pos, lastValid, end, thisPacketEnd});
        }
        
        if (isIOExceptionOccurred) {
            return false;
        }
        
        final int available = available();
        
        if (available >= length) {
            return true;
        }

        if (pos == lastValid) {
            pos = lastValid = end;
        }
        
        // check if we can read the required amount of data to this buf
        if (pos + length > buf.length) {
            // we have to shift available data
            
            // check if we can reuse this array as target
            final byte[] targetArray;
            final int offs;
            if (end + length <= buf.length) { // we can use this array
                targetArray = buf;
                offs = end;
            } else { // this array is not big enough, we have to create a new one
                targetArray =
                        new byte[Math.max(buf.length, AjpConstants.MAX_PACKET_SIZE * 2)];
                offs = 0;
                end = 0;
            }
            
            if (available > 0) {
                System.arraycopy(buf, pos, targetArray, offs, available);
            }
            
            buf = targetArray;
            pos = offs;
            lastValid = pos + available;
        }

        boolean error = false;
        try {
            while (lastValid - pos < length) {
                error = true;
                final int readNow;
                try {
                    readNow = inputStream.read(buf, lastValid, buf.length - lastValid);
                    if (readNow < 0) {
                        return false;
                    }
                } catch (IOException ioe) {
                    return false;
                }
                
                error = false;
                lastValid += readNow;
            }
        } finally {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "{0}: ensureAvailable:exit. error={1}, pos={2}, lastValid={3}, end={4}, thisPacketEnd={5}",
                        new Object[]{getChannel(), error, pos, lastValid, end, thisPacketEnd});

            }
            if (error) {
                ajpProcessorTask.error();  // Notify ProcessorTask about the error
                isIOExceptionOccurred = true;
                pos = lastValid = thisPacketEnd = end;
            }
        }
        
        return true;
    }

    @Override
    public void endRequest() throws IOException {
        pos = thisPacketEnd;
        end = 0;
        dataPacketRemaining = 0;
        isIOExceptionOccurred = false;
    }
    
    @Override
    public void recycle() {
        thisPacketEnd = 0;
        dataPacketRemaining = 0;
        isIOExceptionOccurred = false;
        
        super.recycle();
    }

    void parseDataChunk() throws IOException {
        pos = thisPacketEnd; // Reset pos to the next message
        
        readAjpMessageHeader();
        readAjpPacketPayload();
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        
        if (ajpRequest.getLength() != available()) {
            throw new IOException("Unexpected: read more data than JK_AJP13_DATA has");
        }

        if (ajpRequest.getType() != AjpConstants.JK_AJP13_DATA) {
            throw new IOException("Expected message type is JK_AJP13_DATA");
        }
        
        // Skip the content length field - we know the size from the packet header
        pos += 2;
        
        dataPacketRemaining = available();
    }
    
    private void requestDataChunk() throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "{0}: requestDataChunk. pos={1}, lastValid={2}, end={3}, thisPacketEnd={4}",
                    new Object[]{ajpProcessorTask.getSelectionKey().channel(), pos, lastValid, end, thisPacketEnd});
        }
        ((AjpOutputBuffer) request.getResponse().getOutputBuffer())
                .writeEncodedAjpMessage(GET_BODY_CHUNK_PACKET, 0,
                GET_BODY_CHUNK_PACKET.length, true);
    }
    
    private SelectableChannel getChannel() {
        return ajpProcessorTask != null &&
                ajpProcessorTask.getSelectionKey() != null ?
                ajpProcessorTask.getSelectionKey().channel() : null;
    }
    
    // ------------------------------------- InputStreamInputBuffer Inner Class

        
    /**
     * This class is an AJP input buffer which will read its data from an input
     * stream.
     */
    protected class AjpInputStreamInputBuffer 
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         */
        public int doRead(ByteChunk chunk, Request req ) 
            throws IOException {
    
            final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
            if (!ajpRequest.isExpectContent()) {
                return -1;
            }

            if (ajpRequest.getContentBytesRemaining() <= 0) {
                return -1;
            }

            if (dataPacketRemaining <= 0) {
                requestDataChunk();
                parseDataChunk();
            }

            final int length = dataPacketRemaining;
            chunk.setBytes(buf, pos, dataPacketRemaining);
            pos = pos + dataPacketRemaining;
            ajpRequest.setContentBytesRemaining(
                    ajpRequest.getContentBytesRemaining() - dataPacketRemaining);
            dataPacketRemaining = 0;
            
            return length;
        }
    }
}
