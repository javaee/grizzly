/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filter;

import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Stream which wraps bytes into an Custom Protocol.
 * See {@link com.sun.grizzly.filter.MessageBase#parseHeader} for
 * a dscription of the header layout
 *
 * @author John Vieten 23.06.2008
 * @version 1.0
 */
public abstract class ProtocolOutputStream extends OutputStream {
    private  IOExceptionHandler exceptionHandler;
    public  void setExceptionHandler(IOExceptionHandler handler) {
        exceptionHandler = handler;
    }

    protected byte buf[];
    private int position = 0;
    /**
     * To help processing of Framents
     * every Message (except a Fragment) has a unique ID
     */
    private static AtomicInteger uniqueGlobalMessageId = new AtomicInteger(0);

    private Integer requestId;
    private byte messageType;
    private byte hasMoreFragments = 0;
    private boolean gzip;
    private boolean applicationLayerError;
    private Integer sessionId;
    private boolean closedFlag = false;
    private int messageCount = 0;
    private int capacity = 0;
    private int uniqueMessageId = 0;
    private BytesTrafficListener trafficListener;
    private RemoteInputStream inputStream;


    public abstract  void write(ByteBuffer b,AsyncWriteCallbackHandler callbackHandler);

    private void setHeaderMask() {

        buf[0] = (byte) ((Message.Magic >>> 24) & 0xFF);
        buf[1] = (byte) ((Message.Magic >>> 16) & 0xFF);
        buf[2] = (byte) ((Message.Magic >>> 8) & 0xFF);
        buf[3] = (byte) ((Message.Magic >>> 0) & 0xFF);
        buf[4] = (byte) 1;
        buf[5] = (messageCount == 0) ? messageType : Message.Message_Fragment;
        buf[6] = closedFlag ? 0 : hasMoreFragments;
        if (gzip) {
            buf[6] = (byte) ((int) buf[6] | (int) Message.GZIP_BIT);

        }
        if(applicationLayerError) {
             buf[6] = (byte) ((int) buf[6] | (int) Message.APPLICATION_LAYER_ERROR_BIT);
        }


        if (messageCount == 0) {
                uniqueMessageId = uniqueGlobalMessageId.incrementAndGet();
                // just in case
                if (uniqueMessageId==Integer.MAX_VALUE) {
                    uniqueGlobalMessageId .set(0);
                }
         }


        buf[7] = (byte) ((uniqueMessageId >>> 24) & 0xFF);
        buf[8] = (byte) ((uniqueMessageId >>> 16) & 0xFF);
        buf[9] = (byte) ((uniqueMessageId >>> 8) & 0xFF);
        buf[10] = (byte) ((uniqueMessageId >>> 0) & 0xFF);

        // size
        buf[11] = (byte) ((position >>> 24) & 0xFF);
        buf[12] = (byte) ((position >>> 16) & 0xFF);
        buf[13] = (byte) ((position >>> 8) & 0xFF);
        buf[14] = (byte) ((position >>> 0) & 0xFF);

        if (requestId != null) {
            buf[15] = (byte) ((requestId >>> 24) & 0xFF);
            buf[16] = (byte) ((requestId >>> 16) & 0xFF);
            buf[17] = (byte) ((requestId >>> 8) & 0xFF);
            buf[18] = (byte) ((requestId >>> 0) & 0xFF);
        }
        if (sessionId != null) {
            buf[19] = (byte) ((sessionId >>> 24) & 0xFF);
            buf[20] = (byte) ((sessionId >>> 16) & 0xFF);
            buf[21] = (byte) ((sessionId >>> 8) & 0xFF);
            buf[22] = (byte) ((sessionId >>> 0) & 0xFF);
        }
        messageCount++;
    }

    public void setApplicationLayerError(boolean applicationLayerError) {
        this.applicationLayerError = applicationLayerError;
    }


    public void setMessageType(byte messageType) {
        this.messageType = messageType;
    }

    public void setInputStream(RemoteInputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void setTrafficListener(BytesTrafficListener trafficListener) {
        this.trafficListener = trafficListener;
    }


    /**
     * @param asynWritable
     * @param messageType
     * @param requestId
     * @param sessionId
     * @param gzip
     */
    public ProtocolOutputStream(final byte messageType,
                                final Integer requestId,
                                final Integer sessionId,
                                final boolean gzip) {
        buf = new byte[Message.MessageMaxLength];

        this.position = Message.HeaderLength;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.messageType = messageType;
        this.capacity = Message.MessageMaxLength;
        this.gzip = gzip;

    }
    /**
     * @param asynWritable
     * @param messageType
     * @param gzip
     */
    public ProtocolOutputStream(final byte messageType,
                                final boolean gzip) {
        buf = new byte[Message.MessageMaxLength];
        this.position = Message.HeaderLength;
        this.requestId = 0;
        this.sessionId = 0;
        this.messageType = messageType;
        this.capacity = Message.MessageMaxLength;
        this.gzip = gzip;

    }

    private void writeMessage() throws IOException {
        setHeaderMask();
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf, 0, position);
        final WriteCallbackHandler writeCallbackHandler = new WriteCallbackHandler();

        write(byteBuffer,writeCallbackHandler);

        position = Message.HeaderLength;
        buf = new byte[Message.MessageMaxLength];
        if (trafficListener != null) {
            trafficListener.traffic();
        }
    }



    public synchronized void write(int b) throws IOException {
        if (position >= buf.length) {
            hasMoreFragments = Message.MORE_FRAGMENTS_BIT;
            writeMessage();
        }
        buf[position++] = (byte) b;
    }


    @Override
    public synchronized void write(byte b[], int off, int len) throws IOException {
        while (len > (capacity - position)) {
            int nCopy = capacity - position;
            System.arraycopy(b, off, buf, position, nCopy);
            position += nCopy;
            hasMoreFragments = Message.MORE_FRAGMENTS_BIT;
            writeMessage();
            len = len - nCopy;
            off += nCopy;
        }
        if (len == 0) return;
        System.arraycopy(b, off, buf, position, len);
        position += len;
    }

    public Integer getRequestId() {
        return requestId;
    }

    @Override
    public synchronized void flush() throws IOException {
       hasMoreFragments = Message.MORE_FRAGMENTS_BIT;
       writeMessage();
    }


    @Override
    public void close() throws IOException {
        closedFlag = true;
        writeMessage();
        buf = null;
        super.close();
    }

    private class WriteCallbackHandler implements AsyncWriteCallbackHandler {
        public void onWriteCompleted(SelectionKey selectionKey, AsyncQueueWriteUnit asyncWriteRecord) {

        }

        public void onException(Exception e,
                                  SelectionKey selectionKey,
                                  ByteBuffer byteBuffer, Queue<AsyncQueueWriteUnit> asyncWriteQueueRecords) {
            if (!(e instanceof IOException)) {
                e = new IOException(e.getMessage());
            }
            
            if (inputStream != null) {
                inputStream.setIOException((IOException) e);
            }
            if (exceptionHandler != null) {
                exceptionHandler.handle((IOException) e);
            } else {
                e.printStackTrace();
            }
        }
    }


}
