/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.benchmark;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.util.Ascii;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.memory.BufferUtils;
import com.sun.grizzly.memory.MemoryManager;
import java.io.IOException;

/**
 * Simple Web app filter, which responses with the content of the required size.
 * 
 * @author Alexey Stashok
 */
public class HttpGetFilter extends BaseFilter {

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final HttpContent httpContent = (HttpContent) ctx.getMessage();

        if (!httpContent.isLast()) {
            // no action until we have all content
            return ctx.getStopAction();
        }

        final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
        final int size = getSize(request.getQueryStringBC());

        System.out.println("size: " + size);
        
        final HttpResponsePacket response = HttpResponsePacket.builder()
                .protocol("HTTP/1.1")
                .status(200)
                .reasonPhrase("OK")
                .contentLength(size)
                .header("Content-Type", "text/plain")
                .build();

        MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();
        final Buffer b = mm.allocate(size);
        BufferUtils.fill(b, (byte) 'A');

        final HttpContent content = HttpContent.builder(response).content(b).build();

        ctx.write(content);
        
        return ctx.getStopAction();
    }

    private static int getSize(BufferChunk queryString) {
        if (!queryString.isNull()) {
            final int idx = queryString.indexOf("size=", 0);
            if (idx != -1) {
                final int sizeIdx = idx + "size=".length();
                int idx2 = queryString.indexOf('&', sizeIdx);
                if (idx2 == -1) {
                    idx2 = queryString.length();
                }
                
                return (int) Ascii.parseLong(queryString, sizeIdx, idx2 - sizeIdx);
            }
        }

        return 1024;
    }
}
