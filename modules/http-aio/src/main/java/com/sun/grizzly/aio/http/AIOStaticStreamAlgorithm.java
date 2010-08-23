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

package com.sun.grizzly.aio.http;

import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.util.StreamAlgorithm;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * This algorithm doesn't parse the bytes, delegating the work to the 
 * InternalInputBuffer.
 *
 * @author Jeanfrancois Arcand
 */
public class AIOStaticStreamAlgorithm implements StreamAlgorithm<AsynchronousSocketChannel>{

    
    private AsynchronousSocketChannel channel;

    private int port;

    private AIOStaticHandler handler;

    
    public AIOStaticStreamAlgorithm() {
        handler = new AIOStaticHandler();
    }
    
    /**
     * The {@link AsynchronousSocketChannel} used by this class.
     */
    public void setChannel(AsynchronousSocketChannel channel){
        this.channel = channel;
        if (channel != null){
            handler.attachChannel(channel);
        }
    }

    
    /**
     * Do nothing, as the {@link AIOInputReader} will take care of reading the
     * missing bytes.
     */
    @Override
    public ByteBuffer preParse(ByteBuffer byteBuffer){ 
        return byteBuffer;
    }
    
    
    /**
     * Do not parse the bytes and automatically flip the {@link ByteBuffer}
     */
    public boolean parse(ByteBuffer byteBuffer){
        byteBuffer.flip();
        return true;
    }
    
    
    /**
     * Return the {@link Interceptor} used by this algorithm.
     */
    public Interceptor getHandler(){
        return handler;
    }
    
    
    /***
     * Recycle this object.
     */
    @Override
    public void recycle(){
        channel = null;
        if ( handler != null){
            handler.attachChannel(null);
        }
    }

    public int contentLength() {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    public int headerLength() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public ByteBuffer allocate(boolean useDirect, boolean useView, int size) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public ByteBuffer postParse(ByteBuffer byteBuffer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public ByteBuffer rollbackParseState(ByteBuffer byteBuffer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}
