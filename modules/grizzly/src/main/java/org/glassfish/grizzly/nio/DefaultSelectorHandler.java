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
package org.glassfish.grizzly.nio;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.util.ConcurrentQueuePool;
import org.glassfish.grizzly.util.ObjectPool;
import org.glassfish.grizzly.util.PoolableObject;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.util.LinkedTransferQueue;

/**
 * Default implementation of NIO <code>SelectorHandler</code>
 * 
 * @author Alexey Stashok
 */
public class DefaultSelectorHandler implements SelectorHandler {
    protected enum OpType {NONE, REG_CHANNEL, REG_KEY, UNREG_KEY};

    protected long selectTimeout;

    protected final ObjectPool<SelectionKeyOperation> pendingOperationPool;

    public DefaultSelectorHandler() {
        pendingOperationPool =
                new ConcurrentQueuePool<SelectionKeyOperation>() {
                    @Override
                    public SelectionKeyOperation newInstance() {
                        return new SelectionKeyOperation();
                    }
                };
    }

    public long getSelectTimeout() {
        return selectTimeout;
    }

    public void setSelectTimeout(long selectTimeout) {
        this.selectTimeout = selectTimeout;
    }    

    public void preSelect(SelectorRunner selectorRunner) throws IOException {
        processPendingOperations(selectorRunner);
    }

    public Set<SelectionKey> select(SelectorRunner selectorRunner)
            throws IOException {
        Selector selector = selectorRunner.getSelector();

        selector.select(selectTimeout);
        return selector.selectedKeys();
    }

    public void postSelect(SelectorRunner selectorRunner) throws IOException {
    }

    public void registerKey(SelectorRunner selectorRunner, SelectionKey key,
            int interest) throws IOException {
        if (Thread.currentThread() == selectorRunner.getRunnerThread()) {
            registerKey0(key, interest);
        } else {
            SelectionKeyOperation operation = pendingOperationPool.poll();
            operation.setOpType(OpType.REG_KEY);
            operation.setSelectionKey(key);
            operation.setInterest(interest);
            addPendingOperation(selectorRunner, operation);
        }
    }

    public void unregisterKey(SelectorRunner selectorRunner, SelectionKey key,
            int interest) throws IOException {
        if (Thread.currentThread() == selectorRunner.getRunnerThread()) {
            unregisterKey0(key, interest);
        } else {
            SelectionKeyOperation operation = pendingOperationPool.poll();
            operation.setOpType(OpType.UNREG_KEY);
            operation.setSelectionKey(key);
            operation.setInterest(interest);
            addPendingOperation(selectorRunner, operation);
        }
    }

