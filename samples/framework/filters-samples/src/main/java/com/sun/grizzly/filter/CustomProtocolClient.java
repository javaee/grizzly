/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filter;

import com.sun.grizzly.*;
import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.async.AsyncQueueWritable;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;


import com.sun.grizzly.util.DefaultThreadPool;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;

import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;

import static com.sun.grizzly.filter.CustomProtocolHelper.logger;
import static com.sun.grizzly.filter.CustomProtocolHelper.log;

import javax.net.ssl.SSLEngine;

/**
 * Out of the box TCP/TLS Client for using CustomProtocol Comunictaion Layer.
 * An usage example would be:
 * <p><pre><code>
 * CustomProtocolClient client = new CustomProtocolClient();
 * client.start();
 * client.connect(new InetSocketAddress("localhost", 8087));
 * <p/>
 * <p/>
 * </code></pre></p>
 * <p/>
 * With {@link CustomProtocolClient#start} a {@link DefaultProtocolChain} is build which
 * listens and forwards Read Events to the CustomProtocol Layer. The read bytes get internally parsed to
 * Messages. If the Message is an Message Reply the bytes get tranfered to the
 * {@link com.sun.grizzly.filter.RemoteInputStream} the client is waiting on.
 * <p/>
 * If the Message is an server broadcast the bytes are handed of as an {@link java.io.InputStream) to
 * {@link CustomProtocolClient#service(java.io.InputStream, java.io.OutputStream)}. Therefore
 * a user should override {@link CustomProtocolClient#service(java.io.InputStream, java.io.OutputStream)}
 * to process any broadcast messages.
 *
 * @author John Vieten 28.06.2008
 * @version 1.0
 */
public class CustomProtocolClient implements Client {
    private Controller controller;
    private ConnectorHandler connectorHandler;
    private TCPSelectorHandler selectorHandler;
    private DefaultProtocolChain protocolChain = new DefaultProtocolChain();
    private List<ProtocolFilter> beforeParserList = new ArrayList<ProtocolFilter>();
    private List<ProtocolFilter> afterParserList = new ArrayList<ProtocolFilter>();
    private BytesTrafficListener bytesArrivedListener;
    private AtomicInteger requestId = new AtomicInteger();
    private int session = 0;
    private boolean gzip;
    private MessageDispatcher dispatcher;
    private IOExceptionHandler ioExceptionHandler;
    private ReplyMessageFactory replyInputStreamFactory;
    private SSLConfig sslConfig;
    private Controller.Protocol protocol;
    private int minWorkerThreads = 5;
    private int maxWorkerThreads = 20;


    public CustomProtocolClient() {
    }

    /**
     * Configures Client to use TLS
     *
     * @param sslConfig if null no TLS will be configured
     */
    public CustomProtocolClient(SSLConfig sslConfig) {
        this.sslConfig = sslConfig;
        if (sslConfig == null) {
            this.protocol = Controller.Protocol.TCP;
        } else {
            this.protocol = Controller.Protocol.TLS;
        }
    }

    /**
     * If bytes send to server are gzipped
     *
     * @return zipped
     */
    public boolean isGzip() {
        return gzip;
    }

    /**
     * If bytes send to server should be gzipped
     *
     * @param gzip true if they should
     */
    public void setGzip(boolean gzip) {
        this.gzip = gzip;
    }


    public void setBytesArrivedListener(BytesTrafficListener bytesArrivedListener) {
        this.bytesArrivedListener = bytesArrivedListener;
    }

    /**
     * Giving the {@link com.sun.grizzly.TCPConnectorHandler} to the Client
     */
    private ConnectorHandler getConnectorHandler() {
        if (connectorHandler == null) {
            connectorHandler = controller.acquireConnectorHandler(protocol);
        }

        return connectorHandler;
    }

    private SSLConnectorHandler getSSLConnectorHandler() {
        return (SSLConnectorHandler) getConnectorHandler();
    }


    /**
     * Set up the Grizzly Worker Threads.
     *
     * @param minWorkerThreads min count of grizzly workers
     * @param maxWorkerThreads max count of grizzly workers
     */
    public void setThreadSizes(int minWorkerThreads, int maxWorkerThreads) {
        this.minWorkerThreads = minWorkerThreads;
        this.maxWorkerThreads = maxWorkerThreads;
    }

