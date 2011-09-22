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
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
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

import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

public class PostsTest extends AjpTestBase {
    public void largePost() throws IOException, InstantiationException {
        startServer();
        final ByteBuffer headers = readBinaryFile("src/test/resources/large-post-header.bytes");
        final ByteBuffer body = readBinaryFile("src/test/resources/large-post-body.bytes");
        send(headers);
        send(body);
        List<AjpResponse> responses = AjpMessageUtils.parseResponse(readResponses());
        boolean wait = true;
        while(wait) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        System.out.println("responses = " + responses);
    }

    private void startServer() throws IOException, InstantiationException {
        configureHttpServer(new GrizzlyAdapter() {
                @Override
                public void service(GrizzlyRequest request, GrizzlyResponse response) throws Exception {
                    String line;
                    while((line = request.getReader().readLine()) != null) {
                        System.out.println("line = " + line);
                    }
                }
            });
    }

    private ByteBuffer readBinaryFile(final String name) throws IOException {
        File file = new File(name);
        FileChannel headers = new FileInputStream(file).getChannel();
        ByteBuffer buffer = ByteBuffer.allocate((int) file.length());
        try {
            headers.read(buffer);
        } finally {
            headers.close();
        }

        buffer.flip();
        return buffer;
    }

    public static void main(String[] args) throws IOException, InstantiationException, InterruptedException {
        new PostsTest().startServer();
        while(true) {
            Thread.sleep(5000);
        }
    }
}
