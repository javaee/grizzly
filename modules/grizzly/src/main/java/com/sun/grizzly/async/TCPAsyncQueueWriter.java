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

package com.sun.grizzly.async;

import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.SelectionKeyHandler;
import com.sun.grizzly.SelectorHandler;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Future;

/**
 * TCP implementation of {@link AsyncQueueWriter}
 * 
 * @author Alexey Stashok
 */
public class TCPAsyncQueueWriter extends AbstractAsyncQueueWriter {

    public TCPAsyncQueueWriter(SelectorHandler selectorHandler) {
        super(selectorHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<AsyncQueueWriteUnit> write(SelectionKey key,
            SocketAddress dstAddress,
            ByteBuffer buffer, AsyncWriteCallbackHandler callbackHandler,
            AsyncQueueDataProcessor writePreProcessor, ByteBufferCloner cloner)
            throws IOException {
        
        if (dstAddress != null) {
            throw new UnsupportedOperationException("Destination address should be null for TCP!");
        }
        
        return super.write(key, null, buffer, callbackHandler,
                writePreProcessor, cloner);
    }
    
    protected OperationResult doWrite(WritableByteChannel channel,
            SocketAddress dstAddress, ByteBuffer byteBuffer,
            OperationResult dstResult) throws IOException {
        int written = 0;
        int lastWriteBytes = 0;
            try{
            do {
                lastWriteBytes = channel.write(byteBuffer);
                if (lastWriteBytes > 0) {
                    written += lastWriteBytes;
                }

            } while(lastWriteBytes > 0 && byteBuffer.hasRemaining());
        } catch (IOException ex){
            lastWriteBytes = -1;
            throw ex;
        } finally {
             if (lastWriteBytes == -1){
                SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                if (skh instanceof BaseSelectionKeyHandler){                  
                    ((BaseSelectionKeyHandler)skh).notifyRemotlyClose(
                            selectorHandler.keyFor((SelectableChannel)channel));
                }                 
            }           
        }
        dstResult.bytesProcessed = written;
        return dstResult;
    }
}