    /**
     * Users can add addtional ProtocolFilters to the Reading Chain.
     * These filter here will be called before  or after the  CustomProtocol Filters will execute
     *
     * @param protocolFilter Filter for chain
     * @param beforeParser   before or after
     */
    public void addProtocolFilter(ProtocolFilter protocolFilter, boolean beforeParser) {
        if (beforeParser) {
            beforeParserList.add(protocolFilter);
        } else {
            afterParserList.add(protocolFilter);
        }
    }

    /**
     * Adds a Filter  before the  CustomProtocol Filters will execute.
     *
     * @param protocolFilter Filter for chain
     */
    public void addProtocolFilter(ProtocolFilter protocolFilter) {
        addProtocolFilter(protocolFilter, true);
    }

    /**
     * Setups Grizzly Controller and Components and starts Grizzly.
     *
     * @throws Exception Exception
     */
    public void start() throws Exception {

        controller = new Controller();
        replyInputStreamFactory = new ReplyMessageFactory();

        DefaultThreadPool defp = new DefaultThreadPool();
        defp.setCorePoolSize(minWorkerThreads);
        defp.setMaximumPoolSize(maxWorkerThreads);
        defp.setInitialByteBufferSize(com.sun.grizzly.filter.Message.MessageMaxLength);
        controller.setThreadPool(defp);

        selectorHandler = (protocol == Controller.Protocol.TLS) ? new SSLSelectorHandler(true) : new TCPSelectorHandler(true);
        BaseSelectionKeyHandler keyHandler = new BaseSelectionKeyHandler();
        selectorHandler.setSelectionKeyHandler(keyHandler);
        controller.addSelectorHandler(selectorHandler);

        for (ProtocolFilter protocolFilter : beforeParserList) {
            protocolChain.addFilter(protocolFilter);
        }


        protocolChain.addFilter(CustomProtocolParser.createParserProtocolFilter(
                bytesArrivedListener,
                replyInputStreamFactory,
                sslConfig));

        dispatcher = new MessageDispatcher() {
            public void onMessageError(MessageError msg, Context ctx) {
                CustomProtocolClient.this.onMessageError(msg.getMessage());
            }

            public void onRequestMessage(RequestMessage msg, Context ctx) {
                ProtocolOutputStream outputStream =
                        new ProtocolOutputStream(
                                Message.Message_Request,
                                msg.getRequestId(),
                                msg.getSessionId(),
                                false) {
                            public void write(ByteBuffer b, AsyncWriteCallbackHandler callback){
                                try {
                                    ((AsyncQueueWritable) getConnectorHandler()).writeToAsyncQueue(b, callback);
                                } catch (IOException e) {
                                    if (getConnectorHandler().getUnderlyingChannel().isOpen()) {
                                        callback.onException(e, null, b, null);
                                    } else {
                                        // do nothing because chanel is cloased
                                    }
                                }
                            }
                        };
                outputStream.setTrafficListener(bytesArrivedListener);
                outputStream.setExceptionHandler(ioExceptionHandler);
                service(msg.getInputStream(), outputStream);
            }
        };
        protocolChain.addFilter(dispatcher);

        for (ProtocolFilter protocolFilter : afterParserList) {
            protocolChain.addFilter(protocolFilter);
        }
        protocolChain.setContinuousExecution(true);

        controller.setProtocolChainInstanceHandler(
                new ProtocolChainInstanceHandler() {
                    public ProtocolChain poll() {
                        return protocolChain;
                    }

                    public boolean offer(ProtocolChain protocolChain) {
                        return false;

                    }
                });

        ProtocolChainInstanceHandler pciHandler = new DefaultProtocolChainInstanceHandler() {

            public ProtocolChain poll() {
                return protocolChain;
            }

            public boolean offer(ProtocolChain protocolChain) {
                return false;
            }
        };

        controller.setProtocolChainInstanceHandler(pciHandler);
        CustomProtocolHelper.startController(controller);
        if (sslConfig != null) {
            getSSLConnectorHandler().configure(sslConfig);
        }

    }


