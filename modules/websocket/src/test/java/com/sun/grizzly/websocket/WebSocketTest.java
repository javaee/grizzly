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

import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.TemporaryInterceptor;
import com.sun.grizzly.ssl.SSLSelectorThread;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.AbstractThreadPool;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.net.SSLSupport;
import com.sun.grizzly.util.net.jsse.JSSEImplementation;
import com.sun.grizzly.websocket.WebSocketImpl.WebSocketPublicClosedMethod;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import junit.framework.TestCase;

/**
 *
 * @author gustav trede
 */
public class WebSocketTest  extends TestCase{

    private final int port = 18890;
    private SelectorThread st;
    private volatile CountDownLatch latch;
    private final Throwable empty = new Throwable()
        { @Override public String toString() { return ""; }};
    private final AtomicReference<Throwable> firstFault = new AtomicReference<Throwable>();    
    private final AtomicInteger clientsglobalrec = new AtomicInteger();
    private final AtomicInteger allCientsincomingDataWasThrottledCounter = new AtomicInteger();
    private final AtomicInteger allCientsWriteThrottledCounter = new AtomicInteger();
    private volatile int longestidle; 
    private boolean loglifecycle;
    private final int cpus = Runtime.getRuntime().availableProcessors();    
    private boolean wantTimeOutException;
    protected static volatile boolean shouldDie;

    private SSLConfig sslConfig;
    private SSLContext sslctx;

    public WebSocketTest(){
    }

    public WebSocketTest(String testName) {
        super(testName);
    }

