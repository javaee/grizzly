/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.websocket;

import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.websocket.WebSocketImpl.IOExceptionWrap;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author gustav trede
 */
public class TCPIOhandler{

    protected final static Logger logger = LoggerUtils.getLogger();

    private final List<Handshaker> handshakers = new ArrayList<Handshaker>(2);
    protected final SocketChannel channel;
    protected SelectThread selthread;
    protected SelectionKey key;
    protected int keyInterest;
    
    private boolean notpendingconnection;
    
    protected SelectorLogicHandler selectLogicHandler;

    /**
     * 
     * @param addr
     * @throws IOException
     */
    public TCPIOhandler(SocketAddress addr) throws IOException{
        this(connectAsync(addr));
    }

    /**
     *
     * @param channel
     */
    public TCPIOhandler(SocketChannel channel) {
        if (channel == null){
            throw new IllegalArgumentException("channel is null");
        }
        this.channel = channel;
        this.notpendingconnection = !channel.isConnectionPending();
    }

    /**
     *
     * @param addr
     * @return
     * @throws IOException
     */
    protected final static SocketChannel connectAsync(SocketAddress addr)
            throws IOException{
        SocketChannel ch = null;
        try{
            ch = SocketChannel.open();
            ch.configureBlocking(false);
            ch.connect(addr);
            return ch;
        }catch(Throwable t) {
            try{
                if (ch != null){
                    ch.close();
                }
            }catch(IOException ie) { }
            throw (t instanceof IOException)?(IOException)t:new IOExceptionWrap(t);
        }
    }

    /**
     *
     * @param att
     * @param selthread
     * @throws IOException
     */
    public void enteredSelectThread(SelectorLogicHandler att,
            SelectThread selthread) throws IOException {
        this.selectLogicHandler = att;
        this.selthread = selthread;        
    }

    /**
     *
     * @param hs
     */
    public final void addHandshaker(Handshaker hs) {
        handshakers.add(hs);        
    }

    /**
     *
     * @param buff
     * @return 
     * @throws Exception
     */
    public final boolean doHandshakeChain(ByteBuffer buff) throws Exception {
        if (notpendingconnection || finishConnect()){
           /* int sr = 64;
            int rb = 16384;
            if (channel.socket().getSendBufferSize()!=sr)
                channel.socket().setSendBufferSize(sr);
            //channel.socket().setReceiveBufferSize(rb);
            if (channel.socket().getSendBufferSize()!=sr)
                System.err.println(channel.socket().getSendBufferSize());
            //if (channel.socket().getReceiveBufferSize()!=rb)
              //  System.err.println(channel.socket().getReceiveBufferSize());
                */
           while(!handshakers.isEmpty()){
               if (!handshakers.get(0).doHandshake(buff)){
                   return false;
               }              
               handshakers.remove(0);
           }
           return true;
        }
        return false;
    }

    /**
     * First call is for unregistered channel, checking for finished connection
     * to save the cost of a kater key interest change.
     * Second call and not connected means OP_CONNECT failed.
     * @throws IOException
     */
    private boolean finishConnect() throws IOException{
        if (channel.finishConnect()){
            return notpendingconnection = true;
        }
        if (key == null){
            initialInterest(SelectionKey.OP_CONNECT);
            return false;
        }
        throw new IOException("SocketChannel.finishConnect() returned false");
    }

    /**
     * Sets the SelectionKey interest during handshake phase, key can be null
     * until the first {@link Handshaker#doHandshake} method call completes.
     * @param interest
     * @return 
     * @throws IOException
     */
    public final boolean initialInterest(int interest) throws IOException{
        boolean changed = keyInterest != interest;
        if (changed){
            keyInterest = interest;
            if (key != null){
                key.interestOps(interest);
                return changed;
            }                
            key = selthread.register(channel,interest,selectLogicHandler);
        }
        return changed;
    }

    /**
     *
     * @param interest
     */
    public final void addKeyInterest(int interest){
        key.interestOps(keyInterest|=interest);
    }

    /**
     *
     * @param interest
     */
    public final void removeKeyInterest(int interest){
        key.interestOps(keyInterest&=~interest);
    }

    /**
     *
     * @param interest
     */
    public final void setKeyInterest(int interest){
        key.interestOps(keyInterest=interest);
    }

    /**
     * 
     * @param buff
     * @return
     * @throws IOException
     */
    public int read(ByteBuffer buffer) throws IOException{
        int read = channel.read(buffer);
        if (read <= 0){
            //if reads are disabled buffer can be full for a read ready key.
            if (read == -1 || (keyInterest & SelectionKey.OP_READ) == 0)
                throw otherpeerclosed;
            throw new RuntimeException(
                    "Likely a bug, channel.read returned 0 for "+buffer);
        }
        return read;
    }

    /**
     * Returns true if all data was written.
     * Returns false if buffer still has remaining data.
     * @param buffer
     * @return
     * @throws IOException
     */
    public boolean write(ByteBuffer buffer) throws IOException{
        channel.write(buffer);
        return buffer.remaining() == 0;
    }

    public final void writeInterestTaskOffer(){
        selthread.offerTask(writeinterester);
    }

    private final Runnable writeinterester = new Runnable() {
        public void run() {
            try{
                key.interestOps(keyInterest|=SelectionKey.OP_WRITE);
            }catch(Throwable t){
                selectLogicHandler.close(t);
            }
        }
    };

    /**
     * 
     * @throws IOException
     */
    public void close() throws IOException{
        try {
           // selthread.socketCount.decrementAndGet();
        }finally{
            channel.close();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+
            (selthread!=null?" "+selthread:"")+
            " "+channel!=null?(channel+" channel.isOpen: "+channel.isOpen()):""+
            " currentKeyInterest: "+keyInterest+
            key!=null?((!key.isValid()?" key.isValid: false":
            " key.interestOps: "+key.interestOps()+
            " key.readyOps: "+key.readyOps())):""
            ;
    }


    protected final static IOException otherpeerclosed =
            new IOException("Other peer closed the connection."){
                @Override
                public Throwable fillInStackTrace() { return this; }
            };

}
