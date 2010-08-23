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

import java.nio.ByteBuffer;

import static com.sun.grizzly.filter.CustomProtocolHelper.log;
/**
 * Note  idea taken from  com.sun.corba.se.impl.protocol.giopmsgheaders.MessageBase
 * Knows how to construct various CustomProtocol Messages out of an ByteBuffer.
 *
 * @author John Vieten
 *         Version 1.0
 */
public abstract class MessageBase implements Message {

    private int messageSize;
    private int bytesRemaining =0;
    protected int requestId;
    protected int sessionId;
    protected boolean useGzip;
    protected int uniqueMessageId;

    protected byte flags;
    protected byte messageType;


    /**
     * Parses the Header of the Custom Protocol.
     * The Protocol has the following format:
     *
     * byte  1 Magic      :Used to identify Protocol
     * byte  2 Magic
     * byte  3 Magic
     * byte  4 Magic
     * byte  5 Version    : Version of protocol. 
     * byte  6 Message    : RequestMessage,ReplyMessage,FragmentMessage,...
     * byte  7 Flags      : In the moment : MoreFragmentsToFollow, Gzip, ApplicationLayerError
     * byte  8 Unique Id  : Helper for Fragments (Only unique by connection)
     * byte  9 Unique Id
     * byte 10 Unique Id
     * byte 11 Unique Id
     * byte 12 Size       : How many bytes the Message contains (with Header)
     * byte 13 Size
     * byte 14 Size
     * byte 15 Size
     * byte 16 Request ID : The Request Id of Message (Used to Reply to Requests)
     * byte 17 Request ID
     * byte 18 Request ID
     * byte 19 Request ID
     * byte 20 Session ID : Session Id of Message (Used to authenticate Client).
     * byte 21 Session ID   
     * byte 22 Session ID
     * byte 23 Session ID
     *
     *
     * @param buf  Bytebuffer with read in bytes
     * @param startPosition the message beginning
     *  @param replyInputStreamFactory need for creating a replymessage
     * @return a Message based on the Header bytes
     * @throws MessageParseException Indicating a parse Exception
     */

    public static MessageBase parseHeader(ByteBuffer buf,
                                          int startPosition,
                                          ReplyMessageFactory replyInputStreamFactory)
            throws MessageParseException{
        MessageBase result;
        try {

            byte[] it = new byte[HeaderLength];
            int restorePosition = buf.position();
            buf.position(startPosition);
            buf.get(it);
            buf.position(restorePosition);

            checkMagic(buf,startPosition);


            if (it[4] > CurrentVersion) {

            }
            switch (it[5]) {
                case Message_Request:
                    result = new RequestMessage();
                    break;
                case Message_Reply:
                    // todo will make this part nicer....
                    int wantedrequestId=extractRequestID(buf,startPosition);
                    result = replyInputStreamFactory.getReplyMessage(wantedrequestId);

                    break;
                case Message_Fragment:
                    result = new FragmentMessage();
                    break;
                case Message_Error:
                    result = new MessageError();
                    break;
                default: throw new MessageParseException(
                        getErrorMsg(buf, startPosition, "Unknown Message Type"),
                        MessageError.ErrorCode.ERROR_CODE_UNKOWN_MESSAGE_TYPE);

            }
            result.messageType = it[5];
            result.flags = it[6];
            result.uniqueMessageId = readInt(it[7], it[8], it[9], it[10]);

            result.messageSize = readInt(it[11], it[12], it[13], it[14]);
            result.unmarshalRequestID(buf,startPosition);
            result.unmarshalSessionID(buf,startPosition);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(CustomProtocolHelper.printBuffer("Parse Buffer;",buf));  
            throw new MessageParseException(
                        getErrorMsg(buf, startPosition, "Bad Header Format"),
                        MessageError.ErrorCode.ERROR_CODE_HEADER_FORMAT);

        }
        return result;
    }

    /**
     * Checks if the current byteBuffer has a message starting with the
     * correct <@link Message#Magic>
     * @param buf current buffer containing message bytes
     * @param startPosition  start of current message
     * @throws MessageParseException
     */
    public static void checkMagic(ByteBuffer buf, int startPosition) throws MessageParseException{
            int b1, b2, b3, b4;
            b1 = (buf.get(startPosition) << 24) & 0xFF000000;
            b2 = (buf.get(startPosition+1) << 16) & 0x00FF0000;
            b3 = (buf.get(startPosition+2)<< 8) & 0x0000FF00;
            b4 = (buf.get(startPosition+3) << 0) & 0x000000FF;

            int magic = (b1 | b2 | b3 | b4);

            if (magic != Magic) {
                System.out.println(CustomProtocolHelper.printBuffer("", buf));
                throw new MessageParseException(
                        getErrorMsg(buf, startPosition, "Unknown Magic"),
                        MessageError.ErrorCode.ERROR_CODE_MAGIC);
            }

    }

    

