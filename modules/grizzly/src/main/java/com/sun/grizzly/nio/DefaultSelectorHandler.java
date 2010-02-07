/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.nio;

import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.GrizzlyFuture;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.ReadyFutureImpl;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of NIO <code>SelectorHandler</code>
 * 
 * @author Alexey Stashok
 */
public class DefaultSelectorHandler implements SelectorHandler {
    private static final Logger logger = Grizzly.logger(DefaultSelectorHandler.class);

    public static final boolean IS_WORKAROUND_SELECTOR_SPIN =
            System.getProperty("os.name").equalsIgnoreCase("linux") &&
                !System.getProperty("java.version").startsWith("1.7");
    
    protected volatile long selectTimeout = 1000;

    // Selector spin workaround artifacts

    /**
     * The threshold for detecting selector.select spin on linux,
     * used for enabling workaround to prevent server from hanging.
     */
    private final int spinRateTreshold = 2000;

    private long lastSpinTimestamp;
    private int emptySpinCounter;
    private final Map<Selector, Long> spinnedSelectorsHistory;
    private final Object spinSync;
    // ----------------

    public DefaultSelectorHandler() {
        spinnedSelectorsHistory = new WeakHashMap<Selector, Long>();
        spinSync = new Object();
    }

    @Override
    public long getSelectTimeout() {
        return selectTimeout;
    }

    @Override
    public void setSelectTimeout(long selectTimeout) {
        this.selectTimeout = selectTimeout;
    }    

    @Override
    public void preSelect(SelectorRunner selectorRunner) throws IOException {
        processPendingTasks(selectorRunner);
    }

    @Override
    public Set<SelectionKey> select(final SelectorRunner selectorRunner)
            throws IOException {
        final Selector selector = selectorRunner.getSelector();
        if (selectorRunner.getPostponedTasks().isEmpty()) {
            selector.select(selectTimeout);
        } else {
            selector.selectNow();
        }

        final Set<SelectionKey> selectedKeys = selector.selectedKeys();
        
        if (IS_WORKAROUND_SELECTOR_SPIN) {
            if (!selectedKeys.isEmpty()) {
                resetSpinCounter();
            } else {
                long sr = getSpinRate();
                if (sr > spinRateTreshold) {
                    workaroundSelectorSpin(selectorRunner);
                }
            }
        }

        return selectedKeys;
    }

    @Override
    public void postSelect(SelectorRunner selectorRunner) throws IOException {
    }

    @Override
    public void registerKey(SelectorRunner selectorRunner, SelectionKey key,
            int interest) throws IOException {
        if (Thread.currentThread() == selectorRunner.getRunnerThread()) {
            registerKey0(key, interest);
        } else {
            final SelectorHandlerTask task = new RegisterKeyTask(key, interest);
            addPendingTask(selectorRunner, task);
        }
    }

    @Override
    public void unregisterKey(SelectorRunner selectorRunner, SelectionKey key,
            int interest) throws IOException {
        if (Thread.currentThread() == selectorRunner.getRunnerThread()) {
            unregisterKey0(key, interest);
        } else {
            final SelectorHandlerTask task = new UnRegisterKeyTask(key, interest);
            addPendingTask(selectorRunner, task);
        }
    }