    /**
     * Stops the Grizzly Framework
     *
     * @throws Exception Exception
     */
    public void stop() throws Exception {
        if (dispatcher != null) dispatcher.stop();
        CustomProtocolHelper.stopController(controller);

    }

    /**
     * Connects to an Server.
     * Method  {@link #start} must have been called before.
     *
     * @param address host and port
     * @throws IOException Exception
     */

    public void connect(InetSocketAddress address) throws IOException {
        CountDownLatch waitLatch = new CountDownLatch(1);
        getConnectorHandler().connect(address, getCallbackHandler(waitLatch), selectorHandler);
        try {
            waitLatch.await();
        } catch (InterruptedException e) {
        }
    }

    public void connect(InetSocketAddress address,
                        InetSocketAddress proxy,
                        String userAgent,
                        String userName,
                        String pass) throws IOException {

        CountDownLatch proxyHandshakeDone = new CountDownLatch(1);
        ProxyCallbackHandler proxyHandler = new ProxyCallbackHandler(
                userName != null,
                getCallbackHandler(null),
                getConnectorHandler(),
                proxyHandshakeDone,
                address.getHostName(),
                address.getPort(),
                userAgent,
                userName,
                pass) {

            public void onException(String msg, Exception e) {
                logger().log(Level.SEVERE, msg, e);
            }
        };


        getConnectorHandler().connect(proxy, proxyHandler, selectorHandler);
        try {
            proxyHandshakeDone.await();
        } catch (Exception e) {
            logger().log(Level.SEVERE, "Timeout in wait", e);
        }
        if (!proxyHandler.wasHandshakeSuccessfull()) {
            throw new IOException(proxyHandler.getHandshakeException().getMessage());
        }

    }


    /**
     * Gets the Default Callbackhandler uses by {@link #connect}
     * An usecase would be a Proxy CallbackHandler which delegates to this CallbackHandler
     *
     * @return CallbackHandler  default
     */
    private CallbackHandler getCallbackHandler(final CountDownLatch waitLatch) {
        CallbackHandler handler = null;
        switch (protocol) {
            case TLS:
                handler = new SSLCallbackHandler<Context>() {
                    public void onConnect(IOEvent<Context> ioEvent) {
                        SelectionKey k = ioEvent.attachment().getSelectionKey();
                        try {
                            getConnectorHandler().finishConnect(k);
                            log("finishConnect");
                            final ByteBuffer readBB = ByteBuffer.allocate(9000);
                            boolean ready = ((SSLConnectorHandler) getConnectorHandler()).handshake(readBB, false);
                            if (ready) {
                                onHandshake(ioEvent);
                            }
                        } catch (java.io.IOException ex) {
                            ioExceptionHandler.handle(ex);
                            return;
                        }
                        catch (Throwable ex) {
                            logger().log(Level.SEVERE, "onConnect", ex);
                        }

                    }


                    public void onRead(IOEvent<Context> ioEvent) {
                        try {
                            Context ctx = ioEvent.attachment();
                            SelectionKey key = ctx.getSelectionKey();
                            if (!key.isValid()) {
                                log("onRead() key not valid");
                                return;
                            }
                            key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));

                            ctx.getProtocolChain().execute(ioEvent.attachment());

                        } catch (Throwable e) {
                            CustomProtocolHelper.logger().log(Level.SEVERE, "onRead", e);
                        }
                    }

                    public void onWrite(IOEvent<Context> ioEvent) {
                        System.out.println("on write");
                    }

                    /**
                     * Only gets called if SSL Mode
                     * @param ioEvent
                     */
                    public void onHandshake(IOEvent<Context> ioEvent) {
                        Context ctx = ioEvent.attachment();
                        SelectionKey key = ctx.getSelectionKey();
                        SSLEngine sslEngine = ((SSLConnectorHandler) getConnectorHandler()).getSSLEngine();
                        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
                        workerThread.setSSLEngine(sslEngine);
                        ThreadAttachment attachment = workerThread.updateAttachment(ThreadAttachment.Mode.SSL_ENGINE);
                        key.attach(attachment);
                        ioEvent.attachment().getSelectorHandler().register(key, SelectionKey.OP_READ);
                        if (waitLatch != null) {
                            waitLatch.countDown();
                        }
                    }
                };
                break;

