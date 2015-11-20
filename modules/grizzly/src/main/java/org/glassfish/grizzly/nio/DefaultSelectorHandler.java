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

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.utils.Futures;
import org.glassfish.grizzly.utils.JdkVersion;

/**
 * Default implementation of NIO <code>SelectorHandler</code>
 * 
 * @author Alexey Stashok
 */
public class DefaultSelectorHandler implements SelectorHandler {
    private static final long DEFAULT_SELECT_TIMEOUT_MILLIS = 30000;

    private static final Logger logger = Grizzly.logger(DefaultSelectorHandler.class);

    public static final boolean IS_WORKAROUND_SELECTOR_SPIN =
            Boolean.getBoolean(DefaultSelectorHandler.class.getName() + ".force-selector-spin-detection") ||
            (System.getProperty("os.name").equalsIgnoreCase("linux") &&
                    JdkVersion.getJdkVersion().compareTo("1.7.0") < 0);
    
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
    public boolean preSelect(final SelectorRunner selectorRunner) throws IOException {
        return processPendingTasks(selectorRunner);
    }

    @Override
    public Set<SelectionKey> select(final SelectorRunner selectorRunner)
            throws IOException {
        final Selector selector = selectorRunner.getSelector();
        final boolean hasPostponedTasks =
                !selectorRunner.getPostponedTasks().isEmpty();
        
        // The selector.select(...) returns the *new* SelectionKey count,
        // so it may return 0 even in the case, when there are unprocessed, but
        // ready SelectionKeys in the Selector's selected key set.
        if (!hasPostponedTasks) {
            selector.select(selectTimeout);
        } else {
            selector.selectNow();
        }

        final Set<SelectionKey> selectedKeys = selector.selectedKeys();

        if (IS_WORKAROUND_SELECTOR_SPIN) {
            selectorRunner.checkSelectorSpin(
                    !selectedKeys.isEmpty() || hasPostponedTasks,
                    SPIN_RATE_THRESHOLD);
        }

        return selectedKeys;
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
            selectorRunner.addPendingTask(new RegisterKeyTask(key, interest));
        }
    }

    private static void registerKey0(final SelectionKey selectionKey, final int interest) {
        if (selectionKey.isValid()) {
            final int currentOps = selectionKey.interestOps();
            if ((currentOps & interest) != interest) {
                selectionKey.interestOps(currentOps | interest);
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void deregisterKeyInterest(final SelectorRunner selectorRunner,
            final SelectionKey key, final int interest) throws IOException {
        if (key.isValid()) {
            final int currentOps = key.interestOps();
            if ((currentOps & interest) != 0) {
                key.interestOps(currentOps & (~interest));
            }
        }
    }

    @Override
    public void registerChannel(final SelectorRunner selectorRunner,
            final SelectableChannel channel, final int interest,
            final Object attachment) throws IOException {
        
        final FutureImpl<RegisterChannelResult> future =
                SafeFutureImpl.create();

        registerChannelAsync(selectorRunner, channel, interest,
                attachment, Futures.toCompletionHandler(future));
        try {
            future.get(selectTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public void registerChannelAsync(
            SelectorRunner selectorRunner, SelectableChannel channel,
            int interest, Object attachment,
            CompletionHandler<RegisterChannelResult> completionHandler) {

        if (isSelectorRunnerThread(selectorRunner)) {
            registerChannel0(selectorRunner, channel, interest, attachment,
                    completionHandler);
        } else {
            addPendingTask(selectorRunner,
                    new RegisterChannelOperation(
                    channel, interest, attachment, completionHandler));
        }
    }

    @Override
    public void deregisterChannel(final SelectorRunner selectorRunner,
                                  final SelectableChannel channel)
            throws IOException {

        final FutureImpl<RegisterChannelResult> future =
                SafeFutureImpl.create();

        deregisterChannelAsync(selectorRunner, channel,
                Futures.toCompletionHandler(future));
        try {
            future.get(selectTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public void deregisterChannelAsync(
            final SelectorRunner selectorRunner, final SelectableChannel channel,
            final CompletionHandler<RegisterChannelResult> completionHandler) {

        if (isSelectorRunnerThread(selectorRunner)) {
            deregisterChannel0(selectorRunner, channel,
                    completionHandler);
        } else {
            addPendingTask(selectorRunner, new DeregisterChannelOperation(
                    channel, completionHandler));
        }
    }

    @Override
    public void execute(
            final SelectorRunner selectorRunner, final Task task,
            final CompletionHandler<Task> completionHandler) {
        if (isSelectorRunnerThread(selectorRunner)) {
            try {
                task.run();
                if (completionHandler != null) {
                    completionHandler.completed(task);
                }
            } catch (Exception e) {
                if (completionHandler != null) {
                    completionHandler.failed(e);
                }
            }            
        } else {
            addPendingTask(selectorRunner, new RunnableTask(task,
                    completionHandler));
        }
    }

    @Override
    public void enque(
            final SelectorRunner selectorRunner, final Task task,
            final CompletionHandler<Task> completionHandler) {
        
        if (isSelectorRunnerThread(selectorRunner)) {
            // If this is Selector thread - put the task to postponed queue
            // it's faster than using pending task queue, which is thread-safe
            final Queue<SelectorHandlerTask> postponedTasks =
                    selectorRunner.getPostponedTasks();
            postponedTasks.offer(new RunnableTask(task, completionHandler));
            
        } else {
            addPendingTask(selectorRunner, new RunnableTask(task,
                    completionHandler));
        }
    }
    
    private void addPendingTask(final SelectorRunner selectorRunner,
            final SelectorHandlerTask task) {

        if (selectorRunner == null) {
            task.cancel();
            return;
        }

        selectorRunner.addPendingTask(task);
        
        if (selectorRunner.isStop() &&
                selectorRunner.getPendingTasks().remove(task)) {
            task.cancel();
        }
    }

    private boolean processPendingTasks(final SelectorRunner selectorRunner)
            throws IOException {

        return processPendingTaskQueue(selectorRunner, selectorRunner.obtainPostponedTasks())
                &&
                (!selectorRunner.hasPendingTasks ||
                processPendingTaskQueue(selectorRunner, selectorRunner.getPendingTasks()));
    }

    private boolean processPendingTaskQueue(final SelectorRunner selectorRunner,
            final Queue<SelectorHandlerTask> selectorHandlerTasks)
            throws IOException {
        SelectorHandlerTask selectorHandlerTask;
        while((selectorHandlerTask = selectorHandlerTasks.poll()) != null) {
            if (!selectorHandlerTask.run(selectorRunner)) {
                return false;
            }
        }
        
        return true;
    }

    private static void registerChannel0(final SelectorRunner selectorRunner,
            final SelectableChannel channel, final int interest,
            final Object attachment,
            final CompletionHandler<RegisterChannelResult> completionHandler) {

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
                } else {
                    // Channel has been registered already,
                    // but the key is not valid
                    final Queue<SelectorHandlerTask> postponedTasks =
                            selectorRunner.getPostponedTasks();
                    final RegisterChannelOperation operation =
                            new RegisterChannelOperation(channel, interest,
                            attachment, completionHandler);

                    postponedTasks.add(operation);
                }
            } else {
                failChannelRegistration(completionHandler,
                        new ClosedChannelException());
            }
        } catch (IOException e) {
            failChannelRegistration(completionHandler, e);
        }
    }

    private static void failChannelRegistration(
            final CompletionHandler<RegisterChannelResult> completionHandler,
            final Throwable error) {

        if (completionHandler != null) {
            completionHandler.failed(error);
        }
    }

    private static void deregisterChannel0(final SelectorRunner selectorRunner,
                                    final SelectableChannel channel,
                                    final CompletionHandler<RegisterChannelResult> completionHandler) {

        final Throwable error;
        try {
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
                    return;
                }

                error = new IllegalStateException("Channel is not registered");
            } else {
                error = new ClosedChannelException();
            }
            
            Futures.notifyFailure(null, completionHandler, error);
        } catch (IOException e) {
            Futures.notifyFailure(null, completionHandler, e);
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

    private static boolean isSelectorRunnerThread(final SelectorRunner selectorRunner) {
        return selectorRunner != null &&
                Thread.currentThread() == selectorRunner.getRunnerThread();
    }

    protected static final class RegisterKeyTask implements SelectorHandlerTask {
        private final SelectionKey selectionKey;
        private final int interest;

        public RegisterKeyTask(final SelectionKey selectionKey, final int interest) {
            this.selectionKey = selectionKey;
            this.interest = interest;
        }

        @Override
        public boolean run(final SelectorRunner selectorRunner) throws IOException {
            SelectionKey localSelectionKey = selectionKey;
            if (IS_WORKAROUND_SELECTOR_SPIN) {
                localSelectionKey = selectorRunner.checkIfSpinnedKey(selectionKey);
            }

            registerKey0(localSelectionKey, interest);
            
            return true;
        }

        @Override
        public void cancel() {
        }
    }

    protected static final class RegisterChannelOperation implements SelectorHandlerTask {
        private final SelectableChannel channel;
        private final int interest;
        private final Object attachment;
        private final CompletionHandler<RegisterChannelResult> completionHandler;

        private RegisterChannelOperation(final SelectableChannel channel,
                final int interest, final Object attachment,
                final CompletionHandler<RegisterChannelResult> completionHandler) {
            this.channel = channel;
            this.interest = interest;
            this.attachment = attachment;
            this.completionHandler = completionHandler;
        }

        @Override
        public boolean run(final SelectorRunner selectorRunner) throws IOException {
            registerChannel0(selectorRunner, channel, interest,
                    attachment, completionHandler);
            return true;
        }

        @Override
        public void cancel() {
            if (completionHandler != null) {
                completionHandler.failed(new IOException("Selector is closed"));
            }
        }
    }
    
    protected static final class RunnableTask implements SelectorHandlerTask {
        private final Task task;
        private final CompletionHandler<Task> completionHandler;

        private RunnableTask(final Task task,
                final CompletionHandler<Task> completionHandler) {
            this.task = task;
            this.completionHandler = completionHandler;
        }

        @Override
        public boolean run(final SelectorRunner selectorRunner) throws IOException {
            boolean continueExecution = true;
            
            try {
                continueExecution = task.run();
                if (completionHandler != null) {
                    completionHandler.completed(task);
                }
            } catch (Throwable t) {
                logger.log(Level.FINEST, "doExecutePendiongIO failed.", t);
                
                if (completionHandler != null) {
                    completionHandler.failed(t);
                }
            }
            
            return continueExecution;
        }

        @Override
        public void cancel() {
            if (completionHandler != null) {
                completionHandler.failed(new IOException("Selector is closed"));
            }
        }
    }
    

    protected static final class DeregisterChannelOperation implements SelectorHandlerTask {
        private final SelectableChannel channel;
        private final CompletionHandler<RegisterChannelResult> completionHandler;

        private DeregisterChannelOperation(final SelectableChannel channel,
                                           final CompletionHandler<RegisterChannelResult> completionHandler) {
            this.channel = channel;
            this.completionHandler = completionHandler;
        }

        @Override
        public boolean run(final SelectorRunner selectorRunner) throws IOException {
            deregisterChannel0(selectorRunner, channel, completionHandler);
            return true;
        }

        @Override
        public void cancel() {
            if (completionHandler != null) {
                completionHandler.failed(new IOException("Selector is closed"));
            }
        }
    }
}
