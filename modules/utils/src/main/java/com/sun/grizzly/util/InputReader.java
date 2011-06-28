/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import javax.net.ssl.SSLEngine;

/**
 * This class implement IO stream operations on top of a {@link ByteBuffer}. 
 * Under the hood, this class use a temporary Selector pool for reading
 * bytes when the client ask for more and the current Selector is not yet ready.
 * 
 * @author Jeanfrancois Arcand
 */
public class InputReader extends InputStream {

    /**
     * The {@link Channel} type is used to avoid invoking the instanceof
     * operation when registering the Socket|Datagram Channel to the Selector.
     */ 
    public enum ChannelType { SocketChannel, DatagramChannel }
    
    
    /**
     * By default this class will cast the Channel to a SocketChannel.
     */
    private ChannelType defaultChannelType = ChannelType.SocketChannel;
    
        
    private static int defaultReadTimeout = 30000;
    
    /**
     * The wrapped <code>ByteBuffer</code<
     */
    protected ByteBuffer byteBuffer;

    /**
     * The encrypted {@link ByteBuffer}
     */
    protected ByteBuffer inputBB;
    
    /**
     * The {@link SelectionKey} used by this stream.
     */
    public SelectionKey key = null;
    
    
    /**
     * The time to wait before timing out when reading bytes
     */
    protected int readTimeout = defaultReadTimeout;
    
    
    /**
     * Is the stream secure.
     */
    private boolean secure = false;
    
    /**
     * Is the underlying {@link SocketChannel} closed?
     */
    private boolean isClosed = false;

    /**
     * The SSLEngine to use for unwrapping bytes.
     */
    protected SSLEngine sslEngine;

