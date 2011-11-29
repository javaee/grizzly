/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.Transport.State;
import org.glassfish.grizzly.threadpool.WorkerThread;
import org.glassfish.grizzly.utils.StateHolder;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayDeque;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class is responsible for processing certain (single) {@link SelectorHandler}
 * 
 * @author Alexey Stashok
 */
public final class SelectorRunner implements Runnable {
    private final static Logger logger = Grizzly.logger(SelectorRunner.class);
    
    private final NIOTransport transport;
    private final AtomicReference<State> stateHolder;
    
    private final Queue<SelectorHandlerTask> pendingTasks;
    
    private Queue<SelectorHandlerTask> currentPostponedTasks;
    private final Queue<SelectorHandlerTask> evenPostponedTasks;
    private final Queue<SelectorHandlerTask> oddPostponedTasks;

    private volatile int dumbVolatile = 1;
    private Selector selector;
    private Thread selectorRunnerThread;

    // State fields
    private boolean isResume;

    private int lastSelectedKeysCount;
    private Set<SelectionKey> readyKeySet;
    private Iterator<SelectionKey> iterator;
    private SelectionKey key = null;
    private int keyReadyOps;

    private final AtomicBoolean selectorWakeupFlag = new AtomicBoolean();
    private final AtomicInteger runnerThreadActivityCounter = new AtomicInteger();

    public static SelectorRunner create(final NIOTransport transport)
            throws IOException {
        return new SelectorRunner(transport, SelectorFactory.instance().create());
    }

    private SelectorRunner(final NIOTransport transport,
            final Selector selector) {
        this.transport = transport;
        this.selector = selector;
        stateHolder = new AtomicReference<State>(State.STOP);

        pendingTasks = new ConcurrentLinkedQueue<SelectorHandlerTask>();
        evenPostponedTasks = new ArrayDeque<SelectorHandlerTask>();
        oddPostponedTasks = new ArrayDeque<SelectorHandlerTask>();
        currentPostponedTasks = evenPostponedTasks;
    }

    public NIOTransport getTransport() {
        return transport;
    }

    public Selector getSelector() {
        if (dumbVolatile != 0) {
            return selector;
        }

        return null;
    }

    /**
     * Sets the {@link Selector}, associated with the runner.
     * The method should be called from the runner thread.
     * @param selector
     */
    void setSelector(final Selector selector) {
        this.selector = selector;
        dumbVolatile++;
    }

    private void setRunnerThread(final Thread runnerThread) {
        this.selectorRunnerThread = runnerThread;
        dumbVolatile++;
    }

    public Thread getRunnerThread() {
        if (dumbVolatile != 0) {
            return selectorRunnerThread;
        }
        
        return null;
    }

    public State getState() {
        return stateHolder.get();
    }
    
