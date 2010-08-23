/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