    // ------------------------------------------------- Constructor -------//
    
    
    public InputReader () {
    }

    
    public InputReader (final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    // ---------------------------------------------------------------------//
    
    
    /**
     * Set the wrapped {@link ByteBuffer}
     * @param byteBuffer The wrapped byteBuffer
     */
    public void setByteBuffer(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }
    
    
    /**
     * Get the wrapped {@link ByteBuffer}
     * @return {@link ByteBuffer}
     */
    public ByteBuffer getByteBuffer() {
        return  byteBuffer;
    }
    
    
    /**
     * Return the available bytes 
     * @return the wrapped byteBuffer.remaining()
     */
    @Override
    public int available () {
        return (byteBuffer.remaining());
    }

    
    /**
     * Close this stream. 
     */
    @Override
    public void close () {
        isClosed = true;
    }

    
    /**
     * Return true if mark is supported.
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    
    /**
     * Read the first byte from the wrapped {@link ByteBuffer}.
     */
    @Override
    public int read() throws IOException {
        if (!byteBuffer.hasRemaining()){
            byteBuffer.clear();
            int eof = doRead();

            if (eof <= 0){
                return -1;
            }
        }
        return (byteBuffer.hasRemaining() ? (byteBuffer.get () & 0xff): -1);
     }

    
    /**
     * Read the bytes from the wrapped {@link ByteBuffer}.
     */
    @Override
    public int read(byte[] b) throws IOException {
        return (read (b, 0, b.length));
    }

    
    /**
     * Read the first byte of the wrapped {@link ByteBuffer}.
     * @param offset 
     * @param length 
     */
    @Override
    public int read(byte[] b, int offset, int length) throws IOException {
        if (!byteBuffer.hasRemaining()) {
            byteBuffer.clear();
            int eof = doRead();

            if (eof <= 0){
                return -1;
            }
        }
 
        if (length > byteBuffer.remaining()) {
            length = byteBuffer.remaining();
        }
        byteBuffer.get(b, offset, length);
         
        return (length);
    }
    
    
    /**
     * Read the bytes of the wrapped {@link ByteBuffer}.
     * @param bb {@link ByteBuffer}
     * @return - number of bytes read
     * @throws java.io.IOException 
     */
    public int read(ByteBuffer bb) throws IOException {
        //Switch Buffer
        ByteBuffer oldBB = byteBuffer;
        byteBuffer = bb;
        int initialPosition = bb.position();
        int eof = doRead();

        if (eof <= 0){
            return -1;
        }
        // Back to the default one.
        byteBuffer = oldBB;
        
        // Calculate the number of bytes were read
        int bytesRead = bb.limit() - initialPosition;
        return bytesRead;
    }
    
    
    /**
     * Recycle this object.
     */ 
    public void recycle(){
        sslEngine = null;
        secure = false;
        
        byteBuffer = null;  
        inputBB = null;
        key = null;
        isClosed = false;
    }
    
    
    /**
     * Set the {@link SelectionKey} used to reads bytes.
     * @param key {@link SelectionKey}
     */
    public void setSelectionKey(SelectionKey key){
        this.key = key;
    }
    
    
    /**
     * Read bytes using the read <code>ReadSelector</code>
     * @return - number of bytes read
     * @throws java.io.IOException 
     */
    protected int doRead() throws IOException{        
        if ( key == null || isClosed ) return -1;
 
        int nRead = -1;
        try{
            if (secure){
                nRead = doSecureRead();
            } else {
                nRead = doClearRead();
            }
        } catch (IOException ex){
            nRead = -1;
            throw ex;
        } finally {
            if (nRead == -1){
                if (Thread.currentThread() instanceof WorkerThread){
                    ConnectionCloseHandlerNotifier notifier = (ConnectionCloseHandlerNotifier)
                            ((WorkerThread)Thread.currentThread())
                                .getAttachment().getAttribute("ConnectionCloseHandlerNotifier");
                    if (notifier != null){
                        notifier.notifyRemotlyClose(key);
                    }                  
                }
            }
            return nRead;
        }               
    }
        
    
    /**
     * Read and decrypt bytes from the underlying SSL connections. All
     * the SSLEngine operations are delegated to class {@link SSLUtils}.
     * @return  number of bytes read
     * @throws java.io.IOException 
     */    
    protected  int doSecureRead() throws IOException {
        Utils.Result r = SSLUtils.doSecureRead((SocketChannel) key.channel(), 
                sslEngine, byteBuffer, 
                inputBB);
        
        byteBuffer.flip();       
        isClosed = r.isClosed;      
        return r.bytesRead;
    }   
        
        
    protected int doClearRead() throws IOException{
        Utils.Result r = Utils.readWithTemporarySelector(key.channel(), 
                byteBuffer, readTimeout);
        byteBuffer.flip();
        isClosed = r.isClosed;
        return r.bytesRead;
    } 

    
    /**
     * Return the timeout between two consecutives Selector.select() when a 
     * temporary Selector is used.
     * @return read timeout being used
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    
    /**
     * Set the timeout between two consecutives Selector.select() when a 
     * temporary Selector is used.
     * @param rt - read timeout
     */    
    public void setReadTimeout(int rt) {
        readTimeout = rt;
    }

    
    /**
     * Return the Selector.select() default time out.
     * @return  default time out
     */
    public static int getDefaultReadTimeout() {
        return defaultReadTimeout;
    }

    
    /**
     * Set the default Selector.select() time out.
     * @param aDefaultReadTimeout  time out value
     */
    public static void setDefaultReadTimeout(int aDefaultReadTimeout) {
        defaultReadTimeout = aDefaultReadTimeout;
    }

    
    /**
     * Return the {@link Channel} type. The return value is SocketChannel
     * or DatagramChannel.
     * @return  {@link Channel} being used
     */
    public ChannelType getChannelType() {
        return defaultChannelType;
    }

    
    /**
     * Set the {@link Channel} type, which is ocketChannel
     * or DatagramChannel.
     * @param channelType  {@link Channel} to use
     */
    public void setChannelType(ChannelType channelType) {
        this.defaultChannelType = channelType;
    }

    
    /**
     * Is this Stream secure.
     * @return  true is stream is secure, otherwise false
     */
    public boolean isSecure() {
        return secure;
    }

    
    /**
     * Set this stream secure.
     * @param secure  true to set stream secure, otherwise false
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public SSLEngine getSslEngine() {
        return sslEngine;
    }


    public void setSslEngine(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }
    
    public ByteBuffer getInputBB() {
        return inputBB;
    }

    
    public void setInputBB(ByteBuffer inputBB) {
        this.inputBB = inputBB;
    }    
}

