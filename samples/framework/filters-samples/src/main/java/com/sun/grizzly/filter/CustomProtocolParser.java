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

import com.sun.grizzly.ProtocolParser;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.util.WorkerThread;
import static com.sun.grizzly.filter.CustomProtocolHelper.byteBufferHasEnoughSpace;
import static com.sun.grizzly.filter.CustomProtocolHelper.log;
import static com.sun.grizzly.filter.CustomProtocolHelper.sliceBuffer;
import static com.sun.grizzly.filter.CustomProtocolHelper.giveGrizzlyNewByteBuffer;

import java.nio.ByteBuffer;


/**
 * Filter for parsing Messages of the CustomProtocol.
 * The parsing is done in two stages.
 * <p/>
 * First the Protocol Header bytes are read.
 * With this an {@link MessageBase} is constructed
 * Then the payload of the message is read and on completion
 * a {@link MessageBase} is handled to the next Filter in the chain.
 * <p/>
 * CustomProtocolParser tries to avoid byte copying. Therefore when
 * a new Message has being parsed depending on its size and left space
 * a {@link ByteBuffer} slice is created
 * or the message takes ownership of the complete {@link ByteBuffer} and gives
 * {@link com.sun.grizzly.util.WorkerThread} a completely new {@link ByteBuffer}.
 *
 * @author John Vieten 21.06.2008
 * @version 1.0
 */
public class CustomProtocolParser implements ProtocolParser<MessageBase> {
    protected int nextMsgStartPos;
    protected boolean hasMoreBytesToParse;
    protected boolean expectingMoreData;
    protected MessageBase msg;
    protected ByteBuffer byteBuffer;
    protected BytesTrafficListener bytesArrivedListener;
    protected ReplyMessageFactory replyMessageFactory;
    protected boolean debug = false;
    protected SSLConfig sslConfig;

    protected CustomProtocolParser() {

    }
    protected CustomProtocolParser(SSLConfig sslConfig) {
       this.sslConfig=sslConfig;
    }

    /**
     * Since CustomProtocolParser is a statefull createParserProtocolFilter this indicates
     * proper initilization.
     *
     * @param listener gets notified if a chunk of bytes arrives
     * @param replyInputStreamFactory need for creating a replymessage
     *  @param sslConfig configures parser tob used wit SSL
     * @return ParserProtocolFilter for creating CustomProtocolParser
     */
    public static ParserProtocolFilter createParserProtocolFilter(
            final BytesTrafficListener listener,
            final ReplyMessageFactory replyInputStreamFactory,
            final SSLConfig sslConfig
            ) {
        ParserProtocolFilter parserProtocolFilter =  new ParserProtocolFilter() {
            /**
             * Note This method will be only be called if <code>hasMoreBytesToParse</code> and
             * <code>isExpectingMoreData</code> return false.
             *
             * @return new CustomProtocolParser
             */
            public ProtocolParser newProtocolParser() {
                CustomProtocolParser parser = new CustomProtocolParser(sslConfig);
                parser.setBytesArrivedListener(listener);
                parser.setReplyMessageFactory(replyInputStreamFactory);
                return parser;
            }
        };
        parserProtocolFilter.setSSLConfig(sslConfig);
        return   parserProtocolFilter;
    }

    /**
     * When <code>getNextMessage()</code> is called parser first has to check if
     * anouther incomplete {@link com.sun.grizzly.filter.MessageBase} is in the current {@link ByteBuffer}.
     * If yes <code>hasMoreBytesToParse()</code> returns true and <@link CustomProtocolParser will get
     * another chance to parse the current {@link ByteBuffer} starting at position <code>nextMsgStartPos</code>
     * <p/>
     * The current {@link com.sun.grizzly.filter.MessageBase}  now takes ownership of a slice of the
     * current {@link ByteBuffer}. By slicing the costs of an byte array copy are avoided.
     *
     * @return MessageBase the parsed Message
     */
    public MessageBase getNextMessage() {
        MessageBase tmp = msg;
        if (tmp.isError()) {
            // ok let someone else decide what should be done
            hasMoreBytesToParse = false;

        } else {
            hasMoreBytesToParse = getByteBufferMsgBytes() > msg.getNeededBytesSize();
            if (debug) logState("getNextMessage()");
            int startSlice = nextMsgStartPos + (msg.isClean() ? Message.HeaderLength : 0);
            int endSlice = nextMsgStartPos + msg.getNeededBytesSize();
            nextMsgStartPos += msg.getNeededBytesSize();
            msg.addByteBuffer(sliceBuffer(byteBuffer, startSlice, endSlice).asReadOnlyBuffer());
            msg = null;
        }
        expectingMoreData = false;

        return tmp;
    }

    /**
     * Are there more bytes to be parsed in the current {@link ByteBuffer}
     *
     * @return <tt>true</tt> if there are more bytes to be parsed.
     *         Otherwise <tt>false</tt>.
     */
    public boolean hasMoreBytesToParse() {
        return hasMoreBytesToParse;
    }


