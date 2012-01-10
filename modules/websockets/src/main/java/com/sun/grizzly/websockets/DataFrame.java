/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets;

/**
 * In memory representation of a websocket frame.
 *
 * @see <a href="http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-05#section-4.3">Frame Definition</a>
 */
public class DataFrame {
    private String payload;
    private byte[] bytes;
    private final FrameType type;
    private boolean last = true;

    public DataFrame(FrameType type) {
        this.type = type;
    }

    public DataFrame(FrameType type, String data) {
        this(type, data, true);
    }

    public DataFrame(FrameType type, String data, boolean fin) {
        this.type = type;
        setPayload(data);
        last = fin;
    }

    public DataFrame(FrameType type, byte[] data) {
        this(type, data, true);
    }

    public DataFrame(FrameType type, byte[] data, boolean fin) {
        this.type = type;
        type.setPayload(this, data);
        last = fin;
    }

    public FrameType getType() {
        return type;
    }

    public String getTextPayload() {
        return payload;
    }

    public final void setPayload(String payload) {
        this.payload = payload;
    }

    public void setPayload(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        if (payload != null) {
            bytes = Utf8Utils.encode(new StrictUtf8(), payload);
        }
        return bytes;
    }

    public void respond(WebSocket socket) {
        getType().respond(socket, this);
    }

    @Override
    public String toString() {
        return new StringBuilder("DataFrame")
                .append("{")
                .append("last=").append(last)
                .append(", type=").append(type.getClass().getSimpleName())
                .append(", payload='").append(getTextPayload()).append('\'')
//                .append(", bytes=").append(Arrays.toString(bytes))
                .append('}')
                .toString();
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}
