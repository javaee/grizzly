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

package com.sun.grizzly.http.algorithms;

import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.util.StreamAlgorithm;
import com.sun.grizzly.util.ByteBufferFactory;
import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;


/**
 * Base class for {@link StreamAlgorithm} implementation.
 * @author Jeanfrancois Arcand
 */
public abstract class StreamAlgorithmBase implements StreamAlgorithm<SocketChannel>{
 
    
    private int port = 8080;
    
    /**
     * Manipulate the bytes stream and determine if the process can continue.
     *    
     * @return true if the algorithm determines the process can continue.
     */    
    public abstract boolean parse(ByteBuffer byteBuffer);
    
    
    /**
     * The actual length of the stream we are able to read.
     */
    protected int contentLength = -1;
    
           
    /**
     * The {@link ByteBuffer} current limit value
     */    
    protected int curLimit = -1;
    
    
    /**
     * The {@link ByteBuffer} current position value
     */    
    protected int curPosition = -1;
    
    
    /**
     * The position, within the {@link ByteBuffer}, when the HTTP
     * headers are completely reads.
     */
    protected int headerLength = -1;
    
    
    /**
     * In case we were'nt able to parse the request line, we will continue
     * parsing from that position.
     */
    protected int lastStatePosition = -1;    
   
    
    /**
     * If the stream wasn't read fully, keep the state of the http parsing.
     */
    protected int state = 0;
    
   
    /**
     * If a new {@link ByteBuffer} is created because the stream is 
     * too small, cache the original byteBuffer and reuse it once the 
     * transaction has completed.
     */
    protected ByteBuffer primaryByteBuffer = null;
    
    
    /**
     * If <tt>true</tt>, use a {@link ByteBuffer} view instead of 
     * {@link ByteBuffer}
     */    
    protected boolean useByteBufferView = false;
   
    
    /**
     * Are we using direct {@link ByteBuffer}
     */
    protected boolean useDirectByteBuffer;
    
    
    /**
     * The {@link SocketChannel} associated with this algorithm.
     */
    protected SocketChannel socketChannel;
    
    
    /**
     * An {@link Interceptor} implementation used to implement a 
     * static resources cache.
     */
    protected Interceptor handler;

    
    public StreamAlgorithmBase() {
        handler = new DummyHandler();
    }
    
    // ------------------------------------------------------- Methods ------//
    
    
    /**
     * Return the stream content-length. If the content-length wasn't parsed,
     * return -1.
     */
    public int contentLength(){
        return contentLength;
    }
    
    
    /**
     * Return the stream header length. The header length is the length between
     * the start of the stream and the first occurance of character '\r\n' .
     */
    public int headerLength(){
        return headerLength;
    }
    
    
    /**
     * Before parsing the bytes, initialize and prepare the algorithm.
     * @param byteBuffer the {@link ByteBuffer} used by this algorithm
     * @return {@link ByteBuffer} used by this algorithm
     */
    public ByteBuffer preParse(ByteBuffer byteBuffer){
        if (byteBuffer.position() == byteBuffer.capacity()){
            // Add space at the end for \n\r
            int bufferSize = contentLength > 0 ?
                                    contentLength + headerLength + 5: 
                                    byteBuffer.capacity() * 2;
            byteBuffer = swapBuffer(byteBuffer,bufferSize);
        }  
        return byteBuffer;
    }
    
    /**
     * After parsing the bytes, post process the {@link ByteBuffer} 
     * @param byteBuffer the {@link ByteBuffer} used by this algorithm
     * @return {@link ByteBuffer} used by this algorithm
     */
    public ByteBuffer postParse(ByteBuffer byteBuffer){
        // Swap buffer to its original size.
        if (primaryByteBuffer != null) {
            primaryByteBuffer.clear();
            byteBuffer = primaryByteBuffer;
            primaryByteBuffer = null;
        }
        return byteBuffer;
    }

    
    /**
     * Reset this object to its default state.
     */
    public void recycle(){
        contentLength = -1;
        lastStatePosition= -1;
        headerLength = -1; 
        curLimit = -1;
        curPosition = -1;
        state = 0;
    }
    
    
    // --------------------------------------------------- Utils --------------/
    
    
    /**
     * Allocate a {@link ByteBuffer}
     * @param useDirectByteBuffer to create a direct {@link ByteBuffer}
     * @param useByteBufferView to create {@link ByteBuffer} view
     * @param size the size of the {@link ByteBuffer}
     * @return a {@link ByteBuffer}
     */   
    public ByteBuffer allocate(boolean useDirectByteBuffer, 
                               boolean useByteBufferView, 
                               int size){
        ByteBuffer byteBuffer;
        if (useByteBufferView){
            byteBuffer = ByteBufferFactory.allocateView(size,
                                                        useDirectByteBuffer);
        } else if ( useDirectByteBuffer ){
            byteBuffer = ByteBuffer.allocateDirect(size);            
        } else {
            byteBuffer = ByteBuffer.allocate(size);                       
        }
        return byteBuffer;
    }
    
    
    /**
     * Allocate a new {@link ByteBuffer} and put the content of the current
     * one into it.
     */
    private ByteBuffer swapBuffer(ByteBuffer byteBuffer, int size){
        ByteBuffer tmp = allocate(useDirectByteBuffer,useByteBufferView, size);

        byteBuffer.flip();
        tmp.put(byteBuffer);
        
        // Keep a pointer to the original one.
        if ( primaryByteBuffer == null) {
            primaryByteBuffer = byteBuffer;
        }
        byteBuffer = tmp;
        return byteBuffer;
    }        
    
    
    /**
     * Rollback the {@link ByteBuffer} to its previous state in case
     * an error as occured.
     */
    public ByteBuffer rollbackParseState(ByteBuffer byteBuffer){
        /**
         * Set the {@link ByteBuffer} position/limit to the value
         * they were before trying to process the stream.
         */
        if (curLimit != -1 && curPosition != -1){
            byteBuffer.limit(curLimit);
            byteBuffer.position(curPosition);

            // Reset so we can process a new request.
            contentLength = -1;
            headerLength = -1;  
        }
        return byteBuffer;
    }    
    
    
    /**
     * Dump the ByteBuffer content. This is used only for debugging purpose.
     */
    protected String dump(ByteBuffer byteBuffer){                   
        ByteBuffer dd = byteBuffer.duplicate();
        dd.flip();
        
        int length = dd.limit();
        byte[] dump = new byte[length];
        dd.get(dump,0,length);
        return(new String(dump) + "\n----------------------------" + dd 
        + "\ncontentLength: " + contentLength 
        + "\nheaderLength: " + headerLength); 
    }
    
    
    /**
     * The {@link SocketChannel} used by this class.
     */
    public void setChannel(SocketChannel socketChannel){
        this.socketChannel = socketChannel;
        if ( socketChannel != null)
            handler.attachChannel(socketChannel); 
    }
    
    
    /**
     * Set the port
     */
    public void setPort(int port){
        this.port = port;
    }
    
    
    /**
     * Return the port
     */
    public int getPort(){
        return port;
    }
    
    
    /**
     * Empty handler used when Grizzly is bundled outside of GlassFish.
     *
     * @author Jeanfrancois Arcand
     */
    public static class DummyHandler implements Interceptor<Object,SocketChannel>{

        public DummyHandler() {
        }

        public int handle(Object e, int handlerCode) throws IOException {
            return Interceptor.CONTINUE;
        }
        
        public void attachChannel(SocketChannel sc){
            ; // Not used.
        }

    }
}