    @Override
    public void registerChannel(SelectorRunner selectorRunner,
            SelectableChannel channel, int interest, Object attachment)
            throws IOException {
        
        final Future<RegisterChannelResult> future =
                registerChannelAsync(selectorRunner, channel, interest,
                attachment, null);
        try {
            future.get(selectTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public GrizzlyFuture<RegisterChannelResult> registerChannelAsync(
            SelectorRunner selectorRunner, SelectableChannel channel,
            int interest, Object attachment,
            CompletionHandler<RegisterChannelResult> completionHandler)
            throws IOException {

        final FutureImpl<RegisterChannelResult> future =
                FutureImpl.<RegisterChannelResult>create();

        if (Thread.currentThread() == selectorRunner.getRunnerThread()) {
            registerChannel0(selectorRunner, channel, interest, attachment,
                    completionHandler, future);
        } else {
            final SelectorHandlerTask task = new RegisterChannelOperation(
                    channel, interest, attachment, future, completionHandler);

            addPendingTask(selectorRunner, task);
        }

        return future;
    }

    @Override
    public GrizzlyFuture<Runnable> executeInSelectorThread(
            final SelectorRunner selectorRunner, final Runnable runnableTask,
            final CompletionHandler<Runnable> completionHandler) {
        if (Thread.currentThread() == selectorRunner.getRunnerThread()) {
            try {
                runnableTask.run();
            } catch (Exception e) {
                if (completionHandler != null) {
                    completionHandler.failed(e);
                }
                
                return ReadyFutureImpl.<Runnable>create(e);
            }
            if (completionHandler != null) {
                completionHandler.completed(runnableTask);
            }
            
            return ReadyFutureImpl.<Runnable>create(runnableTask);
        } else {
            final FutureImpl<Runnable> future = FutureImpl.<Runnable>create();

            final SelectorHandlerTask task = new RunnableTask(runnableTask,
                    future, completionHandler);
            addPendingTask(selectorRunner, task);
            return future;
        }
    }

    private void addPendingTask(final SelectorRunner selectorRunner,
            final SelectorHandlerTask task) {
        final Queue<SelectorHandlerTask> pendingTasks =
                selectorRunner.getPendingTasks();
        pendingTasks.offer(task);

        selectorRunner.wakeupSelector();
    }

    private void processPendingTasks(final SelectorRunner selectorRunner)
            throws IOException {

        processPendingTaskQueue(selectorRunner, selectorRunner.getPostponedTasks());
        processPendingTaskQueue(selectorRunner, selectorRunner.getPendingTasks());
    }

    private void processPendingTaskQueue(final SelectorRunner selectorRunner,
            final Queue<SelectorHandlerTask> selectorHandlerTasks)
            throws IOException {
        SelectorHandlerTask selectorHandlerTask;
        while((selectorHandlerTask = selectorHandlerTasks.poll()) != null) {
            selectorHandlerTask.run(selectorRunner);
        }
    }

    private void registerKey0(SelectionKey selectionKey, int interest) {
        if (selectionKey.isValid()) {
            int currentOps = selectionKey.interestOps();
            if ((currentOps & interest) != interest) {
                selectionKey.interestOps(currentOps | interest);
            }
        }
    }

    private void unregisterKey0(SelectionKey selectionKey, int interest) {
        if (selectionKey.isValid()) {
            int currentOps = selectionKey.interestOps();
            if ((currentOps & interest) != 0) {
                selectionKey.interestOps(currentOps & (~interest));
            }
        }
    }

    private void registerChannel0(SelectorRunner selectorRunner,
            SelectableChannel channel, int interest, Object attachment,
            CompletionHandler completionHandler,
            FutureImpl<RegisterChannelResult> future) throws IOException {

        if (future == null || !future.isCancelled()) {
            try {
                if (channel.isOpen()) {

                    final Selector selector = selectorRunner.getSelector();
                    
                    final SelectionKey key = channel.keyFor(selector);

                    // Check whether the channel has been registered on a selector
                    boolean isKeyValid = true;
                    if (key == null || (isKeyValid = key.isValid())) {
                        // If channel is not registered or key is valid
                        final SelectionKey registeredSelectionKey =
                                channel.register(selector, interest, attachment);

                        selectorRunner.getTransport().
                                getSelectionKeyHandler().
                                onKeyRegistered(registeredSelectionKey);

                        final RegisterChannelResult result =
                                new RegisterChannelResult(selectorRunner,
                                registeredSelectionKey, channel);

                        if (completionHandler != null) {
                            completionHandler.completed(result);
                        }
                        if (future != null) {
                            future.result(result);
                        }
                        return;
                    }

                    if (!isKeyValid) {
                        // Channel has been registered already,
                        // but the key is not valid
                        selectorRunner.getPostponedTasks().add(
                                new RegisterChannelOperation(channel, interest,
                                attachment, future, completionHandler));
                    }
                } else {
                    Throwable error = new ClosedChannelException();
                    if (completionHandler != null) {
                        completionHandler.failed(error);
                    }
                    
                    if (future != null) {
                        future.failure(error);
                    }
                }
            } catch (IOException e) {
                if (completionHandler != null) {
                    completionHandler.failed(e);
                }
                
                if (future != null) {
                    future.failure(e);
                }
                throw e;
            }
        }
    }

    @Override
    public boolean onSelectorClosed(SelectorRunner selectorRunner) {
        try {
            workaroundSelectorSpin(selectorRunner);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void resetSpinCounter(){
        emptySpinCounter  = 0;
    }

    private int getSpinRate(){
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

    private SelectionKey checkIfSpinnedKey(final SelectorRunner selectorRunner,
            final SelectionKey key) {
        if (!key.isValid() && key.channel().isOpen() &&
                spinnedSelectorsHistory.containsKey(key.selector())) {
            final SelectionKey newKey = key.channel().keyFor(
                    selectorRunner.getSelector());
            newKey.attach(key.attachment());
            return newKey;
        }

        return key;
    }

    private void workaroundSelectorSpin(final SelectorRunner selectorRunner)
            throws IOException {
        synchronized(spinSync) {
            spinnedSelectorsHistory.put(selectorRunner.getSelector(),
                    System.currentTimeMillis());
            selectorRunner.switchToNewSelector();
        }
    }

    protected final class RegisterKeyTask implements SelectorHandlerTask {
        private final SelectionKey selectionKey;
        private final int interest;

        public RegisterKeyTask(SelectionKey selectionKey, int interest) {
            this.selectionKey = selectionKey;
            this.interest = interest;
        }

        @Override
        public void run(final SelectorRunner selectorRunner) throws IOException {
            SelectionKey localSelectionKey = selectionKey;
            if (IS_WORKAROUND_SELECTOR_SPIN) {
                localSelectionKey = checkIfSpinnedKey(selectorRunner, selectionKey);
            }

            registerKey0(localSelectionKey, interest);
        }
    }

    protected final class UnRegisterKeyTask implements SelectorHandlerTask {
        private final SelectionKey selectionKey;
        private final int interest;

        public UnRegisterKeyTask(SelectionKey selectionKey, int interest) {
            this.selectionKey = selectionKey;
            this.interest = interest;
        }

        @Override
        public void run(final SelectorRunner selectorRunner) throws IOException {
            SelectionKey localSelectionKey = selectionKey;
            if (IS_WORKAROUND_SELECTOR_SPIN) {
                localSelectionKey = checkIfSpinnedKey(selectorRunner, selectionKey);
            }

            unregisterKey0(localSelectionKey, interest);
        }
    }

    protected final class RegisterChannelOperation implements SelectorHandlerTask {
        private final SelectableChannel channel;
        private final int interest;
        private final Object attachment;
        private final FutureImpl<RegisterChannelResult> future;
        private final CompletionHandler<RegisterChannelResult> completionHandler;

        private RegisterChannelOperation(SelectableChannel channel,
                int interest, Object attachment,
                FutureImpl<RegisterChannelResult> future,
                CompletionHandler<RegisterChannelResult> completionHandler) {
            this.channel = channel;
            this.interest = interest;
            this.attachment = attachment;
            this.future = future;
            this.completionHandler = completionHandler;
        }

        @Override
        public void run(final SelectorRunner selectorRunner) throws IOException {
            registerChannel0(selectorRunner, channel, interest,
                    attachment, completionHandler, future);
        }
    }

    protected final class RunnableTask implements SelectorHandlerTask {
        private final Runnable task;
        private final FutureImpl<Runnable> future;
        private final CompletionHandler<Runnable> completionHandler;

        private RunnableTask(Runnable runnableTask, FutureImpl<Runnable> future,
                CompletionHandler<Runnable> completionHandler) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public void run(final SelectorRunner selectorRunner) throws IOException {
            try {
                task.run();
                if (completionHandler != null) {
                    completionHandler.completed(task);
                }

                future.result(task);
            } catch (Throwable t) {
                logger.log(Level.FINEST, "doExecutePendiongIO failed.", t);
                
                if (completionHandler != null) {
                    completionHandler.completed(task);
                }

                future.failure(t);
            }
        }
    }
}