   @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        sslctx = null;
        sslConfig = null;
        clearthreadpoolQueue();
        WebSocketContext.getAll().clear();
        if (st != null)
            st.stopEndpoint();
    }

    @SuppressWarnings("deprecation")
    private void clearthreadpoolQueue(){
        SelectThread.workers.getQueue().clear();
    }
    
    private void init(boolean ssl) throws Exception{        
        ProcessorTask.temphack = new HttpInterceptor();
        firstFault.set(empty);
        clientsglobalrec.set(0);
        allCientsincomingDataWasThrottledCounter.set(0);
        allCientsWriteThrottledCounter.set(0);
        wantTimeOutException = false;
        loglifecycle = false;
        RuntimeMXBean rmx = ManagementFactory.getRuntimeMXBean();
        System.err.println("JVM: "+rmx.getVmVendor()+" "+rmx.getVmName()+" "+rmx.getVmVersion()+
                " JVM mem: "+Runtime.getRuntime().totalMemory()/(1<<20)+" Mbyte"+
                " params: "+rmx.getInputArguments());
        if (ssl){
            initSSLconfig();
            SSLSelectorThread sslst = new SSLSelectorThread();
            sslst.setSSLImplementation(new JSSEImplementation());
            //sslst.setSelectorReadThreadsCount(selectorReadThreadsCount);
            //sslst.setAlgorithmClassName(SSLEchoStreamAlgorithm.class.getName());
            sslst.setSSLConfig(sslConfig);
            sslctx = sslConfig.createSSLContext();
            st = sslst;
        }else{
            st = new SelectorThread();
        }

        st.setPort(port);
        st.setDisplayConfiguration(false);
        st.setAdapter(new GrizzlyAdapter() {
            public void service(GrizzlyRequest request,GrizzlyResponse response){
                fail("websocket upgrade logic failed, ordinary " +
                        "adapter.service was used to handle the request.");
            }});
        st.setEnableAsyncExecution(false);
       // st.setAsyncHandler(new DefaultAsyncHandler());
        st.setTcpNoDelay(true);
        st.setLinger(-1);
        st.listen();
    }

    class HttpInterceptor extends TemporaryInterceptor {
        @Override
        public boolean checkForUpgrade(Request request) {
            return "WebSocket".equals(request.getHeader("Upgrade"));
        }
        @Override
        public boolean doUpgrade(SelectionKey key, Request request, SSLSupport sslSupport) {
            Request req = request;
                String resource = req.requestURI().toString();
                WebSocketContext ct=WebSocketContext.getWebScocketContext(resource);
            if (ct != null){
                /*String protocol = req.getHeader("WebSocket-Protocol");
                if (protocol != null && !protocol.equals(wsc.getProtocol())){
                    protocol = null;
                }*/
                String origin = req.getHeader("origin");
                String host   = req.getHeader("Host");
                if (origin != null && host != null){
                    WorkerThread wt  = (WorkerThread) Thread.currentThread();
                    SocketChannel channel = (SocketChannel)key.channel();
                    TCPIOhandler ioh = sslSupport == null ?
                        new TCPIOhandler(channel) :
                        new SSLIOhandler(channel,wt.getSSLEngine());
                    String location = null; //TODO fix location
                    //TODO: use hostname+port from server logic instead ?
                    WebSocketImpl.doOPen(
                            new WebsocketServerHandshake(origin,location,host,ct),
                            ioh, ct);
                    key.attach(WebSocketImpl.dummyAttachment);
                    //TODO: fix so not shares lock with grizzly selector:
                    key.cancel();
                    if (sslSupport != null){
                        wt.setSSLEngine(null);
                    }
                    return false;
                }
            }
            return true;
        }
    };

    private void initSSLconfig() throws Exception{
        sslConfig = new SSLConfig();
        ClassLoader cl = getClass().getClassLoader();
        URL cacertsUrl = cl.getResource("ssltest-cacerts.jks");
        String trustStoreFile = new File(cacertsUrl.toURI()).getAbsolutePath();
        if (cacertsUrl != null) {
            sslConfig.setTrustStoreFile(trustStoreFile);
            sslConfig.setTrustStorePass("changeit");
        }
        System.out.println("SSL certs path: " + trustStoreFile);
        URL keystoreUrl = cl.getResource("ssltest-keystore.jks");
        String keyStoreFile = new File(keystoreUrl.toURI()).getAbsolutePath();
        if (keystoreUrl != null) {
            sslConfig.setKeyStoreFile(keyStoreFile);
            sslConfig.setKeyStorePass("changeit");
        }
        System.out.println("SSL keystore path: " + keyStoreFile);
        SSLConfig.DEFAULT_CONFIG = sslConfig;
    }
   
    public void testIdleTimeoutLowLoadSSL() throws Exception{
        init(true);
        doIdleTimeout(512, 1, 5);
        //System.err.println(System.getProperty("jsse.SSLEngine.acceptLargeFragments"));
    }   

    public void testChatsSSL() throws Exception{
        doTestChats(true);
    }

    public void testChatsPlain() throws Exception{
        doTestChats(false);
    }

    private void doTestChats(boolean useSSL) throws Exception{
        SelectThread.getStatistics(true);
        init(useSSL);
        final int shortTestSeconds = 3;
        final int longTestSeconds  = 30;
        int m = 8;
        // test here is in low perf mode now ((,
        // had to temporarly disable real world like chat tests
        // inorder to more easily  ensure stable self balanced load.
        
        int[][] v  = new int[][]{            
            { 1   , 16   ,m   ,shortTestSeconds },
            { 16  , 4    ,m   ,shortTestSeconds },
            { 4   , 16   ,m   ,shortTestSeconds },
            { 16  , 16   ,m   ,shortTestSeconds },
            { 1   , 64   ,m   ,shortTestSeconds },
            { 8   , 64   ,m   ,shortTestSeconds }
            
            // disabled heavier tests to unit test dont fail on weak systems :
            
           //,{ 8192, 2    ,8   ,longTestSeconds  },
            //{ 1024, 8    ,8   ,longTestSeconds  },
            //{ 1   , 8192 ,8   ,longTestSeconds  }
        };
        for (int[] iv:v){
            doOnetestChat(iv[0],iv[1],iv[3],iv[2]);
        }
    }

    private void doOnetestChat(final int numberofchats,final int clientsPerChat,
            final int chatTimeSec, final int msgRepeats) throws Exception{
        WebSocketContext.getAll().clear();
        shouldDie = false;
        ServerChatListener[] sli = new ServerChatListener[numberofchats];
        final CountDownLatch cd = new CountDownLatch(numberofchats*clientsPerChat);
        for (int i=0;i < numberofchats;i++){
            sli[i] = new ServerChatListener(clientsPerChat);
            String chat = "chat"+i;
            addContext(chat,sli[i],true).setDataFrameLimits(1024, 1<<10, 10);
            for (int x=1;x<=clientsPerChat;x++){
                if (i*clientsPerChat+x > 2000){
                  Thread.sleep(10);
                }
                WebSocket.open((sslctx==null?"ws":"wss")+"://localhost:"+port+"/"+chat,
                new ClientChatListener( x==clientsPerChat ? msgRepeats : 0,cd ),
                    null,null,sslctx);
            }
        }
        Thread.sleep(chatTimeSec*1000);
        shouldDie = true;        
        double tp = 0;
        for (ServerChatListener sl:sli){
            tp += sl.shutdown();
        }
        clearthreadpoolQueue();
        if (!cd.await(2, TimeUnit.SECONDS)){
            Throwable t = firstFault.getAndSet(empty);
            if (t!=empty){
                t.printStackTrace();
            }
            fail("clientclose latch timedout: count:"+cd.getCount()+" "+firstFault.getAndSet(empty) );
        }
        System.err.println("Throughput: "+((int)(tp+0.5))/1000d+" M chat msgs/sec."+
                " SSL:"+(sslctx!=null)+" readthrottles:"+
                allCientsincomingDataWasThrottledCounter.getAndSet(0)+" writethrottles:"+
                allCientsWriteThrottledCounter.getAndAdd(0)
                +" "+SelectThread.getStatistics(true));
        Throwable t = firstFault.getAndSet(empty);
        if (t != empty ){
            throw (Exception) t;
        }
    }

    private class ClientChatListener implements WebSocketListener{
        private final int msgRepeats;
        private final CountDownLatch deathlatch;
        public ClientChatListener(int msgRepeats,CountDownLatch deathlatch) {
            this.msgRepeats = msgRepeats;
            this.deathlatch = deathlatch;
        }

        public void onOpen(WebSocket wsoc) {     
        }

        public void onMessage(WebSocket wsoc, DataFrame dataframe) {
            if (msgRepeats>0 
                    && wsoc.getBufferedOnMessageBytes() == 0
                    && wsoc.getBufferedSendBytes() == 0
                    ){
                for (int i=0;i<msgRepeats;i++){
                    wsoc.send(dataframe);
                }            
            }
        }
        
        public void onClosed(WebSocket wsoc) {
            allCientsincomingDataWasThrottledCounter.addAndGet(wsoc.getWebSocketContext().readDataThrottledCounter.getAndSet(0));
            allCientsWriteThrottledCounter.addAndGet(wsoc.getWebSocketContext().writeDataThrottledCounter.getAndSet(0));
            if (!shouldDie){
                failInsideWebSocket(wsoc.getClosedCause());
            }
            deathlatch.countDown();
        }
    }
   
    private class ServerChatListener implements WebSocketListener{
        private final ConcurrentHashMap<WebSocket,Boolean> clients;
        private final int wantedclients;
        private final AtomicInteger msgc = new AtomicInteger();
        private volatile long t1;

        public ServerChatListener(int clients) {
            this.clients = new ConcurrentHashMap<WebSocket, Boolean>(clients);
            this.wantedclients = clients;
        }

        public void onOpen(WebSocket webcon) {
            clients.put(webcon, Boolean.TRUE);
            if (clients.size() == wantedclients){
                t1 = System.currentTimeMillis();
                try {
                    onMessage(null, new DataFrame("Hello blablablablabla here, hows it going ?."));
                } catch (CharacterCodingException ex) {
                    failInsideWebSocket(ex);
                }
            }            
        }

        public void onMessage(WebSocket wsoc, DataFrame dataframe) {
            //if (wsoc == null || wsoc.getBufferedSendBytes() < 1000  ){
                msgc.addAndGet(1+wantedclients);
                for (WebSocket wso:clients.keySet()){
                    //if (wso != webcon){
                        wso.send(dataframe);
                }
            //}
        }

        public double shutdown(){
            double ret = (double)msgc.get()/(System.currentTimeMillis() - t1);
            WebSocket wso = null;
            //int apa = 0;
            for (WebSocket wsoc:clients.keySet()){
                wsoc.close();
                //apa+= ((WebSocketImpl)wsoc).apa;
                wso = wsoc;
            }
            //if (apa != 0)
               // System.err.println(apa);
            clients.clear();
            if (wso != null){
                allCientsincomingDataWasThrottledCounter.addAndGet(wso.
                      getWebSocketContext().readDataThrottledCounter.getAndSet(0));
                allCientsWriteThrottledCounter.addAndGet(wso.
                      getWebSocketContext().writeDataThrottledCounter.getAndSet(0));
            }
            return ret;
        }

        public void onClosed(WebSocket webcon) {            
            if (!shouldDie){
                failInsideWebSocket(webcon.getClosedCause());
            }
        }
    }

  
   /* public void testar() throws Exception{
        init(true);
        dotestFrames(8, (1<<13)+0, (1<<13)+0, 40000, 0);
        dotestFrames(32, (1<<1)+1865, (1<<1)+1865, 120000, 0);
    }*/

