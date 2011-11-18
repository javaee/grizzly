/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.util.http.MimeHeaders;

import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

public class AjpForwardRequestPacket extends AjpPacket {
    private final String method;
    private final String resource;
    private final MimeHeaders headers = new MimeHeaders();
    private final Map<String, String> attributes = new LinkedHashMap<String, String>();
    private final int port;

    public AjpForwardRequestPacket(String method, String resource, int port, int remotePort) {
        this.method = method;
        this.resource = resource;
        this.port = port;
        attributes.put("AJP_REMOTE_PORT", remotePort + "");
    }

    public void addHeader(String header, String value) {
        headers.addValue(header).setString(value);
    }

    @Override
    protected ByteBuffer buildContent() {
        ByteBuffer header = ByteBuffer.allocate(2);
        header.put(AjpConstants.JK_AJP13_FORWARD_REQUEST);
        header.put(AjpConstants.getMethodCode(method));
        header = putString(header, "HTTP/1.1");
        header = putString(header, resource);
        header = putString(header, "127.0.0.1");
        header = putString(header, null);
        header = putString(header, "localhost");
        header = putShort(header, (short) port);
        header = ensureCapacity(header, 1).put((byte) 0);
        header = putHeaders(header);
        header = putAttributes(header);

        header.flip();
        return header;
    }

    private ByteBuffer putAttributes(ByteBuffer header) {
        ByteBuffer buffer = header;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            buffer = ensureCapacity(buffer, 1)
                    .put(AjpConstants.SC_A_REQ_ATTRIBUTE);
            buffer = putString(buffer, entry.getKey());
            buffer = putString(buffer, entry.getValue());

        }
        buffer = ensureCapacity(buffer, 1)
                .put(AjpConstants.SC_A_ARE_DONE);

        return buffer;
    }

    private ByteBuffer putHeaders(ByteBuffer header) {
        ByteBuffer buffer = header;
        if(headers.getValue("host") == null) {
            headers.addValue("host").setString("localhost:" + port);
        }
        buffer = putShort(buffer, (short) headers.size());
        final Enumeration<String> enumeration = headers.names();
        while (enumeration.hasMoreElements()) {
            final String name = enumeration.nextElement();
            final byte headerCode = AjpConstants.getHeaderCode(name);
            if (headerCode != -1) {
                buffer = putShort(buffer, (short) (0xA000 | headerCode));
            } else {
                buffer = putString(buffer, name);
            }
            buffer = putString(buffer, headers.getHeader(name));
        }

        return buffer;
    }
}