    public void postpone() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof WorkerThread) {
            ((WorkerThread) currentThread).setSelectorThread(false);
        }
        
        runnerThreadActivityCounter.compareAndSet(1, 0);
        selectorRunnerThread = null;
        isResume = true;
        dumbVolatile++;
    }

    public synchronized void start() {
        if (!stateHolder.compareAndSet(State.STOP, State.STARTING)) {
            logger.log(Level.WARNING,
                    "SelectorRunner is not in the stopped state!");
            return;
        }
        
        transport.getKernelThreadPool().execute(this);
    }
    
    public synchronized void stop() {
        stateHolder.set(State.STOPPING);
        wakeupSelector();

        // we prefer Selector thread shutdown selector
        // but if it's not running - do that ourselves.
        if (runnerThreadActivityCounter.compareAndSet(0, -1)) {
            // The thread is not running
            shutdownSelector();
        }
    }

    private void shutdownSelector() {
        final Selector localSelector = getSelector();
        if (localSelector != null) {
            try {
                SelectionKey[] keys = new SelectionKey[0];
                while(true) {
                    try {
                        keys = localSelector.keys().toArray(keys);
                        break;
                    } catch (ConcurrentModificationException ignored) {
                    }
                }

                for (final SelectionKey selectionKey : keys) {
                    final Connection connection =
                            transport.getSelectionKeyHandler().
                            getConnectionForKey(selectionKey);
                    try {
                        connection.close();
                    } catch (Exception ignored) {
                    }
                }
            } catch (ClosedSelectorException e) {
                // If Selector is already closed - OK
            } finally {
                try {
                    localSelector.close();
                } catch (Exception ignored) {
                }
            }
        }

        abortTasksInQueue(pendingTasks);
        abortTasksInQueue(evenPostponedTasks);
        abortTasksInQueue(oddPostponedTasks);
    }

    public void wakeupSelector() {
        final Selector localSelector = getSelector();
        if (localSelector != null &&
                selectorWakeupFlag.compareAndSet(false, true)) {
            try {
                localSelector.wakeup();
            } catch (Exception e) {
                logger.log(Level.FINE, "Error during selector wakeup", e);
            }
        }
    }

    @Override
    public void run() {
        if (!runnerThreadActivityCounter.compareAndSet(0, 1)) {
            return;
        }

        final Thread currentThread = Thread.currentThread();
        if (!isResume) {
            if (!stateHolder.compareAndSet(State.STARTING, State.START)) {
                return;
            }

            currentThread.setName(currentThread.getName() + " SelectorRunner");
        }

        setRunnerThread(currentThread);
        final boolean isWorkerThread = currentThread instanceof WorkerThread;
        if (isWorkerThread) {
            ((WorkerThread) currentThread).setSelectorThread(true);
        }
        
        final StateHolder<State> transportStateHolder = transport.getState();

        boolean isSkipping = false;
        
        try {
            while (!isSkipping && !isStop()) {
                if (transportStateHolder.getState() != State.PAUSE) {
                    isSkipping = !doSelect();
                } else {
                    try {
                        transportStateHolder
                                .notifyWhenStateIsNotEqual(State.PAUSE, null)
                                .get(5000, TimeUnit.MILLISECONDS);
                    } catch (Exception ignored) {
                    }
                }
            }
        } finally {
            runnerThreadActivityCounter.compareAndSet(1, 0);

            if (isStop()) {
                stateHolder.set(State.STOP);
                setRunnerThread(null);
                if (runnerThreadActivityCounter.compareAndSet(0, -1)) {
                    shutdownSelector();
                }
            }

            if (isWorkerThread) {
                ((WorkerThread) currentThread).setSelectorThread(false);
            }
        }
    }

    /**
     * This method handle the processing of all Selector's interest op
     * (OP_ACCEPT,OP_READ,OP_WRITE,OP_CONNECT) by delegating to its Handler.
     * By default, all java.nio.channels.Selector operations are implemented
     * using SelectorHandler. All SelectionKey operations are implemented by
     * SelectionKeyHandler. Finally, ProtocolChain creation/re-use are implemented
     * by InstanceHandler.
     */
    protected boolean doSelect() {
        final SelectorHandler selectorHandler = transport.getSelectorHandler();
        
        try {
            
            if (isResume) {
                // If resume SelectorRunner - finish postponed keys
                isResume = false;
                
                // readyKeySet==null means execution was suspended on preSelect(..)
                if (readyKeySet != null) {
                    if (keyReadyOps != 0) {
                        if (!iterateKeyEvents()) return false;
                    }

                    if (!iterateKeys()) return false;
                    readyKeySet.clear();
                }
            }

            lastSelectedKeysCount = 0;

            if (!selectorHandler.preSelect(this)) {
                return false;
            }

            readyKeySet = selectorHandler.select(this);
            selectorWakeupFlag.set(false);

            if (stateHolder.get() == State.STOPPING) return true;
            
            lastSelectedKeysCount = readyKeySet.size();
            
            if (lastSelectedKeysCount != 0) {
                iterator = readyKeySet.iterator();
                if (!iterateKeys()) return false;
                readyKeySet.clear();
            }

            readyKeySet = null;
            iterator = null;
            selectorHandler.postSelect(this);
        } catch (ClosedSelectorException e) {
            if (isRunning()) {
                if (selectorHandler.onSelectorClosed(this)) {
                    return true;
                }
            }
            
            notifyConnectionException(key,
                    "Selector was unexpectedly closed", e,
                    Level.SEVERE, Level.FINE);
        } catch (Exception e) {
            notifyConnectionException(key,
                    "doSelect exception", e,
                    Level.SEVERE, Level.FINE);
        } catch (Throwable t) {
            logger.log(Level.SEVERE,"doSelect exception", t);
            transport.notifyTransportError(t);
        }

        return true;
    }

    private boolean iterateKeys() {
        while (iterator.hasNext()) {
            try {
                key = iterator.next();
                keyReadyOps = key.readyOps();
                if (!iterateKeyEvents()) {
                    return false;
                }
            } catch (IOException e) {
                keyReadyOps = 0;
                notifyConnectionException(key, "Unexpected IOException. Channel " + key.channel() + " will be closed.", e, Level.WARNING, Level.FINE);
            } catch (CancelledKeyException e) {
                keyReadyOps = 0;
                notifyConnectionException(key, "Unexpected CancelledKeyException. Channel " + key.channel() + " will be closed.", e, Level.FINE, Level.FINE);
            }
        }
        return true;
    }


    private boolean iterateKeyEvents()
            throws IOException {

        final SelectionKeyHandler selectionKeyHandler = transport.getSelectionKeyHandler();
        final IOStrategy ioStrategy = transport.getIOStrategy();
        final IOEvent[] ioEvents = selectionKeyHandler.getIOEvents(keyReadyOps);
        final NIOConnection connection =
                selectionKeyHandler.getConnectionForKey(key);

        for (IOEvent ioEvent : ioEvents) {
            NIOConnection.notifyIOEventReady(connection, ioEvent);
            
            final int interest = ioEvent.getSelectionKeyInterest();
            keyReadyOps &= (~interest);
            if (selectionKeyHandler.onProcessInterest(key, interest)) {
                if (!ioStrategy.executeIoEvent(connection, ioEvent)) {
                    return false;
                }
            }
        }

        return true;
    }

    public Queue<SelectorHandlerTask> getPendingTasks() {
        return pendingTasks;
    }

    public Queue<SelectorHandlerTask> getPostponedTasks() {
        return currentPostponedTasks;
    }

    public Queue<SelectorHandlerTask> obtainPostponedTasks() {
        final Queue<SelectorHandlerTask> tasksToReturn = currentPostponedTasks;
        currentPostponedTasks = (currentPostponedTasks == evenPostponedTasks) ?
                oddPostponedTasks : evenPostponedTasks;
        return tasksToReturn;
    }
    
    boolean isStop() {
        final State state = stateHolder.get();

        return state == State.STOP || state == State.STOPPING;
    }
    
    private boolean isRunning() {
        return stateHolder.get() == State.START;
    }

    /**
     * Notify transport about the {@link Exception} happened, log it and cancel
     * the {@link SelectionKey}, if it is not null
     * 
     * @param key {@link SelectionKey}, which was processed, when the {@link Exception} occured
     * @param description error description
     * @param e {@link Exception} occurred
     * @param runLogLevel logger {@link Level} to use, if transport is in running state
     * @param stoppedLogLevel logger {@link Level} to use, if transport is not in running state
     */
    private void notifyConnectionException(final SelectionKey key,
            final String description,
            final Exception e, final Level runLogLevel,
            final Level stoppedLogLevel) {
        if (isRunning()) {
            logger.log(runLogLevel, description, e);

            if (key != null) {
                try {
                    final Connection connection =
                            transport.getSelectionKeyHandler().getConnectionForKey(key);

                    if (connection != null) {
                        connection.close();
                    } else {
                        final SelectableChannel channel = key.channel();
                        transport.getSelectionKeyHandler().cancel(key);
                        channel.close();
                    }
                } catch (IOException cancelException) {
                    logger.log(Level.FINE, "IOException during cancelling key",
                            cancelException);
                }
            }

            transport.notifyTransportError(e);
        } else {
            logger.log(stoppedLogLevel, description, e);
        }
    }

    /**
     * Number of {@link SelectionKey}s, which were selected last time.
     * Operation is not thread-safe.
     *
     * @return number of {@link SelectionKey}s, which were selected last time.
     */
    public int getLastSelectedKeysCount() {
        return lastSelectedKeysCount;
    }

    protected final void switchToNewSelector() throws IOException {
        final Selector oldSelector = selector;
        final Selector newSelector = SelectorFactory.instance().create();

        final Set<SelectionKey> keys = oldSelector.keys();
        for (final SelectionKey selectionKey : keys) {
            try {
                selectionKey.channel().register(newSelector,
                        selectionKey.interestOps(), selectionKey.attachment());
            } catch (Exception e) {
                logger.log(Level.FINE, "Error switching channel to a new selector", e);
            }
        }

        setSelector(newSelector);

        try {
            oldSelector.close();
        } catch (Exception ignored) {
        }
    }

    private void abortTasksInQueue(final Queue<SelectorHandlerTask> taskQueue) {
        SelectorHandlerTask task;
        while ((task = taskQueue.poll()) != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
    }
}
