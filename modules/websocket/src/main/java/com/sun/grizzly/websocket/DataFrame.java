/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.websocket;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;

/**
 * WebSocket dataframe(s) wrapper.
 *
 * @author gustav trede
 * @since 2009
 */
public class DataFrame {

    protected final boolean isText;
    protected final ByteBuffer rawFrameData;
    protected CharBuffer text; // not volatile for now.
    //protected String textb;

    /**
     *
     * @param textUTF8
     * @throws CharacterCodingException
     */
    public DataFrame(String textUTF8) throws CharacterCodingException {
        this(CharBuffer.wrap(textUTF8));//can throw NPE
    }

    /**
     *
     * @param textUTF8
     * @throws CharacterCodingException
     */
    public DataFrame(CharBuffer textUTF8) throws CharacterCodingException {
        if (textUTF8 == null)
            throw new IllegalArgumentException("textUTF8 is null");
        this.isText       = true;
        this.text         = textUTF8;
        this.rawFrameData = WebSocketUtils.encode(textUTF8);
    }

    /**
     * TODO: allow this method as public ?<br>
     * The passed rawFrameData ByteBuffer data bytes might not be allowed to
     * modified:
     * Send method will slice the rawFrameData if it cant be sent at once.
     * The slice will be processed by another thread.<br>
     *
     * Does not validate the data frame.
     * @param istext
     * @param rawFrameData
     */
    public DataFrame(boolean istext, ByteBuffer rawFrameData) {
        if (rawFrameData == null)
            throw new IllegalArgumentException("rawFrameData is null");
        this.isText       = istext;
        this.rawFrameData = rawFrameData;
    }

    @Override
    protected DataFrame clone(){
        return new DataFrame(isText,rawFrameData.duplicate());
    }


    /**
     * Returns true if text and false if binary frame.
     * @return
     */
    public boolean isText() {
        return isText;
    }

    /**
     * Returns null if not a text frame.
     * @return
     * @throws CharacterCodingException
     */
    public CharBuffer getText() throws CharacterCodingException {
        return text==null&&isText?text=WebSocketUtils.decode(rawFrameData):text;
    }

    /**
     * TODO: allow this method as public ?
     * see the corresponding constructors jdoc.
     * @return
     */
    public ByteBuffer getRawFrameData() {
        return rawFrameData;
    }


    /**
     * TODO: design and impl some validation.
     * @param rawFrameData
     * @return
     */
    static boolean validateFrame(ByteBuffer rawFrameData){
        return true;
    }

    @Override
    public String toString() {
        return DataFrame.class.getSimpleName()+
                " istext:"+isText+" rawdata:"+rawFrameData+" text:"+text ;
    }

}
