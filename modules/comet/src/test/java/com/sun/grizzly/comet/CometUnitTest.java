/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.comet;

import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.StatsThreadPool;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;


/**
 *
 * @author Gustav Trede
 */
public class CometUnitTest extends TestCase {
    private final int port = 19000;
    private SocketAddress connectadr;
    private final int socketreusedelayMilliSec = 0;
    private static volatile boolean status;
    private static volatile boolean testisdone;
    private SelectorThread st;
    private final String context = "/cometText";
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
    protected void setUp() throws Exception {
        super.setUp();
        init(false);
    }

    protected void init(boolean useconcurrentcomethandler) throws Exception{
        connectadr = new InetSocketAddress("localhost", port);
        RuntimeMXBean rmx = ManagementFactory.getRuntimeMXBean();
        System.err.println("JVM: "+rmx.getVmVendor()+" "+rmx.getVmName()+" "+rmx.getVmVersion()+" params: "+rmx.getInputArguments());
        st = new SelectorThread();
        st.setPort(port);
        st.setDisplayConfiguration(true);
        st.setAdapter(new CometTestAdapter(context,useconcurrentcomethandler,-1));
        st.setEnableAsyncExecution(true);
        AsyncHandler asyncHandler = new DefaultAsyncHandler();
        asyncHandler.addAsyncFilter(new CometAsyncFilter());
        st.setAsyncHandler(asyncHandler);
        st.setTcpNoDelay(true);
        st.setLinger(-1);
        /*st.setThreadPool(  new StatsThreadPool(16,
                    32, 50,
                    StatsThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT,
                    TimeUnit.MILLISECONDS));*/
        try {
            st.listen();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }


    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (st != null)
            st.stopEndpoint();
    }

   /* public void testLongPollingSocketReuse() throws Exception{
        doActualLogic(true,false,40,20);
    }*/


   /* public void testLongPollingNewSocket() throws Exception{
        doActualLogic(false,false,64,5);
    }
*/


    public void testStreaming2() throws Exception{
        doActualLogic(false,true,10,4);
    }

    protected void doActualLogic(final boolean socketreuse,final boolean streaming,
            final int secondspertest,final int threadcount) throws Exception{
        System.err.println(streaming?"STREAMING-":"LONGPOLLING-"+(socketreuse?"SOCKETREUSE":"NEWSOCKET")+" client threads: "+threadcount);
       //int cpus = Runtime.getRuntime().availableProcessors();
        testisdone = false;
        msgc.set(0);
        CometTestAdapter.usetreaming = streaming;
        status = true;
        final CountDownLatch threadsaredone = new CountDownLatch(threadcount);
        try{
            for (int i=0;i<threadcount;i++){
                new Thread("cometUnitTestClient"){
                    @Override
                    public void run(){
                        try {
                            connectClient(socketreuse,streaming);
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
            ThreadPoolExecutor cometexecutor = (ThreadPoolExecutor)CometEngine.getEngine().threadPool;
            final long t0 = System.currentTimeMillis(); 
            long t1 = t0;
            int oldtotal = 0;
            int eventbroadcasts = 100;
            while(t1-t0 < secondspertest*1000 ){
                long t2 = System.currentTimeMillis();
                long deltatime = t2-t1;
                if (deltatime>2300){
                    t1 = t2;
                    int currenttotalmsg = msgc.get();
                    System.err.println(
                      "  events/sec : "+((currenttotalmsg-oldtotal)*1000/deltatime)+
                      "  comethandlers: "+CometTestAdapter.cometContext.handlers.size()+
                      "  cometWorkqueue: "+cometexecutor.getQueue().size()
                    );
                    oldtotal = currenttotalmsg;
                }
                int queuesize = cometexecutor.getQueue().size();
                if (queuesize < 10000){
                    eventbroadcasts = (eventbroadcasts*5)/4;
                }
                if (queuesize < 30000){
                    for (int i=0;i<eventbroadcasts;i++){
                        CometTestAdapter.cometContext.notify(joinmessage);
                    }
                }
                synchronized(connectstr){
                    connectstr.wait(10);
                }
            }
            testisdone = true;
            threadsaredone.await(12,TimeUnit.SECONDS);
        }catch(Exception ea){
            status = false;
            throw ea;
        }finally {
            if (status)
                assertTrue(true);
            else
                fail("error");
        }
    }

    static AtomicInteger msgc = new AtomicInteger();

    protected void connectClient(final boolean socketreuse,final boolean streaming) throws Exception {
        InputStream in = null;
        OutputStream out = null;
        Socket socket = null;
        int msgcount = 0;
        try{
        while (!testisdone){
                if (socket == null){
                    socket = newSocket(10000);
                    out = socket.getOutputStream();
                    in = new BufferedInputStream(socket.getInputStream());
                }
                
                out.write(connectstr);
                out.flush();

                boolean _status = false;
                int b;
                while ((b =  in.read()) != joinmessage && !testisdone){
                    
                }
                
                //{
                    //if (b==joinmessage){
                        if (msgcount++==10){ //lowers thread contention
                            msgc.getAndAdd(10);
                            msgcount = 0;
                        }
                        in.read();
                        in.read();
                        in.read();
                        in.read();
                        in.read();
                        _status = true;
                        //break;
                    //}
               // }
                
                while(streaming && _status){
                    b = in.read();
                    in.read();
                    in.read();
                    in.read();
                    in.read();
                    in.read();
                    _status = (b == joinmessage);
                    if (_status && msgcount++==10){ //lowers thread contention
                        msgc.getAndAdd(10);
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
        socket.setReuseAddress(false);
        socket.setReceiveBufferSize(8192);
        socket.setSendBufferSize(1024);
        socket.setSoLinger(false, 0);        
        socket.setSoTimeout(timeout);
        socket.connect(connectadr);
        return socket;
    }
}