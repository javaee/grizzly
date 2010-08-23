/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.comet;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.grizzly.util.Utils;
import junit.framework.TestCase;


/**
 *
 * @author Gustav Trede
 */
public class CometUnitTest extends TestCase {
    private final int port = 19100;
    private SocketAddress connectadr;
    private final int socketreusedelayMilliSec = 40;
    private static volatile boolean status;
    private static volatile boolean testisdone;
    private SelectorThread st;
    private final String context = "/cometTextn";
    private final byte joinmessage = 126;
    private final byte[] connectstr=
                    ("POST /index.html/comet HTTP/1.1\r\n"+
                     "Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n"+
                     "Host: localhost\r\n"+
                     "Content-Length: 0\r\n\r\n").getBytes();

    public CometUnitTest(String testName) {
        super(testName);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (st != null)
            st.stopEndpoint();
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();                
        init(false);        
    }

    protected void init(boolean useconcurrentcomethandler) throws Exception{
        connectadr = new InetSocketAddress("localhost", port);
        RuntimeMXBean rmx = ManagementFactory.getRuntimeMXBean();
        Utils.dumpErr("JVM: "+rmx.getVmVendor()+" "+rmx.getVmName()+" "+rmx.getVmVersion()+" params: "+rmx.getInputArguments());
        st = new SelectorThread();
        st.setPort(port);
        st.setDisplayConfiguration(Utils.VERBOSE_TESTS);
        st.setAdapter(new CometTestAdapter(context,useconcurrentcomethandler,-1));
        st.setEnableAsyncExecution(true);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter());
        st.setAsyncHandler(asyncHandler);
        st.setTcpNoDelay(true);
        st.setLinger(-1);
         
        try {
            st.listen();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }


   /* public void testLongPollingSocketReuse() throws Exception{
        doActualLogic(true,false,40,20);
    }*/


  /*  public void testLongPollingNewSocket() throws Exception{
        doActualLogic(false,false,6500,64);
   }*/


    public void testStreaming1() throws Throwable{
        //doActualLogic(false,true,15,1,false);
    }

   /* public void testStreaming2() throws Throwable{
        doActualLogic(false,true,21,4, false);
    }

    public void testStreaming3() throws Throwable{
        doActualLogic(false,true,21,64, false);
    }*/
    
   /* public void testStreaming5() throws Throwable{
        doActualLogic(false,true, 15, 256);
    }*/

    protected void doActualLogic(final boolean socketreuse,final boolean streaming,
            final int secondspertest,final int threadcount, boolean spreadnotify) throws Throwable{
        Utils.dumpErr((streaming?"STREAMING-":"LONGPOLLING-")+(socketreuse?"SOCKETREUSE":"NEWSOCKET")+" client threads: "+threadcount+" spreadNotifyToManyThreads: "+spreadnotify);
       //int cpus = Runtime.getRuntime().availableProcessors();
         ((DefaultNotificationHandler)CometTestAdapter.cometContext.notificationHandler).
                 setSpreadNotifyToManyToThreads(spreadnotify);
        testisdone = false;
        msgc.set(0);
        CometTestAdapter.usetreaming = streaming;        
        final CountDownLatch threadsaredone = new CountDownLatch(threadcount);
        try{
            status = true;
            for (int i=0;i<threadcount;i++){
                final boolean first = false;
                new Thread("cometUnitTestClient"){
                    @Override
                    public void run(){
                        try {
                            connectClient(socketreuse,streaming, first);
                        } catch (Exception ex) {
                            if (!testisdone && status){
                                status = false; //can happen a few times due to not hreadsafe. but itt ok
                                ex.printStackTrace();
                            }
                        }finally{
                            threadsaredone.countDown();
                        }
                    }
                }.start();
            }
            
            Thread.currentThread().setPriority(Thread.currentThread().getPriority()+1);
            //NewDefaultThreadPool tp = (NewDefaultThreadPool)CometEngine.getEngine().threadPool;
            //ThreadPoolExecutor tp = (ThreadPoolExecutor)CometEngine.getEngine().threadPool;
            final long t0 = System.currentTimeMillis(); 
            long t1 = t0;
            int oldtotal = 0;
            final int waittime = 20;
            int eventbroadcasts = 900000/(threadcount*(1000/waittime));
            while(t1-t0 < secondspertest*1000 ){
                //int queuesize = tp.getQueuedTasksCount();
              //  int queuesize = tp.getQueue().size();
                int queuesize = 0;

                long t2 = System.currentTimeMillis();
                long deltatime = t2-t1;
                if (deltatime>4500){
                    t1 = t2;
                    int currenttotalmsg = msgc.get();
                    Utils.dumpErr(
                      "  K events/sec : "+((currenttotalmsg-oldtotal+500)/deltatime)+
                      "  comethandlers: "+CometTestAdapter.cometContext.handlers.size()+
                      "  workqueue: "+queuesize+
                      "  broadcastsper: "+eventbroadcasts
                    );
                    oldtotal = currenttotalmsg;
                }
                
                if (streaming){
                    
                    /*if (queuesize < (spreadnotify?threadcount:1)*300 ){
                        eventbroadcasts = (eventbroadcasts*5)/4;
                    }*/
                    if (queuesize < (spreadnotify?threadcount:1)*100){
                        for (int i=0;i<eventbroadcasts;i++){
                            CometTestAdapter.cometContext.notify(joinmessage);
                        }
                   }
                }else{
                    CometTestAdapter.cometContext.notify(joinmessage);
                }
                
                synchronized(connectstr){
                    connectstr.wait((waittime));
                }
            }
            testisdone = true;
            Utils.dumpErr("test is done. waiting for clients to die.");
            threadsaredone.await(6,TimeUnit.SECONDS);
            Utils.dumpErr("clients are done.");
            assertTrue(status);
        }catch(Throwable ea){
            throw ea;
        }
    }