/*
   public void testMyperfregressionSSL() throws Exception{
          // 2 selector threads and 64K sobuff in openssolaris
          ///ew FixedThreadPool(
             //    40*Runtime.getRuntime().availableProcessors(),
               // new LinkedTransferQueue<Runnable>(),
        //argLine=-XX:TLABSize=2M -XX:-ResizeTLAB -XX:ParallelGCThreads=4 -XX:-PrintGCDetails -d64 -XX:BiasedLockingStartupDelay=0 -XX:+DisableExplicitGC -XX:CompileThreshold=1000 -XX:+AggressiveOpts -XX:-DontCompileHugeMethods -Xmx2G -Xms2G

        init(true);
        int[][] tv = new int[][]{
                                  { 64 , 1<<1,  1<<1,  1<<16, 60000 },
                                  { 256, 1<<1,  1<<1,  1<<14, 31500 },
                                  { 512, 1<<1,  1<<1,  1<<13, 30000 },
                                  { 64 , 1<<12, 1<<12, 1<<14, 72000 },
                                  { 256, 1<<12, 1<<12, 1<<12, 35000 },
                                  { 512, 1<<12, 1<<12, 1<<12, 33500 },
                                  { 64 , 1<<14, 1<<14, 1<<13, 26200 },
                                  { 256, 1<<14, 1<<14, 1<<11, 27000 },
                                  { 512, 1<<14, 1<<14, 1<<10, 27000 }
                                                                      };
        final int iter = 1;

        for (int[] v:tv){
            for (int i=0;i<iter;i++){
                dotestFrames(v[0],v[1],v[2],v[3],v[4]);
            }
        }
    }*/
    
  /*  public void testMyperfregression() throws Exception{
          // 2 selector threads and 64K sobuff in openssolaris
          ///ew FixedThreadPool(
             //    40*Runtime.getRuntime().availableProcessors(),
               // new LinkedTransferQueue<Runnable>(),        
        //argLine=-XX:TLABSize=2M -XX:-ResizeTLAB -XX:ParallelGCThreads=4 -XX:-PrintGCDetails -d64 -XX:BiasedLockingStartupDelay=0 -XX:+DisableExplicitGC -XX:CompileThreshold=1000 -XX:+AggressiveOpts -XX:-DontCompileHugeMethods -Xmx2G -Xms2G
        
        init(false);
        int[][] tv = new int[][]{
                                 // { 64 , 1<<1,  1<<1,  1<<18, 20000 },
                                //  { 512, 1<<1,  1<<1,  1<<15, 17000 },

                                  { 64 , 100,  100,  1<<18, 21500 },
                                  { 512, 100,  100,  1<<15, 26000 },
                                  
                                  { 64 , 1<<12, 1<<12, 1<<16, 17000 },
                                  { 512, 1<<12, 1<<12, 1<<13, 18000 },
                                 

                                  { 64 , 1<<14, 1<<14, 1<<14, 12200 },
                                  { 512, 1<<14, 1<<14, 1<<11, 14500 }
                                                                      };
        final int iter = 2;
        
        for (int[] v:tv){
            for (int i=0;i<iter;i++){
                dotestFrames(v[0],v[1],v[2],v[3],v[4]);
            }
        }
    }*/

  /*  public void testMixedLoadPatternsPLAINTCP() throws Exception{
        dotestMixedLoadPatterns(false,8,64);
    }

    public void testMixedLoadPatternsSSL() throws Exception{
        dotestMixedLoadPatterns(true,8,64);
    }*/

    public void dotestMixedLoadPatterns(boolean ssl,int minc, int maxc) throws Exception{
        init(ssl);
        dota(minc);
        if (minc != maxc)
            dota(maxc);
    }

    private void dota(int c) throws Exception{
        for (int i=3;i<=16;i++){
            dotestFrames(c, 1<<i, 1<<i, 1, 0);
        }
        int i = 1<<16;
        dotestFrames(c,   2, 2,   i, 0);
        dotestFrames(c,   3, 3,   i, 0);
        dotestFrames(c, 129, 129, i, 0);
    }
    
 

    public int dotestFrames(int clients,int minframelangth, int maxframelength,
            int maxframes, int targettime) throws Exception{
        String ctxname = "a";
        ServerListener sli = new ServerListener();
        WebSocketContext ctx = addContext(ctxname,sli,true);
        //ctx.initialReadBufferLength = (1<<24);
        ctx.setDataFrameLimits(maxframelength, maxframes, 2*maxframes);
       // loglifecycle =true;
        clearthreadpoolQueue();
        int fa = 0;
        long t1 = System.nanoTime();
        for (int i=minframelangth;i<=maxframelength;i<<=1){
           // if (i>4)
              //  fa=1;
            //TODO:reuse connections for inner loop.
            for (int x=i-fa;x<=i;x++){
                int msgs=ctx.dataFrameSendQueueLimitBytes/(x);
                String ti;
                //ti = startAndwait(clients,1+(msgs>>2),x, false, false,ctxname,40);
                ti = startAndwait(clients,msgs   ,x, true,ctxname,300,targettime);
                System.err.println("ping-pong total "+msgs*clients+" frames a "+x+
                        " bytes using "+clients+" clients "+ti);
            }
        }
        WebSocketContext.remove("/"+ctxname);
        return (int) ((System.nanoTime()-t1)/1000000);
    }
    
    private void doIdleTimeout(int clientcount, int idletimeout,int latchextratime) throws Exception{
        wantTimeOutException = true;
        longestidle = 0;
        for (int a=0;a<1;a++){
            String ctxname = "apa";
            latch = new CountDownLatch(clientcount);
            WebSocketListener sli = new WebSocketListener() {
                public void onOpen(WebSocket webcon) {}
                public void onMessage(WebSocket webcon, DataFrame dataframe){}
                public void onClosed(WebSocket webcon) {
                    Throwable r = webcon.getClosedCause();
                    if (r instanceof WebSocketImpl.TimedOutException){
                        int idle = ((WebSocketImpl.TimedOutException)r)
                                .idleMillisec;
                        while(longestidle < idle ) //if multiple sector threads.
                            longestidle = idle;                        
                    }else{
                        failInsideWebSocket(r);
                    }
                    latch.countDown();
                }};
            WebSocketContext serverctx = addContext(ctxname,sli,true);
            serverctx.setIdleTimeoutSeconds(idletimeout);            
            serverctx.setHandshakeTimeoutSeconds(-1);
            startAndwait(clientcount,0,2,false,ctxname,idletimeout+latchextratime,0);
            WebSocketContext.remove("/"+ctxname);            
        }
        System.err.println(" sockets:"+2*clientcount+" longest idle: "+
                longestidle/1000d +" diff from optimal: "+
                (longestidle/1000d -idletimeout)+ " seconds ");
    }

    private WebSocketContext addContext(String ctxname,WebSocketListener wsl,
            boolean allEventsInthreadPool) throws IOException{
        WebSocketContext ctx = 
                WebSocketContext.create("/"+ctxname, null,wsl,false,false);        
        ctx.doOnCloseEventsInThreadPool   = allEventsInthreadPool;
        return ctx;
    }

    private String startAndwait(final int ccount,final int fcount,int msgsize,boolean binary
            ,String ctx,int timeout, int targettime) throws Exception{
        latch = new CountDownLatch(ccount);
        //allCientsincomingDataWasThrottledCounter.set(0);
        long t0 = System.nanoTime();
        WebSocketContext serverctx =
                startClients(ccount,fcount,msgsize,binary,ctx);        
        long t1 = System.nanoTime();
        final int wanted = fcount*ccount;
        String err = "";
        if (!latch.await(timeout,TimeUnit.SECONDS)){
            err = "latchtimeout at count:"+latch.getCount();
        }
        long t2 = System.nanoTime();
        int value = clientsglobalrec.getAndSet(0);
        if (value != wanted && wanted!=0 ){
            err += " frames "+value+" != "+wanted+
                   " framelength "+msgsize+(binary?" binary":" text")+
                   " sockets "+ccount*2;
        }        
        int ssfl = serverctx.getEventListenerExceptionCounter();
        if (ssfl != 0){
            err += " failed serverside evenmethods "+ssfl;
        }
        Throwable t = firstFault.get();
        if (t != empty){
            t.printStackTrace();
            t = new Exception(err+getThrottleInfo(serverctx),t);
            throw new AssertionError(t);
        }
        if (err.length()>0){
            fail(err+getThrottleInfo(serverctx));
        }        
        int msec = (int) ((t2-t1)/1000000);
        return "connect "+(t1-t0)/1000000+" ms, postcon "+
                msec/1000d+" sec."+getThrottleInfo(serverctx)+(targettime==0?"":(msec+" TARGETTIME: "+targettime));
    }

    private String getThrottleInfo(WebSocketContext serverctx){
        int rf = serverctx.readDataThrottledCounter.getAndSet(0);
        return " readthrotle(srv: "+rf+
               " clients: "+allCientsincomingDataWasThrottledCounter.getAndSet(0)+")"+
               " writethrotle:srv: "+serverctx.writeDataThrottledCounter.getAndSet(0)+" ";
    }

    private WebSocketContext startClients(int ccount,int reclimit,int msgsize,
            boolean binary,String cta) throws IOException{
        WebSocketContext cs = WebSocketContext.getWebScocketContext("/"+cta);
        while(ccount-->0){
           WebSocketImpl.openClient((sslctx==null?"ws":"wss")+"://localhost:"+port+"/"+cta,
                   null,null,
                   new ClientListener(reclimit,msgsize,binary),
                   sslctx,cs);
        }
        return cs;
    }

    private class ClientListener implements WebSocketListener{
        private final int framelength;                    
        private final AtomicInteger  remainingFramesTorecieve = new AtomicInteger();
        private final int framestorecieve;
        private final boolean binary;
        private final AtomicBoolean started = new AtomicBoolean();      
        private final CountDownLatch latch_ = latch;

        public ClientListener(int reclimit, int framelength,boolean binary) {
            this.remainingFramesTorecieve.set(reclimit);
            this.framestorecieve = reclimit;
            this.framelength  = framelength;
            this.binary = binary;
        }

        public void onOpen(WebSocket webcon) {
            if (!started.compareAndSet(false, true)){
                failInsideWebSocket("clientlistener.onOpen called more then once.");
                webcon.close();
                return;
            }
            if (framelength<2){
                failInsideWebSocket("too small framelength in test config:"+framelength);
                webcon.close();
                return;
            }            
            ByteBuffer bb;
            if (binary){                                
                int lengthHeader = 1;                
                int cacledlength;
                int payload;
                do{                                        
                    payload = framelength - (1+lengthHeader);
                    int length = payload;
                    lengthHeader = 1;                    
                    while((length>>=7) >0){
                        lengthHeader++;
                    }
                }while ((cacledlength=1+lengthHeader+payload) > framelength);
                if (cacledlength != framelength){
                    failInsideWebSocket("screwed up binaryle"
                         +"ngth header calculation for a defined frame length:"+
                        cacledlength+" != "+framelength+"(expected)");
                    webcon.close();
                    return;
                }
                bb = ByteBuffer.allocate(framelength);
                bb.put(0,WebSocketImpl.BINARYTYPE);                
                int i = 1;
                int k = 0x80;
                while (--lengthHeader>=0){
                    if (lengthHeader == 0)
                        k = 0;
                    bb.put(i++,(byte) (((payload>>(lengthHeader*7))&0x7f)|k));
                    //System.err.println(bb.get(i-1)+" k: "+k);
                }
                if (framelength>2)
                   bb.put(i,(byte)0x82); // dummy byte
            }else{
                bb = ByteBuffer.allocate(framelength);
                bb.put(0,WebSocketImpl.TEXTTYPE);
                bb.put(framelength-1, WebSocketImpl.TEXTterminate);
                if (framelength>2)
                    bb.put(1,(byte)0x82); // dummy byte right after frame byte
            }
            if (loglifecycle)
               System.err.println(webcon+" sending:"+bb+" count:"+ remainingFramesTorecieve.get());
            int g =  remainingFramesTorecieve.get();
            while(g-->0){
                if (!webcon.send(bb)){
                    return;
                    //failInsideWebSocket("client failed initial sends");
                    //latch.countDown();
                }
            }
        }

        public void onMessage(WebSocket webcon, DataFrame message) {
            if (loglifecycle)
                System.err.println(" client onMessage "+webcon+" msg: "+message+" remaining msg to get: "+(remainingFramesTorecieve.get()-1));
            if (message.rawFrameData.capacity() != framelength){
                failInsideWebSocket("onMessage wrong framelength:"+message.rawFrameData+" expected:"+framelength);
                webcon.close();
                return;
            }
            int gd = remainingFramesTorecieve.decrementAndGet();
           // if (gd>=0)
                clientsglobalrec.incrementAndGet();
            if (gd==0){
                webcon.close();
            }
        }

        public void onClosed(WebSocket webcon) {
            allCientsincomingDataWasThrottledCounter.addAndGet(((WebSocketImpl)webcon).readDataThrottledCounter);
            int rem = remainingFramesTorecieve.get();
            if (rem!=0){
                failInsideWebSocket(webcon,"onClossed missed frames:"+rem+" of "+framestorecieve);
            }
            Throwable t = webcon.getClosedCause();
            if (!(t instanceof WebSocketPublicClosedMethod) && !wantTimeOutException
                 || (wantTimeOutException &&  !(t instanceof IOException))
                 || (t instanceof SSLException)){
                failInsideWebSocket(t);
            }
           // if (((WebSocketImpl)webcon).readsdone > 0 )
             //   System.err.println(" ***readsdoneC: "+((WebSocketImpl)webcon).readsdone);
            if (!wantTimeOutException){
                latch_.countDown();
            }
        }
    }
    
    private class ServerListener implements WebSocketListener{
        public void onOpen(WebSocket webcon) {
            if (loglifecycle)
                System.err.println(webcon);
        }

        public void onMessage(WebSocket webcon, DataFrame message) {
            if (loglifecycle)
                System.err.println("server onm: "+message);
            webcon.send(message);
        }

        public void onClosed(WebSocket webcon) {
           // if (((WebSocketImpl)webcon).readsdone > 0 )
             //   System.err.println(" ***readsdoneS: "+((WebSocketImpl)webcon).readsdone);
            Throwable t = webcon.getClosedCause();
            //if (!(t == TCPIOhandler.otherpeerclosed) && !t.getMessage().equals("Connection reset by peer"))
                //    t.printStackTrace();
            if (!(t instanceof IOException) || (t instanceof SSLException)){
                failInsideWebSocket(t);
            }
        }
    }

    private void failInsideWebSocket(WebSocket websoc,String s){
        failInsideWebSocket(new Exception(s,websoc.getClosedCause()));
    }
    private void failInsideWebSocket(String s){
        failInsideWebSocket(new Exception(s));
    }
    private void failInsideWebSocket(Throwable t){
       if (firstFault.compareAndSet(empty, t)){
           t.printStackTrace();
       }
    }



      /*  String host1 =  gnu.inet.encoding.IDNA.toASCII("www.f�rgbolaget.nu");
        System.err.println("* "+host1);
        String host =  gnu.inet.encoding.IDNA.toASCII("f�rgbolaget",true,true);
                 host= "www."+host+".nu";
              System.err.println("* "+host);
          SocketChannel ss = SocketChannel.open(new InetSocketAddress(host,80));
                System.err.println(ss);
                ByteBuffer bb = ByteBuffer.wrap("apan".getBytes());
                System.err.println(ss.write(bb));    fail();*/

/*
    public static SSLContext getSSLcontext(boolean getfromjar,String filename, String pwd) throws Exception {
        KeyStore ks  = KeyStore.getInstance("JKS");
        if (getfromjar)
            ks.load("".getClass().getResourceAsStream("/keystore_client_pc"), pwd.toCharArray());
        else
            ks.load(new FileInputStream(filename), pwd.toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, pwd.toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), getTrustManager(ks).getTrustManagers(), null);
        return ctx;
    }

    private static TrustManagerFactory getTrustManager(KeyStore ks) throws Exception   {
        CertPathParameters pkixParams = new PKIXBuilderParameters(ks, new X509CertSelector());
        ManagerFactoryParameters trustParams = new CertPathTrustManagerParameters(pkixParams);
        TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
        factory.init(trustParams);
        return factory;
    }*/
}