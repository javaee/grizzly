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
import com.sun.grizzly.util.WorkerThreadImpl;
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
                        private final CharBuffer reponseBuffer = CharBuffer.allocate(4096);
                        
                        
                        /**
                         * Encoder used to encode the HTML response
                         */
                        private final CharsetEncoder encoder =
                                Charset.forName("UTF-8").newEncoder();
                        
                        public boolean execute(Context ctx){
                            try{
                                ByteBufferInputStream inputStream = (ByteBufferInputStream)
                                        ctx.getAttribute(ResourceAllocationFilter.BYTEBUFFER_INPUTSTREAM);
                                final WorkerThread workerThread = (WorkerThread)Thread.currentThread();
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
        new WorkerThreadImpl(controller).start();
        
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

            os.write("GET /index.html HTTP/1.0\n".getBytes());
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
            com.sun.grizzly.util.Utils.dumpOut("Unable to connect to: " + port);
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