    static AtomicInteger msgc = new AtomicInteger();

    protected void connectClient(final boolean socketreuse,final boolean streaming,boolean notifyBeforeRead) throws Exception {
        InputStream in = null;
        OutputStream out = null;
        Socket socket = null;
        final int deltaadd = 500;
        int msgcount = 0;
        try{
        while (!testisdone){
                if (socket == null){
                    socket = newSocket(5000);
                    out = socket.getOutputStream();
                    in = new BufferedInputStream(socket.getInputStream());
                }
                
                out.write(connectstr);
                out.flush();
                
                
                int b;
                if (notifyBeforeRead)
                    CometTestAdapter.cometContext.notify(joinmessage);
                while ((b =  in.read()) != joinmessage && !testisdone){
                    
                }

                if (!streaming)
                    msgc.getAndIncrement();
                else{
                    if (msgcount++==deltaadd){ //lowers thread contention
                        msgc.getAndAdd(deltaadd);
                        msgcount = 0;
                    }                    
                }
                //{
                    //if (b==joinmessage){

                        in.read();
                        in.read();
                        in.read();
                        in.read();
                        in.read();
                     boolean _status = true;
                
                while(streaming && _status && !testisdone){
                    if (notifyBeforeRead)
                        CometTestAdapter.cometContext.notify(joinmessage);
                    b = in.read();
                    in.read();
                    in.read();
                    in.read();
                    in.read();
                    in.read();
                    _status = (b == joinmessage);
                    if (_status && msgcount++==deltaadd){ //lowers thread contention
                        msgc.getAndAdd(deltaadd);
                        msgcount = 0;
                    }
                }

                if (!_status && !testisdone){
                    if (b==-1)
                        fail("server closed connection");
                    else
                        fail("client did not recieve expected message, got:'"+b+"'");
                }
                        
                if (!socketreuse){
                    socket.close();
                    socket = null;
                }
                if (!streaming && socketreusedelayMilliSec > 0){
                    Thread.sleep(socketreusedelayMilliSec);
                }
            }
        }finally{
            if (socket != null){
                socket.close();
            }
        }
    }

    private Socket newSocket(int timeout) throws Exception{
        Socket socket = new Socket();        
        socket.setReuseAddress(true);
        //socket.setReceiveBufferSize(2048);
        //socket.setSendBufferSize(512);
        socket.setSoLinger(false, 0);        
        socket.setSoTimeout(timeout);
        socket.setTcpNoDelay(true);
        socket.connect(connectadr);
        return socket;
    }
}
