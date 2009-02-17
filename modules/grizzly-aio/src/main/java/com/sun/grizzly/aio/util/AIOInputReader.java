/*
 * The contents of this file are subject to the terms 
 * of the Common Development and Distribution License 
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at 
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing 
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL 
 * Header Notice in each file and include the License file 
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.aio.util;

import com.sun.grizzly.util.InputReader;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channel;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This class implement IO stream operations on top of a <code>ByteBuffer</code>. 
 * Under the hood, this class use a temporary Selector pool for reading
 * bytes when the client ask for more and the current Selector is not yet ready.
 * 
 * @author Jeanfrancois Arcand
 */
public class AIOInputReader extends InputReader {

    /**
     * The <code>Channel</code> type is used to avoid invoking the instanceof
     * operation when registering the Socket|Datagram Channel to the Selector.
     */ 
    public enum ChannelType { SocketChannel, DatagramChannel,
    AsynchronousSocketChannel, AsynchronousDatagramChannel}
    
    
    /**
     * The Channel used to read bytes
     */
    private Channel channel;
    
    
    // ------------------------------------------------- Constructor -------//
    
    
    public AIOInputReader () {
    }

    
    public AIOInputReader (final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    // ---------------------------------------------------------------------//

        
    @Override
    protected int doClearRead() throws IOException{
        int count = -1;
        Future<Integer> result = 
                ((AsynchronousSocketChannel)channel).read(byteBuffer);

        try{
            count = result.get(readTimeout, TimeUnit.MILLISECONDS);
        } catch (Throwable ex){
            throw new EOFException(ex.getMessage());
        }

        byteBuffer.flip();
        return count;           
    } 

    public Channel getChannel() {
        if (key != null){
            return key.channel();
        } else {
            return channel;
        }
    }

    
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

}

