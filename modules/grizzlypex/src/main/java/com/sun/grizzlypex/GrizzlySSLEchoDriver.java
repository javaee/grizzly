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

package com.sun.grizzlypex;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListener;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.SSLCallbackHandler;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.SSLConnectorHandler;
import com.sun.grizzly.SSLSelectorHandler;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.filter.SSLEchoFilter;
import com.sun.grizzly.filter.SSLReadFilter;
import com.sun.grizzlypex.utils.ControllerConfigurator;
import com.sun.grizzlypex.utils.ControllerUtils;
import com.sun.grizzlypex.utils.Utils;
import com.sun.japex.TestCase;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import com.sun.grizzly.Controller.Protocol;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;

/**
 * @author Alexey Stashok
 */
public class GrizzlySSLEchoDriver extends GrizzlyJapexDriverBase
        implements ControllerStateListener, SSLCallbackHandler<Context> {
    
    public static final int TIME_OUT = 30 * 1000;
    
    private AtomicBoolean isControllerStarted = new AtomicBoolean();
    private AtomicBoolean isControllerStopped = new AtomicBoolean();
    
    private Throwable controllerException;
    
    protected Controller controller;
    
    protected AtomicBoolean isConnected = new AtomicBoolean();
    protected AtomicBoolean isCompleted = new AtomicBoolean();
    
    protected SSLConnectorHandler connectorHandler;
    
    protected ByteBuffer sendData;
    protected ByteBuffer receivedData;
    
    private Random randomizer = new Random();
    
    protected SelectorHandler getSelectorHandler(TestCase testCase) {
        SSLSelectorHandler selectorHandler = new SSLSelectorHandler();
        selectorHandler.setPort(getPort(testCase));
        return selectorHandler;
    }
    
    protected Controller createController(TestCase testCase, SSLContext sslContext) {
        ControllerConfigurator controllerConfigurator = new ControllerConfigurator();
        controllerConfigurator.setReadThreadsCount(getReadThreadsCount(testCase));
        controllerConfigurator.addSelectorHandler(getSelectorHandler(testCase));
        SSLReadFilter sslReadFilter = new SSLReadFilter();
        sslReadFilter.setSSLContext(sslContext);
        controllerConfigurator.addProtocolFilter(sslReadFilter);
        controllerConfigurator.addProtocolFilter(new SSLEchoFilter());
        return controllerConfigurator.createController();
    }
    
    @Override
    public void prepare(TestCase testCase) {
        SSLConfig config = new SSLConfig();
        String customTrustStore = getParam("ssl.trustStore");
        String customKeyStore = getParam("ssl.keyStore");
        
        if (customTrustStore != null) {
            config.setTrustStoreFile(customTrustStore);
        }
        
        if (customKeyStore != null) {
            config.setKeyStoreFile(customKeyStore);
        }
        
        SSLContext sslContext = config.createSSLContext();
        
        controller = createController(testCase, sslContext);
        isControllerStarted.set(false);
        isControllerStopped.set(false);
        controllerException = null;
        controller.addStateListener(this);
        ControllerUtils.startController(controller);
        Utils.wait(this, TIME_OUT, isControllerStarted);
        
        if (controllerException != null || !isControllerStarted.get()) {
            if (!isControllerStarted.get()) {
                throw new IllegalStateException("Controller start timeout");
            }
            throw new IllegalStateException(controllerException);
        }
        
        connectorHandler = (SSLConnectorHandler) controller.acquireConnectorHandler(Protocol.TLS);
        connectorHandler.setSSLContext(sslContext);
        byte[] buffer = new byte[testCase.getIntParam("size")];
        randomizer.nextBytes(buffer);
        sendData = ByteBuffer.wrap(buffer);
        receivedData = ByteBuffer.allocate(connectorHandler.getApplicationBufferSize());
        
        try {
            isConnected.set(false);
            connectorHandler.connect(
                    new InetSocketAddress(getHost(testCase), getPort(testCase)), this);
            
            Utils.wait(this, TIME_OUT, isConnected);
            
            if (!connectorHandler.isConnected()) {
                throw new IllegalStateException("Connection timeout!");
            } else if (!connectorHandler.isHandshakeDone()) {
                throw new IllegalStateException("Handshake timeout!");
            }
            
        } catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    byte counter;
    long writeCounter;
    @Override
    public void run(TestCase testCase) {
        isCompleted.set(false);
        sendData.put(0, counter++);
        try {
            long written = connectorHandler.write(sendData, false);
            while(connectorHandler.read(receivedData, false) > 0);
            
            if (receivedData.position() < sendData.limit()) {
                Utils.wait(this, TIME_OUT, isCompleted);
            }
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            int sentCheck = sendData.get(0);
            int receivedCheck = receivedData.get(0);
            if (sentCheck != receivedCheck) {
                throw new AssertionError("Sent and received data don't match!\nSent[0]: " + 
                        sentCheck + " Received[0]: " + receivedCheck + 
                        "\n sentBuffer: " + sendData + " receivedBuffer: " + receivedData);
            }
            sendData.position(0);
            receivedData.position(0);
        }
    }

    @Override
    public void finish(TestCase testCase) {
        if (connectorHandler != null) {
            try {
                connectorHandler.close();
            } catch (IOException ex) {
            }
            
            controller.releaseConnectorHandler(connectorHandler);
            connectorHandler = null;
        }
        
        ControllerUtils.stopController(controller);
        controller = null;
        Utils.wait(this, TIME_OUT, isControllerStopped);
    }
    
////////////// ControllerStateListener ////////////////////////////
    public void onStarted() {
        //empty
    }
    
    public synchronized void onReady() {
        isControllerStarted.set(true);
        notifyAll();
    }
    
    public synchronized void onStopped() {
        isControllerStopped.set(true);
        notifyAll();
    }
    
    public synchronized void onException(Throwable throwable) {
        controllerException = throwable;
        isControllerStarted.set(true);
        isControllerStopped.set(true);
        notifyAll();
    }
    
////////////// CallbackHandler ////////////////////////////
    public synchronized void onConnect(IOEvent<Context> event) {
        connectorHandler.finishConnect(event.attachment().getSelectionKey());
        try {
            if (connectorHandler.handshake(receivedData, false)) {
                onHandshake(event);
            }
        } catch (Exception e) {
            e.printStackTrace();
            isConnected.set(true);
            notifyAll();
        }
    }
    
    public synchronized void onHandshake(IOEvent<Context> event) {
        event.attachment().getSelectionKey().interestOps(0);
        isConnected.set(true);
        notifyAll();
    }
    
    public void onRead(IOEvent<Context> event) {
        long readBytes = 0;
        try {
            readBytes = connectorHandler.read(receivedData, false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        SelectionKey key = event.attachment().getSelectionKey();
        if (receivedData.position() < sendData.limit()) {
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        } else {
            key.interestOps(0);
            synchronized(this) {
                isCompleted.set(true);
                notifyAll();
            }
        }
    }
    
    public void onWrite(IOEvent<Context> event) {
        try {
            long written = connectorHandler.write(sendData, false);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        if (sendData.hasRemaining()) {
            SelectionKey key = event.attachment().getSelectionKey();
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }
}
