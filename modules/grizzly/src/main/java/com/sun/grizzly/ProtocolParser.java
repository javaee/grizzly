/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import java.nio.ByteBuffer;

/**
 * An interface that knows how to parse bytes into a protocol data unit.
 *
 * @author Charlie Hunt
 */
public interface ProtocolParser<T> {
    
    /**
     * Is this ProtocolParser expecting more data ?
     * 
     * This method is typically called after a call to 
     * {@link ProtocolParser#hasNextMessage()} to determine if the
     * {@link ByteBuffer} which has been parsed contains a partial message
     * 
     * @return - <tt>true</tt> if more bytes are needed to construct a
     *           message;  <tt>false</tt>, if no 
     *           additional bytes remain to be parsed into a protocol data unit.
     *	 	 Note that if no partial message exists, this method should
     *		 return false.
     */
    public boolean isExpectingMoreData();
    
    /**
     * Are there more bytes to be parsed in the {@link ByteBuffer} given
     * to this ProtocolParser's {@link ProtocolParser#startBuffer()} ?
     * 
     * This method is typically called after processing the protocol message,
     * to determine if the {@link ByteBuffer} has more bytes which
     * need to parsed into a next message.
     * 
     * @return <tt>true</tt> if there are more bytes to be parsed.
     *         Otherwise <tt>false</tt>.
     */
    public boolean hasMoreBytesToParse();

    
    /**
     * Get the next complete message from the buffer, which can then be
     * processed by the next filter in the protocol chain. Because not all
     * filters will understand protocol messages, this method should also
     * set the position and limit of the buffer at the start and end
     * boundaries of the message. Filters in the protocol chain can
     * retrieve this message via context.getAttribute(MESSAGE)
     *
     * @return The next message in the buffer. If there isn't such a message,
     *	return <tt>null</tt>.
     *
     */
    public T getNextMessage();

    /**
     * Indicates whether the buffer has a complete message that can be
     * returned from {@link ProtocolParser#getNextMessage()}. Smart 
     * implementations of this will set up all the information so that an
     * actual call to {@link ProtocolParser#getNextMessage()} doesn't need to
     * re-parse the data.
     */
    public boolean hasNextMessage();

    /**
     * Set the buffer to be parsed. This method should store the buffer and
     * its state so that subsequent calls to
     * {@link ProtocolParser#getNextMessage()} will return distinct messages,
     * and the buffer can be restored after parsing when the
     * {@link ProtocolParser#releaseBuffer()} method is called.
     */
    public void startBuffer(ByteBuffer bb);
    
    /**
     * No more parsing will be done on the buffer passed to
     * {@link ProtocolParser#startBuffer()}.
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
    public boolean releaseBuffer();

    /**
     * Used to associate a particular parser with a connection
     */
    public static String PARSER = "ProtocolParser";

    /**
     * Holds the message returned from <code>getNextMessage</code> for
     * use by downstream filters
     */
    public static String MESSAGE = "ProtocolMessage";
}

