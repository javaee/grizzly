/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.rcm;

import com.sun.grizzly.Controller;
import com.sun.grizzly.Context;
import com.sun.grizzly.ControllerStateListener;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.util.ByteBufferInputStream;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.WorkerThread;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import junit.framework.TestCase;

/**
 * Basic RCM test.
 *
 * @author Jeanfrancois Arcand
 */
public class RCMTest extends TestCase implements ControllerStateListener {
    
    private CountDownLatch startLatch;
    private CountDownLatch stopLatch;
    
    private Controller controller;

    static int port = 18891;
    
    static String folder = ".";
    
    public RCMTest() {
    }
    
    
    /*
     * @see TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception{
        super.setUp();
        startLatch = new CountDownLatch(1);
        stopLatch = new CountDownLatch(1);
        
        controller = new Controller();
        final ResourceAllocationFilter parser = new ResourceAllocationFilter();
        
        TCPSelectorHandler handler = new TCPSelectorHandler();
        handler.setPort(port);
        controller.addSelectorHandler(handler);
        
        controller.setProtocolChainInstanceHandler(
                new DefaultProtocolChainInstanceHandler() {
            
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                
                if (protocolChain == null) {
                    protocolChain = new DefaultProtocolChain(){
                        
                        @Override
                        public void execute(Context ctx) throws Exception {
                            ByteBuffer byteBuffer =
                                    (ByteBuffer)ctx.getAttribute
                                    (ResourceAllocationFilter.BYTE_BUFFER);
                            // Switch ByteBuffer
                            if (byteBuffer != null){
                                ((WorkerThread)Thread.currentThread())
                                    .setByteBuffer(byteBuffer);
                            }
                            
                            if (protocolFilters.size() != 0){
                                int currentPosition = super.executeProtocolFilter(ctx);
                                super.postExecuteProtocolFilter(currentPosition, ctx);
                            }
                        }
                    };
                    protocolChain.addFilter(parser);
                    protocolChain.addFilter(new ProtocolFilter(){
                        /**
                         * <code>CharBuffer</code> used to store the HTML response, containing
                         * the headers and the body of the response.
                         */
                        private CharBuffer reponseBuffer = CharBuffer.allocate(4096);
                        
                        
                        /**
                         * Encoder used to encode the HTML response
                         */
                        private CharsetEncoder encoder =
                                Charset.forName("UTF-8").newEncoder();
                        
                        public boolean execute(Context ctx){
                            try{
                                ByteBufferInputStream inputStream = (ByteBufferInputStream)
                                        ctx.getAttribute(ResourceAllocationFilter.BYTEBUFFER_INPUTSTREAM);
                                final WorkerThread workerThread = ((WorkerThread)Thread.currentThread());
                                ByteBuffer bb = workerThread.getByteBuffer();
                                bb.flip();
                                inputStream.setByteBuffer(bb);
                                inputStream.setSelectionKey(ctx.getSelectionKey());
                                
                                byte[] requestBytes = new byte[8192];
                                inputStream.read(requestBytes);
                                SocketChannel socketChannel =
                                        (SocketChannel)ctx.getSelectionKey().channel();
                                
                                reponseBuffer.clear();
                                reponseBuffer.put("HTTP/1.1 200 OK\r\n");
                                appendHeaderValue("Content-Type", "text/html");
                                appendHeaderValue("Content-Length", 0 + "");
                                appendHeaderValue("Date", new Date().toString());
                                appendHeaderValue("RCMTest", "passed");
                                appendHeaderValue("Connection", "Close");
                                reponseBuffer.put("\r\n\r\n");
                                reponseBuffer.flip();
                                ByteBuffer rBuf = encoder.encode(reponseBuffer);
                                OutputWriter.flushChannel(socketChannel,rBuf);
                                ctx.removeAttribute(ResourceAllocationFilter.BYTEBUFFER_INPUTSTREAM);
                                ctx.removeAttribute(ResourceAllocationFilter.BYTE_BUFFER);
                                ctx.removeAttribute(ResourceAllocationFilter.INVOKE_NEXT);
                          } catch (Throwable t){
                                t.printStackTrace();
                                ctx.setAttribute(Context.THROWABLE,t);
                            }
                            ctx.setKeyRegistrationState(
                                    Context.KeyRegistrationState.CANCEL);
                            return true;
                        }
                        
  
                        /**
                         * Utility to add headers to the HTTP response.
                         */
                        private void appendHeaderValue(String name, String value) {
                            reponseBuffer.put(name);
                            reponseBuffer.put(": ");
                            reponseBuffer.put(value);
                            reponseBuffer.put("\r\n");
                        }
                        public boolean postExecute(Context ctx){
                            
                            return true;
                        }
                    });
                }
                return protocolChain;
            }
        });
        
        controller.addStateListener(this);
        new Thread(controller).start();
        
        try {
            startLatch.await();
        } catch (InterruptedException ex) {
        }
    }
    
    
    /*
     * @see TestCase#tearDown()
     */
    @Override
    protected void tearDown() throws Exception {
        controller.stop();
        try {
            stopLatch.await();
        } catch (InterruptedException ex) {
        }
    }

    public void testRCM(){
        try{
            Socket s = new Socket("127.0.0.1", port);
            OutputStream os = s.getOutputStream();
            s.setSoTimeout(10000);

            os.write(("GET /index.html HTTP/1.0\n").getBytes());
            os.write("\n".getBytes());

            InputStream is = s.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line = null;

            int index;
            while ((line = bis.readLine()) != null) {
                if (line.startsWith("RCMTest")){
                    assertTrue(true);
                    return;
                }
            }
        } catch (Throwable ex){
            System.out.println("Unable to connect to: " + port);
            ex.printStackTrace();
        }
        fail("Wrong header response");
    }


    public void onStarted() {
    }

    public void onReady() {
        startLatch.countDown();
    }

    public void onStopped() {
        stopLatch.countDown();
    }

    public void onException(Throwable e) {
        startLatch.countDown();
        stopLatch.countDown();
    }
    
    
}
