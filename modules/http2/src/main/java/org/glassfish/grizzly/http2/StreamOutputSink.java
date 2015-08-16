/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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
 * Interface represents an output sink associated with specific {@link Http2Stream}. 
 * 
 * @author Alexey Stashok
 */
interface StreamOutputSink {
    public boolean canWrite();
    public void notifyWritePossible(WriteHandler writeHandler);

    /**
     * The method is called by HTTP2 Filter once WINDOW_UPDATE message comes
     * for this {@link Http2Stream}.
     * 
     * @param delta the delta.
     * @throws org.glassfish.grizzly.http2.Http2StreamException
     */
    public void onPeerWindowUpdate(int delta) throws Http2StreamException;

    public void writeDownStream(HttpPacket httpPacket) throws IOException;
    public void writeDownStream(HttpPacket httpPacket,FilterChainContext ctx)
            throws IOException;
    
    /**
     * Send an {@link HttpPacket} to the {@link Http2Stream}.
     * 
     * The writeDownStream(...) methods have to be synchronized with shutdown().
     * 
     * @param httpPacket {@link HttpPacket} to send
     * @param completionHandler the {@link CompletionHandler},
     *          which will be notified about write progress.
     * @throws IOException 
     */
    public void writeDownStream(HttpPacket httpPacket,
                                FilterChainContext ctx,
                                CompletionHandler<WriteResult> completionHandler)
            throws IOException;
    
    public <E> void writeDownStream(HttpPacket httpPacket,
                                    FilterChainContext ctx,
                                    CompletionHandler<WriteResult> completionHandler,
                                    MessageCloner<Buffer> messageCloner)throws IOException;

    public void writeDownStream(Source source, FilterChainContext ctx)
            throws IOException;
    
    /**
     * Flush {@link Http2Stream} output and notify {@link CompletionHandler} once
     * all output data has been flushed.
     * 
     * @param completionHandler {@link CompletionHandler} to be notified
     */
    public void flush(CompletionHandler<Http2Stream> completionHandler);
    
    /**
     * @return the number of writes (not bytes), that haven't reached network layer
     */
    public int getUnflushedWritesCount();
    
    /**
     * Closes the output sink by adding last DataFrame with the FIN flag to a queue.
     * If the output sink is already closed - method does nothing.
     */
    public void close();

    /**
     * Unlike {@link #close()} this method forces the output sink termination
     * by setting termination flag and canceling all the pending writes.
     */
    public void terminate(Http2Stream.Termination terminationFlag);
    
    public boolean isClosed();
}
