/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.websockets.draft06;

import java.io.UnsupportedEncodingException;

import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.FramingException;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.frametypes.ClosingFrameType;

public class ClosingFrame extends DataFrame {
    public static final byte[] EMPTY_BYTES = new byte[0];
    private int code;

    public ClosingFrame(int code, String reason) {
        super(new ClosingFrameType(), reason, true);
        this.code = code;
    }

    public ClosingFrame(byte[] data) {
        super(new ClosingFrameType(), data);
    }

    public int getCode() {
        return code;
    }

    @Override
    public void setPayload(byte[] bytes) {
        if (bytes.length > 0) {
            code = (int) WebSocketEngine.toLong(bytes, 0, 2);
            if (bytes.length > 2) {
                try {
                    setPayload(new String(bytes, 2, bytes.length - 2, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    throw new FramingException(e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public byte[] getBytes() {
        if (code == -1) {
            return EMPTY_BYTES;
        }
        final byte[] bytes = WebSocketEngine.toArray(code);
        final byte[] reasonBytes = super.getBytes() == null ? EMPTY_BYTES : super.getBytes();
        final byte[] frameBytes = new byte[2 + reasonBytes.length];
        System.arraycopy(bytes, bytes.length - 2, frameBytes, 0, 2);
        System.arraycopy(reasonBytes, 0, frameBytes, 2, reasonBytes.length);
        return frameBytes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ClosingFrame");
        sb.append("{code=").append(code);
        sb.append(", payload=").append(getTextPayload() == null ? null : "'" + getTextPayload() + "'");
        sb.append('}');
        return sb.toString();
    }
}
