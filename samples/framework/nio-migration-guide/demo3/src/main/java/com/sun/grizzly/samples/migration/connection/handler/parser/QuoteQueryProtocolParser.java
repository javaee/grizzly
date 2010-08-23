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

package com.sun.grizzly.samples.migration.connection.handler.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import com.sun.grizzly.ProtocolParser;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.WorkerThread;

public class QuoteQueryProtocolParser implements ProtocolParser<String> {
    // the limit, if the limit is reach the connection will be closed
    protected static final int LIMITBB = 5;
    protected CharsetDecoder f_asciiDecoder = Charset.forName("ISO-8859-1").newDecoder();
    protected ByteBuffer processingBuffer;
    protected String query = null;
    private boolean eoqFound = false;
    
    private boolean maxBufferReached = false;
    
    // first method to been called
    /**
     * Is this ProtocolParser expecting more data ?
     * 
     * This method is typically called after a call to <code>parseBytes()</code>
     * to determine if the {@link ByteBuffer} which has been parsed
     * contains a partial message
     * 
     * @return - <tt>true</tt> if more bytes are needed to construct a
     *           message;  <tt>false</tt>, if no 
     *           additional bytes remain to be parsed into a <code>T</code>.
     *	 	 Note that if no partial message exists, this method should
     *		 return false.
     */
    public boolean isExpectingMoreData() {

        System.out.println("isExpectingMoreData");

        // we need to loop until when get a query
        return !eoqFound;
    }
    
    // next method after isExpectingMoreData or releaseBuffer
    /**
     * Are there more bytes to be parsed in the {@link ByteBuffer} given
     * to this ProtocolParser's <code>setBuffer</code> ?
     * 
     * This method is typically called after a call to <code>parseBytes()</code>
     * to determine if the {@link ByteBuffer} has more bytes which need to
     * parsed into a message.
     * 
     * @return <tt>true</tt> if there are more bytes to be parsed.
     *         Otherwise <tt>false</tt>.
     */
    public boolean hasMoreBytesToParse() {
        System.out.println("hasMoreBytesToParse");
    	/*
        if (debug) System.out.println("hasMoreBytesToParse()");
        if (savedBuffer == null) {
        if (debug) System.out.println("hasMoreBytesToParse() savedBuffer == null, return false");
        return false;
        }
         */
        return eoqFound && processingBuffer != null && processingBuffer.position() > 0;
    }
    
    // if isExpectingMoreData : 2th method
    /**
     * No more parsing will be done on the buffer passed to
     * <code>startBuffer.</code>
     * Set up the buffer so that its position is the first byte that was
     * not part of a full message, and its limit is the original limit of
     * the buffer.
     *
     * @return -- true if the parser has saved some state (e.g. information
     * data in the buffer that hasn't been returned in a full message);
     * otherwise false. If this method returns true, the framework will
     * make sure that the same parser is used to process the buffer after
     * more data has been read.
     */
    public boolean releaseBuffer() {
        System.out.println("releaseBuffer");

        if (processingBuffer != null) {
            processingBuffer.compact();
        }
        
        processingBuffer = null;

        eoqFound = false;

        return false;
    }
    
    // method after hasMoreBytesToParse if it's true
    /**
     * Set the buffer to be parsed. This method should store the buffer and
     * its state so that subsequent calls to <code>getNextMessage</code>
     * will return distinct messages, and the buffer can be restored after
     * parsing when the <code>releaseBuffer</code> method is called.
     */
    public void startBuffer(ByteBuffer bb) {
        System.out.println("startBuffer");

        bb.flip();
        processingBuffer = bb;
        
        //System.out.println("capacity=" + processingBuffer.capacity());
        
    }

    /**
     * Get the next complete message from the buffer, which can then be
     * processed by the next filter in the protocol chain. Because not all
     * filters will understand protocol messages, this method should also
     * set the position and limit of the buffer at the start and end
     * boundaries of the message. Filters in the protocol chain can
     * retrieve this message via context.getAttribute(MESSAGE)
     *
     * @return The next message in the buffer. If there isn't such a message,
     *	return <code>null.</code>
     *
     */
    public String getNextMessage() {
        System.out.println("getNextMessage");
    	if(maxBufferReached){
    		return "MAX";
    	}
        return query;
    }

    /**
     * Indicates whether the buffer has a complete message that can be
     * returned from <code>getNextMessage</code>. Smart implementations of
     * this will set up all the information so that an actual call to
     * <code>getNextMessage</code> doesn't need to re-parse the data.
     */
    public boolean hasNextMessage() {
        System.out.println("hasNextMessage");

        if (processingBuffer == null) {
            return false;
        }
        if (processingBuffer.hasRemaining()) {
            System.out.println("hasNextMessage: " + new String(processingBuffer.array(), processingBuffer.arrayOffset(), processingBuffer.remaining()));
            // decode the buffer
            String msg = null;
            try {

                ByteBuffer tmp = processingBuffer.duplicate();
				//tmp.flip();   // not needed because processingBuffer was previously flip
                msg = f_asciiDecoder.decode(tmp).toString();
            } catch (CharacterCodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            //System.out.println("msg=" + msg);

            int index = msg.indexOf("[eoq]");
            if (index > -1) {

                query = msg.substring(0, index);
                //System.out.println("Query = " + query);

                // We need to kept what is after the EOQ
                processingBuffer.clear();
                String substr = msg.substring(index + "[eoq]".length());
                System.out.println("index: " + index + " substr: " + substr + " substr.len: " + substr.length());
                processingBuffer.put(msg.substring(index + "[eoq]".length()).getBytes());
                processingBuffer.flip();
                eoqFound = true;
            } else {
                // Check if buffer is full
                if (processingBuffer.remaining() == processingBuffer.capacity()) {
                    // If full - reallocate
                    
                    // but check if the max length is attein
                	if(processingBuffer.capacity() + processingBuffer.remaining()<LIMITBB){
	                    ByteBuffer newBB = ByteBufferFactory.allocateView(
	                            processingBuffer.capacity() * 2, 
	                            processingBuffer.isDirect());
	                    newBB.put(processingBuffer);
	                    processingBuffer = newBB;
	                    WorkerThread workerThread = (WorkerThread) Thread.currentThread();
	                    workerThread.setByteBuffer(processingBuffer);
                	} else {
                		System.out.println("BUFFER MAX REACH!");
                		
                        processingBuffer.clear();
                        
                		maxBufferReached = true;
                		
                		return maxBufferReached;
                	}
                }
                
                eoqFound = false; 
            }

        }

        //System.out.println("hasNextMessage() result = " + eoqFound);

        return eoqFound;
    }
}
