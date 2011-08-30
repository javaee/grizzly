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

package com.sun.grizzly.websockets;

import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.buf.ByteChunk;

import java.io.IOException;
import java.util.List;

public abstract class BaseNetworkHandler implements NetworkHandler {
    protected final ByteChunk chunk = new ByteChunk();

    protected abstract int read();

    public byte[] readLine() throws IOException {
        if (chunk.getLength() <= 0) {
            read();
        }

        int idx = chunk.indexOf('\n', 0);
        if (idx != -1) {
            int eolBytes = 1;
            final int offset = chunk.getOffset();
            idx += offset;

            if (idx > offset && chunk.getBuffer()[idx - 1] == '\r') {
                idx--;
                eolBytes = 2;
            }

            final int size = idx - offset;

            final byte[] result = new byte[size];
            chunk.substract(result, 0, size);

            chunk.setOffset(chunk.getOffset() + eolBytes); // Skip \r\n or \n
            return result;
        }

        return null;
    }

    public List<String> getBytes() {
        return getByteList(chunk.getStart(), chunk.getEnd());
    }

    private List<String> getByteList(final int start, final int end) {
        return Utils.toHexStrings(chunk.getBytes(), start, end);
    }

    @Override
    public String toString() {
        return String.format("Active: %s" /*+ ", Full: %s"*/, getBytes().toString()/*, getByteList(0, chunk.getEnd())*/);
    }
}
