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

package com.sun.grizzly.http;

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.ByteChunk;
import java.net.HttpURLConnection;
import java.net.URL;
import junit.framework.TestCase;

/**
 *
 * @author Alexey Stashok
 */
public class AsyncHTTPResponseTest extends TestCase {
    public static final int PORT = 18890;

    private static final byte[] abcd = new byte[] {'a', 'b', 'c', 'd'};

    public void testSimpleAsyncResponse() throws Exception {
        int responseLength = 1024 * 1024;

        SelectorThread selectorThread = new SelectorThread();
        try {
            selectorThread.setPort(PORT);
            selectorThread.setAsyncHttpWriteEnabled(true);
            selectorThread.setAdapter(
                    new BigResponseAdapter(responseLength));
            selectorThread.listen();

            HttpURLConnection connection = (HttpURLConnection)
                    new URL("http://localhost:" + PORT).openConnection();

            int code = connection.getResponseCode();
            assertEquals(code, 200);

            int length = connection.getContentLength();
            assertEquals(length, responseLength);

            byte[] content = new byte[length];

            int readBytes;
            int offset = 0;
            do {
                readBytes = connection.getInputStream().read(content, offset,
                        length - offset);
                offset += readBytes;
            } while(readBytes != -1 && offset < length);

            assertEquals(offset, length);

            checkResult(content);
        } finally {
            selectorThread.stopEndpoint();
        }
    }

    public static class BigResponseAdapter implements Adapter {
        private int length;

        public BigResponseAdapter(int length) {
            this.length = length;
        }

        public void service(Request req, Response res) throws Exception {
            ByteChunk chunk = new ByteChunk(length);
            byte[] content = new byte[length];
            for (int i=0; i<length; i++) {
                content[i] = abcd[i % abcd.length];
            }
            
            res.setStatus(200);
            res.setContentLength(length);
            res.setContentType("text/html");
            chunk.append(content, 0, length);
            res.getOutputBuffer().doWrite(chunk, res);
        }

        public void afterService(Request req, Response res) throws Exception {
        }
    }

    private static boolean checkResult(byte[] content) {
        for (int i=0; i<content.length; i++) {
            if (content[i] != abcd[i % abcd.length]) {
                return false;
            }
        }

        return true;
    }
}