    public void registerChannel(SelectorRunner selectorRunner,
            SelectableChannel channel, int interest, Object attachment)
            throws IOException {
        
        Future<RegisterChannelResult> future =
                registerChannelAsync(selectorRunner, channel, interest,
                attachment, null);
        try {
            future.get(selectTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public Future<RegisterChannelResult> registerChannelAsync(
            SelectorRunner selectorRunner, SelectableChannel channel,
            int interest, Object attachment,
            CompletionHandler<RegisterChannelResult> completionHandler)
            throws IOException {

        FutureImpl<RegisterChannelResult> future =
                new FutureImpl<RegisterChannelResult>();

        if (Thread.currentThread() == selectorRunner.getRunnerThread()) {
            registerChannel0(selectorRunner, channel, interest, attachment,
                    completionHandler, future);
        } else {
            SelectionKeyOperation operation = pendingOperationPool.poll();
            operation.setOpType(OpType.REG_CHANNEL);
            operation.setChannel(channel);
            operation.setInterest(interest);
            operation.setAttachment(attachment);

            operation.setFuture(future);

            operation.setCompletionHandler(completionHandler);

            addPendingOperation(selectorRunner, operation);
        }

        return future;
    }

    protected void addPendingOperation(SelectorRunner selectorRunner,
            SelectionKeyOperation operation) {
        LinkedTransferQueue<SelectionKeyOperation> pendingSelectorOps =
                getSelectorPendingOperations(selectorRunner);
        pendingSelectorOps.offer(operation);

        selectorRunner.getSelector().wakeup();
    }

    protected void processPendingOperations(SelectorRunner selectorRunner)
            throws IOException {

        LinkedTransferQueue<SelectionKeyOperation> pendingSelectorOps =
                getSelectorPendingOperations(selectorRunner);

        for (Iterator<SelectionKeyOperation> it = pendingSelectorOps.iterator();
                it.hasNext();) {

            SelectionKeyOperation pendingOperation = it.next();
            it.remove();
            processPendingOperation(selectorRunner, pendingOperation);
            pendingOperationPool.offer(pendingOperation);
        }
    }

    protected void processPendingOperation(SelectorRunner selectorRunner,
            SelectionKeyOperation operation) throws IOException {
        OpType opType = operation.getOpType();
        int interest = operation.getInterest();

        switch (opType) {
            case REG_CHANNEL:
                registerChannel0(selectorRunner, operation.getChannel(),
                        interest,
                        operation.getAttachment(), 
                        operation.getCompletionHandler(),
                        operation.getFuture());
                break;
            case REG_KEY:
                registerKey0(operation.getSelectionKey(), interest);
                break;
            case UNREG_KEY:
                unregisterKey0(operation.getSelectionKey(), interest);
                break;
            default:
                throw new IllegalStateException("Unknown operation type: " +
                        opType);
        }
    }

    protected LinkedTransferQueue<SelectionKeyOperation>
            getSelectorPendingOperations(SelectorRunner selectorRunner) {
        return selectorRunner.getPendingOperations();
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
                    SelectionKey registeredSelectionKey =
                            channel.register(selectorRunner.getSelector(),
                            interest,
                            attachment);

                    selectorRunner.getTransport().
                            getSelectionKeyHandler().
                            onKeyRegistered(registeredSelectionKey);

                    RegisterChannelResult result =
                            new RegisterChannelResult(selectorRunner,
                            registeredSelectionKey, channel);

                    if (completionHandler != null) {
                        completionHandler.completed(null, result);
                    }
                    if (future != null) {
                        future.setResult(result);
                    }
                } else {
                    Throwable error = new ClosedChannelException();
                    if (completionHandler != null) {
                        completionHandler.failed(null, error);
                    }
                    
                    if (future != null) {
                        future.failure(error);
                    }
                }
            } catch (IOException e) {
                if (completionHandler != null) {
                    completionHandler.failed(null, e);
                }
                
                if (future != null) {
                    future.failure(e);
                }
                throw e;
            }
        }
    }

    protected static class SelectionKeyOperation implements PoolableObject {
        private OpType opType;
        private SelectionKey selectionKey;
        private SelectableChannel channel;
        private int interest;
        private Object attachment;
        private FutureImpl<RegisterChannelResult> future;
        private CompletionHandler completionHandler;

        public OpType getOpType() {
            return opType;
        }

        public void setOpType(OpType opType) {
            this.opType = opType;
        }

        public SelectionKey getSelectionKey() {
            return selectionKey;
        }

        public void setSelectionKey(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        public SelectableChannel getChannel() {
            return channel;
        }

        public void setChannel(SelectableChannel channel) {
            this.channel = channel;
        }

        public int getInterest() {
            return interest;
        }

        public void setInterest(int interest) {
            this.interest = interest;
        }

        public Object getAttachment() {
            return attachment;
        }

        public void setAttachment(Object attachment) {
            this.attachment = attachment;
        }

        public FutureImpl<RegisterChannelResult> getFuture() {
            return future;
        }

        public void setFuture(FutureImpl<RegisterChannelResult> future) {
            this.future = future;
        }

        public CompletionHandler getCompletionHandler() {
            return completionHandler;
        }

        public void setCompletionHandler(CompletionHandler completionHandler) {
            this.completionHandler = completionHandler;
        }

        public void prepare() {
        }

        public void release() {
            opType = OpType.NONE;
            interest = 0;
            setSelectionKey(null);
            setChannel(null);
            future = null;
            completionHandler = null;
            attachment = null;
        }
    }
}
