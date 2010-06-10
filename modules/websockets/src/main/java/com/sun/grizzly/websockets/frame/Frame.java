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

public abstract class Frame {

    private static final Logger logger = Grizzly.logger(Frame.class);
    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    public static final Frame createTextFrame(String text) {
        return createFrame(0, text);
    }

    public static final Frame createCloseFrame() {
        return new FixedLengthFrame(0xFF, null);
    }

    public static final Frame createFrame(int type, String text) {
        return createFrame(type, text, UTF8_CHARSET);
    }

    public static final Frame createFrame(int type, String text, Charset charset) {
        return createFrame(type, MemoryUtils.wrap(null, text, charset));
    }

    public static final Frame createFrame(int type, Buffer data) {
        if ((type & 0x80) == 0) {
            return new StreamFrame(type, data);
        } else {
            return new FixedLengthFrame(type, data);
        }
    }

    protected int type;
    
    protected String text;
    protected Buffer buffer;

    private Charset lastCharset;
    
    protected Frame(int type, Buffer buffer) {
        this.type = type;
        this.buffer = buffer;
    }

    public int getType() {
        return type;
    }

    public String getAsText() {
        return getAsText(UTF8_CHARSET);
    }

    public String getAsText(Charset charset) {
        if (text == null || !charset.equals(lastCharset)) {
            text = buffer.toStringContent(charset);
            lastCharset = charset;
        }
        return text;
    }

    public Buffer getAsBinary() {
        return buffer;
    }

    @Override
    public String toString() {
        return getAsText();
    }

    public abstract boolean isClose();

    public abstract Buffer encode();

    public abstract DecodeResult decode(Buffer buffer);
}