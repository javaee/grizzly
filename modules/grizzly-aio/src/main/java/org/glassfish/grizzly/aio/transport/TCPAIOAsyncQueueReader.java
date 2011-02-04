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

package org.glassfish.grizzly.aio.transport;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.ReadResult;
import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.aio.AIOConnection;
import org.glassfish.grizzly.aio.AIOTransport;
import org.glassfish.grizzly.aio.AbstractAIOAsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.impl.SafeFutureImpl;

/**
 * The TCP transport {@link AsyncQueueReader} implementation, based on
 * the Java AIO
 *
 * @author Alexey Stashok
 */
public final class TCPAIOAsyncQueueReader extends AbstractAIOAsyncQueueReader {
    public TCPAIOAsyncQueueReader(final AIOTransport transport) {
        super(transport);
    }

    protected void read0(
            final AIOConnection connection,
            final AsyncReadQueueRecord queueRecord)
            throws IOException {
        ((TCPAIOConnection) connection).readAsync(
                (Buffer) queueRecord.getMessage(),
                queueRecord, queueRecord.getCurrentResult());
    }

    @Override
    public void processAsync(final Connection connection) throws IOException {
        final TCPAIOConnection.IOResult lastReadResult =
                ((TCPAIOConnection) connection).getLastReadResult();
        
        lastReadResult.reset();
        
        super.processAsync(connection);
    }
    
    protected void addRecord(final Connection connection,
            final Buffer buffer,
            final CompletionHandler completionHandler,
            final Interceptor<ReadResult> interceptor) {
        final AsyncReadQueueRecord record = AsyncReadQueueRecord.create(
                connection, buffer, SafeFutureImpl.create(),
                ReadResult.create(connection),
                completionHandler, interceptor);
        ((TCPAIOConnection) connection).getAsyncReadQueue().getQueue().add(record);
    } 
}
