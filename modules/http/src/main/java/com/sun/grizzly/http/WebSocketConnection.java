/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.grizzly.http;

import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.util.SelectedKeyAttachmentLogic;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 *
 * @author gustav trede
 * @since 2009-04
 */
public class WebSocketConnection extends SelectedKeyAttachmentLogic implements Runnable{
    
    private static final byte TEXT_TYPE       = (byte)0x00;
    private static final byte BINARY_TYPE     = (byte)0x80;
    private static final byte TEXT_TERMINATOR = (byte)0xff;

    private final WebScocketContext   context;
    private final SelectionKey        selectionKey;
    
    private ByteBuffer writeBuffer;
    private ByteBuffer readBuffer;
    
    public WebSocketConnection(WebScocketContext context,SelectionKey selectionKey) {    
        this.context             = context;
        this.selectionKey        = selectionKey;
    }

    public WebScocketContext getContext() {
        return context;
    }
    
    @Override
    public boolean timedOut(SelectionKey key) {
        //if (context.removeClient(this)){
            context.getEventlistener().connectionClosedByIdleTimeout(this);
       // }
        return true;
    }

    @Override
    public void handleSelectedKey(SelectionKey selectionKey) {
        //todo detect if read only operation, if so we can always pendingIO them
        // due to reads dont do network io, but read from socket buffer only.
        if (context.getDoManyClientIOPerThread()){
            getSelectorHandler().addPendingIO(this);
        }else{
            getSelectorHandler().getThreadPool().execute(this);
        }
    }
    
    public void run() {
        try{
            //todo how do we detect clientside closing ?
            SocketChannel channel = ((SocketChannel)selectionKey.channel());
            if (selectionKey.isReadable()){
                if (readBuffer != null){
                    readBuffer  = getByteBuffer();
                    //todo read 0
                }
                // not impl yet
                channel.read(readBuffer);
            }
            if (writeBuffer != null && selectionKey.isWritable()){
                channel.write(writeBuffer);
                if (!writeBuffer.hasRemaining()){
                    selectionKey.interestOps(SelectionKey.OP_READ);
                    context.getEventlistener().dataWriteCompleted(this,writeBuffer);
                    writeBuffer = null;
                }
            }         
        }catch(IOException e){
            close();
            context.getEventlistener().connectionClosedIOException(this);
        }
    } 

    /**
     * Method to allow for low overhead write, where the data is known to be UTF8.<br>
     * Buffer must follow the WebSocket frame spec:
     * containing sequence(s) of UTF8 that start with 0x00 byte and end with 0xff byte.<br>
     * {@link WebSocketEventListener}.dataWriteCompleted method will be called if the write completes.
     * @param buffer
     */
    public void write(ByteBuffer buffer){
        SocketChannel channel = ((SocketChannel)selectionKey.channel());
        try{
            channel.write(buffer);
            if (buffer.hasRemaining() ){
                 writeBuffer = buffer;
                 selectionKey.interestOps(SelectionKey.OP_READ|SelectionKey.OP_WRITE);
            }else{
                context.getEventlistener().dataWriteCompleted(this, buffer);
            }
            //todo flush !!
        }catch(IOException e){
            close();
            context.getEventlistener().connectionClosedIOException(this);
        }
    }

    /**
     * Closes the Socket.
     * Does not fire {@link WebSocketEventListener}.connectionClosedXXX event, its up to the calling context
     * to fire the correct event type, if any.
     */
    public void close(){
        getSelectorHandler().getSelectionKeyHandler().cancel(selectionKey);
    }

    private SelectorHandler getSelectorHandler(){
        return (SelectorHandler) ((WorkerThreadImpl)Thread.currentThread()).getPendingIOhandler();
    }

    private ByteBuffer getByteBuffer(){
        WorkerThread wt = ((WorkerThread)Thread.currentThread());
        ByteBuffer byteBuffer = wt.getByteBuffer();
        if (byteBuffer == null){
            byteBuffer = ByteBuffer.allocate(8192);
            wt.setByteBuffer(byteBuffer);
        } else {
            byteBuffer.clear();
        }
        return byteBuffer;
    }
}
