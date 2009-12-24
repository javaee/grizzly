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
import com.sun.grizzly.util.SelectedKeyAttachmentLogic;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * TODO: {@link DataFrame} design and QA. more unit testing.
 * TODO: throttle and concurrent send , user control of the situation.
 * if we dont fail  on writebuffer limit reached
 * we must propagate that info somehow,
 * currently there is only  closed and its exception  as info
 * and a boolean  return from send method
 * so either change boolean to enum  or add exception thrown from send method ?
 * <br>
 * TODO: The current design allows for one eventlistener per context.
 * Is there a real need for more at this API level, nothing stops from
 *  listeners to chain each others. ?
 * If more then one, it would be of interest to have
 * per listener flags for per eventype to run in threadpool.
 * To implement that efficently would require abstract baseclass instead of
 * interface that its today.
 * <br>
 * TODO: should client be allowed to provide origin string?
 * if not we must find a suitable hostname to use, how do we choose when
 * several hostnames exist, just grab one and be happy ?.
 * <br>
 * TODO: add serverside  {@link  WebsocketHandshake#origin}  security ?,
 * its client provided info.. so can never do any real securiy based on it.
 * lists of allowed origins per resourcepath?.
 * <br>
 * TODO: send() method optional frame validation ?
 * <br>
 * TODO: RFC 3490. cant use URI as param due to its not compliant.
 * can use seperate param for host value, but that seems to rely on that
 *  the OS is doing the IDNA conversion. we need to provide IDNA conversion even
 *  for that simple case, full URI/string parser that is IDNA compliant
 * would be optimal.
 * <br>
 * TODO: context lifecycle ? when removed, the websockets still fetches
 * config parameters in runtime  from the context and keeps working.
 * Should we keep a list of websockets per context so we
 *  can kill them and also allow for easy to use msg broadcast ?.
 * its perhaps better to leave that to service implementator ,
 * or we can provide another layer ontop that can be used if wanted.
 * <br>
 * Further optimize buffer.compact and resize logic in parseFrame().
 * <br>
 * TODO: is there a need for pluggable handler for uncaught exceptions
 * from services ({@link WebSocketListener}) per context or global ?.
 * It could be used to detect unhealthy services and remove them, or at least
 * gather statistics instead if just filling an error log with hard to use data?.
 * <br><br>
 * Implementation is based on :<br>
 * <a href="http://tools.ietf.org/html/draft-hixie-thewebsocketprotocol"
 * >http://tools.ietf.org/html/draft-hixie-thewebsocketprotocol</a><br>
 *
 * @author gustav trede
 * @since 2009
 */
public class WebSocketImpl extends WebSocket implements SelectorLogicHandler{

    protected final static Logger logger = LoggerUtils.getLogger();

    protected final static byte TEXTTYPE      = (byte)0x00;
    protected final static byte BINARYTYPE    = (byte)0x80;
    protected final static byte TEXTterminate = (byte)0xff;

    private final static ByteBuffer emptybuffer = ByteBuffer.allocate(0);

    public final static SelectedKeyAttachmentLogic dummyAttachment =
            new SelectedKeyAttachmentLogic() {
        public void handleSelectedKey(SelectionKey selectionKey){}
    };

    protected enum ReadyState{CONNECTING,OPEN,CLOSED};

    private final static AtomicReferenceFieldUpdater<WebSocketImpl,ReadyState>
            state = AtomicReferenceFieldUpdater.
            newUpdater(WebSocketImpl.class, ReadyState.class, "readystate");

    private volatile ReadyState readystate = ReadyState.CONNECTING;

    private byte  frametype = -1;
    private int   framestart;
    private int   bindataend;
    private int   bindatalength;
    private int   parsed;

    /**
     * Its value is updated at:<br>
     * Once when entering selectthread as a new connection.<br>
     * Handshake completion (readystate changes to OPEN).<br>
     * Readystate is OPEN and the SelectionKey readyOps contains OP_READ.<br>
     */
    private long idleTimestamp;
    
    /**
     * Fast access to the idletimeout value at the current readystate.
     */
    private long currentTimeOut;

    protected volatile int readDataThrottledCounter;

    /**
     * Socket native read buffer is not included in this value.
     */
    private final AtomicInteger bufferedOnMessageRAMbytes = new AtomicInteger();

    /**
     * Socket native write buffer is not included in this value
     */
    private final AtomicInteger bufferedSendBytes = new AtomicInteger();

    private int written ;

    protected ByteBuffer readBuffer;
    private byte[] readByteArray;

    private ByteBuffer currentwrite;

    private final MySwapList<ByteBuffer> writeQueue;
    
    private volatile Throwable closedCause;

    protected final WebSocketContext ctx;

    protected final TCPIOhandler iohandler;

    private final WebsocketHandshake handshake;

    private SelectThread selthread;

    /**
     * 
     * @param uri
     * @param origin
     * @param protocol
     * @param listener
     * @param sslctx
     * @param copysettingsfrom
     * @return
     * @throws IOException
     */
    protected final static WebSocket openClient(String uri, String origin,
            String protocol,WebSocketListener listener,SSLContext sslctx,
             WebSocketContext copysettingsfrom)
            throws IOException{
        try{
            WebsocketClientHandshake wsh =
                  new WebsocketClientHandshake(uri,origin,protocol);
            TCPIOhandler ioh;
            if (wsh.secureonly){
                if (sslctx == null){
                    throw new MalformedURLException(
                            "wss but SSLContext is null");
                }
                SSLEngine ssleEngine = sslctx.createSSLEngine(
                   wsh.remoteAddress.getHostName(),wsh.remoteAddress.getPort());
                ssleEngine.setUseClientMode(true);
                ioh = new SSLIOhandler(wsh.remoteAddress,ssleEngine);
            }else{
                ioh = new TCPIOhandler(wsh.remoteAddress);
            }
            WebSocketContext ctx =
                    new WebSocketContext("/notused",protocol,listener);
            if (copysettingsfrom!=null)
                ctx.copySomeSettingsFrom(copysettingsfrom);
            return doOPen(wsh, ioh, ctx);
        }catch(IOException ie){
            throw ie;
        }
        catch(RuntimeException e){
            throw new IOExceptionWrap(e);
        }
    }

    /**
     * 
     * @param handshake
     * @param iohandler
     * @param ctx
     * @return
     */
    public final static WebSocket doOPen(WebsocketHandshake handshake,
            TCPIOhandler iohandler,WebSocketContext ctx) {
        WebSocketImpl wsi = new WebSocketImpl(handshake,iohandler,ctx);
        SelectThread.moveToSelectThread(wsi);
        return wsi;
    }

    /**
     * 
     * @param handshake
     * @param iohandler
     * @param ctx
     */
    private WebSocketImpl(WebsocketHandshake handshake,TCPIOhandler iohandler,
            WebSocketContext ctx) {
        if (ctx == null)
            throw new IllegalArgumentException("ctx is null");
        if (iohandler == null)
            throw new IllegalArgumentException("iohandler is null");
        if (handshake == null)
            throw new IllegalArgumentException("handshake is null");
        handshake.setIOhandler(iohandler);
        this.handshake        = handshake;
        this.iohandler        = iohandler;
        this.ctx              = ctx;
       // this.writeQueue       = SelectThread.getCLQinstance();
        this.writeQueue       = new MySwapList<ByteBuffer>();
    }

    @Override
    public final int getBufferedSendBytes() {
        return bufferedSendBytes.get();
    }

    @Override
    public final int getBufferedOnMessageBytes(){
        return bufferedOnMessageRAMbytes.get();
    }

    @Override
    public final int getReadDataThrottledCounter() {
        return readDataThrottledCounter;
    }

    @Override
    public final WebSocketContext getWebSocketContext() {
        return ctx;
    }

    @Override
    public final SocketAddress getRemotAddress(){
        return iohandler.channel.socket().getRemoteSocketAddress();
    }

    /**
     *
     * @param st
     * @param timestamp
     */
    public final void enteredSelector(SelectThread st, long timestamp){
        try{            
            selthread = st;
            idleTimestamp = timestamp;
            currentTimeOut = ctx.getHandshakeTimeoutSeconds() * 1000000000L;
            iohandler.enteredSelectThread(this, st);
            //TODO make buffersize adaptive for SSL too, handle SSL overflow.
            int bufflength = (iohandler instanceof SSLIOhandler) ?
                // ensure room for max dataframesize and for SSL to read without
                // overflow
                Math.max(ctx.getMaxDataFramelengthBytes(),4096)+
                ((SSLIOhandler)iohandler).getRequiredReadBuffersize() :
                ctx.initialReadBufferLength;
            readBuffer = ByteBuffer.allocate(bufflength);
            readByteArray = readBuffer.array();
            handshake.bbf = readBuffer;            
            if(iohandler.doHandshakeChain(readBuffer)){
                setOPENstate(timestamp);
            }
        }catch(Throwable e){
            close(e,true);
        }
    }

    /**
     * 
     * @param key
     * @param timestamp
     */
    public final void handleSelectedKey(SelectionKey key, long timestamp){
        try {        
            if (readystate != ReadyState.OPEN){
                if (readystate == ReadyState.CONNECTING){
                    if (iohandler.doHandshakeChain(readBuffer)){
                        setOPENstate(timestamp);
                    }
                }
                return;
            }
            //System.err.println(key+" read:"+key.isReadable()+"  write:"+key.isWritable()+"  connect:"+key.isConnectable());
            final int ops = key.readyOps();
            if ((ops & SelectionKey.OP_READ)!=0 ){
                idleTimestamp = timestamp; 
                iohandler.read(readBuffer);
                parseFrame();
            }
            if ((ops & SelectionKey.OP_WRITE)!=0 ){
                doSelectThreadWrite();
            }
        } catch (Throwable ex) {
            close(ex,true);
        }
    }

    /**
     *
     * @param timestamp
     * @throws Throwable
     */
    private final void setOPENstate(long timestamp) throws Throwable {
        parsed = handshake.parsed;
        final boolean hasdata = parsed > 0;
        if (hasdata){
            iohandler.setKeyInterest(0);
        }
        if(state.compareAndSet(this,ReadyState.CONNECTING,ReadyState.OPEN)){
            this.idleTimestamp = timestamp;
            currentTimeOut = ctx.getIdleTimeoutSeconds() * 1000000000L;
            SelectThread.workers.execute(new Runnable() {
                public void run() {
                    fireOpen(hasdata);
                }});
            return;
        }
    }

    private final void fireOpen(boolean  dataremainstoread){
        try{
            ctx.eventlistener.onOpen(this);
            if (dataremainstoread){
                selthread.offerTask(reEnableReadInterest);
            }
        }catch(Throwable t){
            handleListenerException(t);
        }
    }

    /**
     * 
     * @param timestamp
     */
    public final void idleCheck(long timestamp){
        final long currentTimeOut_ = currentTimeOut;
        if (currentTimeOut_ > 0 && (idleTimestamp+currentTimeOut_)-timestamp<0){
            close(new TimedOutException(timestamp),true);
        }
    }
        
    private final void parseFrame() throws Throwable{
        final int flimit = ctx.maxDataFramelengthBytes;        
        final byte[] ba  = readByteArray;
        final ByteBuffer readbuf = readBuffer;
        final int pos = readbuf.position();
        int parsed_   = parsed;
        int fstart    = framestart;
        int bend      = bindataend;
        while(parsed_ < pos ){
            if (frametype == -1){
                frametype = (byte) (ba[fstart = parsed_++] & BINARYTYPE);
                continue;
            }
            
            if (frametype == TEXTTYPE){
                int end = indexOf(parsed_,TEXTterminate,ba,pos);
                boolean done = end++ > 0;
                if (!done){
                    end = 1+pos;
                    parsed_ = pos;
                }                
                if (end-fstart <= flimit){
                   // System.err.println((handshake instanceof WebsocketServerHandshake)+" ** parsed "+parsed_+" limit:"+pos+" fstart:"+fstart+" end:"+end+" "+" fle: "+(end-fstart)+" flimit:"+flimit+" buf:"+readbuf );
                    if (done && frameisdone(fstart, (parsed_=end)-fstart, ba)){
                        break;
                    }
                    continue;
                }
                throw new WebSocketClientSentTooLargeFrame(end-fstart,flimit);
            }
            
            if (frametype == BINARYTYPE){
                while(bend == 0 && parsed_ < pos){
                    int v =  ba[parsed_];
                    if ((v&0x80)==0x80){
                        v &= 0x7f;
                    }else{
                        bend = 1+parsed_;
                    }
                    final int length = ++parsed_-fstart+
                            (bindatalength = (bindatalength<<7) + v);
                    if (length <= flimit){
                        if (bend > 0){
                            bend += bindatalength;
                            bindatalength = 0;
                            break;
                        }
                        continue;
                    }
                    throw new WebSocketClientSentTooLargeFrame(length,flimit);
                }
                
                if (bend > 0 && bend <= pos ){
                        parsed_ = bend;
                        bend = 0;
                        bindataend = 0;
                        if (frameisdone(fstart,parsed_-fstart,ba)){
                            break;
                        }
                        continue;
                }
                parsed_ = pos;
                continue;
            }
            throw new BadWebSocketFrameTypeFromClientException(frametype);
        }        

        if (frametype == -1){
            parsed = 0;
            readbuf.clear();            
            return;
        }
       /* if (pos<4096){
            parsed = parsed_;
            return;
        }*/
        //TODO: dont sticky large buffers forever.impl and configuration needed.
        //TODO: improve adaptive resizing
        final int minIncrease =(bend > 0 ? bend-fstart : Math.min(flimit,16384))
                -ba.length
        //ensuring theres at least +x bytes to read into beyond framedata needed
                + 10000;

        readbuf.limit(pos).position(fstart);
        if (minIncrease <= 0){
            readbuf.compact();
        }else{
            ByteBuffer bb = ByteBuffer.allocate(ba.length + minIncrease);
            readByteArray = bb.put(readbuf).array();
            readBuffer = bb;                            
        }

        parsed_ -= fstart;
        if (bend > 0){
            bindataend = bend-fstart;
        }
        parsed = parsed_;
        framestart = 0;
       if (!readBuffer.hasRemaining() || readBuffer.position() == readBuffer.capacity())//TODO remove when QA done
            throw new RuntimeException(this.toString());
    }

    private final boolean frameisdone(int framestart,int length,byte[] ba) {
        //System.err.println((handshake instanceof WebsocketServerHandshake)+" ** fstart:"+framestart+" fle: "+length+" readbuf: "+readBuffer);
        final ByteBuffer bbf = ByteBuffer.allocate(length);
        bbf.put(ba,framestart,length);
        bbf.flip();
        //TODO validate UTF8: user configurable if to trust data or validate.
        final DataFrame frame = new DataFrame(frametype==TEXTTYPE, bbf);
        frametype = -1;
        final int memsize = length +
                WebSocketContext.memOverheadPerQueuedReadFrame;
        final boolean throttled = bufferedOnMessageRAMbytes.addAndGet(memsize)
                > ctx.dataFrameReadQueueLimitBytes;
        if (throttled){
            readDataThrottledCounter++;
            ctx.readDataThrottledCounter.incrementAndGet();
            iohandler.removeKeyInterest(SelectionKey.OP_READ);
        }
        //TODO: make package private DataFrame subclass thats Runnable
        // IF the mem footprint is not increased (the added websocketimpl ref
        // fits in the remaining alingnment overhead in 64bit mode)
        SelectThread.workers.execute(new Runnable() {
            public void run() {
               fireOnMessage(frame,memsize);
            }
        });        
        return throttled;
    }

    /**
     * TODO: Need to test: Re enable read earlier then queu eempty, at half full
     *  and calculate consume before the onMessage call.
     * @param frame
     */
    private final void fireOnMessage(DataFrame frame, final int memsize){
        if(bufferedOnMessageRAMbytes.addAndGet(-memsize) == 0 &&
                (iohandler.keyInterest&SelectionKey.OP_READ)==0){
            selthread.offerTask(reEnableReadInterest);
        }
        try{
           ctx.eventlistener.onMessage(this, frame);
        }catch(Throwable t){
            handleListenerException(t);
        }        
    }

    private final Runnable reEnableReadInterest = new Runnable() {
        public void run() {
            try{
                iohandler.addKeyInterest(SelectionKey.OP_READ);
                parseFrame();//consume any remaining data.
            }catch(Throwable t){
                close(t,true);
            }
        }};

    @Override
    public final boolean send(DataFrame dataframe) {
        if (dataframe == null){
            close(new IllegalArgumentException("dataframe is null"),false);
            return false;
        }
        return send(dataframe.rawFrameData);
    }

    @Override
    public final boolean send(String textUTF8){
        try{
            if (textUTF8 == null){
                throw new IllegalArgumentException("textUTF8 is null");
            }
            return send(WebSocketUtils.encode(textUTF8));
        }catch(Throwable t){
            close(t,false);
        }
        return false;
    }

    //protected volatile int apa;
    /**
     * 
     * @param rawdataframe
     * @return
     */
    @Override
    public final boolean send(ByteBuffer rawdataframe){
        try {
            final ReadyState rs = readystate;
            if (rs == ReadyState.OPEN){
                if (rawdataframe == null){
                    throw new IllegalArgumentException("rawdataframe is null");
                }
                int tosend = rawdataframe.remaining();
                final int buffsize = bufferedSendBytes.addAndGet(tosend);
                final int bufflimit = ctx.dataFrameSendQueueLimitBytes;
                if (bufflimit-buffsize < 0 ){
                    throw new WebSocketWriteQueueOverflow(buffsize,bufflimit);
                }
                if (tosend == buffsize){
                    boolean done = iohandler.write(rawdataframe);
                    int written_;
                    if (!done){ //TODO:If send(String) slice is not needed:
                        written_ = rawdataframe.remaining();
                        writeQueue.setProducerfirstElement(written_ > 0 ?
                            rawdataframe.slice() : emptybuffer);
                        tosend -= written_;
                    }
                    written_ = tosend;
                    while(done && bufferedSendBytes.addAndGet(-written_) > 0){
                        //apa++;
                        written_ = 0;
                        //Another thread called send and our write is completed,
                        //Its likely that socket native buffer is not full:
                        //Lets do as much IO in worker thread as possible
                        //without waiting for it (workerqueue).
                        ByteBuffer bb;
                        done = false;  //TODO: QA
                        while((bb=writeQueue.peek())!=null){
                            final int a = bb.position();
                            done = iohandler.write(bb);
                            written_ += bb.position() - a;
                            if (done){
                                writeQueue.remove();
                            }else break;
                        }
                    }
                    if (!done){
                        this.written = written_;
                        iohandler.writeInterestTaskOffer();
                    }
                    rawdataframe.rewind();// frame is ready for another send
                    return true;
                }
                writeQueue.add(rawdataframe.duplicate());
                return true;
            }
            if (rs == ReadyState.CONNECTING){
                throw new NotYetConnectedException();
            }
        }catch(java.nio.BufferOverflowException boe){
            close(TCPIOhandler.otherpeerclosed,false);
        }catch (Throwable ex) {
            close(ex,false);
        }
        rawdataframe.rewind();// frame is ready for another send
        return false;
    }
 /*  public final synchronized boolean send(ByteBuffer rawdataframe){
        try {
            final ReadyState rs = readystate;
            if (rs == ReadyState.OPEN){
                if (rawdataframe == null){
                    throw new IllegalArgumentException("rawdataframe is null");
                }
                final int tosend = rawdataframe.remaining();
                final int buffsize = bufferedSendBytes.addAndGet(tosend);
                final int bufflimit = ctx.dataFrameSendQueueLimitBytes;
                if (bufflimit-buffsize < 0 ){
                    throw new WebSocketWriteQueueOverflow(buffsize,bufflimit);
                }
                if (tosend == buffsize){
                    boolean done = iohandler.write(rawdataframe);
                    if (!done){ //TODO:If send(String) slice is not needed:
                        int written_ = rawdataframe.remaining();
                        currentwrite = written_ > 0?
                            rawdataframe.slice() : emptybuffer;
                        written = tosend - written_;
                    }
                    else{
                        written = 0;
                        currentwrite = null;
                    }
                    while(done && bufferedSendBytes.addAndGet(-tosend) > 0){
                        //Another thread called send and our write is completed,
                        //Its likely that socket native buffer is not full:
                        //Lets do as much IO in worker thread as possible.
                        //TODO: compare with LTQ using take and poll
                        // LTQ.take can truly ensure we fill the socket
                        // the "cost" would be a worker thread waiting for other
                        // threads between their bufferedSendByte.addAndGet
                        // and their writeQueue.add to complete.
                        // the benefit is to only ever register write interest
                        // when native socket buffer is full.
                        // Question is how likely such concurrent bursts are ?.
                        // the logic to handle them are however almost free.

                        int written_ = 0;
                        ByteBuffer bb;
                        //while((bb = wq.poll())==null); // or ltq.take()
                        while((bb = writeQueue.poll())!=null){
                            int a = bb.position();
                            done = iohandler.write(bb);
                            written_ += bb.position() - a;
                            if (!done){
                                currentwrite = bb;
                                written = written_;
                                break;
                            }
                        }
                    }
                    if (!done){
                        iohandler.writeInterestTaskOffer();
                    }
                    rawdataframe.rewind();// frame is ready for another send
                    return true;
                }
                writeQueue.add(rawdataframe.duplicate());
                return true;
            }
            if (rs == ReadyState.CONNECTING){
                throw new NotYetConnectedException();
            }
        }catch(java.nio.BufferOverflowException boe){
            close(TCPIOhandler.otherpeerclosed,false);
        }catch (Throwable ex) {
            close(ex,false);
        }
        rawdataframe.rewind();// frame is ready for another send
        return false;
    }*/
   /* private final void dowrite(){
        try{
            //TODO move currentwrite into swaplist if thats final datastructure,
            // setfirstelement for producer and consumer uses peek and poll.

            ByteBuffer bb = currentwrite;
            if (bb != null){
                currentwrite = null;
            }else{
                bb =  writeQueue.poll();
            }
            int written_ = written;
            boolean done = true;
            while(bb!=null){
                int a = bb.position();
                done = iohandler.write(bb);
                written_ += bb.position() - a;
                if (!done){
                    currentwrite = bb;
                    break;
                }
                bb = writeQueue.poll();
            }

            if (done){
                if(bufferedSendBytes.addAndGet(-written_) == 0){
                    iohandler.removeKeyInterest(SelectionKey.OP_WRITE);
                    return;
                }
                written_ = 0;
            }
            written = written_;
     
        }catch(java.nio.BufferOverflowException boe){
            close(TCPIOhandler.otherpeerclosed,true);
        }catch(Throwable t){
            close(t,true);
        }
    }*/

    /**
     * TODO: QA
     * @return
     * @throws IOException
     */
    private final boolean drainWriteQuee() throws IOException{
        ByteBuffer bb;
        boolean done = true;
        int writes = 0; //limit number of writes, maxtime spent here
        while(done && (bb=writeQueue.peek())!=null && ++writes < 50 ){
            final int a = bb.position();
            if (done = iohandler.write(bb)){
                writeQueue.remove();
            }
            written += bb.position() - a;
        }
        return done;
    }

    private final void doSelectThreadWrite(){
        try{ //TODO: QA ensure this logic works good. 
            if (drainWriteQuee()){
                if(bufferedSendBytes.addAndGet(-written) == 0){
                    iohandler.removeKeyInterest(SelectionKey.OP_WRITE);
                    return;
                }
                written = 0;
            }
        }catch(java.nio.BufferOverflowException boe){
            close(TCPIOhandler.otherpeerclosed,true);
        }catch(Throwable t){
            close(t,true);
        }
    }

    @Override
    public final void close(){
        close(new WebSocketPublicClosedMethod(),false);
    }

    @Override
    public final Throwable getClosedCause() {
        return closedCause;
    }

    public void close(Throwable cause) {
        close(cause,true);
    }

    /**
     * fires eventlistener.onClosed if readystate is not already closed
     * @param cause
     */
    private final void close(Throwable cause,boolean callerIsSelectThread){
        if (state.getAndSet(this,ReadyState.CLOSED) != ReadyState.CLOSED){
            if (cause == null){
                cause = new IllegalArgumentException(
                 "Throwable cause is null. thats a bug and must be fixed,"+
                        " the cause of closed state is now unknown.");
            }
            closedCause = cause;
            if (cause instanceof WebSocketWriteQueueOverflow){
                ctx.writeDataThrottledCounter.incrementAndGet();
            }
            if (needsLoging(cause) || needsLoging(cause.getCause())){
                logger.log(Level.SEVERE,"websocket abnormal close cause",cause);
            }
            if (callerIsSelectThread){
                if (ctx.doOnCloseEventsInThreadPool){
                    SelectThread.workers.execute(new Runnable() {
                        public void run() {
                            fireClosed();
                        }});
                }else{
                    fireClosed();
                }
                doclose();
                return;
            }
            selthread.tasksforSelectThread.offer(new Runnable(){
                public void run() {
                    doclose();
                }});
            //selthread.wakeUpSelector(); //channel.close can wait some ?
            fireClosed();
       }
    }

    private boolean needsLoging(Throwable t){
        return t instanceof Error || t instanceof RuntimeException ;
                //&& !(t instanceof java.nio.BufferOverflowException);
    }

    private final void doclose(){
        try {
            iohandler.close();
        }catch(IOException ie){ }
        catch (Throwable t){
            logger.log(Level.SEVERE,"WebSocket.doclose()",t);
        }
    }

    private final void fireClosed(){
        try{
            ctx.eventlistener.onClosed(this);
        }catch(Throwable t){
            handleListenerException(t);
        }
    }

    private final void handleListenerException(Throwable t){
        ctx.eventListenerExceptionCounter.incrementAndGet();
        logger.log(Level.SEVERE,"WebSocketLister failed",t);
    }

    @Override
    public String toString(){
        return getClass().getSimpleName()
        +(handshake!=null?" "+handshake:"")+"\r\n"
        +" READYSTATE: "+readystate+(readystate==readystate.CLOSED?(" cause: "+
           closedCause):"")+ " idletimeout current state:"+
           currentTimeOut/1000000000+" seconds"+"\r\n"
       +"parsed:"+parsed+" readbuffer:"+readBuffer+"\r\n"
       +"frametype:"+frametype+" framestart:"+framestart+" bindataend:"
                +bindataend+" bindatalength:"+bindatalength+"\r\n"
            /*+" idletimeout:"+idleTimeout+(idleTimeout!=
            WebSocketContext.TIMEOUTDISABLEDVALUE?" seconds:"+
            (idleTimeout/1000000000):"")+"\r\n"
        +" idleTimestamp:"+idleTimestamp
        +" handshaketimeout:"+handshakeTimeout+
                (handshakeTimeout!=WebSocketContext.TIMEOUTDISABLEDVALUE?
                    " seconds:"+(handshakeTimeout/1000000000):"")*/
        +" bufferedSendBytes:"+bufferedSendBytes.get()+
                " currentwrite:"+currentwrite+"\r\n"
        +" bufferedOnMessageRAMbytes:"+bufferedOnMessageRAMbytes+
                " readDataThrottledCounter:"+readDataThrottledCounter+"\r\n"
        +" iohandler: "+iohandler
        ;
    }

    /**
     * Would be nice if ByteBuffer had an indexOf method.
     * @param start
     * @param find
     * @param ba
     * @param limit
     * @return
     */
    protected final static int indexOf(int start,int find,byte[] ba,int limit){
        for (int i=start;i<limit;i++){
            if (ba[i] == find)
                return i;
        }
        return -1;
    }

    public final class WebSocketPublicClosedMethod extends Exception{
    }

    public final class WebSocketSendInvalidFrameException extends Exception{
        public WebSocketSendInvalidFrameException(String msg) {
            super(msg);
        }
    }

    public final class WebSocketWriteQueueOverflow extends Nostacktrace{
        public final long buffsize, limit;
        public WebSocketWriteQueueOverflow(long buffsize, int limit) {
            this.buffsize = buffsize<0?buffsize+(1L<<32):buffsize;
            this.limit = limit;
        }
        @Override
        public String getMessage() {
            return buffsize+" bytes > "+limit;
        }
    }

    public final class WebSocketClientSentTooLargeFrame extends Nostacktrace{
        public final int length,limit;
        public WebSocketClientSentTooLargeFrame(int length, int limit) {
            this.length = length;
            this.limit = limit;
        }
        @Override
        public String getMessage() {
            return length+">"+limit;
        }
    }

    public final class BadWebSocketFrameTypeFromClientException
            extends Nostacktrace{
        public final byte frametypebyte;
        public BadWebSocketFrameTypeFromClientException(byte frametype) {
            frametypebyte = frametype;
        }
        @Override
        public String getMessage() {
            return frametypebyte+" != "+TEXTTYPE+" or "+BINARYTYPE;
        }
    }

    public final class TimedOutException extends Nostacktrace{
        public final int idleMillisec;
        public TimedOutException(long timestamp) {
            idleMillisec =(int)
                ((-((idleTimestamp+currentTimeOut)-timestamp)+currentTimeOut)
                    /1000000);
        }
        @Override
        public String getMessage() {
            return idleMillisec +" milliSec idle";
        }
    }

    protected static class Nostacktrace extends Exception{
        public Nostacktrace(){
        }
        public Nostacktrace(String msg) {
            super(msg);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    protected final static class IOExceptionWrap extends IOException{
        public IOExceptionWrap(Throwable t) {
            initCause(t);
        }
    }

    @SuppressWarnings("unchecked")
    final class MySwapList<T> {
        T[] producerItems = (T[]) new Object[8];
        int producedCounter;

        T[] consumerItems = (T[]) new Object[8];
        int consumerCounter = 1;
        int consumerDatalength;

        public MySwapList() {
        }

        public final synchronized boolean add(T obj){
            if (++producedCounter == producerItems.length){
                T[] list2 = (T[])new Object[producerItems.length*2];
                System.arraycopy(producerItems,0,list2 ,0,producerItems.length);
                producerItems = list2;
            }
            producerItems[producedCounter] = obj;
            return true;
        }

        public final synchronized void setProducerfirstElement(T obj){
            producerItems[0] = obj;
        }

        final synchronized T[] swaplists(){
            final T[] list = producerItems;
            producerItems = consumerItems;
            consumerDatalength = producedCounter;
            producedCounter = 0;
            return list;
        }

        public final void remove() {
            consumerItems[consumerCounter++] = null;
        }

        public final T peek(){
            int i = consumerCounter;
            if (i > consumerDatalength){
                if ( consumerItems.length>32){
                    consumerItems = (T[]) new Object[32];
                }                                
                consumerCounter = i = 
                        (consumerItems = swaplists())[0] != null ? 0 : 1;
            }
            return consumerItems[i];
        }
    }

 /* @SuppressWarnings("unchecked")
    final class MySwapList<T> {
        T[] producerItems = (T[]) new Object[8];
        int producedCounter;

        T[] consumerItems = (T[]) new Object[8];
        int consumerCounter;
        int consumerDatalength;

        public MySwapList() {
        }

        public final synchronized boolean add(T obj){
            if (producedCounter >= producerItems.length){
                T[] list2 = (T[])new Object[producerItems.length*2];
                System.arraycopy(producerItems,0,list2 ,0,producerItems.length);
                producerItems = list2;
            }
            producerItems[producedCounter++] = obj;
            return true;
        }

        // ugly test code for now.
        public final synchronized void setProducerfirstElement(T obj){
            if (producedCounter > 0 && producedCounter < producerItems.length){
                System.arraycopy(producerItems,0,producerItems ,1,producedCounter);
            }else
            if (producedCounter == producerItems.length){
                T[] list2 = (T[])new Object[producerItems.length*2];
                System.arraycopy(producerItems,0,list2 ,1,producerItems.length);
                producerItems = list2;
            }
            producerItems[0] = obj;
        }

        final synchronized T[] swaplists(){
            final T[] list = producerItems;
            producerItems = consumerItems;
            consumerDatalength = producedCounter;
            producedCounter = 0;
            return list;
        }

        public final void remove() {
            consumerItems[consumerCounter++] = null;
        }

        public final T peek(){
            int i = consumerCounter;
            if (i >= consumerDatalength){
                if ( consumerItems.length>32){
                    consumerItems = (T[]) new Object[32];
                }
                consumerItems = swaplists();
                consumerCounter = i = 0;
            }
            return consumerItems[i];
        }
    }*/
}
