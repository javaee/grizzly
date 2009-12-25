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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLSession;

/**
 * TODO: gethold of ssl buffers used in grizzly in non standalone mode
 *  to transfer data that might exist after handshake ?. 
 * TODO: add config for use of native buffers.
 * TODO: handle SSL rehandshake.
 * TODO: JDK SSLSessionContextImpl needs cleanup.
 *
 */
public class SSLIOhandler extends TCPIOhandler implements Handshaker{

    //protected final static int maxSSLbuffersize = (1<<18) +(1<<15);

    private final SSLEngine sslEngine;

    private HandshakeStatus hr,delayedwrapstatus;

    private boolean initilSSLHandshakedone;
    
    private boolean needsinit = true;

    private ByteBuffer sslReadBuffer;

    private ByteBuffer sslWriteBuffer;


    /**
     *
     * @param addr
     * @param ssleng
     * @throws IOException
     */
    public SSLIOhandler(SocketAddress addr,SSLEngine ssleng)throws IOException{
        this(connectAsync(addr),ssleng);
    }

    /**
     *
     * @param channel
     * @param sslengine
     */
    public SSLIOhandler(SocketChannel channel,SSLEngine sslengine){
        super(channel);
        if (sslengine == null){
            throw new IllegalArgumentException("sslengine is null");
        }
        this.sslEngine = sslengine;
        super.addHandshaker(this);
        init();
    }

    /**
     * Returns the number of decrypted bytes.
     * @param buffer
     * @return 
     * @throws IOException
     */
    @Override
    public int read(ByteBuffer buffer) throws IOException{
        ByteBuffer rbuf = sslReadBuffer;
        compactClear(rbuf);
        int bytesRead = super.read(rbuf);
       // boolean firstreadfilledbuffer = rbuf.remaining() == 0;
       /* if (bytesRead == rbuf.capacity() && bytesRead < maxSSLbuffersize){
            sslReadBuffer = rbuf = increaseBuffer(rbuf);
        }*/
       // System.err.println("read "+bytesRead +" id:"+key.hashCode()+" " +rbuf+" selthread: "+selthread+" targetbuf:"+buffer);
        rbuf.flip();
        int produced = 0;
        do {            
            SSLEngineResult result = sslEngine.unwrap(rbuf,buffer);
            //System.err.println("readP"+bytesRead +" id:"+key.hashCode()+" " +rbuf+" selthread: "+selthread+" targetbuf:"+buffer);
              Status st = result.getStatus();
            if (st == Status.OK)
                produced += result.bytesProduced();

            if (st == Status.BUFFER_UNDERFLOW) {
                /*if (firstreadfilledbuffer){
                    firstreadfilledbuffer = false;
                    rbuf.compact();
                    bytesRead = channel.read(rbuf);
                    if (bytesRead == -1){
                        throw otherpeerclosed;
                    }
                    rbuf.flip();
                    if (bytesRead > 0 )
                        continue;
                }*/
                return produced;
            }
            if (st == Status.BUFFER_OVERFLOW) {
                //if (produced > 0 )
                    //return produced;
                throw new RuntimeException("SSLread Bufferoverflow in:"+ selectLogicHandler);
            }
            if (st == Status.CLOSED){
                throw new IOException("SSL closed during unwrap.");
            }
            if (result.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING){
                throw new RuntimeException("SSL unwrap "+result);
            }
        }while (rbuf.hasRemaining());
        return produced;
    }

    /**
     * @param buffer
     * @return true
     * @throws IOException
     */
   private SSLEngineResult handshakeread(ByteBuffer bb) throws IOException {
        ByteBuffer sslread = sslReadBuffer;
        compactClear(sslread);
        final int bytesRead = channel.read(sslread);
        if (bytesRead == -1)
            throw otherpeerclosed;
        sslread.flip();
        /*if (bytesRead == 0 && !sslread.hasRemaining()) {
            return 0;
        }*/
        SSLEngineResult result ;
        do {
            result = sslEngine.unwrap(sslread, bb);

        } while (result.getStatus() == Status.OK &&
                result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP);
        Status st = result.getStatus();
            if (st == SSLEngineResult.Status.CLOSED) {
              throw new IOException("SSL handshake "+st);
            }
            if (st == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                throw new RuntimeException(st+" in "+bb);
            }
        hr = result.getHandshakeStatus();
        return result;
    }

    private boolean flush() throws IOException{
        final ByteBuffer wbuf = sslWriteBuffer;
        if (channel.write(wbuf) == 0){//TODO: remove this when QA is done ?
            throw new RuntimeException("SSL.flush 0 bytes from "+wbuf);
        }
        return wbuf.remaining() == 0;
    }

    @Override
    public boolean write(ByteBuffer buffer) throws IOException {
        // 16635 at least is needed free or buffer overflow will trigger.
        
        final ByteBuffer wbuf = sslWriteBuffer;
        if (buffer.remaining() == 0 && wbuf.hasRemaining() ){
            return super.write(wbuf);
        }        

        wbuf.clear();

        //int written = 0;
        do {
            SSLEngineResult rs = sslEngine.wrap(buffer, wbuf);
            Status st = rs.getStatus();
            //written += rs.bytesConsumed();
            if (st == Status.OK) {
                if (rs.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING) {
                    throw new RuntimeException("SSL wrap "+rs.getHandshakeStatus());
                }
                continue;
            }
            if (st == Status.BUFFER_OVERFLOW) {                
                /*if (wbuf.capacity() < maxSSLbuffersize) {
                    sslWriteBuffer = wbuf = increaseBuffer(wbuf);
                    continue;
                }*/
                break;
            }
            if (st == Status.BUFFER_UNDERFLOW) { 
                throw new RuntimeException("SSL wrap underflow: " + buffer);
            }
            throw new IOException("SSL write wrap failed due to closed");
        } while (buffer.hasRemaining());

        wbuf.flip();
        return super.write(wbuf) && buffer.remaining() == 0;
    }

