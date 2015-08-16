/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http2;

import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpPacket;

/**
 * The {@link StreamOutputSink} implementation, which is used when upgrading
 * HTTP -> HTTP/2 connections.
 * 
 * @author Alexey Stashok
 */
public class UpgradeOutputSink implements StreamOutputSink {
    private final Http2Connection connection;
    private boolean isClosed;

    public UpgradeOutputSink(Http2Connection connection) {
        this.connection = connection;
    }
    
    @Override
    public boolean canWrite() {
        return connection.getConnection().canWrite();
    }

    @Override
    public void notifyWritePossible(WriteHandler writeHandler) {
        connection.getConnection().notifyCanWrite(writeHandler);
    }

    @Override
    public void onPeerWindowUpdate(int delta) throws Http2StreamException {
    }

    @Override
    public void writeDownStream(HttpPacket httpPacket) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void writeDownStream(HttpPacket httpPacket, FilterChainContext ctx) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void writeDownStream(HttpPacket httpPacket, FilterChainContext ctx, CompletionHandler<WriteResult> completionHandler) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public <E> void writeDownStream(HttpPacket httpPacket, FilterChainContext ctx, CompletionHandler<WriteResult> completionHandler, MessageCloner<Buffer> messageCloner) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void writeDownStream(Source source, FilterChainContext ctx) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void flush(CompletionHandler<Http2Stream> completionHandler) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int getUnflushedWritesCount() {
        return 0;
    }

    @Override
    public void close() {
        terminate(null);
    }

    @Override
    public void terminate(Http2Stream.Termination termination) {
        synchronized (this) {
            if (isClosed) {
                return;
            }
            
            isClosed = true;
        }
        
        termination.doTask();
    }

    @Override
    public synchronized boolean isClosed() {
        return isClosed;
    }
}