    private static String getErrorMsg(ByteBuffer buf, int startPosition, String hint) {
        StringBuffer sb = new StringBuffer();
        sb.append("Server Protocol Error. Could not parse Header").append(hint).append("\n");
        sb.append("Position:").append(buf.position()).append("\n");
        sb.append("Limit:").append(buf.limit()).append("\n");
        sb.append("Parse Position:").append(startPosition).append("\n");
       // sb.append(CustomProtocolHelper.printBuffer("", buf));

        return sb.toString();
    }

    protected void setMessageType(byte messageType) {
        this.messageType = messageType;
    }

    public byte getMessageType() {
        return messageType;
    }

    public int getUniqueMessageId() {
        return uniqueMessageId;
    }

    public boolean isError() {
        return getMessageType() == Message_Error;
    }


    public boolean isClean() {
       return bytesRemaining ==0;
    }
    void allDataParsed() {

    }

    void addByteBuffer(ByteBuffer byteBuffer) {
        if(bytesRemaining==0) {
            bytesRemaining = HeaderLength;
        }
        bytesRemaining +=byteBuffer.remaining();
        log("MessageBase.addByteBuffer:"+bytesRemaining);
    }



    public int getMessageSize() {
        return messageSize;
    }


    public int getNeededBytesSize() {
        return messageSize - bytesRemaining;
    }
     /**Signals that more  Messages will follow all belonging to
      * the first Message with all the same uniqueId.
      * The last Message will return on {@link #moreFragmentsToFollow} false.
     *
     * @return  if more fragments are expected
     */

    public boolean moreFragmentsToFollow() {
        return ((this.flags & MORE_FRAGMENTS_BIT) == MORE_FRAGMENTS_BIT);
    }
    public boolean isGzip() {
        return ((this.flags & GZIP_BIT) == GZIP_BIT);
    }
    public boolean isApplicationLayerException() {
        return ((this.flags & APPLICATION_LAYER_ERROR_BIT) == APPLICATION_LAYER_ERROR_BIT);
    }



    public int getRequestId() {
        return requestId;
    }

    void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    private void unmarshalRequestID(final ByteBuffer byteBuffer,final int s) {
        int b1, b2, b3, b4;
        b1 = (byteBuffer.get(s+15) << 24) & 0xFF000000;
        b2 = (byteBuffer.get(s+16) << 16) & 0x00FF0000;
        b3 = (byteBuffer.get(s+17) << 8) & 0x0000FF00;
        b4 = (byteBuffer.get(s+18) << 0) & 0x000000FF;
        setRequestId (b1 | b2 | b3 | b4);
    }

    /**
     * helper just until i make code nicer
     * @param byteBuffer
     * @param s
     * @return
     */
     private static int extractRequestID(final ByteBuffer byteBuffer,final int s) {
        int b1, b2, b3, b4;
        b1 = (byteBuffer.get(s+15) << 24) & 0xFF000000;
        b2 = (byteBuffer.get(s+16) << 16) & 0x00FF0000;
        b3 = (byteBuffer.get(s+17) << 8) & 0x0000FF00;
        b4 = (byteBuffer.get(s+18) << 0) & 0x000000FF;
        return (b1 | b2 | b3 | b4);
    }

    public int getSessionId() {
        return sessionId;
    }

    private void unmarshalSessionID(final ByteBuffer byteBuffer,final int s) {
        int b1, b2, b3, b4;
        b1 = (byteBuffer.get(s+19) << 24) & 0xFF000000;
        b2 = (byteBuffer.get(s+20) << 16) & 0x00FF0000;
        b3 = (byteBuffer.get(s+21) << 8) & 0x0000FF00;
        b4 = (byteBuffer.get(s+22) << 0) & 0x000000FF;


        this.sessionId = (b1 | b2 | b3 | b4);
    }




    private static int readInt(byte b1, byte b2, byte b3, byte b4) {
        int a1, a2, a3, a4;
        a1 = (b1 << 24) & 0xFF000000;
        a2 = (b2 << 16) & 0x00FF0000;
        a3 = (b3 << 8) & 0x0000FF00;
        a4 = (b4 << 0) & 0x000000FF;
        return (a1 | a2 | a3 | a4);
    }


}
