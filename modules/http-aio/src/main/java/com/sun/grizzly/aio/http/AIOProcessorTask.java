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

package com.sun.grizzly.aio.http;

import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.ActionCode;
import java.io.InputStream;
import java.io.OutputStream;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;

import com.sun.grizzly.util.InputReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * Process HTTP request. This class is based on
 * <code>com.sun.grizzly.tcp.http11.Http11Processor</code>
 *
 * @author Jean-Francois Arcand
 */
public class AIOProcessorTask extends ProcessorTask {

    private AsynchronousSocketChannel channel;

    public AIOProcessorTask(boolean init) {
        super(init);
    }

    public AIOProcessorTask(boolean init, boolean bufferResponse) {
        super(init, bufferResponse);
    }

    /**
     * Initialize the stream and the buffer used to parse the request.
     */
    @Override
    public void initialize() {
        started = true;
        request = new Request();

        response = new Response();
        response.setHook(this);

        inputBuffer = new InternalInputBuffer(request, requestBufferSize);
        outputBuffer = new AsyncSocketChannelOutputBuffer(response,
                maxHttpHeaderSize,
                asyncExecution);
        request.setInputBuffer(inputBuffer);

        response.setOutputBuffer(outputBuffer);
        request.setResponse(response);

        initializeFilters();
    }

    /**
     * Pre process the request by decoding the request line and the header.
     * @param input the InputStream to read bytes
     * @param output the OutputStream to write bytes
     */
    @Override
    public void preProcess(InputStream input, OutputStream output)
            throws Exception {

        // Make sure this object has been initialized.
        if (!started) {
            initialize();
        }
        // Setting up the I/O
        inputBuffer.setInputStream(input);
        inputStream = (InputReader) input;
        AsyncSocketChannelOutputBuffer channelOutputBuffer =
                ((AsyncSocketChannelOutputBuffer) outputBuffer);
        channelOutputBuffer.setChannel(channel);
        configPreProcess();
    }

    public AsynchronousSocketChannel getChannel() {
        return channel;
    }

    public void setChannel(AsynchronousSocketChannel channel) {
        this.channel = channel;
    }

    /**
     * Send an action to the connector.
     * 
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @SuppressWarnings("unchecked")
    @Override
    public void action(ActionCode actionCode, Object param) {
        InetAddress remote = null;
        InetAddress local = null;
        try {

            if (actionCode == ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE) {
                if (remoteAddr == null) {
                    remote = ((InetSocketAddress) channel.getRemoteAddress()).getAddress();
                    if (remote != null) {
                        remoteAddr = remote.getHostAddress();
                    }
                }
                request.remoteAddr().setString(remoteAddr);

            } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE) {

                if (localName == null) {
                    local = ((InetSocketAddress) channel.getLocalAddress()).getAddress();

                    if (local != null) {
                        localName = local.getHostName();
                    }
                }
                request.localName().setString(localName);

            } else if (actionCode == ActionCode.ACTION_REQ_HOST_ATTRIBUTE) {

                if ((remoteHost == null)) {
                    remote = ((InetSocketAddress) channel.getRemoteAddress()).getAddress();

                    if (remote != null) {
                        remoteHost = remote.getHostName();
                    }

                    if (remoteHost == null) {
                        if (remoteAddr != null) {
                            remoteHost = remoteAddr;
                        } else { // all we can do is punt
                            request.remoteHost().recycle();
                        }
                    }
                }
                request.remoteHost().setString(remoteHost);

            } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE) {

                if (localAddr == null) {
                    local = ((InetSocketAddress) channel.getLocalAddress()).getAddress();

                    localAddr = local.getHostAddress();
                }

                request.localAddr().setString(localAddr);

            } else if (actionCode == ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE) {

                if ((remotePort == -1)) {
                    remotePort = ((InetSocketAddress) channel.getRemoteAddress()).getPort();
                }
                request.setRemotePort(remotePort);

            } else if (actionCode == ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE) {

                if ((localPort == -1)) {
                    localPort = ((InetSocketAddress) channel.getLocalAddress()).getPort();
                }
                request.setLocalPort(localPort);

            }
        } catch (IOException ex) {

        }
        super.action(actionCode, param);
    }
}

