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

package com.sun.grizzly.aio;

import com.sun.grizzly.ConnectorHandler;
import com.sun.grizzly.ContextTask;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.State;
import com.sun.grizzly.util.StateHolder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.NetworkChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Server side TCP support build on top of NIO.2 (AIO).
 * 
 * @author Jeanfrancois Arcand
 */
public class TCPAIOHandler implements AIOHandler {

    private AsynchronousChannelGroup asyncChannelGroup;
    
    /**
     * The socket tcpDelay.
     * 
     * Default value for tcpNoDelay is disabled (set to true).
     */
    protected boolean tcpNoDelay = true;
    /**
     * The socket reuseAddress
     */
    protected boolean reuseAddress = true;
    /**
     * The socket linger.
     */
    protected int linger = -1;
    
    
    // timeout for read and writes.
    public final static long TIMEOUT_IN_SECONDS = 30;
    
    
    private long timeout = TIMEOUT_IN_SECONDS;

    
    /**
     * Server socket backlog.
     */
    protected int ssBackLog = 4096;

    
    private AsynchronousServerSocketChannel listener;


    /**
     * Attributes, associated with the {@link AIOHandler} instance
     */
    protected Map<String, Object> attributes;


    private int port = 8080;


    private AIOHandlerRunner aioHandlerRunner;


    private Controller controller;


    final CountDownLatch latch = new CountDownLatch(1);


    /**
     * This {@link AIOHandler} StateHolder, which is shared among
     * AIOHandler and its clones
     */
    protected StateHolder<State> stateHolder = new StateHolder<State>(true);


    /**
     * The {@link ExecutorService} used by this instance. If null -
     * {@link Controller}'s {@link ExecutorService} will be used
     */
    protected ExecutorService threadPool;


    /**
     * The ProtocolChainInstanceHandler used by this instance. If not set, and instance
     * of the DefaultInstanceHandler will be created.
     */
    protected ProtocolChainInstanceHandler instanceHandler;


    /**
     * Flag, which shows whether shutdown was called for this {@link AIOHandler}
     */
    protected AtomicBoolean isShutDown = new AtomicBoolean(false);

    public TCPAIOHandler(Controller controller) {
        this.controller = controller;
    }

    /**
     * A token decribing the protocol supported by an implementation of this
     * interface
     */
    public Controller.Protocol protocol() {
        return Controller.Protocol.TCP;
    }

    public void initialize() throws IOException {
        listener = AsynchronousServerSocketChannel.open(
                asyncChannelGroup).bind(new InetSocketAddress(port),ssBackLog);
    }


    public void start() {
        try {
            isShutDown.set(false);
            initialize();
            // accept connections
            listener.accept(null,
                    new CompletionHandler<AsynchronousSocketChannel, Void>() {

                        public void completed(final AsynchronousSocketChannel channel, Void attachment) {

                            try {
                                ProtocolChain protocolChain = controller.getProtocolChainInstanceHandler().poll();
                                AIOContext context = (AIOContext) controller.pollContext();
                                context.setAIOHandler(TCPAIOHandler.this);
                                context.setChannel(channel);
                                context.setProtocolChain(protocolChain);
                                context.setAttribute("timeout", timeout);
                                configureChannel(channel);

                                ContextTask ct = new AIOContextTask();
                                ct.setContext(context);
                                context.execute(ct,false);

                                listener.accept(null, this);
                            } catch (Throwable ex) {
                                Controller.logger().log(Level.SEVERE, "completed", ex);
                            }
                        }

                        public void failed(Throwable t, Void attachment) {
                           if (Controller.logger().isLoggable(Level.FINEST)){
                                Controller.logger().log(Level.FINEST,
                                        "Pending Connection failed", attachment);
                            }
                        }

                        public void cancelled(Void attachment) {
                            if (Controller.logger().isLoggable(Level.FINEST)){
                                Controller.logger().log(Level.FINEST,
                                        "Pending Connection cancelled", attachment);
                            }
                        }
                    });
        } catch (Throwable t) {
            Controller.logger().log(Level.SEVERE, "Accept()", t);
        }
    }

    public AIOHandlerRunner getAioHandlerRunner() {
        return aioHandlerRunner;
    }

    public void setAioHandlerRunner(AIOHandlerRunner aioHandlerRunner) {
        this.aioHandlerRunner = aioHandlerRunner;
    }