    /**
     * Indicates whether the buffer has a complete message that can be
     * returned from <code>getNextMessage</code>
     * <p/>
     * Basic algorithm :
     * <p/>
     * 1. When in state no message check if there are enough bytes to parse the header.
     * 1.1 If not check if there is enough space in {@link ByteBuffer} to hold the needed bytes.
     * 1.1.1 If not create a new {@link ByteBuffer} with current header bytes.
     * Remark: This should not happen very often so the copy is affordable.
     * 1.1.2 return and  <code>isExpectingMoreData()</code> should be true.
     * <p/>
     * 2.  When no uncomplete message parse header and construct a new {@link com.sun.grizzly.filter.MessageBase}
     * 2.1 If more bytes are needed and {@link ByteBuffer} has enough space return and expect more bytes.
     * 2.1.1 if not enough space hand {@link com.sun.grizzly.filter.MessageBase} the current {@link ByteBuffer} and
     * give Grizzly a new one and return and expect more bytes.
     * 3. The {@link ByteBuffer} contains enough bytes to construct a complete
     * {@link com.sun.grizzly.filter.MessageBase} so parser does not expect more
     * bytes and <code>getNextMessage</code> will be called.
     */
    public boolean hasNextMessage() {
        hasMoreBytesToParse = false;
        try {
            if (msg == null) {
                if (getByteBufferMsgBytes() < Message.HeaderLength) {

                    if (getByteBufferMsgBytes() >= Message.MagicByteLength) {
                        MessageBase.checkMagic(byteBuffer, nextMsgStartPos);
                    }

                    if (!byteBufferHasEnoughSpace(Message.HeaderLength, byteBuffer)) {
                        byteBuffer.position(nextMsgStartPos);
                        CustomProtocolHelper.giveGrizzlyNewByteBuffer(byteBuffer);
                        nextMsgStartPos = 0;
                    }
                    expectingMoreData = true;
                    if (debug) logState("hasNextMessage() not enough bytes for header");
                    return false;
                }

                msg = MessageBase.parseHeader(byteBuffer, nextMsgStartPos, replyMessageFactory);

            }
            int missingBytesMsg = msg.getNeededBytesSize() - getByteBufferMsgBytes();
            expectingMoreData = missingBytesMsg > 0;
            if (expectingMoreData && !byteBufferHasEnoughSpace(missingBytesMsg, byteBuffer)) {
                // for a given message this can only happen one time!!

                byteBuffer.limit(byteBuffer.position());
                byteBuffer.position(nextMsgStartPos + Message.HeaderLength);
                msg.addByteBuffer(byteBuffer.asReadOnlyBuffer());

                byteBuffer = CustomProtocolHelper.giveGrizzlyNewByteBuffer();
                nextMsgStartPos = 0;
                if (debug) logState("hasNextMessage() not enough space");
            }
        } catch (MessageParseException e) {
            msg = e.convertToMessage();
            expectingMoreData = false;
        }

        return !expectingMoreData;
    }

    /**
     * Does message need more bytes to be read in?
     *
     * @return - <tt>true</tt> if more bytes are needed to construct a
     *         message;  <tt>false</tt>, if no
     *         additional bytes remain to be parsed into a <code>T</code>.
     */
    public boolean isExpectingMoreData() {
        return expectingMoreData;
    }

    /** if this parser is expecting more bytes to be read then the current bytebuffer
       will not be released. On the other hand if parser is done with bytebuffer then
       a new empty bytebuffer is handed to {@link WorkerThread}. Normally this would not
       be necessary but since {@link MessageDispatcher} is going to hand this byteBuffer
       to another Thread a new byteBuffer is given to {@link WorkerThread}
    */
    public boolean releaseBuffer() {
        if (!expectingMoreData) {
            nextMsgStartPos = 0;
            hasMoreBytesToParse = false;
            giveGrizzlyNewByteBuffer();
        }

        return expectingMoreData;
    }

    /**
     * Gets called when new bytes have been added to the current {@link ByteBuffer}.
     *
     * @param byteBuffer the current {@link ByteBuffer}
     */
    public void startBuffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
        if (byteBuffer.capacity() < Message.MessageMaxLength) {
            log("Warning bytebuffer capacity too small. Capacity" + byteBuffer.capacity());
        }
        if (bytesArrivedListener != null) {
            bytesArrivedListener.traffic();
        }
    }

    /**
     * @return the read in bytes by {@link com.sun.grizzly.filter.ReadFilter}
     *         of the current {@link ByteBuffer}
     */
    private int getByteBufferMsgBytes() {
        return byteBuffer.position() - nextMsgStartPos;
    }

    /**
     * @param bytesArrivedListener a listener getting notified whene byte chunks arrive.
     */

    public void setBytesArrivedListener(BytesTrafficListener bytesArrivedListener) {
        this.bytesArrivedListener = bytesArrivedListener;
    }

    /** Clients can be regsitered on a ReplyMessage
     *  The Factory holds  to these Messages
     *
     * @param replyMessageFactory
     */
    public void setReplyMessageFactory(ReplyMessageFactory replyMessageFactory) {
        this.replyMessageFactory = replyMessageFactory;
    }

    private void logState(String where) {
        log(where + " "
                + "Thread:" + Thread.currentThread().getName()
                + ",position:" + byteBuffer.position()
                + ",nextMsgStartPos:" + nextMsgStartPos
                + ",expectingMoreData:" + expectingMoreData
                + ",hasMoreBytesToParse:" + hasMoreBytesToParse
                + ",msg size:" + msg.getMessageSize()
                + ",missingmsg size:" + msg.getNeededBytesSize()
                + ",Type: " + (int) msg.getMessageType()
                + ",moreFrags: " + msg.moreFragmentsToFollow()
        );
    }


}
