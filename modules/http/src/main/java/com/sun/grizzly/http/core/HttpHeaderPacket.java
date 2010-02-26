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
package com.sun.grizzly.http.core;

import com.sun.grizzly.util.MimeHeaders;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.memory.BufferUtils;

/**
 *
 * @author oleksiys
 */
public abstract class HttpHeaderPacket implements HttpPacket {

    protected MimeHeaders headers = new MimeHeaders();
    protected BufferChunk protocolBC = BufferChunk.newInstance();
    protected Buffer content = BufferUtils.EMPTY_BUFFER;

    public abstract boolean isRequest();

    @Override
    public final boolean isHeader() {
        return true;
    }

    // -------------------- Headers --------------------
    public MimeHeaders getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.getHeader(name);
    }

    public void setHeader(String name, String value) {
        headers.setValue(name).setString(value);
    }

    public void addHeader(String name, String value) {
        headers.addValue(name).setString(value);
    }

    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }

    // Common HTTP packet attributes
    public BufferChunk getProtocolBC() {
        return protocolBC;
    }

    public String getProtocol() {
        return getProtocolBC().toString();
    }

    public void setProtocol(String protocol) {
        this.protocolBC.setString(protocol);
    }

    public Buffer getContent() {
        return content;
    }

    public void setContent(Buffer content) {
        this.content = content;
    }

    public void recycle() {
        protocolBC.recycle();
        headers.clear();
        content = BufferUtils.EMPTY_BUFFER;
    }

    public static abstract class Builder<T extends Builder> {

        protected HttpHeaderPacket packet;

        public final T protocol(String protocol) {
            packet.setProtocol(protocol);
            return (T) this;
        }

        public final T content(Buffer content) {
            packet.setContent(content);
            return (T) this;
        }

        public final T header(String name, String value) {
            packet.addHeader(name, value);
            return (T) this;
        }
    }
}
