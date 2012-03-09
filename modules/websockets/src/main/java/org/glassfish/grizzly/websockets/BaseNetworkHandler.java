/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.websockets;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

import org.glassfish.grizzly.Buffer;

public abstract class BaseNetworkHandler implements NetworkHandler {
    protected Buffer buffer;

    public String readLine(String encoding) throws IOException {
        final int position = buffer.position();
        try {
            ByteBuffer chunk = ByteBuffer.allocate(buffer.remaining());
            buffer.get(chunk);

            int eolIdx = 0;
            int index = 0;
            while(eolIdx == 0 && index < chunk.limit()) {
                final byte b = chunk.get(index);
                if(b == '\n' || b == '\r') {
                    eolIdx = index;
                    if(index + 1 < chunk.limit()) {
                        final byte next = chunk.get(index + 1);
                        if(next == '\n' || next == '\r') {
                            eolIdx++;
                            index++;
                        }
                    }
                } else {
                    index++;
                }
            }
            if (eolIdx != -1) {
                String result = buffer.toStringContent(Charset.forName(encoding), position, eolIdx);
                buffer.position(eolIdx+1);
                return result;
            }
        } catch (BufferUnderflowException e) {
            buffer.position(position);
            throw e;
        }
        return null;
    }

    public List<String> getBytes() {
        return getByteList(buffer.position(), buffer.limit());
    }

    private List<String> getByteList(final int start, final int end) {
        return WebSocketEngine.toString(buffer.toByteBuffer(start, end).array(), start, end);
    }

    @Override
    public String toString() {
        return String
            .format("Active: %s" /*+ ", Full: %s"*/, getBytes().toString()/*, getByteList(0, buffer.getEnd())*/);
    }
}
