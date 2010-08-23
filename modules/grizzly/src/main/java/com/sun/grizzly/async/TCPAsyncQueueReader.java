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
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

/**
 *
 * @author Alexey Stashok
 */
public class TCPAsyncQueueReader extends AbstractAsyncQueueReader {
    public TCPAsyncQueueReader(SelectorHandler selectorHandler) {
        super(selectorHandler);
    }

    protected OperationResult doRead(ReadableByteChannel channel, ByteBuffer byteBuffer,
            AsyncQueueDataProcessor readPostProcessor, OperationResult dstResult) throws IOException {
        int totalReadBytes = 0;
        
        if (readPostProcessor != null) {
            ByteBuffer inputByteBuffer = null;
            int oldPosition = byteBuffer.position();
            
            do {
                inputByteBuffer = readPostProcessor.getInternalByteBuffer();
                int readBytes = doRead(channel, inputByteBuffer);
                
                if (readBytes > 0) {
                    readPostProcessor.process(byteBuffer);
                } else if (readBytes == -1) { 
                    if (byteBuffer.position() == oldPosition) {
                        throw new EOFException();
                    } else {
                        break;
                    }
                }
                totalReadBytes += readBytes;
            } while(byteBuffer.hasRemaining() && 
                    !inputByteBuffer.hasRemaining());
        } else {
            totalReadBytes = doRead(channel, byteBuffer);
        }

        dstResult.bytesProcessed = totalReadBytes;
        dstResult.address =
                ((SocketChannel) channel).socket().getRemoteSocketAddress();
        return dstResult;
    }
    
    private int doRead(ReadableByteChannel channel,ByteBuffer byteBuffer)
            throws IOException {
        int readBytes = 0;
        int lastReadBytes = -1;
        try{
            do {
                lastReadBytes = channel.read(byteBuffer);
                if (lastReadBytes > 0) {
                    readBytes += lastReadBytes;
                } else if (lastReadBytes == -1 && readBytes == 0) {
                    readBytes = -1;
                }

            } while(lastReadBytes > 0 && byteBuffer.hasRemaining());
        } catch (IOException ex){
            lastReadBytes = -1;
            throw ex;
        } finally{
            if (lastReadBytes == -1 || readBytes == -1){
                SelectionKeyHandler skh = selectorHandler.getSelectionKeyHandler();
                if (skh instanceof BaseSelectionKeyHandler){                  
                    ((BaseSelectionKeyHandler)skh).notifyRemotlyClose(
                            selectorHandler.keyFor((SelectableChannel)channel));
                }                 
            }
        }
        
        return readBytes;
    }
}
