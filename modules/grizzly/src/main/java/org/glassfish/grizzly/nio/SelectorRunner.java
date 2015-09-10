/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayDeque;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.threadpool.Threads;
import org.glassfish.grizzly.utils.StateHolder;

/**
 * Class is responsible for processing certain (single) {@link SelectorHandler}
 * 
 * @author Alexey Stashok
 */
public final class SelectorRunner implements Runnable {
    private final static Logger LOGGER = Grizzly.logger(SelectorRunner.class);
    
    private final static String THREAD_MARKER = " SelectorRunner";
    
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
        return new SelectorRunner(transport,
                Selectors.newSelector(transport.getSelectorProvider()));
    }
    
    volatile boolean hasPendingTasks;
    
    private SelectorRunner(final NIOTransport transport,
            final Selector selector) {
        this.transport = transport;
        this.selector = selector;
        stateHolder = new AtomicReference<State>(State.STOPPED);

        pendingTasks = new ConcurrentLinkedQueue<SelectorHandlerTask>();
        evenPostponedTasks = new ArrayDeque<SelectorHandlerTask>();
        oddPostponedTasks = new ArrayDeque<SelectorHandlerTask>();
        currentPostponedTasks = evenPostponedTasks;
    }

    void addPendingTask(final SelectorHandlerTask task) {
        pendingTasks.offer(task);
        hasPendingTasks = true;

        wakeupSelector();
    }

    private void wakeupSelector() {
        final Selector localSelector = getSelector();
        if (localSelector != null &&
                selectorWakeupFlag.compareAndSet(false, true)) {
            try {
                localSelector.wakeup();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error during selector wakeup", e);
            }
        }
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
     * @param selector the new {@link Selector} to be associated with this
     *                 {@link SelectorRunner}.
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
        assert selectorRunnerThread != null;
        removeThreadNameMarker(selectorRunnerThread);
        
        Threads.setService(false);
        
        runnerThreadActivityCounter.compareAndSet(1, 0);
        selectorRunnerThread = null;
        isResume = true;
        dumbVolatile++;
    }

    public synchronized void start() {
        if (!stateHolder.compareAndSet(State.STOPPED, State.STARTING)) {
            LOGGER.log(Level.WARNING,
                    LogMessages.WARNING_GRIZZLY_SELECTOR_RUNNER_NOT_IN_STOPPED_STATE_EXCEPTION());
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
                    connection.terminateSilently();
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

    @Override
    public void run() {
        if (!runnerThreadActivityCounter.compareAndSet(0, 1)) {
            return;
        }

        final Thread currentThread = Thread.currentThread();
        try {
            if (!isResume) {
                if (!stateHolder.compareAndSet(State.STARTING, State.STARTED)) {
                    return;
                }

                addThreadNameMarker(currentThread);
            }

            setRunnerThread(currentThread);
            Threads.setService(true);

            final StateHolder<State> transportStateHolder = transport.getState();

            boolean isSkipping = false;
        
            while (!isSkipping && !isStop()) {
                if (transportStateHolder.getState() != State.PAUSED) {
                    isSkipping = !doSelect();
                } else {
                    try {
                        transportStateHolder
                                .notifyWhenStateIsNotEqual(State.PAUSED, null)
                                .get(5000, TimeUnit.MILLISECONDS);
                    } catch (Exception ignored) {
                    }
                }
            }
        } finally {
            runnerThreadActivityCounter.compareAndSet(1, 0);

            if (isStop()) {
                stateHolder.set(State.STOPPED);
                setRunnerThread(null);
                if (runnerThreadActivityCounter.compareAndSet(0, -1)) {
                    shutdownSelector();
                }
            }

            removeThreadNameMarker(currentThread);
            Threads.setService(false);
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
            LOGGER.log(Level.SEVERE,"doSelect exception", t);
            transport.notifyTransportError(t);
        }

        return true;
    }

    private boolean iterateKeys() {
        final Iterator<SelectionKey> it = iterator;

        while (it.hasNext()) {
            try {
                key = it.next();
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

        final SelectionKey keyLocal = key;
        final SelectionKeyHandler selectionKeyHandler = transport.getSelectionKeyHandler();
        final IOStrategy ioStrategy = transport.getIOStrategy();
        final IOEvent[] ioEvents = selectionKeyHandler.getIOEvents(keyReadyOps);
        final NIOConnection connection =
                selectionKeyHandler.getConnectionForKey(keyLocal);

        for (IOEvent ioEvent : ioEvents) {
            NIOConnection.notifyIOEventReady(connection, ioEvent);
            
            final int interest = ioEvent.getSelectionKeyInterest();
            keyReadyOps &= (~interest);
            if (selectionKeyHandler.onProcessInterest(keyLocal, interest)) {
                if (!ioStrategy.executeIoEvent(connection, ioEvent)) {
                    return false;
                }
            }
        }

        return true;
    }

    public Queue<SelectorHandlerTask> getPendingTasks() {
        hasPendingTasks = false;
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

        return state == State.STOPPED || state == State.STOPPING;
    }
    
    private boolean isRunning() {
        return stateHolder.get() == State.STARTED;
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
            LOGGER.log(runLogLevel, description, e);

            if (key != null) {
                try {
                    final Connection connection =
                            transport.getSelectionKeyHandler().getConnectionForKey(key);

                    if (connection != null) {
                        connection.closeSilently();
                    } else {
                        final SelectableChannel channel = key.channel();
                        transport.getSelectionKeyHandler().cancel(key);
                        channel.close();
                    }
                } catch (IOException cancelException) {
                    LOGGER.log(Level.FINE, "IOException during cancelling key",
                            cancelException);
                }
            }

            transport.notifyTransportError(e);
        } else {
            LOGGER.log(stoppedLogLevel, description, e);
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
        final Selector newSelector = Selectors.newSelector(transport.getSelectorProvider());

        final Set<SelectionKey> keys = oldSelector.keys();
        final SelectionKeyHandler selectionKeyHandler =
                transport.getSelectionKeyHandler();
        
        for (final SelectionKey selectionKey : keys) {
            if (selectionKey.isValid()) {
                try {
                    final NIOConnection nioConnection =
                            selectionKeyHandler.getConnectionForKey(selectionKey);
                    final SelectionKey newSelectionKey =
                            selectionKey.channel().register(newSelector,
                            selectionKey.interestOps(), selectionKey.attachment());

                    nioConnection.onSelectionKeyUpdated(newSelectionKey);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error switching channel to a new selector", e);
                }
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
    
    
    /********************** Selector spin discovering *************************/
    
    private long lastSpinTimestamp;
    private int emptySpinCounter;
    
    private final Map<Selector, Long> spinnedSelectorsHistory =
            new WeakHashMap<Selector, Long>();

    final void resetSpinCounter() {
        emptySpinCounter  = 0;
    }

    /**
     * Increments spin counter and returns current spin rate (emptyspins/sec).
     * 
     * @return current spin rate (emptyspins/sec).
     */
    final int incSpinCounter() {
        if (emptySpinCounter++ == 0){
            lastSpinTimestamp = System.nanoTime();
        } else if (emptySpinCounter == 1000) {
            long deltatime = System.nanoTime() - lastSpinTimestamp;
            int contspinspersec = (int) (1000 * 1000000000L / deltatime);
            emptySpinCounter  = 0;
            return contspinspersec;
        }
        
        return 0;
    }
    
    final SelectionKey checkIfSpinnedKey(final SelectionKey key) {
        if (!key.isValid() && key.channel().isOpen() &&
                spinnedSelectorsHistory.containsKey(key.selector())) {
            final SelectionKey newKey = key.channel().keyFor(getSelector());
            newKey.attach(key.attachment());
            return newKey;
        }

        return key;
    }

    final void workaroundSelectorSpin() throws IOException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Workaround selector spin. selector={0}", getSelector());
        }

        spinnedSelectorsHistory.put(getSelector(), System.currentTimeMillis());
        switchToNewSelector();
    }

    final void checkSelectorSpin(final boolean hasSelectedKeys,
            final int spinRateThreshold) throws IOException {
        if (hasSelectedKeys) {
            resetSpinCounter();
        } else {
            final long sr = incSpinCounter();
            if (sr > spinRateThreshold) {
                workaroundSelectorSpin();
            }
        }
    }

    private void addThreadNameMarker(final Thread currentThread) {
        final String name = currentThread.getName();
        if (!name.endsWith(THREAD_MARKER)) {
            currentThread.setName(name + THREAD_MARKER);
        }
    }
    
    private void removeThreadNameMarker(final Thread currentThread) {
        final String name = currentThread.getName();
        if (name.endsWith(THREAD_MARKER)) {
            currentThread.setName(
                    name.substring(0, name.length() - THREAD_MARKER.length()));
        }
    }
}
