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
import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;


import com.sun.grizzly.util.DefaultThreadPool;
import java.io.OutputStream;
import java.io.IOException;

import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;

/**
 * Out of the box TCP (TLS optional) Server for using CustomProtocol Comunictaion Layer.
 *
 * @author John Vieten 16.09.2008
 * @version 1.0
 */
public abstract class CustomProtocolServer implements Server {
    private int port;
    private Controller controller;
    private MessageDispatcher serverDispatcher;
    private Controller.Protocol protocol;
    private SSLConfig sslConfig;

    protected CustomProtocolServer(int port) {
        this(port, null);
    }

    protected CustomProtocolServer(int port, SSLConfig sslConfig) {
        this.protocol = (sslConfig == null) ? Controller.Protocol.TCP : Controller.Protocol.TLS;
        this.port = port;
        this.sslConfig = sslConfig;
    }

    /**
     * Can be used to write to a specific connection.
     *
     * @param key SelectionKey
     * @return OutputStream
     */
    public OutputStream getOutputStream(final SelectionKey key) {

        return
                new ProtocolOutputStream(
                        Message.Message_Request,
                        false
                ) {
                    public void write(ByteBuffer b, AsyncWriteCallbackHandler callbackHandler) {
                        try {
                            if (protocol == Controller.Protocol.TLS) {
                                AsyncQueueDataProcessor preProcessor = SSLPreProcessor.fromSelectionKey(key);
                                controller.getSelectorHandler(protocol).
                                        getAsyncQueueWriter().write(key, b, callbackHandler, preProcessor);
                            } else {
                                controller.getSelectorHandler(protocol).
                                        getAsyncQueueWriter().write(key, b, callbackHandler);
                            }
                        } catch (IOException e) {
                            if (key.isValid()) {
                                callbackHandler.onException(e, null, b, null);
                            }
                        }
                    }
                };
    }

    public OutputStream getOutputStream(final Context ctx) {
        return
                new ProtocolOutputStream(
                        Message.Message_Request,
                        false
                ) {
                    public void write(ByteBuffer b, AsyncWriteCallbackHandler callback){
                        try {
                            if (CustomProtocolServer.this.protocol == Controller.Protocol.TLS) {
                                AsyncQueueDataProcessor preProcessor =
                                        SSLPreProcessor.fromSelectionKey(ctx.getSelectionKey());
                                ctx.getAsyncQueueWritable().writeToAsyncQueue(b, callback, preProcessor);
                            } else {
                                ctx.getAsyncQueueWritable().writeToAsyncQueue(b, callback);
                            }
                        } catch (IOException e) {
                            if (ctx.getSelectionKey().isValid()) {
                                callback.onException(e, null, b, null);
                            }
                        }
                    }
                };
    }


    /**
     * Starts this server.
     */
    public void start() {
        ReplyMessageFactory replyMessageFactory = new ReplyMessageFactory();
        controller = new Controller();
        DefaultThreadPool defp = new DefaultThreadPool();
        defp.setInitialByteBufferSize(Message.MessageMaxLength);
        controller.setThreadPool(defp);
        TCPSelectorHandler tcpSelectorHandler =
                (protocol == Controller.Protocol.TLS) ? new SSLSelectorHandler() : new TCPSelectorHandler();

        BaseSelectionKeyHandler keyHandler = new BaseSelectionKeyHandler();
        tcpSelectorHandler.setSelectionKeyHandler(keyHandler);
        tcpSelectorHandler.setPort(port);
        controller.addSelectorHandler(tcpSelectorHandler);
        final DefaultProtocolChain protocolChain = new DefaultProtocolChain();
        protocolChain.addFilter(CustomProtocolParser.createParserProtocolFilter(null, replyMessageFactory, sslConfig));
        serverDispatcher = new MessageDispatcher() {
            public void onMessageError(MessageError msg, Context ctx) {
                System.out.println("error");
            }

            public void onRequestMessage(RequestMessage msg, final Context ctx) {
                ProtocolOutputStream outputStream =
                        new ProtocolOutputStream(
                                Message.Message_Reply,
                                msg.getRequestId(),
                                msg.getSessionId(),
                                false) {
                            public void write(ByteBuffer b, AsyncWriteCallbackHandler callback){

                                try {
                                    if (CustomProtocolServer.this.protocol == Controller.Protocol.TLS) {
                                        AsyncQueueDataProcessor preProcessor =
                                                SSLPreProcessor.fromSelectionKey(ctx.getSelectionKey());
                                        ctx.getAsyncQueueWritable().writeToAsyncQueue(b, callback, preProcessor);
                                    } else {
                                        ctx.getAsyncQueueWritable().writeToAsyncQueue(b, callback);
                                    }
                                } catch (IOException e) {
                                    if (ctx.getSelectionKey().isValid()) {
                                        callback.onException(e, null, b, null);
                                    }
                                }

                            }
                        };


                service(msg.getInputStream(), outputStream, msg.getSessionId(), ctx);
            }
        };
        System.out.println("creteated new dispatcher");
        protocolChain.addFilter(serverDispatcher);
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
        System.out.print("Server : Starting server on port :" + port);
        CustomProtocolHelper.startController(controller);
        if (sslConfig != null) {
            ((SSLConnectorHandler) controller.acquireConnectorHandler(protocol)).configure(sslConfig);
            System.out.println(" SSL Mode");
        } else {
            System.out.println();
        }
    }

    /**
     * Stops this server
     */
    public void stop() {
        serverDispatcher.stop();
        CustomProtocolHelper.stopController(controller);
        System.out.println("Server : Stopping server on port :" + port);
    }
}
