/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.GrizzlyFuture;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.ReadyFutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of NIO <code>SelectorHandler</code>
 * 
 * @author Alexey Stashok
 */
public class DefaultSelectorHandler implements SelectorHandler {
    private static final long DEFAULT_SELECT_TIMEOUT_MILLIS = 30000;

    private static final Logger logger = Grizzly.logger(DefaultSelectorHandler.class);

    public static final boolean IS_WORKAROUND_SELECTOR_SPIN =
            System.getProperty("os.name").equalsIgnoreCase("linux") &&
                !System.getProperty("java.version").startsWith("1.7");
    
    protected final long selectTimeout;

    // Selector spin workaround artifacts

    /**
     * The threshold for detecting selector.select spin on linux,
     * used for enabling workaround to prevent server from hanging.
     */
    private static final int SPIN_RATE_THRESHOLD = 2000;

    // ----------------

    public DefaultSelectorHandler() {
        this(DEFAULT_SELECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    public DefaultSelectorHandler(final long selectTimeout, final TimeUnit timeunit) {
        this.selectTimeout = TimeUnit.MILLISECONDS.convert(selectTimeout, timeunit);
    }

    @Override
    public long getSelectTimeout() {
        return selectTimeout;
    }

    @Override
    public void preSelect(SelectorRunner selectorRunner) throws IOException {
        processPendingTasks(selectorRunner);
    }

    @Override
    public Set<SelectionKey> select(final SelectorRunner selectorRunner)
            throws IOException {
        final Selector selector = selectorRunner.getSelector();
        final boolean hasSelectedKeys;
        if (selectorRunner.getPostponedTasks().isEmpty()) {
            hasSelectedKeys = selector.select(selectTimeout) > 0;
        } else {
            hasSelectedKeys = selector.selectNow() > 0;
        }

        if (IS_WORKAROUND_SELECTOR_SPIN) {
            selectorRunner.checkSelectorSpin(hasSelectedKeys, SPIN_RATE_THRESHOLD);
        }

        return hasSelectedKeys ? selector.selectedKeys() :
                Collections.<SelectionKey>emptySet();
    }

    @Override
    public void postSelect(final SelectorRunner selectorRunner) throws IOException {
    }

    @Override
    public void registerKeyInterest(final SelectorRunner selectorRunner,
            final SelectionKey key, final int interest) throws IOException {
        if (isSelectorRunnerThread(selectorRunner)) {
            registerKey0(key, interest);
        } else {
            addPendingTaskOptimized(selectorRunner,
                    new RegisterKeyTask(key, interest));
        }
    }

    @Override
    public void deregisterKeyInterest(final SelectorRunner selectorRunner,
                                      final SelectionKey key, final int interest) throws IOException {
        if (isSelectorRunnerThread(selectorRunner)) {
            deregisterKey0(key, interest);
        } else {
            addPendingTaskOptimized(selectorRunner,
                    new DeRegisterKeyTask(key, interest));
        }
    }

    @Override
    public void registerChannel(final SelectorRunner selectorRunner,
            final SelectableChannel channel, final int interest,
            final Object attachment) throws IOException {
        
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
                SafeFutureImpl.create();

        if (isSelectorRunnerThread(selectorRunner)) {
            registerChannel0(selectorRunner, channel, interest, attachment,
                    completionHandler, future);
        } else {
            addPendingTask(selectorRunner,
                    new RegisterChannelOperation(
                    channel, interest, attachment, future, completionHandler));
        }

        return future;
    }

    @Override
    public void deregisterChannel(final SelectorRunner selectorRunner,
                                  final SelectableChannel channel)
            throws IOException {

        final Future<RegisterChannelResult> future =
                deregisterChannelAsync(selectorRunner, channel, null);
        try {
            future.get(selectTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public GrizzlyFuture<RegisterChannelResult> deregisterChannelAsync(
            final SelectorRunner selectorRunner, final SelectableChannel channel,
            final CompletionHandler<RegisterChannelResult> completionHandler)
            throws IOException {

        final FutureImpl<RegisterChannelResult> future =
                SafeFutureImpl.create();

        if (isSelectorRunnerThread(selectorRunner)) {
            deregisterChannel0(selectorRunner, channel,
                    completionHandler, future);
        } else {
            addPendingTask(selectorRunner, new DeregisterChannelOperation(
                    channel, future, completionHandler));
        }

        return future;
    }

    @Override
    public GrizzlyFuture<Runnable> executeInSelectorThread(
            final SelectorRunner selectorRunner, final Runnable runnableTask,
            final CompletionHandler<Runnable> completionHandler) {
        if (isSelectorRunnerThread(selectorRunner)) {
            try {
                runnableTask.run();
            } catch (Exception e) {
                if (completionHandler != null) {
                    completionHandler.failed(e);
                }
                
                return ReadyFutureImpl.create(e);
            }
            if (completionHandler != null) {
                completionHandler.completed(runnableTask);
            }
            
            return ReadyFutureImpl.create(runnableTask);
        } else {
            final FutureImpl<Runnable> future = SafeFutureImpl.create();

            addPendingTask(selectorRunner, new RunnableTask(runnableTask,
                    future, completionHandler));
            return future;
        }
    }

    private void addPendingTask(final SelectorRunner selectorRunner,
            final SelectorHandlerTask task) {

        if (selectorRunner == null) {
            task.cancel();
            return;
        }

        addPendingTaskOptimized(selectorRunner, task);
        
        if (selectorRunner.isStop() &&
                selectorRunner.getPendingTasks().remove(task)) {
            task.cancel();
        }
    }

    private void addPendingTaskOptimized(final SelectorRunner selectorRunner,
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

    private void registerKey0(final SelectionKey selectionKey, final int interest) {
        if (selectionKey.isValid()) {
            final int currentOps = selectionKey.interestOps();
            if ((currentOps & interest) != interest) {
                selectionKey.interestOps(currentOps | interest);
            }
        }
    }

    private void deregisterKey0(final SelectionKey selectionKey, final int interest) {
        if (selectionKey.isValid()) {
            final int currentOps = selectionKey.interestOps();
            if ((currentOps & interest) != 0) {
                selectionKey.interestOps(currentOps & (~interest));
            }
        }
    }

    private void registerChannel0(final SelectorRunner selectorRunner,
            final SelectableChannel channel, final int interest,
            final Object attachment,
            final CompletionHandler<RegisterChannelResult> completionHandler,
            final FutureImpl<RegisterChannelResult> future) throws IOException {

        if (future == null || !future.isCancelled()) {
            try {
                if (channel.isOpen()) {

                    final Selector selector = selectorRunner.getSelector();
                    
                    final SelectionKey key = channel.keyFor(selector);

                    // Check whether the channel has been registered on a selector
                    if (key == null || key.isValid()) {
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
                    } else {
                        // Channel has been registered already,
                        // but the key is not valid
                        final Queue<SelectorHandlerTask> postponedTasks =
                                selectorRunner.getPostponedTasks();
                        final RegisterChannelOperation operation =
                                new RegisterChannelOperation(channel, interest,
                                attachment, future, completionHandler);

                        postponedTasks.add(operation);
                    }
                } else {
                    failChannelRegistration(completionHandler, future,
                            new ClosedChannelException());
                }
            } catch (IOException e) {
                failChannelRegistration(completionHandler, future, e);
                throw e;
            }
        }
    }

    private void failChannelRegistration(
            final CompletionHandler<RegisterChannelResult> completionHandler,
            final FutureImpl<RegisterChannelResult> future,
            final Throwable error) {
            if (completionHandler != null) {
                completionHandler.failed(error);
            }

            if (future != null) {
                future.failure(error);
            }
    }

    private void deregisterChannel0(final SelectorRunner selectorRunner,
                                    final SelectableChannel channel,
                                    final CompletionHandler<RegisterChannelResult> completionHandler,
                                    final FutureImpl<RegisterChannelResult> future) throws IOException {

        if (future == null || !future.isCancelled()) {
            final Throwable error;
            if (channel.isOpen()) {

                final Selector selector = selectorRunner.getSelector();

                final SelectionKey key = channel.keyFor(selector);

                // Check whether the channel has been registered on a selector
                if (key != null) {
                    // If channel is registered
                    selectorRunner.getTransport().getSelectionKeyHandler().cancel(key);

                    selectorRunner.getTransport().
                            getSelectionKeyHandler().
                            onKeyDeregistered(key);

                    final RegisterChannelResult result =
                            new RegisterChannelResult(selectorRunner,
                            key, channel);

                    if (completionHandler != null) {
                        completionHandler.completed(result);
                    }
                    if (future != null) {
                        future.result(result);
                    }
                    return;
                }

                error = new IllegalStateException("Channel is not registered");
            } else {
                error = new ClosedChannelException();
            }
            
            if (completionHandler != null) {
                completionHandler.failed(error);
            }

            if (future != null) {
                future.failure(error);
            }

        }
    }
    
    @Override
    public boolean onSelectorClosed(final SelectorRunner selectorRunner) {
        try {
            selectorRunner.workaroundSelectorSpin();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSelectorRunnerThread(final SelectorRunner selectorRunner) {
        return selectorRunner != null &&
                Thread.currentThread() == selectorRunner.getRunnerThread();
    }

    protected final class RegisterKeyTask implements SelectorHandlerTask {
        private final SelectionKey selectionKey;
        private final int interest;

        public RegisterKeyTask(final SelectionKey selectionKey, final int interest) {
            this.selectionKey = selectionKey;
            this.interest = interest;
        }

        @Override
        public void run(final SelectorRunner selectorRunner) throws IOException {
            SelectionKey localSelectionKey = selectionKey;
            if (IS_WORKAROUND_SELECTOR_SPIN) {
                localSelectionKey = selectorRunner.checkIfSpinnedKey(selectionKey);
            }

            registerKey0(localSelectionKey, interest);
        }

        @Override
        public void cancel() {
        }
    }

    protected final class DeRegisterKeyTask implements SelectorHandlerTask {
        private final SelectionKey selectionKey;
        private final int interest;

        public DeRegisterKeyTask(SelectionKey selectionKey, int interest) {
            this.selectionKey = selectionKey;
            this.interest = interest;
        }

        @Override
        public void run(final SelectorRunner selectorRunner) throws IOException {
            SelectionKey localSelectionKey = selectionKey;
            if (IS_WORKAROUND_SELECTOR_SPIN) {
                localSelectionKey = selectorRunner.checkIfSpinnedKey(selectionKey);
            }

            deregisterKey0(localSelectionKey, interest);
        }

        @Override
        public void cancel() {
        }
    }

    protected final class RegisterChannelOperation implements SelectorHandlerTask {
        private final SelectableChannel channel;
        private final int interest;
        private final Object attachment;
        private final FutureImpl<RegisterChannelResult> future;
        private final CompletionHandler<RegisterChannelResult> completionHandler;

        private RegisterChannelOperation(final SelectableChannel channel,
                final int interest, final Object attachment,
                final FutureImpl<RegisterChannelResult> future,
                final CompletionHandler<RegisterChannelResult> completionHandler) {
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

        @Override
        public void cancel() {
            if (completionHandler != null) {
                completionHandler.failed(new IOException("Selector is closed"));
            }

            if (future != null) {
                future.failure(new IOException("Selector is closed"));
            }
        }
    }

    protected final class DeregisterChannelOperation implements SelectorHandlerTask {
        private final SelectableChannel channel;
        private final FutureImpl<RegisterChannelResult> future;
        private final CompletionHandler<RegisterChannelResult> completionHandler;

        private DeregisterChannelOperation(final SelectableChannel channel,
                                           final FutureImpl<RegisterChannelResult> future,
                                           final CompletionHandler<RegisterChannelResult> completionHandler) {
            this.channel = channel;
            this.future = future;
            this.completionHandler = completionHandler;
        }

        @Override
        public void run(final SelectorRunner selectorRunner) throws IOException {
            deregisterChannel0(selectorRunner, channel, completionHandler, future);
        }

        @Override
        public void cancel() {
            if (completionHandler != null) {
                completionHandler.failed(new IOException("Selector is closed"));
            }

            if (future != null) {
                future.failure(new IOException("Selector is closed"));
            }
        }
    }
    
    protected static final class RunnableTask implements SelectorHandlerTask {
        private final Runnable task;
        private final FutureImpl<Runnable> future;
        private final CompletionHandler<Runnable> completionHandler;

        private RunnableTask(final Runnable task, final FutureImpl<Runnable> future,
                final CompletionHandler<Runnable> completionHandler) {
            this.task = task;
            this.future = future;
            this.completionHandler = completionHandler;
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
                    completionHandler.failed(t);
                }

                future.failure(t);
            }
        }

        @Override
        public void cancel() {
            if (completionHandler != null) {
                completionHandler.failed(new IOException("Selector is closed"));
            }

            if (future != null) {
                future.failure(new IOException("Selector is closed"));
            }
        }
    }
}
