/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filter;

import com.sun.grizzly.Context;
import com.sun.grizzly.Context.AttributeScope;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.async.ByteBufferCloner;
import com.sun.grizzly.util.AttributeHolder;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

/**
 * SSL echo filter, which uses {@link AsyncQueueWriter}
 *
 * @author Alexey Stashok
 */
public class SSLEchoAsyncWriteQueueFilter implements ProtocolFilter {

    private static final String SSL_WRITE_PREPROCESSOR = "SSL_WRITE_PREPROCESSOR";

    private ByteBufferCloner byteBufferCloner = new ByteBufferClonerImpl();

    public boolean execute(Context ctx) throws IOException {
        final WorkerThread workerThread = ((WorkerThread) Thread.currentThread());
        ByteBuffer buffer = workerThread.getByteBuffer();
        buffer.flip();
        if (buffer.hasRemaining()) {
            // Store incoming data in byte[]
            byte[] data = new byte[buffer.remaining()];
            int position = buffer.position();
            buffer.get(data);
            buffer.position(position);
            try {
                SSLEngine sslEngine = workerThread.getSSLEngine();
                AttributeHolder attributes = ctx.getAttributeHolderByScope(AttributeScope.CONNECTION);

                SSLWritePreProcessor preProcessor =
                        (SSLWritePreProcessor) attributes.getAttribute(SSL_WRITE_PREPROCESSOR);
                if (preProcessor == null) {
                    preProcessor = new SSLWritePreProcessor(sslEngine, workerThread.getOutputBB());
                    workerThread.setOutputBB(null);
                    attributes.setAttribute(SSL_WRITE_PREPROCESSOR, preProcessor);
                }

                ctx.getAsyncQueueWritable().writeToAsyncQueue(buffer, null,
                        preProcessor, byteBufferCloner);
            } catch (IOException ex) {
                throw ex;
            }
        }

        buffer.clear();
        return false;
    }

    public boolean postExecute(Context ctx) throws IOException {
        return true;
    }

    private static class SSLWritePreProcessor implements AsyncQueueDataProcessor {

        private SSLEngine sslEngine;
        private ByteBuffer securedOutputBuffer;

        public SSLWritePreProcessor(SSLEngine sslEngine, ByteBuffer securedOutputBuffer) {
            this.sslEngine = sslEngine;
            this.securedOutputBuffer = securedOutputBuffer;
        }

        public ByteBuffer getInternalByteBuffer() {
            return securedOutputBuffer;
        }

        public void process(ByteBuffer byteBuffer) throws IOException {
            if (!byteBuffer.hasRemaining() || securedOutputBuffer.hasRemaining()) {
                return;
            }

            securedOutputBuffer.clear();

            try {
                SSLEngineResult result = sslEngine.wrap(byteBuffer, securedOutputBuffer);
                securedOutputBuffer.flip();
            } catch (Exception e) {
                securedOutputBuffer.position(securedOutputBuffer.limit());
                throw new IOException(e.getMessage());
            }
        }
    }

    private class ByteBufferClonerImpl implements ByteBufferCloner {
        public ByteBuffer clone(ByteBuffer originalByteBuffer) {
            ByteBuffer newBB = ByteBufferFactory.allocateView(
                    originalByteBuffer.remaining(),
                    originalByteBuffer.isDirect());
            newBB.put(originalByteBuffer);
            newBB.flip();
            return newBB;
        }
    }
}