    /*private final ByteBuffer increaseBuffer(ByteBuffer bb){
        bb.flip();
        return ByteBuffer.allocate(
                Math.min(bb.capacity()*2,maxSSLbuffersize)).put(bb);
    }*/

    private final static ByteBuffer zerobuf = ByteBuffer.allocate(0);


    public boolean doHandshake(ByteBuffer bb) throws Exception{
        if (needsinit){
            needsinit = false;
            if (sslEngine.getUseClientMode()){
                sslEngine.beginHandshake();
                hr = sslEngine.getHandshakeStatus();
            }else{
                hr = HandshakeStatus.FINISHED; //for grizzly server
            }
        }
        if (hr == HandshakeStatus.NEED_TASK){
            throw new IOException("SSLhandshake interrupted by unexpected " +
                    "selectionkey ready, most likely is the socket closed");
        }
        while (true) {
          //  System.err.println("hs "+hr);
            switch(hr) {
                case NEED_WRAP:
                    final ByteBuffer wbuf = sslWriteBuffer;
                    if (wbuf.hasRemaining()){
                        System.err.println("wrap flush performed ");
                        if (flush()){
                            hr = delayedwrapstatus;
                            continue;
                        }
                        return false;
                    }
                    wbuf.clear();
                    SSLEngineResult result = sslEngine.wrap(zerobuf, wbuf);
                    wbuf.flip();
                    if (result.getStatus() == Status.OK){
                        channel.write(wbuf);
                        if (wbuf.hasRemaining()){
                            System.err.println("wrap write interest needed ");
                            delayedwrapstatus = result.getHandshakeStatus();
                            initialInterest(SelectionKey.OP_WRITE);
                            return false;
                        }
                        //only updating status when no data remains to write.
                        hr = result.getHandshakeStatus();
                        continue;
                    }
                    throw new IOException("SSLhandshake :"+result);
                case NEED_UNWRAP:
                    SSLEngineResult rs = handshakeread(bb);
                    if (rs.getStatus() == Status.BUFFER_UNDERFLOW){
                       // System.err.println("umwrap read interest needed ");
                        initialInterest(SelectionKey.OP_READ);
                        return false;
                    }
                     continue;
                case NEED_TASK:
                    handletasks();                    
                    return false;
                case FINISHED:
                    if (!initilSSLHandshakedone){
                        initilSSLHandshakedone = true;
                        bb.clear();//TODO remove clear  ?
                        sslWriteBuffer.clear();
                    }
                    return true;
                default:
                    throw new IllegalStateException(
                            "invalid SSL handshake state: "+hr);
            }
        }
    }

    private final void init() {   
        SSLSession session = sslEngine.getSession();
        int packetsize = session.getPacketBufferSize();
        sslReadBuffer  = ByteBuffer.allocate(packetsize+1024);
        sslWriteBuffer = ByteBuffer.allocate(packetsize*2);
        sslWriteBuffer.limit(0);
        sslReadBuffer.limit(0);

    }

    public int getRequiredReadBuffersize(){
        return sslReadBuffer.capacity();
    }

    private final void handletasks() throws IOException{
        SelectThread.workers.execute(new Runnable(){
            public void run() {
                final SelectorLogicHandler slh = selectLogicHandler;
                try{
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    selthread.offerTask(new Runnable() {
                        public void run() {
                            try{
                                hr =  sslEngine.getHandshakeStatus();
                            }catch(Throwable t){
                               slh.close(t);
                            }
                            //timestamp 0 is ok during handshake, its not used.
                            slh.handleSelectedKey(key,0);
                        }
                    });
                }catch(Throwable t){
                    //this call to close might cause select thread to wait on
                    //locks, it should however be extremely rare that close is
                    //needed here,but if something goes wrong its best to close
                    //without having to deal with possible froblems from
                    //threadpools etc.
                    slh.close(t);
                }
            }});
        //TODO would it be safe to leave read interest on ?
        initialInterest(0);
    }

    protected final static void compactClear(ByteBuffer buf) {
        final int pos = buf.position();
        if (pos == buf.limit()) {
            buf.clear();
            return;
        }
        if (pos > 0 ) { 
            buf.compact();
        }
    
    }

    @Override
    public void close() throws IOException {
        try{//TODO SSL compliant shutdown logic needed ?
            sslEngine.closeInbound();
        }finally{
            super.close();
        }
    }

    @Override
    public String toString() {
        return super.toString()+
            " initilSSLHandshakedone: "+initilSSLHandshakedone+
            " handshake: "+hr+"\r\n"+
            " engstatus: "+sslEngine.getHandshakeStatus()+
            " sslEngine: "+sslEngine+
            " sslReadBuffer: "+sslReadBuffer+
            " sslWriteBuffer: "+sslWriteBuffer
            ;
    }

}