    /**
     * {@inheritDoc}
     */
    public void configureChannel(AsynchronousSocketChannel channel) throws IOException {
        if (linger >= 0) {
            channel.setOption(StandardSocketOption.SO_LINGER, linger);
        }

        channel.setOption(StandardSocketOption.TCP_NODELAY, tcpNoDelay);
        channel.setOption(StandardSocketOption.SO_REUSEADDR, reuseAddress);
    }

    /**
     * Closes {@link NetworkChannel}
     */
    public void closeChannel(NetworkChannel channel) throws IOException {
        channel.close();
    }

    /**
     * Set the <code>ProtocolChainInstanceHandler</code> to use for
     * creating instance of <code>ProtocolChain</code>.
     */
    public void setProtocolChainInstanceHandler(ProtocolChainInstanceHandler instanceHandler) {
        this.instanceHandler = instanceHandler;
    }

    /**
     * Return the <code>ProtocolChainInstanceHandler</code>
     */
    public ProtocolChainInstanceHandler getProtocolChainInstanceHandler() {
        return instanceHandler;
    }

    /**
     * {@inheritDoc}
     */
    public ExecutorService getThreadPool() {
        return threadPool;
    }

    /**
     * {@inheritDoc}
     */
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * {@inheritDoc}
     */
    public void pause() {
        stateHolder.setState(State.PAUSED);
    }

    /**
     * {@inheritDoc}
     */
    public void resume() {
        if (!State.PAUSED.equals(stateHolder.getState(false))) {
            throw new IllegalStateException("AIOHandler is not in PAUSED state, but: " +
                    stateHolder.getState(false));
        }

        stateHolder.setState(State.STARTED);
    }

    /**
     * {@inheritDoc}
     */
    public StateHolder<State> getStateHolder() {
        return stateHolder;
    }

    /**
     * Shuntdown this instance by closing its Selector and associated channels.
     */
    public void shutdown() {
        // If shutdown was called for this AIOHandler
        if (isShutDown.getAndSet(true)) {
            return;
        }

        stateHolder.setState(State.STOPPED);
        try {
            listener.close();
        } catch (IOException ex) {
            Controller.logger().log(Level.SEVERE, "shutdown", ex);
        }
    }

    // ----------- AttributeHolder interface implementation ----------- //  
    /**
     * Remove a key/value object.
     * Method is not thread safe
     * 
     * @param key - name of an attribute
     * @return  attribute which has been removed
     */
    public Object removeAttribute(String key) {
        if (attributes == null) {
            return null;
        }

        return attributes.remove(key);
    }

    /**
     * Set a key/value object.
     * Method is not thread safe
     * 
     * @param key - name of an attribute
     * @param value - value of named attribute
     */
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<String, Object>();
        }

        attributes.put(key, value);
    }

    /**
     * Return an object based on a key.
     * Method is not thread safe
     * 
     * @param key - name of an attribute
     * @return - attribute value for the <tt>key</tt>, null if <tt>key</tt>
     *           does not exist in <tt>attributes</tt>
     */
    public Object getAttribute(String key) {
        if (attributes == null) {
            return null;
        }

        return attributes.get(key);
    }

    /**
     * Set a <code>Map</code> of attribute name/value pairs.
     * Old <code>AttributeHolder</code> values will not be available.
     * Later changes of this <code>Map</code> will lead to changes to the current
     * <code>AttributeHolder</code>.
     * 
     * @param attributes - map of name/value pairs
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Return a <code>Map</code> of attribute name/value pairs.
     * Updates, performed on the returned <code>Map</code> will be reflected in
     * this <code>AttributeHolder</code>
     * 
     * @return - <code>Map</code> of attribute name/value pairs
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void releaseConnectorHandler(ConnectorHandler connectorHandler) {
    }

    public ConnectorHandler acquireConnectorHandler() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void copyTo(Copyable copy) {
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /* Enable (true) or disable (false) the underlying Socket's
     * tcpNoDelay.
     * 
     * Default value for tcpNoDelay is enabled (set to true), as according to 
     * the performance tests, it performs better for most cases.
     * 
     * The Connector side should also set tcpNoDelay the same as it is set here 
     * whenever possible.
     */
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public int getLinger() {
        return linger;
    }

    public void setLinger(int linger) {
        this.linger = linger;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }
    
    public long getTimeout() {
        return timeout;
    }
    
    
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public int getSsBackLog() {
        return ssBackLog;
    }

    public void setSsBackLog(int ssBackLog) {
        this.ssBackLog = ssBackLog;
    }
    
    public void setAynchronousChannelGroup(AsynchronousChannelGroup group) {
        asyncChannelGroup = group;
    }
}
