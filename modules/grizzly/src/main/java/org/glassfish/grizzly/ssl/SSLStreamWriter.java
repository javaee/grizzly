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
package org.glassfish.grizzly.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.streams.StreamWriter;
import org.glassfish.grizzly.streams.StreamWriterDecorator;

/**
 * SSL aware {@link StreamWriter} implementation, which work like a wrapper over
 * existing {@link StreamWriter}.
 *
 * @see SSLStreamReader
 *
 * @author Alexey Stashok
 */
public class SSLStreamWriter extends StreamWriterDecorator {

    public SSLStreamWriter() {
        this(null);
    }

    public SSLStreamWriter(StreamWriter underlyingWriter) {
        super(underlyingWriter);
        setUnderlyingWriter(underlyingWriter);
    }

    @Override
    public void setUnderlyingWriter(StreamWriter underlyingWriter) {
        super.setUnderlyingWriter(underlyingWriter);
        
        try {
            checkBuffers();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public SSLEngine getSSLEngine() {
        SSLResourcesAccessor resourceAccessor =
                SSLResourcesAccessor.getInstance();
        return resourceAccessor.getSSLEngine(getConnection());
    }

    Future<Integer> handshakeWrap(CompletionHandler completionHandler)
            throws IOException {
        overflow();
        return flush0(
                getConnection().getTransport().getMemoryManager().allocate(0),
                completionHandler);
    }

    private void checkBuffers() throws IOException {
        SSLEngine sslEngine = getSSLEngine();

        if (sslEngine != null) {
            if (underlyingWriter != null) {
                int underlyingBufferSize = sslEngine.getSession().getPacketBufferSize();
                if (underlyingWriter.getBufferSize() < underlyingBufferSize) {
                    underlyingWriter.setBufferSize(underlyingBufferSize);
                }

                Buffer underlyingBuffer = underlyingWriter.getBuffer();
                if (underlyingBuffer == null ||
                        (underlyingBuffer.remaining() < underlyingBufferSize)) {
                    underlyingWriter.flush();
                }
            }

            int appBufferSize = sslEngine.getSession().getApplicationBufferSize();
            if (bufferSize < appBufferSize) {
                bufferSize = appBufferSize;
            }
        }
    }

    @Override
    protected Future<Integer> flush0(Buffer buffer,
            CompletionHandler<Integer> completionHandler) throws IOException {

        Future lastWriterFuture = null;
        SSLEngine sslEngine = getSSLEngine();

        checkBuffers();

        if (buffer != null) {
            buffer.flip();
            if (buffer.remaining() > 0 && SSLUtils.isHandshaking(sslEngine)) {
                throw new IllegalStateException("Handshake was not completed");
            }

            ByteBuffer byteBuffer = (ByteBuffer) buffer.underlying();
            do {
                Buffer underlyingBuffer = underlyingWriter.getBuffer();
                ByteBuffer underlyingByteBuffer = (ByteBuffer) underlyingBuffer.underlying();
                sslEngine.wrap(byteBuffer, underlyingByteBuffer);
                lastWriterFuture = underlyingWriter.flush();
            } while (buffer.hasRemaining());

            buffer.clear();
        } else if (this.buffer == null) {
            this.buffer = newBuffer(bufferSize);
        }

        return lastWriterFuture;
    }
}
