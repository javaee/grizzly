/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.GrizzlyExecutorService;
import com.sun.grizzly.util.ThreadPoolConfig;
import java.io.IOException;
import java.io.InterruptedIOException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class WebSocketApplication extends WebSocketAdapter {
    private final static Logger LOGGER = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    
    private final Map<WebSocket, Boolean> sockets = new ConcurrentHashMap<WebSocket, Boolean>();
    
    private boolean isRunning = true;
    private final boolean isWriterThreadsEnabled;
    private final long writerTimeoutMillis;
    private final BlockingQueue<ServerNetworkHandler> writeQueue =
            //            DataStructures.<ServerNetworkHandler>getLTQinstance(ServerNetworkHandler.class);
            new LinkedBlockingQueue<ServerNetworkHandler>();
    private final Queue<WriterRunnable> writers;
    private final ExecutorService writersThreadPool;
    private final Thread writersMinotoringThread;
    
    private final int coreWriterThreads;
    private final int maxWriterThreads;
    private final int queueSizeThreadSpawnThreshold;
    private final int queueSizeReceiverDelayingThreshold;

    final AtomicInteger buffersQueued = new AtomicInteger();

    public WebSocketApplication() {
        this(0, 0, 0, 0, 0);
    }

    protected WebSocketApplication(
            final int coreWriterThreads,
            final int maxWriterThreads,
            final int queueSizeThreadSpawnThreshold,
            final int queueSizeReceiverDelayingThreshold,
            final long writerTimeoutMillis) {
        
        if (maxWriterThreads < coreWriterThreads) {
            throw new IllegalArgumentException("maxWriterThreads < coreWriterThreads");
        }
        
        isWriterThreadsEnabled = coreWriterThreads > 0;
        this.coreWriterThreads = coreWriterThreads;
        this.maxWriterThreads = maxWriterThreads;
        this.queueSizeThreadSpawnThreshold = queueSizeThreadSpawnThreshold >= 0 ?
                queueSizeThreadSpawnThreshold : -1;
        this.queueSizeReceiverDelayingThreshold = queueSizeReceiverDelayingThreshold >= 0 ?
                queueSizeReceiverDelayingThreshold : -1;
        
        this.writerTimeoutMillis = writerTimeoutMillis > 0 ? writerTimeoutMillis : -1;

        if (isWriterThreadsEnabled) {
            final ThreadPoolConfig tpc = ThreadPoolConfig.DEFAULT
                    .setPoolName("Websocket-app-" + getClass().getName() + "-writers")
                    .setCorePoolSize(coreWriterThreads)
                    .setMaxPoolSize(maxWriterThreads);

            writersThreadPool = GrizzlyExecutorService.createInstance(tpc);

            writers = new ConcurrentLinkedQueue<WriterRunnable>();
            for (int i = 0; i < coreWriterThreads; i++) {
                final WriterRunnable writerRunnable = new WriterRunnable(true);
                writers.add(writerRunnable);
                writersThreadPool.submit(writerRunnable);
            }

            writersMinotoringThread = new Thread(new WritersMonitoringRunnable(),
                    "Websocket-app-" + getClass().getName() + "-writers-monitoring");
            writersMinotoringThread.setDaemon(true);
            writersMinotoringThread.start();
        } else {
            writersThreadPool = null;
            writersMinotoringThread = null;
            writers = null;
        }
    }

    public WebSocket createWebSocket(ProtocolHandler protocolHandler, final WebSocketListener... listeners) {
        return new DefaultWebSocket(protocolHandler, listeners);
    }

    /**
     * Returns a set of {@link WebSocket}s, registered with the application. The
     * returned set is unmodifiable, the possible modifications may cause
     * exceptions.
     *
     * @return a set of {@link WebSocket}s, registered with the application.
     */
    protected Set<WebSocket> getWebSockets() {
        return sockets.keySet();
    }

    protected boolean add(WebSocket socket) {
        return sockets.put(socket, Boolean.TRUE) == null;
    }

    public boolean remove(WebSocket socket) {
        return sockets.remove(socket) != null;
    }

    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        remove(socket);
        socket.close();
    }

    @Override
    public void onConnect(WebSocket socket) {
        add(socket);
    }

    /**
     * Checks application specific criteria to determine if this application can
     * process the Request as a WebSocket connection.
     *
     * @param request
     * @return true if this application can service this Request
     *
     * @deprecated URI mapping shouldn't be intrinsic to the application.
     *  WebSocketApplications should be registered using {@link WebSocketEngine#register(String, String, WebSocketApplication)}
     *  using standard Servlet url-pattern rules.
     */
    public boolean isApplicationRequest(Request request) {
        return false;
    }

    public List<String> getSupportedExtensions() {
        return Collections.emptyList();
    }

    public List<String> getSupportedProtocols(List<String> subProtocol) {
        return Collections.emptyList();
    }

    protected int getCoreWriterThreads() {
        return coreWriterThreads;
    }

    protected int getMaxWriterThreads() {
        return maxWriterThreads;
    }

    protected int getQueueSizeThreadSpawnThreshold() {
        return queueSizeThreadSpawnThreshold;
    }

    protected int getQueueSizeReceiverDelayingThreshold() {
        return queueSizeReceiverDelayingThreshold;
    }

    
    protected boolean isWriterThreadsEnabled() {
        return isWriterThreadsEnabled;
    }

    /**
     * When invoked, all currently connected WebSockets will be closed.
     */
    synchronized void shutdown() {
        if (!isRunning) {
            return;
        }

        isRunning = false;

        for (WebSocket webSocket : sockets.keySet()) {
            if (webSocket.isConnected()) {
                webSocket.onClose(null);
            }
        }
        sockets.clear();

        if (isWriterThreadsEnabled) {
            try {
                writersThreadPool.shutdownNow();
            } catch (Exception e) {
            }

            try {
                writersMinotoringThread.interrupt();
            } catch (Exception e) {
            }
        }
    }

    void scheduleForWriting(final ServerNetworkHandler networkHandler) {
        if (networkHandler.isInWriteQueue.compareAndSet(false, true)) {
            writeQueue.offer(networkHandler);
            
            if (queueSizeReceiverDelayingThreshold >= 0 &&
                    buffersQueued.get() > queueSizeReceiverDelayingThreshold) {
                helpWriteQueue();
            }
        }
    }

    protected void helpWriteQueue() {
        // give more time to writers
        
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
        }
        
