/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.websockets.frame;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.memory.MemoryUtils;
import java.nio.charset.Charset;
import java.util.logging.Logger;

/**
 * General abstraction, which represents {@link WebSocket} frame.
 * Contains a set of static createXXX methods in order to create specific frame.
 *
 * @author Alexey Stashok
 */
public abstract class Frame {

    private static final Logger logger = Grizzly.logger(Frame.class);
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    /**
     * Create the stream-based frame, which will contain UTF-8 string.
     * So far it's the only frame type officially supported for transferring data over {@link WebSocket}s.
     * @param text the text.
     *
     * @return the {@link Frame}.
     */
    public static final Frame createTextFrame(String text) {
        return createFrame(0, text);
    }

    /**
     * Create the close frame, after sending which the {@link WebSocket} communication will be closed.
     *
     * @return the close frame.
     */
    public static final Frame createCloseFrame() {
        return new FixedLengthFrame(0xFF, null);
    }

    /**
     * Create a custom typed frame, which will contain a text, which will be encoded using UTF-8 charset.
     *
     * @param type a frame type.
     * @param text text value.
     * 
     * @return the frame.
     */
    public static final Frame createFrame(int type, String text) {
        return createFrame(type, text, UTF8_CHARSET);
    }

    /**
     * Create a custom typed frame, which will contain a text, which will be encoded using given charset.
     *
     * @param type a frame type.
     * @param text text value.
     * @param charset text charset to use during the frame encoding.
     *
     * @return the frame.
     */
    public static final Frame createFrame(int type, String text, Charset charset) {
        return createFrame(type, MemoryUtils.wrap(null, text, charset));
    }

    /**
     * Create a custom typed frame, which will contain a binary data.
     * @param type a frame type.
     * @param data binary data
     *
     * @return the frame.
     */
    public static final Frame createFrame(int type, Buffer data) {
        if ((type & 0x80) == 0) {
            return new StreamFrame(type, data);
        } else {
            return new FixedLengthFrame(type, data);
        }
    }

    // the frame type
    protected int type;
    
    // the frame text representation
    protected String text;
    // the charset used during the frame encoding.
    private Charset lastCharset;

    // the frame binary representation
    protected Buffer buffer;

    /**
     * Construct a frame using the given type and binary data.
     * 
     * @param type the frame type.
     * @param buffer the binary data.
     */
    protected Frame(int type, Buffer buffer) {
        this.type = type;
        this.buffer = buffer;
    }

    /**
     * Get the frame type.
     * @return the frame type.
     */
    public int getType() {
        return type;
    }

    /**
     * Get the frame pyload as text using UTF-8 charset.
     * @return the frame pyload as text using UTF-8 charset.
     */
    public String getAsText() {
        return getAsText(UTF8_CHARSET);
    }

    /**
     * Get the frame pyload as text using given charset.
     * @param charset {@link Charset} to use.
     * @return the frame pyload as text using given charset.
     */
    public String getAsText(Charset charset) {
        if (text == null || !charset.equals(lastCharset)) {
            text = buffer.toStringContent(charset);
            lastCharset = charset;
        }
        return text;
    }

    /**
     * Get the frame pyload as binary data.
     * @return the frame pyload as binary data.
     */
    public Buffer getAsBinary() {
        return buffer;
    }

    /**
     * Returns the frame pyload as text using UTF-8 charset.
     * @return the frame pyload as text using UTF-8 charset.
     */
    @Override
    public String toString() {
        return getAsText();
    }

    /**
     * Returns <tt>true</tt>, if this frame is close frame, or <tt>false</tt> otherwise.
     * @return <tt>true</tt>, if this frame is close frame, or <tt>false</tt> otherwise.
     */
    public abstract boolean isClose();

    /**
     * Serializes this frame into a Grizzly {@link Buffer}.
     *
     * @return {@link Buffer}, which contains serialized <tt>Frame</tt> data.
     */
    public abstract Buffer serialize();

    /**
     * Parses data from the Grizzly {@link Buffer} into this <tt>Frame</tt>
     *
     * @return {@link ParseResult}, which represents result of parsing operation.
     */
    public abstract ParseResult parse(Buffer buffer);
}