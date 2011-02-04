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

package org.glassfish.grizzly.aio;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.glassfish.grizzly.AbstractWriter;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.impl.ReadyFutureImpl;

/**
 *
 * @author oleksiys
 */
public abstract class AIOBlockingWriter extends AbstractWriter<SocketAddress> {

    private static final Logger LOGGER = Grizzly.logger(AIOBlockingWriter.class);

    protected final AIOTransport transport;

    public AIOBlockingWriter(final AIOTransport transport) {
        this.transport = transport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GrizzlyFuture<WriteResult<Buffer, SocketAddress>> write(
            Connection connection, SocketAddress dstAddress, Buffer message,
            CompletionHandler<WriteResult<Buffer, SocketAddress>> completionHandler,
            Interceptor<WriteResult<Buffer, SocketAddress>> interceptor)
            throws IOException {
                
        if (message == null) {
            throw new IllegalStateException("Message cannot be null.");
        }

        if (connection == null || !(connection instanceof AIOConnection)) {
            throw new IllegalStateException(
                    "Connection should be AIOConnection and cannot be null.");
        }

        final WriteResult writeResult =
                WriteResult.create(connection, message, dstAddress, 0);

        write0(connection, dstAddress, message, writeResult);

        final GrizzlyFuture<WriteResult<Buffer, SocketAddress>> writeFuture =
                ReadyFutureImpl.<WriteResult<Buffer, SocketAddress>>create(writeResult);
        
        if (completionHandler != null) {
            completionHandler.completed(writeResult);
        }

        message.tryDispose();

        return writeFuture;
    }
    
    /**
     * Flush the buffer by looping until the {@link Buffer} is empty
     *
     * @param connection the {@link Connection}.
     * @param dstAddress the destination address.
     * @param buffer the {@link Buffer} to write.
     * @param currentResult the result of the write operation
     *
     * @return The number of bytes written.
     * 
     * @throws java.io.IOException
     */
    protected abstract int write0(final Connection connection,
            final SocketAddress dstAddress, final Buffer buffer,
            final WriteResult currentResult) throws IOException;

    public AIOTransport getTransport() {
        return transport;
    }
}