//        final int maxIterations = buffersQueued.get();
//
//        for (int i = 0; i < maxIterations && buffersQueued.get() > NORMAL_QUEUE_SIZE; i++) {
//            final ServerNetworkHandler handler = writeQueue.poll();
//            if (handler == null) {
//                return;
//            }
//
//            try {
//                handler.flushWriteQueue();
//
//                handler.isInWriteQueue.set(false);
//
//                if (!handler.isWriteQueueEmpty()
//                        && handler.isInWriteQueue.compareAndSet(false, true)) {
//                    writeQueue.add(handler);
//                }
//            } catch (InterruptedIOException iioe) {
//                Thread.interrupted();
//            } catch (IOException e) {
//                assert handler != null;
//
//                handler.close();
//            } catch (Exception e) {
//            }
//        }
    }

    private class WriterRunnable implements Runnable {

        private final boolean isCore;
        private volatile long startFlushTimestamp = -1;
        private Thread thread;

        private WriterRunnable(boolean isCore) {
            this.isCore = isCore;
        }

        public void run() {
            thread = Thread.currentThread();

            try {
                while (isRunning) {
                    ServerNetworkHandler handler = null;

                    try {
                        handler = isCore ? writeQueue.take()
                                : writeQueue.poll(30, TimeUnit.SECONDS);

                        if (handler == null) { // timeout expired
                            return;
                        }

                        startFlushTimestamp = System.currentTimeMillis();

                        handler.flushWriteQueue();

                        handler.isInWriteQueue.set(false);

                        if (!handler.isWriteQueueEmpty()
                                && handler.isInWriteQueue.compareAndSet(false, true)) {
                            writeQueue.add(handler);
                        }
                    } catch (InterruptedException ie) {
                        Thread.interrupted();
                    } catch (InterruptedIOException iioe) {
                        Thread.interrupted();

                        assert handler != null;

                        if (writerTimeoutMillis > 0 &&
                                startFlushTimestamp != -1
                                && System.currentTimeMillis() - startFlushTimestamp > writerTimeoutMillis) {
                            handler.close();
                        }
                    } catch (IOException e) {
                        assert handler != null;

                        handler.close();
                    } catch (Exception e) {
                    } finally {
                        startFlushTimestamp = -1;
                    }
                }
            } finally {
                writers.remove(this);
            }
        }
    }

    private class WritersMonitoringRunnable implements Runnable {

        public void run() {
            try {
                while (isRunning) {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "Totally queued: {0} number of writers: {1}",
                                new Object[]{buffersQueued, writers.size()});
                    }

                    if (writerTimeoutMillis > 0) {
                        final long currentTimeMillis = System.currentTimeMillis();

                        for (WriterRunnable writer : writers) {

                            final long stamp = writer.startFlushTimestamp;

                            if (stamp != -1) {
                                if (currentTimeMillis - stamp > writerTimeoutMillis) {
                                    writer.thread.interrupt();
                                }
                            }
                        }
                    }
                    
                    if (queueSizeThreadSpawnThreshold >= 0 &&
                            buffersQueued.get() > queueSizeThreadSpawnThreshold) {
                        synchronized (WebSocketApplication.this) {
                            final int writersCount = writers.size();

                            if (isRunning && writersCount < maxWriterThreads) {
                                final WriterRunnable writerRunnable = new WriterRunnable(false);
                                writers.add(writerRunnable);

                                writersThreadPool.submit(writerRunnable);
                            }
                        }
                    }

                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                }
            } catch (Throwable t) {
            }
        }
    }
}