            case TCP:
                handler = new CallbackHandler<Context>() {
                    public void onConnect(IOEvent<Context> ioEvent) {
                        try {
                            SelectionKey k = ioEvent.attachment().getSelectionKey();
                            try {
                                getConnectorHandler().finishConnect(k);
                                log("finishConnect");
                            } catch (java.io.IOException ex) {
                                ioExceptionHandler.handle(ex);
                                return;
                            }
                            catch (Throwable ex) {
                                logger().log(Level.SEVERE, "onConnect", ex);
                                return;
                            }
                            ioEvent.attachment().getSelectorHandler().register(k, SelectionKey.OP_READ);
                        } finally {
                            if (waitLatch != null) {
                                waitLatch.countDown();
                            }
                        }

                    }


                    public void onRead(IOEvent<Context> ioEvent) {
                        try {
                            Context ctx = ioEvent.attachment();
                            SelectionKey key = ctx.getSelectionKey();
                            if (!key.isValid()) {
                                log("onRead() key not valid");
                                return;
                            }
                            key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
                            ctx.getProtocolChain().execute(ioEvent.attachment());
                        } catch (Throwable e) {
                            CustomProtocolHelper.logger().log(Level.SEVERE, "onRead", e);
                        }
                    }

                    public void onWrite(IOEvent<Context> ioEvent) {

                    }
                };
        }
        return handler;
    }

    /**
     * Sends data to server. The caller receives an InputStream with
     * which the server streams an reply back to the client.
     *
     * @return an RequestReplyHolder that blocks until data arrives
     * @throws IOException ex
     */
    public RemoteCall callRemote() throws IOException {
        final int id = getNextRequestId();
        final ReplyMessage message = replyInputStreamFactory.createReplyMessage(id);
        final RemoteInputStream future = (RemoteInputStream) message.getInputStream();
        final OutputStream outputStream = getRequestOutputStream(id, future);
        RemoteCall holder = new RemoteCall();
        holder.setInputStream(future).setOutputStream(outputStream);
        return holder;

    }


    /**
     * Every message send to an server can have a session id attached so that
     * server can keep track of its clients.
     *
     * @param session an id
     */

    public void setSession(int session) {
        this.session = session;
    }


    public OutputStream getOutputStream() {
        return getRequestOutputStream(getNextRequestId(), null);
    }

    private OutputStream getRequestOutputStream(final int requestId, final RemoteInputStream inputStream) {
        ProtocolOutputStream p = new ProtocolOutputStream(
                Message.Message_Request,
                requestId,
                session,
                isGzip()) {
            public void write(ByteBuffer b, AsyncWriteCallbackHandler callbackHandler) {
                try {
                    ((AsyncQueueWritable) getConnectorHandler()).writeToAsyncQueue(b, callbackHandler);
                } catch (IOException e) {
                    if (getConnectorHandler().getUnderlyingChannel().isOpen()) {
                        callbackHandler.onException(e, null, b, null);
                    } else {
                        // do nothing because chanel is cloased
                    }
                }


            }
        };
        p.setInputStream(inputStream);
        p.setTrafficListener(bytesArrivedListener);
        p.setExceptionHandler(ioExceptionHandler);
        return p;

    }

    /**
     * An User should override {@link CustomProtocolClient#service(java.io.InputStream, java.io.OutputStream)}
     * to process bytes that are send from an Server.
     *
     * @param inputStream  bytes send from server
     * @param outputStream reply to server
     */
    public void service(InputStream inputStream, OutputStream outputStream) {

    }

    public void onMessageError(String errorMsg) {
        CustomProtocolHelper.logger().log(Level.SEVERE, "onMessageError() " + errorMsg);
    }

    /**
     * Every Request Message gets an unique request id so that replies can be assigned back to
     * their requests.
     *
     * @return unique id
     */
    private int getNextRequestId() {
        return requestId.incrementAndGet();
    }

    public void setIoExceptionHandler(IOExceptionHandler ioExceptionHandler) {
        this.ioExceptionHandler = ioExceptionHandler;
    }
}
