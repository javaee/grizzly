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

package com.sun.grizzly;

import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.async.AsyncQueueReadable;
import com.sun.grizzly.async.AsyncQueueReader;
import com.sun.grizzly.async.AsyncQueueWritable;
import com.sun.grizzly.async.AsyncQueueWriter;
import com.sun.grizzly.async.AsyncReadCallbackHandler;
import com.sun.grizzly.async.AsyncReadCondition;
import com.sun.grizzly.async.AsyncQueueReadUnit;
import com.sun.grizzly.async.AsyncQueueReaderContextTask;
import com.sun.grizzly.async.AsyncWriteCallbackHandler;
import com.sun.grizzly.async.AsyncQueueWriteUnit;
import com.sun.grizzly.async.AsyncQueueWriterContextTask;
import com.sun.grizzly.async.ByteBufferCloner;
import com.sun.grizzly.util.AttributeHolder;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.SelectionKeyAttachment;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * This Object is used to share information between the Grizzly Framework
 * classes and {@link ProtocolFilter} implementation. Since {@link Context}
 * is a pooled resource
 * {@link Controller#pollContext(java.nio.channels.SelectionKey)}
 * transactions using {@link Context} outside its {@link ProtocolChain}
 * must invoke {@link #incrementRefCount()} and
 * {@link Controller#returnContext(com.sun.grizzly.Context)} to keep its pooling
 * intact.
 * @author Jeanfrancois Arcand
 */
public class NIOContext implements Context {

    /**
     * The current {@link SelectionKey} interest ops this Context is processing.
     */
    private OpType currentOpType;
    /**
     * The {@link ProtocolChain} used to execute this {@link Context}
     */
    private ProtocolChain protocolChain;

    /**
     * Used to share object between {@link ProtocolFilter}.
     * WARNING: Attributes which are added are never removed automatically
     * The removal operation must be done explicitly inside a  {@link ProtocolFilter}.
     */
    private Map<String, Object> attributes = null;
    /**
     * The current connection (@link SelectionKey}.
     */
    private SelectionKey key;
    /**
     * The {@link SelectorHandler} associated with this Context.
     */
    private SelectorHandler selectorHandler;
    /**
     * The {@link Controller} associated with this Context.
     */
    private Controller controller;
    /**
     * The state's of the key registration.
     */
    private KeyRegistrationState keyRegistrationState = KeyRegistrationState.REGISTER;
    /**
     * The current {@linl ExecutorService} that execute this object.
     */
    private ExecutorService threadPool;
    /**
     * An optional {@link IOEvent} that can be invoked
     * before the {@link ProtocolChain} is invoked.
     */
    private IOEvent<Context> ioEvent;
    /**
     * {@link AsyncQueueReader}
     */
    private AsyncQueueReader asyncQueueReader;
    /**
     * {@link AsyncQueueWriter}
     */
    private AsyncQueueWriter asyncQueueWriter;
    /**
     * {@link AsyncQueueReadable}
     */
    private AsyncQueueReadable asyncQueueReadable;
    /**
     * {@link AsyncQueueWritable}
     */
    private AsyncQueueWritable asyncQueueWritable;
    /**
     * Is this context suspended.
     */
    private boolean isSuspended = false;
    /**
     *  Reference Counter indicating how many Threads share this Context.
     *  Starts at one already counting {@link WorkerThread}.
     */
    private AtomicInteger refCounter = new AtomicInteger(1);

    private  ProtocolChainContextTask  protocolChainContextTask;

    private CallbackHandlerContextTask callbackHandlerContextTask;

    private AsyncQueueReaderContextTask asyncQueueReaderContextTask;

    private AsyncQueueWriterContextTask asyncQueueWriterContextTask;

    /**
     * Constructor
     */
    public NIOContext() {
    }


    public void copyTo(Copyable copy) {
        NIOContext copyContext = (NIOContext) copy;
        copyContext.currentOpType = currentOpType;
        copyContext.protocolChain = protocolChain;
        if (attributes != null) {
            copyContext.attributes = new HashMap<String, Object>(attributes);
        }
        copyContext.key = key;
        copyContext.selectorHandler = selectorHandler;
        copyContext.controller = controller;
        copyContext.keyRegistrationState = keyRegistrationState;
        copyContext.threadPool = threadPool;
        copyContext.ioEvent = ioEvent;
        copyContext.asyncQueueReader = asyncQueueReader;
        copyContext.asyncQueueWriter = asyncQueueWriter;
    }

    public ProtocolChainContextTask getProtocolChainContextTask() {
        if (protocolChainContextTask == null){
            protocolChainContextTask = new ProtocolChainContextTask();
        }
        return protocolChainContextTask;
    }

    public CallbackHandlerContextTask getCallbackHandlerContextTask(CallbackHandler callbackHandler) {
        if (callbackHandlerContextTask == null){
            callbackHandlerContextTask = new CallbackHandlerContextTask(callbackHandler);
        }else{
            callbackHandlerContextTask.setCallBackHandler(callbackHandler);
        }
        return callbackHandlerContextTask;
    }

    public AsyncQueueReaderContextTask getAsyncQueueReaderContextTask(AsyncQueueReader asyncQueueReader) {
        if (asyncQueueReaderContextTask == null){
            asyncQueueReaderContextTask = new AsyncQueueReaderContextTask(asyncQueueReader);
        }else{
            asyncQueueReaderContextTask.setAsyncQueueReader(asyncQueueReader);
        }
        return asyncQueueReaderContextTask;
    }

    public AsyncQueueWriterContextTask getAsyncQueueWriterContextTask(AsyncQueueWriter asyncQueueWriter) {
        if (asyncQueueWriterContextTask == null){
            asyncQueueWriterContextTask = new AsyncQueueWriterContextTask(asyncQueueWriter);
        }else{
            asyncQueueWriterContextTask.setAsyncQueueWriter(asyncQueueWriter);
        }
        return asyncQueueWriterContextTask;
    }

    /**
     * Remove a key/value object.
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
     * Return {@link AttributeHolder}, which corresponds to the
     * given {@link AttributeScope}>
     *
     * @param scope - {@link AttributeScope}>
     * @return - {@link AttributeHolder} instance, which contains
     *           {@link AttributeScope}> attributes
     */
    public AttributeHolder getAttributeHolderByScope(AttributeScope scope) {
        AttributeHolder holder = null;
        switch (scope) {
            case REQUEST:
                holder = this;
                break;
            case CONNECTION:
                Object attachment = getSelectionKey().attachment();
                if (attachment instanceof AttributeHolder) {
                    holder = (AttributeHolder) attachment;
                }
                break;
            case SELECTOR:
                holder = selectorHandler;
                break;
            case CONTROLLER:
                holder = controller;
                break;
        }

        return holder;
    }


    /**
     * Set a {@link Map} of attribute name/value pairs.
     * Old {@link AttributeHolder} values will not be available.
     * Later changes of this {@link Map} will lead to changes to the current
     * {@link AttributeHolder}.
     *
     * @param attributes - map of name/value pairs
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }


    /**
     * Return a {@link Map} of attribute name/value pairs.
     * Updates, performed on the returned {@link Map} will be reflected in
     * this {@link AttributeHolder}
     *
     * @return - {@link Map} of attribute name/value pairs
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }


    /**
     * Return the current {@link SelectionKey}.
     * @return - this Context's SelectionKey
     */
    public SelectionKey getSelectionKey() {
        return key;
    }


    /**
     * Set the connection {@link SelectionKey}.
     * @param key - set this Context's SelectionKey
     */
    public void setSelectionKey(SelectionKey key) {
        this.key = key;
    }


    /**
     * Return the current {@link Controller}.
     * @return - this Context's current {@link Controller}
     */
    public Controller getController() {
        return controller;
    }


    /**
     * Set the current {@link Controller}.
     * @param {@link Controller}
     */
    public void setController(Controller controller) {
        this.controller = controller;
    }


    /**
     * Recycle this instance.
     */
    public void recycle() {
        if (isSuspended) {
            throw new IllegalStateException("The Context has been marked as " +
                    "suspended and cannot be recycled");
        }

        getProtocolChainInstanceHandler().offer(protocolChain);
        key = null;
        keyRegistrationState = KeyRegistrationState.REGISTER;
        currentOpType = null;
        protocolChain = null;
        ioEvent = null;
        asyncQueueReader = null;
        asyncQueueWriter = null;
        if (attributes != null) {
            attributes.clear();
        }
        isSuspended = false;
        refCounter.set(1);
    }


    /**
     * Return {@link SelectionKey}'s next registration state.
     * @return this Context's SelectionKey registration state
     */
    public KeyRegistrationState getKeyRegistrationState() {
        return keyRegistrationState;
    }


    /**
     * Set the {@link SelectionKey}'s next registration state
     * @param {@link keyRegistrationState} - set this Context's SelectionKey
     *        registration state
     */
    public void setKeyRegistrationState(KeyRegistrationState keyRegistrationState) {
        this.keyRegistrationState = keyRegistrationState;
    }


    /**
     * Return {@link ProtocolChain} executed by this instance.
     * @return {@link ProtocolChain} instance
     */
    public ProtocolChain getProtocolChain() {
        return protocolChain;
    }


    /**
     * Set the {@link ProtocolChain} used by this {@link Context}.
     * @param protocolChain instance of {@link ProtocolChain} to be used by the Context
     */
    public void setProtocolChain(ProtocolChain protocolChain) {
        this.protocolChain = protocolChain;
    }


    /**
     * Get the current {@link SelectionKey} interest ops this instance is executing.
     * @return OpType the currentOpType.
     */
    public OpType getCurrentOpType() {
        return currentOpType;
    }


    /**
     * Set the current OpType value.
     * @param currentOpType sets current operation type
     */
    public void setCurrentOpType(OpType currentOpType) {
        this.currentOpType = currentOpType;
    }


    /**
     * Configure the {@link #currentOpType} based on the
     * {@link SelectionKey#readyOps()} values.
     * @param key
     */
    public void configureOpType(SelectionKey key) {
        int readyOps = key.readyOps();
        switch (readyOps) {
            case SelectionKey.OP_CONNECT:
                currentOpType = OpType.OP_CONNECT;
                break;
            case SelectionKey.OP_READ:
                currentOpType = OpType.OP_READ;
                break;
            case SelectionKey.OP_WRITE:
                currentOpType = OpType.OP_WRITE;
                break;
            case (SelectionKey.OP_WRITE | SelectionKey.OP_READ):
                currentOpType = OpType.OP_READ_WRITE;
                break;
            case (SelectionKey.OP_ACCEPT):
                currentOpType = OpType.OP_ACCEPT;
                break;
            /**
             * Fallback to an upcoming OP_CONNECT ops. This happens
             * because the {@link SocketChannel#finishConnect()} has not yet
             * been invoked.
             *
             */
            default:
                currentOpType = OpType.OP_CONNECT;
                break;

        }
    }


    /**
     * Execute this Context using the Controller's thread pool
     * @deprecated
     */
    public void execute() {
        // If a IOEvent has been defined, invoke it first and
        // let its associated CallbackHandler decide if the ProtocolChain
        // be invoked or not.
        Object attachment = SelectionKeyAttachment.getAttachment(key);
        if (ioEvent != null && (attachment instanceof CallbackHandler)) {
            execute(getCallbackHandlerContextTask((CallbackHandler) attachment));
        } else {
            execute(getProtocolChainContextTask());
        }
    }


    /**
     * Execute this Context using the Controller's thread pool
     * @param contextTask {@link ContextTask}, which will be
     *                    executed by {@link ExecutorService}
     */
    public void execute(ContextTask contextTask) {
        execute(contextTask, true);
    }


    /**
     * Execute this Context using either Controller's thread pool or
     * current thread
     * @param contextTask {@link ContextTask}, which will be
     *                    executed by {@link ExecutorService}
     * @param runInSeparateThread if true - {@link ContextTask} will
     *      be executed in separate thread, false - in current thread.
     */
    @SuppressWarnings("unchecked")
    public void execute(ContextTask contextTask, boolean runInSeparateThread) {
        if (protocolChain == null) {
            ProtocolChainInstanceHandler pciHandler = getProtocolChainInstanceHandler();
            protocolChain = pciHandler.poll();
        }

        if (contextTask != null) {
            contextTask.setContext(this);
            if (runInSeparateThread) {
                getThreadPool().execute(contextTask);
            } else {
                try {
                    contextTask.call();
                } catch (Exception e) {
                    Controller.logger().log(Level.SEVERE,
                            "Unexpected exception occured, when executing task: " +
                            contextTask, e);
                }
            }
        }
    }


    /**
     * Return the {@link ProtocolChainInstanceListener} associated with this
     * {@link Context}
     * @return ProtocolChainInstanceListener
     */
    public ProtocolChainInstanceHandler getProtocolChainInstanceHandler() {
        ProtocolChainInstanceHandler protocolChainInstanceHandler =
                selectorHandler.getProtocolChainInstanceHandler();
        return protocolChainInstanceHandler != null ? protocolChainInstanceHandler : controller.getProtocolChainInstanceHandler();
    }


    /**
     * Return the {@link ExecutorService} executing this instance.
     * @return {@link ExecutorService}
     */
    public ExecutorService getThreadPool() {
        if (threadPool == null && controller != null) {
            threadPool = controller.getThreadPool();
        }
        return threadPool;
    }


    /**
     * Set the {@link ExecutorService} that will execute this instance.
     * @param threadPool  the {@link ExecutorService} to set
     */
    public void setThreadPool(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }


    /**
     * Set an optional CallbackHandler.
     * @param ioEvent  the {@link IOEvent} to set
     */
    public void setIOEvent(IOEvent<Context> ioEvent) {
        this.ioEvent = ioEvent;
    }


    /**
     * Return the current {@link IOEvent} associated with this
     * instance.
     * @return IOEvent the current {@link IOEvent} associated with this
     * instance.
     */
    /*protected*/ public IOEvent getIOEvent() {
        return ioEvent;
    }


    /**
     * Return the current {@link Controller#Protocol} this instance is executing.
     * @return the current Controller.Protocol this instance is executing.
     */
    public Controller.Protocol getProtocol() {
        return selectorHandler.protocol();
    }


    /**
     * @Deprecated
     *
     * Set the current {@link Controller#Protocol} this instance is executing.
     * @param protocol The current protocol.
     */
    public void setProtocol(Controller.Protocol protocol) {
    }


    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }


    /**
     * Set the current {@link SelectorHandler} this instance is executing.
     * @param selectorHandler {@link SelectorHandler}
     */
    public void setSelectorHandler(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
    }


    /**
     * Returns {@link AsyncQueueReadable}, assciated with the current
     * {@link Context}. This method is not threadsafe.
     *
     * @return {@link AsyncQueueReadable}
     */
    public AsyncQueueReadable getAsyncQueueReadable() {
        if (asyncQueueReadable == null) {
            asyncQueueReadable = new AsyncQueueReadableContextWrapper();
        }

        return asyncQueueReadable;
    }


    /**
     * Returns {@link AsyncQueueWritable}, assciated with the current
     * {@link Context}. This method is not threadsafe.
     *
     * @return {@link AsyncQueueWritable}
     */
    public AsyncQueueWritable getAsyncQueueWritable() {
        if (asyncQueueWritable == null) {
            asyncQueueWritable = new AsyncQueueWritableContextWrapper();
        }

        return asyncQueueWritable;
    }


    /**
     * Return the {@linkAsyncQueueReader}
     * @return the {@linkAsyncQueueReader}
     */
    public AsyncQueueReader getAsyncQueueReader() {
        return asyncQueueReader;
    }


    /**
     * Set the {@linkAsyncQueueReader}
     * @param asyncQueueReader {@linkAsyncQueueReader}
     */
    protected void setAsyncQueueReader(AsyncQueueReader asyncQueueReader) {
        this.asyncQueueReader = asyncQueueReader;
    }


    /**
     * Return the {@linkAsyncQueueWriter}
     * @return the {@linkAsyncQueueWriter}
     */
    protected AsyncQueueWriter getAsyncQueueWriter() {
        return asyncQueueWriter;
    }


    /**
     * Set the {@linkAsyncQueueWriter}
     * @param asyncQueueWriter {@linkAsyncQueueWriter}
     */
    protected void setAsyncQueueWriter(AsyncQueueWriter asyncQueueWriter) {
        this.asyncQueueWriter = asyncQueueWriter;
    }


    private class AsyncQueueReadableContextWrapper implements AsyncQueueReadable {

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
                AsyncReadCallbackHandler callbackHandler) throws IOException {
            return asyncQueueReader.read(key, buffer, callbackHandler);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
                AsyncReadCallbackHandler callbackHandler,
                AsyncReadCondition condition) throws IOException {
            return asyncQueueReader.read(key, buffer, callbackHandler, condition);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
                AsyncReadCallbackHandler callbackHandler,
                AsyncReadCondition condition,
                AsyncQueueDataProcessor readPostProcessor) throws IOException {
            return asyncQueueReader.read(key, buffer, callbackHandler,
                    condition, readPostProcessor);
        }
    }


    private class AsyncQueueWritableContextWrapper implements AsyncQueueWritable {

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
                ByteBuffer buffer) throws IOException {
            return asyncQueueWriter.write(key, buffer);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
                AsyncWriteCallbackHandler callbackHandler) throws IOException {
            return asyncQueueWriter.write(key, buffer, callbackHandler);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
                AsyncWriteCallbackHandler callbackHandler,
                AsyncQueueDataProcessor writePreProcessor) throws IOException {
            return asyncQueueWriter.write(key, buffer, callbackHandler,
                    writePreProcessor);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
                AsyncWriteCallbackHandler callbackHandler,
                AsyncQueueDataProcessor writePreProcessor,
                ByteBufferCloner cloner) throws IOException {
            return asyncQueueWriter.write(key, buffer, callbackHandler,
                    writePreProcessor, cloner);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
                SocketAddress dstAddress, ByteBuffer buffer)
                throws IOException {
            return asyncQueueWriter.write(key, dstAddress, buffer);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
                SocketAddress dstAddress, ByteBuffer buffer,
                AsyncWriteCallbackHandler callbackHandler) throws IOException {
            return asyncQueueWriter.write(key, dstAddress, buffer,
                    callbackHandler);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
                SocketAddress dstAddress, ByteBuffer buffer,
                AsyncWriteCallbackHandler callbackHandler,
                AsyncQueueDataProcessor writePreProcessor) throws IOException {
            return asyncQueueWriter.write(key, dstAddress, buffer,
                    callbackHandler, writePreProcessor);
        }

        /**
         * {@inheritDoc}
         */
        public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
                SocketAddress dstAddress, ByteBuffer buffer,
                AsyncWriteCallbackHandler callbackHandler,
                AsyncQueueDataProcessor writePreProcessor,
                ByteBufferCloner cloner)
                throws IOException {
            return asyncQueueWriter.write(key, dstAddress, buffer,
                    callbackHandler, writePreProcessor, cloner);
        }
    }


    /**
     * Suspend the execution of this {@link Context}. Suspending the execution
     * allow application to store the current instance, and re-use it later
     * by not only the  Thread used when called suspend, but also from any other Thread.
     * A suspended Context will not be re-used by any other transaction and Thread.
     * A suspended Context will keep its current state intact, meaning its
     * SelectionKey, attributes, SelectorHandler, etc, will not change. Internally,
     * The Context will not be recyled and will not be re-used by any Thread.
     *
     * When invoked this method will automatically set the
     * {@link Context#setKeyRegistrationState} to {@link KeyRegistrationState}
     * to KeyRegistrationState.NONE.
     *
     * Invoking this method many times as not effect once suspended.
     */
    public void suspend() {
        if (isSuspended) {
            return;
        }
        isSuspended = true;
        incrementRefCount();
        setKeyRegistrationState(keyRegistrationState.NONE);
    }


    /**
     * Return <tt>true</tt> if this Context has been suspended by
     * invoking {@link suspend}. When suspended, invoking {@link Context#recycle}
     * will throw an {@link IllegalStateException}
     * @return <tt>true</tt> if this Context has been suspended
     */
    public boolean isSuspended() {
        return isSuspended;
    }


    /**
     * Resume a {@link #suspend}ed {@link Context}.
     *  <strong>Resume will not call {@link Context#recycle}</strong>. So
     * after the caller is finished using Context caller must
     * call {@link  Controller#returnContext(com.sun.grizzly.Context)}
     * to  mark it as a candidate  for being re-used by another Thread and connection.
     *
     * <strong>Important. When resumed, all operations done on this
     * object are not thread-safe and there is probability that another
     * thread is already using this object. Never use this object once resumed.</strong>
     *
     * When invoked this method will automatically set the
     * {@link Context#setKeyRegistrationState} to {@link KeyRegistrationState}
     * to KeyRegistrationState.REGISTER and automatically re-enable read and
     * write operations.
     *
     * If the Context hasn't been suspended, calling that method has no effet.
     */
    public void resume() {
        if (!isSuspended) {
            return;
        }
        isSuspended = false;
        selectorHandler.register(key, SelectionKey.OP_READ);
    }


    /**
     * Cancel a {@link #suspend}ed {@link Context}. Invoking this method will
     * automatically clean the state of this Context and mark it as a candidate
     * for being re-used by another Thread and connection.
     *
     * <strong>Important. When cancelled, all operations done on this
     * object are not thread-safe and there is probability that another
     * thread is already using this object. Never use this object once cancelled.</strong>
     *
     *
     * When invoked this method will automatically close the underlying
     * connection (represented by its {@link SelectionKey}.
     *
     * If the Context hasn't been suspended, calling that method has no effet.
     */
    public void cancel() {
        if (!isSuspended) {
            return;
        }
        isSuspended = false;
        selectorHandler.getSelectionKeyHandler().cancel(key);
        getController().returnContext(this);
    }


    /**
     * Called by outer Threads that are not instances of {@link WorkerThread} to
     * indicate that this {@link Context} should not be
     * {@link #recycle()} or offered back to its pool.
     *
     * When a outer Thread is done with {@link Context} it must call
     * {@link Controller#returnContext(com.sun.grizzly.Context) to
     * ensure that {@link Context} will be properly recycled.
     *
     * @return Current Thread reference count
     */
    public void incrementRefCount() {
        refCounter.incrementAndGet();
    }


    /**
     * Decrements the reference count of this {@link Context}.
     * Threads wanting to release {@link Context} should not call
     * this method but instead use
     * {@link Controller#returnContext(com.sun.grizzly.Context)}
     * @return return decremented reference count
     */
    public int decrementRefCount() {
        return refCounter.decrementAndGet();
    }
}
